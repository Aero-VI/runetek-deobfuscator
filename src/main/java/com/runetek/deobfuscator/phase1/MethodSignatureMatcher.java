package com.runetek.deobfuscator.phase1;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Matches method signatures and bytecode patterns to suggest meaningful names.
 * Analyzes parameter types, return types, and instruction patterns.
 */
public class MethodSignatureMatcher {

    /**
     * Analyze methods in a class and suggest renames.
     * @return Map of "name+desc" to suggested name
     */
    // Methods from JDK classes that must never be renamed (could be overrides)
    private static final java.util.Set<String> JDK_OVERRIDE_METHODS = new java.util.HashSet<String>(
            java.util.Arrays.asList(
                // Runnable
                "run+()V",
                // Applet
                "init+()V", "start+()V", "stop+()V", "destroy+()V",
                // Component/Container
                "paint+(Ljava/awt/Graphics;)V", "update+(Ljava/awt/Graphics;)V",
                "repaint+()V",
                // Object
                "equals+(Ljava/lang/Object;)Z", "hashCode+()I", "toString+()Ljava/lang/String;",
                "clone+()Ljava/lang/Object;", "finalize+()V"
            ));

    public Map<String, String> analyzeMethods(ClassNode cn, String className) {
        Map<String, String> suggestions = new LinkedHashMap<String, String>();

        // Check if class extends JDK/external class (any super not in obfuscated set)
        boolean extendsExternal = cn.superName != null && (
                cn.superName.startsWith("java/") || cn.superName.startsWith("javax/") ||
                cn.superName.startsWith("sun/") || cn.superName.startsWith("com/sun/"));

        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("<init>") || mn.name.equals("<clinit>")) continue;
            if (!FieldPatternMatcher.isObfuscatedName(mn.name)) continue;

            // Don't rename methods that could override JDK methods
            String methodKey = mn.name + "+" + mn.desc;
            if (JDK_OVERRIDE_METHODS.contains(methodKey)) continue;

            // If the class extends an external class, be conservative — skip all methods
            // whose name+desc could be an override (we can't inspect JDK bytecode)
            if (extendsExternal && (mn.access & Opcodes.ACC_STATIC) == 0) continue;

            String suggested = suggestMethodName(mn, cn, className);
            if (suggested != null) {
                suggestions.put(methodKey, suggested);
            }
        }

        return suggestions;
    }

    private String suggestMethodName(MethodNode mn, ClassNode cn, String className) {
        String bySignature = matchBySignature(mn);
        if (bySignature != null) return bySignature;

        String byStrings = matchByStringConstants(mn);
        if (byStrings != null) return byStrings;

        String byInstructions = matchByInstructionPattern(mn);
        if (byInstructions != null) return byInstructions;

        return matchByClassContext(mn, className);
    }

    private String matchBySignature(MethodNode mn) {
        if (mn.desc.startsWith("()") && !mn.desc.equals("()V")) {
            if ((mn.access & Opcodes.ACC_STATIC) == 0 && instructionCount(mn) <= 5) {
                return "get" + capitalizeReturnType(mn.desc);
            }
        }

        if (mn.desc.matches("\\([A-Z]\\)V") || mn.desc.matches("\\(L[^;]+;\\)V")) {
            if ((mn.access & Opcodes.ACC_STATIC) == 0 && instructionCount(mn) <= 5) {
                return "setValue";
            }
        }

        if ("()I".equals(mn.desc) && hasMethodCall(mn, "read")) return "readUnsignedByte";
        if ("()Ljava/lang/String;".equals(mn.desc) && hasMethodCall(mn, "read")) return "readString";
        if ("(I)V".equals(mn.desc) && hasMethodCall(mn, "write")) return "writeByte";

        if (mn.desc.contains("Buffer") || mn.desc.contains("[B")) {
            if (mn.desc.endsWith("V")) return "decode";
        }

        return null;
    }

    private String matchByStringConstants(MethodNode mn) {
        if (mn.instructions == null) return null;

        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof LdcInsnNode) {
                LdcInsnNode ldc = (LdcInsnNode) insn;
                if (ldc.cst instanceof String) {
                    String str = ((String) ldc.cst).toLowerCase();
                    if (str.contains("login")) return "processLogin";
                    if (str.contains("error")) return "handleError";
                    if (str.contains("disconnect")) return "onDisconnect";
                    if (str.contains("loading")) return "loadResources";
                    if (str.contains("render")) return "render";
                    if (str.contains("draw")) return "draw";
                    if (str.contains("update")) return "update";
                }
            }
        }
        return null;
    }

    private String matchByInstructionPattern(MethodNode mn) {
        if (mn.instructions == null) return null;

        boolean hasArrayStore = false;
        boolean hasGraphicsCall = false;
        boolean hasSocketOp = false;
        int mathOps = 0;

        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == Opcodes.IASTORE || insn.getOpcode() == Opcodes.AASTORE) {
                hasArrayStore = true;
            }
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;
                if (methodInsn.owner.contains("Graphics") || methodInsn.owner.contains("Image")) {
                    hasGraphicsCall = true;
                }
                if (methodInsn.owner.contains("Socket") || methodInsn.owner.contains("Stream")) {
                    hasSocketOp = true;
                }
            }
            if (insn.getOpcode() >= Opcodes.IADD && insn.getOpcode() <= Opcodes.DREM) {
                mathOps++;
            }
        }

        if (hasGraphicsCall) return "renderGraphics";
        if (hasSocketOp && mn.desc.endsWith("V")) return "processNetworkData";
        if (mathOps > 10 && hasArrayStore) return "computeValues";

        return null;
    }

    private String matchByClassContext(MethodNode mn, String className) {
        if (className == null) return null;

        if ("Buffer".equals(className)) {
            if (mn.desc.startsWith("()") && !"()V".equals(mn.desc)) return "read";
            if (mn.desc.endsWith("V") && !"()V".equals(mn.desc)) return "write";
        } else if ("Node".equals(className)) {
            if ("()V".equals(mn.desc) && instructionCount(mn) < 10) return "unlink";
        } else if ("Model".equals(className)) {
            if (mn.desc.contains("III") && mn.desc.endsWith("V")) return "rotate";
        }
        return null;
    }

    private int instructionCount(MethodNode mn) {
        if (mn.instructions == null) return 0;
        int count = 0;
        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() >= 0) count++;
        }
        return count;
    }

    private boolean hasMethodCall(MethodNode mn, String nameFragment) {
        if (mn.instructions == null) return false;
        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;
                if (methodInsn.name.toLowerCase().contains(nameFragment.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String capitalizeReturnType(String desc) {
        String ret = desc.substring(desc.indexOf(')') + 1);
        if ("I".equals(ret)) return "Int";
        if ("J".equals(ret)) return "Long";
        if ("Z".equals(ret)) return "Boolean";
        if ("B".equals(ret)) return "Byte";
        if ("S".equals(ret)) return "Short";
        if ("F".equals(ret)) return "Float";
        if ("D".equals(ret)) return "Double";
        if ("Ljava/lang/String;".equals(ret)) return "String";
        return "Value";
    }
}
