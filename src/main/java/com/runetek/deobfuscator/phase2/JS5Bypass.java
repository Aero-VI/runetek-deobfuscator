package com.runetek.deobfuscator.phase2;

import com.runetek.deobfuscator.engine.TransformContext;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.Map;

/**
 * Bypasses the JS5 update server connection.
 *
 * RuneTek clients connect to a JS5 update server to download/verify cache files.
 * RSPS servers typically don't implement JS5, so patched clients skip this step.
 *
 * The patch works by finding the state machine transition that triggers the JS5
 * connection and modifying it to skip directly to the next phase (loading interfaces).
 *
 * Pattern: finds where the client sets the JS5 connection state (gameState = 5)
 * after setting the loading state to 10, and patches it to jump to state 30.
 */
public class JS5Bypass {

    /**
     * Apply the JS5 bypass to all classes in the context.
     * @return number of patches applied
     */
    public static int apply(TransformContext context) {
        int patches = 0;

        for (Map.Entry<String, ClassNode> entry : context.classes().entrySet()) {
            ClassNode cn = entry.getValue();
            for (MethodNode mn : cn.methods) {
                if (mn.instructions == null) continue;
                patches += patchMethod(mn, cn.name);
            }
        }

        return patches;
    }

    private static int patchMethod(MethodNode mn, String className) {
        int patches = 0;

        // Strategy: Find ALL "value 5 -> PUTSTATIC field:I" patterns where the same
        // field is also set to 10 elsewhere in the method (confirming it's the game state).
        // Then patch all the 5s to 10 (skip JS5, go to resource loading).

        // First: collect all static int fields that are set to both 5 and 10 in this method
        java.util.Map<String, java.util.Set<Integer>> fieldValues = new java.util.HashMap<String, java.util.Set<Integer>>();

        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() != Opcodes.PUTSTATIC) continue;
            FieldInsnNode fi = (FieldInsnNode) insn;
            if (!"I".equals(fi.desc)) continue;

            AbstractInsnNode prev = prevReal(insn);
            if (prev == null) continue;

            int val = getIntValue(prev);
            if (val < 0) continue;

            String key = fi.owner + "." + fi.name;
            java.util.Set<Integer> vals = fieldValues.get(key);
            if (vals == null) {
                vals = new java.util.HashSet<Integer>();
                fieldValues.put(key, vals);
            }
            vals.add(val);
        }

        // Find fields set to both 5 and 10 — that's the game state field
        String gameStateKey = null;
        for (java.util.Map.Entry<String, java.util.Set<Integer>> entry : fieldValues.entrySet()) {
            java.util.Set<Integer> vals = entry.getValue();
            if (vals.contains(5) && vals.contains(10)) {
                gameStateKey = entry.getKey();
                break;
            }
        }

        if (gameStateKey == null) return 0;

        String gsOwner = gameStateKey.substring(0, gameStateKey.indexOf('.'));
        String gsName = gameStateKey.substring(gameStateKey.indexOf('.') + 1);

        // Now patch ALL assignments of 5 to this field → change to 10
        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() != Opcodes.PUTSTATIC) continue;
            FieldInsnNode fi = (FieldInsnNode) insn;
            if (!fi.owner.equals(gsOwner) || !fi.name.equals(gsName)) continue;

            AbstractInsnNode prev = prevReal(insn);
            if (prev == null) continue;
            if (getIntValue(prev) != 5) continue;

            // Patch: 5 → 10
            replaceIntPush(mn, prev, 10);
            System.out.println("    [JS5 Bypass] Patched gameState 5→10 in " + className + "." + mn.name
                    + " (field " + gsOwner + "." + gsName + ")");
            patches++;
        }

        return patches;
    }

    private static int getIntValue(AbstractInsnNode insn) {
        if (insn.getOpcode() >= Opcodes.ICONST_0 && insn.getOpcode() <= Opcodes.ICONST_5) {
            return insn.getOpcode() - Opcodes.ICONST_0;
        }
        if (insn instanceof IntInsnNode && (insn.getOpcode() == Opcodes.BIPUSH || insn.getOpcode() == Opcodes.SIPUSH)) {
            return ((IntInsnNode) insn).operand;
        }
        return -1;
    }

    private static boolean isIntPush(AbstractInsnNode insn, int value) {
        if (insn.getOpcode() == Opcodes.BIPUSH && insn instanceof IntInsnNode) {
            return ((IntInsnNode) insn).operand == value;
        }
        if (insn.getOpcode() == Opcodes.SIPUSH && insn instanceof IntInsnNode) {
            return ((IntInsnNode) insn).operand == value;
        }
        if (value >= 0 && value <= 5) {
            return insn.getOpcode() == (Opcodes.ICONST_0 + value);
        }
        return false;
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

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        AbstractInsnNode next = insn.getNext();
        while (next != null && next.getOpcode() < 0) {
            next = next.getNext();
        }
        return next;
    }
}
