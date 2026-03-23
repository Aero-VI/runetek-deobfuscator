package com.aeroverra.deobfuscator.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;

public class OpaquePredicateRemover {

    public static int process(Map<String, ClassNode> classes) {
        int totalRemoved = 0;
        for (ClassNode cn : classes.values()) {
            for (MethodNode mn : cn.methods) {
                if (mn.instructions == null || mn.instructions.size() == 0) continue;
                totalRemoved += removeOpaquePredicates(mn);
            }
        }
        return totalRemoved;
    }

    private static int removeOpaquePredicates(MethodNode mn) {
        int removed = 0;

        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; ) {
            AbstractInsnNode next = insn.getNext();

            if (isPushConst(insn)) {
                int val = getConstValue(insn);
                AbstractInsnNode nextReal = nextRealInsn(insn);
                if (nextReal != null && nextReal.getOpcode() == Opcodes.IFEQ && val != 0) {
                    mn.instructions.remove(insn);
                    mn.instructions.remove(nextReal);
                    removed++;
                    insn = next;
                    continue;
                }
                if (nextReal != null && nextReal.getOpcode() == Opcodes.IFNE && val != 0) {
                    JumpInsnNode jump = (JumpInsnNode) nextReal;
                    mn.instructions.remove(insn);
                    mn.instructions.set(nextReal, new JumpInsnNode(Opcodes.GOTO, jump.label));
                    removed++;
                    insn = next;
                    continue;
                }
            }

            insn = next;
        }

        return removed;
    }

    private static boolean isPushConst(AbstractInsnNode insn) {
        int op = insn.getOpcode();
        return (op >= Opcodes.ICONST_M1 && op <= Opcodes.ICONST_5)
                || op == Opcodes.BIPUSH || op == Opcodes.SIPUSH
                || (op == Opcodes.LDC && insn instanceof LdcInsnNode
                    && ((LdcInsnNode) insn).cst instanceof Integer);
    }

    private static int getConstValue(AbstractInsnNode insn) {
        int op = insn.getOpcode();
        if (op >= Opcodes.ICONST_M1 && op <= Opcodes.ICONST_5) {
            return op - Opcodes.ICONST_0;
        }
        if (insn instanceof IntInsnNode) {
            return ((IntInsnNode) insn).operand;
        }
        if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Integer) {
            return (Integer) ((LdcInsnNode) insn).cst;
        }
        return 0;
    }

    private static AbstractInsnNode nextRealInsn(AbstractInsnNode insn) {
        AbstractInsnNode n = insn.getNext();
        while (n != null && n.getOpcode() < 0) n = n.getNext();
        return n;
    }
}
