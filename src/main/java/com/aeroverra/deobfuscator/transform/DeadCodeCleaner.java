package com.aeroverra.deobfuscator.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;

public class DeadCodeCleaner {

    public static int clean(Map<String, ClassNode> classes) {
        int cleaned = 0;
        for (ClassNode cn : classes.values()) {
            for (MethodNode mn : cn.methods) {
                if (mn.instructions == null || mn.instructions.size() == 0) continue;
                if (cleanMethod(mn)) cleaned++;
            }
        }
        return cleaned;
    }

    private static boolean cleanMethod(MethodNode mn) {
        boolean changed = false;

        Set<LabelNode> jumpTargets = new HashSet<>();
        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof JumpInsnNode) {
                jumpTargets.add(((JumpInsnNode) insn).label);
            }
            if (insn instanceof TableSwitchInsnNode) {
                TableSwitchInsnNode tsn = (TableSwitchInsnNode) insn;
                jumpTargets.add(tsn.dflt);
                jumpTargets.addAll(tsn.labels);
            }
            if (insn instanceof LookupSwitchInsnNode) {
                LookupSwitchInsnNode lsn = (LookupSwitchInsnNode) insn;
                jumpTargets.add(lsn.dflt);
                jumpTargets.addAll(lsn.labels);
            }
        }
        for (TryCatchBlockNode tcb : mn.tryCatchBlocks) {
            jumpTargets.add(tcb.handler);
            jumpTargets.add(tcb.start);
            jumpTargets.add(tcb.end);
        }

        List<AbstractInsnNode> toRemove = new ArrayList<>();
        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == Opcodes.NOP) {
                toRemove.add(insn);
            }
        }
        for (AbstractInsnNode insn : toRemove) {
            mn.instructions.remove(insn);
            changed = true;
        }

        return changed;
    }
}
