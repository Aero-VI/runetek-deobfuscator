package com.runetek.deobfuscator.phase2;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.List;

/**
 * ASM-based method transformer that injects hook calls into methods.
 * Supports method entry/exit hooks and field get/set interception.
 */
public class MethodHookVisitor {

    /**
     * Inject entry hooks at the beginning of a method.
     */
    public static int injectEntryHooks(MethodNode mn, List<HookDefinition> hooks) {
        int count = 0;
        for (HookDefinition hook : hooks) {
            if (hook.type() != HookDefinition.HookType.METHOD_ENTRY) continue;

            InsnList injection = buildEventFireCall(hook.name());

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
     * Inject field get hooks after GETFIELD/GETSTATIC instructions.
     */
    public static int injectFieldGetHooks(MethodNode mn, String targetField,
                                           String targetDesc, List<HookDefinition> hooks) {
        int count = 0;
        for (HookDefinition hook : hooks) {
            if (hook.type() != HookDefinition.HookType.FIELD_GET) continue;

            for (int i = mn.instructions.size() - 1; i >= 0; i--) {
                AbstractInsnNode insn = mn.instructions.get(i);
                if (insn instanceof FieldInsnNode) {
                    FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                    if ((insn.getOpcode() == Opcodes.GETFIELD || insn.getOpcode() == Opcodes.GETSTATIC)
                            && fieldInsn.name.equals(targetField)
                            && fieldInsn.desc.equals(targetDesc)) {

                        InsnList injection = new InsnList();
                        injection.add(getDupInsn(targetDesc));
                        addBoxingInsns(injection, targetDesc);
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
     * Inject field set hooks before PUTFIELD/PUTSTATIC instructions.
     */
    public static int injectFieldSetHooks(MethodNode mn, String targetField,
                                           String targetDesc, List<HookDefinition> hooks) {
        int count = 0;
        for (HookDefinition hook : hooks) {
            if (hook.type() != HookDefinition.HookType.FIELD_SET) continue;

            for (int i = mn.instructions.size() - 1; i >= 0; i--) {
                AbstractInsnNode insn = mn.instructions.get(i);
                if (insn instanceof FieldInsnNode) {
                    FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                    if ((insn.getOpcode() == Opcodes.PUTFIELD || insn.getOpcode() == Opcodes.PUTSTATIC)
                            && fieldInsn.name.equals(targetField)
                            && fieldInsn.desc.equals(targetDesc)) {

                        InsnList injection = new InsnList();
                        injection.add(getDupInsn(targetDesc));
                        addBoxingInsns(injection, targetDesc);
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

    private static InsnList buildEventFireCall(String eventName) {
        InsnList insns = new InsnList();
        insns.add(new LdcInsnNode(eventName));
        insns.add(new InsnNode(Opcodes.ICONST_0));
        insns.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        insns.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                EventBus.INTERNAL_NAME,
                "fire",
                EventBus.FIRE_DESCRIPTOR,
                false));
        return insns;
    }

    private static InsnList buildEventFireCallWithTopOfStack(String eventName) {
        InsnList insns = new InsnList();
        insns.add(new InsnNode(Opcodes.ICONST_1));
        insns.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        insns.add(new InsnNode(Opcodes.DUP_X1));
        insns.add(new InsnNode(Opcodes.SWAP));
        insns.add(new InsnNode(Opcodes.ICONST_0));
        insns.add(new InsnNode(Opcodes.SWAP));
        insns.add(new InsnNode(Opcodes.AASTORE));
        insns.add(new LdcInsnNode(eventName));
        insns.add(new InsnNode(Opcodes.SWAP));
        insns.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                EventBus.INTERNAL_NAME,
                "fire",
                EventBus.FIRE_DESCRIPTOR,
                false));
        return insns;
    }

    private static AbstractInsnNode getDupInsn(String desc) {
        if ("J".equals(desc) || "D".equals(desc)) {
            return new InsnNode(Opcodes.DUP2);
        }
        return new InsnNode(Opcodes.DUP);
    }

    private static void addBoxingInsns(InsnList insns, String desc) {
        if ("I".equals(desc)) {
            insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
        } else if ("J".equals(desc)) {
            insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false));
        } else if ("F".equals(desc)) {
            insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false));
        } else if ("D".equals(desc)) {
            insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false));
        } else if ("Z".equals(desc)) {
            insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false));
        } else if ("B".equals(desc)) {
            insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false));
        } else if ("S".equals(desc)) {
            insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false));
        } else if ("C".equals(desc)) {
            insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false));
        }
        // Reference types: already Object, no boxing needed
    }

    private static boolean isReturnInstruction(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        return opcode == Opcodes.RETURN || opcode == Opcodes.IRETURN ||
               opcode == Opcodes.LRETURN || opcode == Opcodes.FRETURN ||
               opcode == Opcodes.DRETURN || opcode == Opcodes.ARETURN;
    }
}
