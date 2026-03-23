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

        // Strategy 1: Find BIPUSH 10 followed by PUTSTATIC to the state field,
        // then look for the nearby BIPUSH 5 PUTSTATIC to the game state field.
        // Patch the 10 to 30 (skip JS5, go straight to interface loading).

        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            // Look for pattern: BIPUSH 10 -> PUTSTATIC -> ... -> BIPUSH 5 -> PUTSTATIC
            // This is: Widget92.intField4 = 10; ... Widget87.intField0 = 5;
            if (!isIntPush(insn, 10)) continue;

            AbstractInsnNode next1 = nextReal(insn);
            if (!(next1 instanceof FieldInsnNode)) continue;
            if (next1.getOpcode() != Opcodes.PUTSTATIC) continue;
            FieldInsnNode stateField = (FieldInsnNode) next1;
            if (!"I".equals(stateField.desc)) continue;

            // Now scan forward a few instructions for BIPUSH 5 -> PUTSTATIC
            AbstractInsnNode scan = next1;
            boolean foundJS5Trigger = false;
            for (int i = 0; i < 8; i++) {
                scan = nextReal(scan);
                if (scan == null) break;

                if (isIntPush(scan, 5)) {
                    AbstractInsnNode afterFive = nextReal(scan);
                    if (afterFive instanceof FieldInsnNode && afterFive.getOpcode() == Opcodes.PUTSTATIC) {
                        FieldInsnNode gameStateField = (FieldInsnNode) afterFive;
                        if ("I".equals(gameStateField.desc)) {
                            foundJS5Trigger = true;
                            break;
                        }
                    }
                }
            }

            if (foundJS5Trigger) {
                // Patch: change the state from 10 to 30 (skip JS5)
                replaceIntPush(mn, insn, 30);
                System.out.println("    [JS5 Bypass] Patched state 10→30 in " + className + "." + mn.name + mn.desc);
                patches++;
            }
        }

        // Strategy 2: Find and NOP out the JS5 socket connection calls
        // Look for string constants "js5connect", "js5io", "js5crc" and replace
        // the error handler to just retry/continue
        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof LdcInsnNode) {
                LdcInsnNode ldc = (LdcInsnNode) insn;
                if (ldc.cst instanceof String) {
                    String str = (String) ldc.cst;
                    if ("js5connect".equals(str) || "js5io".equals(str) || "js5crc".equals(str) ||
                        "js5connect_outofdate".equals(str) || "js5connect_full".equals(str)) {
                        // Don't remove these — just log that we found them
                        // The state patch above is what actually fixes the issue
                    }
                }
            }
        }

        return patches;
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

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        AbstractInsnNode next = insn.getNext();
        while (next != null && next.getOpcode() < 0) {
            next = next.getNext();
        }
        return next;
    }
}
