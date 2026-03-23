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
     * @return Map of "name+desc" → suggested name
     */
    public Map<String, String> analyzeMethods(ClassNode cn, String className) {
        Map<String, String> suggestions = new LinkedHashMap<>();

        for (MethodNode mn : cn.methods) {
            // Skip constructors and static initializers
            if (mn.name.equals("<init>") || mn.name.equals("<clinit>")) continue;
            // Skip already-meaningful names
            if (!FieldPatternMatcher.isObfuscatedName(mn.name)) continue;

            String suggested = suggestMethodName(mn, cn, className);
            if (suggested != null) {
                suggestions.put(mn.name + "+" + mn.desc, suggested);
            }
        }

        return suggestions;
    }

    private String suggestMethodName(MethodNode mn, ClassNode cn, String className) {
        // 1. Check for known signature patterns
        String bySignature = matchBySignature(mn);
        if (bySignature != null) return bySignature;

        // 2. Check for string constants that hint at purpose
        String byStrings = matchByStringConstants(mn);
        if (byStrings != null) return byStrings;

        // 3. Check for instruction patterns
        String byInstructions = matchByInstructionPattern(mn);
        if (byInstructions != null) return byInstructions;

        // 4. Context-aware based on class type
        return matchByClassContext(mn, className);
    }

    private String matchBySignature(MethodNode mn) {
        // Common getter/setter patterns
        if (mn.desc.startsWith("()") && !mn.desc.equals("()V")) {
            // No args, returns something = likely getter
            if ((mn.access & Opcodes.ACC_STATIC) == 0 && instructionCount(mn) <= 5) {
                return "get" + capitalizeReturnType(mn.desc);
            }
        }

        // void(X) with single field store = likely setter
        if (mn.desc.matches("\\([A-Z]\\)V") || mn.desc.matches("\\(L[^;]+;\\)V")) {
            if ((mn.access & Opcodes.ACC_STATIC) == 0 && instructionCount(mn) <= 5) {
                return "setValue";
            }
        }

        // Buffer read patterns
        if (mn.desc.equals("()I") && hasMethodCall(mn, "read")) return "readUnsignedByte";
        if (mn.desc.equals("()Ljava/lang/String;") && hasMethodCall(mn, "read")) return "readString";

        // Buffer write patterns
        if (mn.desc.equals("(I)V") && hasMethodCall(mn, "write")) return "writeByte";

        // Decode/encode patterns
        if (mn.desc.contains("Buffer") || mn.desc.contains("[B")) {
            if (mn.desc.endsWith("V")) return "decode";
        }

        return null;
    }

    private String matchByStringConstants(MethodNode mn) {
        if (mn.instructions == null) return null;

        for (AbstractInsnNode insn : mn.instructions) {
            if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String str) {
                str = str.toLowerCase();
                if (str.contains("login")) return "processLogin";
                if (str.contains("error")) return "handleError";
                if (str.contains("disconnect")) return "onDisconnect";
                if (str.contains("loading")) return "loadResources";
                if (str.contains("render")) return "render";
                if (str.contains("draw")) return "draw";
                if (str.contains("update")) return "update";
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

        for (AbstractInsnNode insn : mn.instructions) {
            if (insn.getOpcode() == Opcodes.IASTORE || insn.getOpcode() == Opcodes.AASTORE) {
                hasArrayStore = true;
            }
            if (insn instanceof MethodInsnNode methodInsn) {
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

        return switch (className) {
            case "Buffer" -> {
                if (mn.desc.startsWith("()") && !mn.desc.equals("()V")) yield "read";
                if (mn.desc.endsWith("V") && !mn.desc.equals("()V")) yield "write";
                yield null;
            }
            case "Node" -> {
                if (mn.desc.equals("()V") && instructionCount(mn) < 10) yield "unlink";
                yield null;
            }
            case "Model" -> {
                if (mn.desc.contains("III") && mn.desc.endsWith("V")) yield "rotate";
                yield null;
            }
            default -> null;
        };
    }

    private int instructionCount(MethodNode mn) {
        if (mn.instructions == null) return 0;
        int count = 0;
        for (AbstractInsnNode insn : mn.instructions) {
            if (insn.getOpcode() >= 0) count++;
        }
        return count;
    }

    private boolean hasMethodCall(MethodNode mn, String nameFragment) {
        if (mn.instructions == null) return false;
        for (AbstractInsnNode insn : mn.instructions) {
            if (insn instanceof MethodInsnNode methodInsn) {
                if (methodInsn.name.toLowerCase().contains(nameFragment.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String capitalizeReturnType(String desc) {
        String ret = desc.substring(desc.indexOf(')') + 1);
        return switch (ret) {
            case "I" -> "Int";
            case "J" -> "Long";
            case "Z" -> "Boolean";
            case "B" -> "Byte";
            case "S" -> "Short";
            case "F" -> "Float";
            case "D" -> "Double";
            case "Ljava/lang/String;" -> "String";
            default -> "Value";
        };
    }
}
