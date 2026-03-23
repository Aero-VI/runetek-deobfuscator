package com.runetek.deobfuscator.phase1;

import org.objectweb.asm.tree.*;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Fixes Class.forName("obfuscated") string constants after renaming.
 *
 * ZKM obfuscator inserts Class.forName() calls using both direct calls
 * and wrapper methods like:
 *   static Class a(String s) { return Class.forName(s); }
 *
 * This pass:
 * 1. Identifies all wrapper methods that call Class.forName
 * 2. Finds LDC strings followed by calls to Class.forName or its wrappers
 * 3. Replaces the old class name string with the new name
 */
public class ClassForNameFixer {

    public static int fix(Map<String, ClassNode> classes, Map<String, String> classNameMap) {
        // Phase 1: Find all wrapper methods that call Class.forName
        Set<String> forNameWrappers = new HashSet<String>();
        // Always include the direct call
        forNameWrappers.add("java/lang/Class.forName");

        for (ClassNode cn : classes.values()) {
            for (MethodNode mn : cn.methods) {
                if (mn.desc.equals("(Ljava/lang/String;)Ljava/lang/Class;") && isForNameWrapper(mn)) {
                    forNameWrappers.add(cn.name + "." + mn.name);
                }
            }
        }

        // Phase 2: Fix all LDC + forName/wrapper call patterns
        int fixes = 0;
        for (ClassNode cn : classes.values()) {
            for (MethodNode mn : cn.methods) {
                if (mn.instructions == null) continue;
                fixes += fixMethod(mn, classNameMap, forNameWrappers);
            }
        }

        return fixes;
    }

    /**
     * Check if a method is a simple wrapper around Class.forName.
     */
    private static boolean isForNameWrapper(MethodNode mn) {
        if (mn.instructions == null) return false;

        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode mi = (MethodInsnNode) insn;
                if ("java/lang/Class".equals(mi.owner) && "forName".equals(mi.name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int fixMethod(MethodNode mn, Map<String, String> classNameMap, Set<String> forNameWrappers) {
        int fixes = 0;

        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof LdcInsnNode)) continue;

            LdcInsnNode ldc = (LdcInsnNode) insn;
            if (!(ldc.cst instanceof String)) continue;

            String str = (String) ldc.cst;
            if (!classNameMap.containsKey(str)) continue;

            // Check if this string flows into Class.forName or a wrapper
            AbstractInsnNode next = nextReal(insn);

            // Pattern 1: LDC "classname" -> INVOKESTATIC Class.forName/wrapper
            if (next instanceof MethodInsnNode) {
                MethodInsnNode mi = (MethodInsnNode) next;
                String methodKey = mi.owner + "." + mi.name;
                if (forNameWrappers.contains(methodKey)) {
                    ldc.cst = classNameMap.get(str);
                    fixes++;
                    continue;
                }
            }

            // Pattern 2: LDC "classname" -> ... -> INVOKESTATIC (with some instructions between)
            if (isConsumedByForName(insn, forNameWrappers, 8)) {
                ldc.cst = classNameMap.get(str);
                fixes++;
            }
        }

        return fixes;
    }

    /**
     * Check if an LDC string is consumed by Class.forName or a wrapper within N instructions.
     */
    private static boolean isConsumedByForName(AbstractInsnNode insn, Set<String> forNameWrappers, int maxDistance) {
        AbstractInsnNode current = insn;
        for (int i = 0; i < maxDistance; i++) {
            current = nextReal(current);
            if (current == null) return false;

            if (current instanceof MethodInsnNode) {
                MethodInsnNode mi = (MethodInsnNode) current;
                String methodKey = mi.owner + "." + mi.name;
                if (forNameWrappers.contains(methodKey)) {
                    return true;
                }
                // If the method takes a String arg and returns Class, it might be a wrapper
                if (mi.desc.equals("(Ljava/lang/String;)Ljava/lang/Class;")) {
                    return true;
                }
                // Hit a non-forName method call — stop looking
                if (!mi.desc.startsWith("()")) {
                    return false;
                }
            }

            // If we hit a branch/jump, stop
            if (current instanceof JumpInsnNode) return false;
        }
        return false;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        AbstractInsnNode next = insn.getNext();
        while (next != null && next.getOpcode() < 0) {
            next = next.getNext();
        }
        return next;
    }
}
