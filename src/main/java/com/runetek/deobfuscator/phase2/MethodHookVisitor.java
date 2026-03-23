package com.runetek.deobfuscator.phase2;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.List;

/**
 * ASM-based method transformer that injects hook calls into methods.
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

    private static boolean isReturnInstruction(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        return opcode == Opcodes.RETURN || opcode == Opcodes.IRETURN ||
               opcode == Opcodes.LRETURN || opcode == Opcodes.FRETURN ||
               opcode == Opcodes.DRETURN || opcode == Opcodes.ARETURN;
    }
}
