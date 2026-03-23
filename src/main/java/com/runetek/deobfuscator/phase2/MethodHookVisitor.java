package com.runetek.deobfuscator.phase2;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.List;

/**
 * ASM-based method transformer that injects hook calls into methods.
 * Supports method entry/exit hooks and field get/set interception.
 * Works at the InsnList level for precise control over injection points.
 */
public class MethodHookVisitor {

    /**
     * Inject entry hooks at the beginning of a method.
     * Inserts: EventBus.fire("hookName", new Object[]{arg0, arg1, ...});
     */
    public static int injectEntryHooks(MethodNode mn, List<HookDefinition> hooks) {
        int count = 0;
        for (HookDefinition hook : hooks) {
            if (hook.type() != HookDefinition.HookType.METHOD_ENTRY) continue;

            InsnList injection = buildEventFireCall(hook.name());

            // Insert at the very beginning of the method
            if (mn.instructions.size() > 0) {
                mn.instructions.insert(mn.instructions.getFirst(), injection);
            } else {
                mn.instructions.add(injection);
            }
            count++;
        }
        return count;
    }

    /**
     * Inject exit hooks before every RETURN instruction.
     */
    public static int injectExitHooks(MethodNode mn, List<HookDefinition> hooks) {
        int count = 0;
        for (HookDefinition hook : hooks) {
            if (hook.type() != HookDefinition.HookType.METHOD_EXIT) continue;

            // Find all return instructions
            for (int i = mn.instructions.size() - 1; i >= 0; i--) {
                AbstractInsnNode insn = mn.instructions.get(i);
                if (isReturnInstruction(insn)) {
                    InsnList injection = buildEventFireCall(hook.name());
                    mn.instructions.insertBefore(insn, injection);
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Inject field get hooks — wraps GETFIELD/GETSTATIC instructions for the target
     * field with an EventBus.fire() call that passes the field value.
     *
     * After each matching GETFIELD/GETSTATIC, injects:
     *   EventBus.fire("hookName", new Object[]{ &lt;field-value-on-stack&gt; });
     *
     * The field value remains on the stack for the original consumer.
     */
    public static int injectFieldGetHooks(MethodNode mn, String targetField,
                                           String targetDesc, List<HookDefinition> hooks) {
        int count = 0;
        for (HookDefinition hook : hooks) {
            if (hook.type() != HookDefinition.HookType.FIELD_GET) continue;

            for (int i = mn.instructions.size() - 1; i >= 0; i--) {
                AbstractInsnNode insn = mn.instructions.get(i);
                if (insn instanceof FieldInsnNode fieldInsn) {
                    if ((insn.getOpcode() == Opcodes.GETFIELD || insn.getOpcode() == Opcodes.GETSTATIC)
                            && fieldInsn.name.equals(targetField)
                            && fieldInsn.desc.equals(targetDesc)) {

                        // After the GETFIELD, the value is on the stack.
                        // We duplicate it, box if primitive, then fire the event.
                        InsnList injection = new InsnList();

                        // DUP the value (so original consumer still gets it)
                        injection.add(getDupInsn(targetDesc));

                        // Box if primitive
                        addBoxingInsns(injection, targetDesc);

                        // Build: EventBus.fire("hookName", new Object[]{ value })
                        // We need to wrap the boxed value in an Object[]
                        InsnList fireCall = buildEventFireCallWithTopOfStack(hook.name());
                        injection.add(fireCall);

                        mn.instructions.insert(insn, injection);
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Inject field set hooks — before each PUTFIELD/PUTSTATIC for the target field,
     * fires an event with the value being written.
     *
     * Before each matching PUTFIELD/PUTSTATIC, injects:
     *   EventBus.fire("hookName", new Object[]{ &lt;new-value&gt; });
     *
     * The set operation proceeds normally after the hook fires.
     */
    public static int injectFieldSetHooks(MethodNode mn, String targetField,
                                           String targetDesc, List<HookDefinition> hooks) {
        int count = 0;
        for (HookDefinition hook : hooks) {
            if (hook.type() != HookDefinition.HookType.FIELD_SET) continue;

            for (int i = mn.instructions.size() - 1; i >= 0; i--) {
                AbstractInsnNode insn = mn.instructions.get(i);
                if (insn instanceof FieldInsnNode fieldInsn) {
                    if ((insn.getOpcode() == Opcodes.PUTFIELD || insn.getOpcode() == Opcodes.PUTSTATIC)
                            && fieldInsn.name.equals(targetField)
                            && fieldInsn.desc.equals(targetDesc)) {

                        // Before PUTFIELD, the stack has: ..., objectref, value
                        // We DUP the value, box it, fire the event, then let PUTFIELD proceed
                        InsnList injection = new InsnList();

                        // DUP the value (under objectref for PUTFIELD, on top for PUTSTATIC)
                        injection.add(getDupInsn(targetDesc));

                        // Box if primitive
                        addBoxingInsns(injection, targetDesc);

                        // Fire the event with the value
                        InsnList fireCall = buildEventFireCallWithTopOfStack(hook.name());
                        injection.add(fireCall);

                        mn.instructions.insertBefore(insn, injection);
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Build an InsnList that calls EventBus.fire(eventName, new Object[0]).
     */
    private static InsnList buildEventFireCall(String eventName) {
        InsnList insns = new InsnList();

        // Push event name
        insns.add(new LdcInsnNode(eventName));

        // Push empty Object array (no args for now)
        insns.add(new InsnNode(Opcodes.ICONST_0));
        insns.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));

        // Call EventBus.fire(String, Object[])
        insns.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                EventBus.INTERNAL_NAME,
                "fire",
                EventBus.FIRE_DESCRIPTOR,
                false));

        return insns;
    }

    /**
     * Build an InsnList that takes a boxed value already on top of the stack,
     * wraps it in a single-element Object[], and calls EventBus.fire(eventName, args).
     */
    private static InsnList buildEventFireCallWithTopOfStack(String eventName) {
        InsnList insns = new InsnList();

        // Stack: ..., boxedValue
        // Create Object[1] and store the value
        insns.add(new InsnNode(Opcodes.ICONST_1));
        insns.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        // Stack: ..., boxedValue, array
        insns.add(new InsnNode(Opcodes.DUP_X1));
        // Stack: ..., array, boxedValue, array
        insns.add(new InsnNode(Opcodes.SWAP));
        // Stack: ..., array, array, boxedValue
        insns.add(new InsnNode(Opcodes.ICONST_0));
        // Stack: ..., array, array, boxedValue, 0
        insns.add(new InsnNode(Opcodes.SWAP));
        // Stack: ..., array, array, 0, boxedValue
        insns.add(new InsnNode(Opcodes.AASTORE));
        // Stack: ..., array  (with boxedValue at index 0)

        // Push event name and swap with array
        insns.add(new LdcInsnNode(eventName));
        // Stack: ..., array, eventName
        insns.add(new InsnNode(Opcodes.SWAP));
        // Stack: ..., eventName, array

        // Call EventBus.fire(String, Object[])
        insns.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                EventBus.INTERNAL_NAME,
                "fire",
                EventBus.FIRE_DESCRIPTOR,
                false));

        return insns;
    }

    /**
     * Get the appropriate DUP instruction for a given field descriptor.
     * Long and double take two slots, everything else takes one.
     */
    private static AbstractInsnNode getDupInsn(String desc) {
        return switch (desc) {
            case "J", "D" -> new InsnNode(Opcodes.DUP2);
            default -> new InsnNode(Opcodes.DUP);
        };
    }

    /**
     * Add boxing instructions to convert a primitive on top of the stack to its wrapper type.
     */
    private static void addBoxingInsns(InsnList insns, String desc) {
        switch (desc) {
            case "I" -> insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
            case "J" -> insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false));
            case "F" -> insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false));
            case "D" -> insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false));
            case "Z" -> insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false));
            case "B" -> insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false));
            case "S" -> insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false));
            case "C" -> insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false));
            // Reference types — already an Object, no boxing needed
            default -> { }
        }
    }

    private static boolean isReturnInstruction(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        return opcode == Opcodes.RETURN || opcode == Opcodes.IRETURN ||
               opcode == Opcodes.LRETURN || opcode == Opcodes.FRETURN ||
               opcode == Opcodes.DRETURN || opcode == Opcodes.ARETURN;
    }
}
