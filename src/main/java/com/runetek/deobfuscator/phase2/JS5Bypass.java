package com.runetek.deobfuscator.phase2;

import com.runetek.deobfuscator.engine.TransformContext;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.Map;

/**
 * Bypasses the JS5 update server connection.
 *
 * Strategy: find methods containing JS5 strings ("js5io", "js5connect") and
 * patch all error state assignments (1000) to 25 (login screen state).
 * This lets the JS5 code attempt to run (initializing resources), but when
 * the connection inevitably fails, instead of crashing it proceeds to login.
 */
public class JS5Bypass {

    public static int apply(TransformContext context) {
        int patches = 0;

        for (Map.Entry<String, ClassNode> entry : context.classes().entrySet()) {
            ClassNode cn = entry.getValue();
            for (MethodNode mn : cn.methods) {
                if (mn.instructions == null) continue;
                patches += patchJS5ErrorStates(mn, cn.name, "js5io");
                patches += patchJS5ErrorStates(mn, cn.name, "js5connect");
                patches += patchJS5ErrorStates(mn, cn.name, "js5crc");
            }
        }

        return patches;
    }

    /**
     * Find methods containing the given JS5 marker string and patch all
     * error state (1000) assignments to 25 (login screen).
     */
    private static int patchJS5ErrorStates(MethodNode mn, String className, String marker) {
        boolean found = false;
        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof LdcInsnNode && marker.equals(((LdcInsnNode) insn).cst)) {
                found = true;
                break;
            }
        }
        if (!found) return 0;

        int patched = 0;
        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() != Opcodes.PUTSTATIC) continue;
            FieldInsnNode fi = (FieldInsnNode) insn;
            if (!"I".equals(fi.desc)) continue;

            AbstractInsnNode prev = prevReal(insn);
            if (prev == null) continue;
            if (getIntValue(prev) != 1000) continue;

            // Change error state 1000 → 25 (login screen)
            replaceIntPush(mn, prev, 25);
            patched++;
            System.out.println("    [JS5 Bypass] Patched error 1000→25 in " + className
                    + "." + mn.name + " (field " + fi.owner + "." + fi.name + ", marker: " + marker + ")");
        }
        return patched;
    }

    private static int getIntValue(AbstractInsnNode insn) {
        if (insn.getOpcode() >= Opcodes.ICONST_0 && insn.getOpcode() <= Opcodes.ICONST_5) {
            return insn.getOpcode() - Opcodes.ICONST_0;
        }
        if (insn instanceof IntInsnNode && (insn.getOpcode() == Opcodes.BIPUSH || insn.getOpcode() == Opcodes.SIPUSH)) {
            return ((IntInsnNode) insn).operand;
        }
        if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Integer) {
            return (Integer) ((LdcInsnNode) insn).cst;
        }
        return -1;
    }

    private static void replaceIntPush(MethodNode mn, AbstractInsnNode insn, int newValue) {
        IntInsnNode replacement = new IntInsnNode(Opcodes.BIPUSH, newValue);
        mn.instructions.set(insn, replacement);
    }

    private static AbstractInsnNode prevReal(AbstractInsnNode insn) {
        AbstractInsnNode prev = insn.getPrevious();
        while (prev != null && prev.getOpcode() < 0) {
            prev = prev.getPrevious();
        }
        return prev;
    }
}
