package com.runetek.deobfuscator.phase1;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Matches field patterns based on type, access modifiers, and context
 * to suggest meaningful field names.
 */
public class FieldPatternMatcher {

    /**
     * Analyze fields in a class and suggest renames.
     * @return Map of original field name → suggested name
     */
    public Map<String, String> analyzeFields(ClassNode cn, String className) {
        Map<String, String> suggestions = new LinkedHashMap<>();

        int intIndex = 0;
        int stringIndex = 0;
        int boolIndex = 0;
        int arrayIndex = 0;

        for (FieldNode fn : cn.fields) {
            String suggested = suggestFieldName(fn, className, intIndex, stringIndex, boolIndex, arrayIndex);
            if (suggested != null && isObfuscatedName(fn.name)) {
                suggestions.put(fn.name, suggested);
            }

            // Track type counts for context
            switch (fn.desc) {
                case "I", "J" -> intIndex++;
                case "Ljava/lang/String;" -> stringIndex++;
                case "Z" -> boolIndex++;
                default -> { if (fn.desc.startsWith("[")) arrayIndex++; }
            }
        }

        return suggestions;
    }

    private String suggestFieldName(FieldNode fn, String className, int intIdx, int strIdx, int boolIdx, int arrIdx) {
        // Context-aware naming based on parent class type
        return switch (className) {
            case "Widget" -> suggestWidgetField(fn, intIdx, strIdx, boolIdx);
            case "Buffer" -> suggestBufferField(fn, intIdx);
            case "Node" -> suggestNodeField(fn, intIdx);
            case "Model" -> suggestModelField(fn, intIdx, arrIdx);
            case "Client" -> suggestClientField(fn, intIdx, strIdx);
            default -> suggestGenericField(fn, intIdx, strIdx, boolIdx);
        };
    }

    private String suggestWidgetField(FieldNode fn, int intIdx, int strIdx, int boolIdx) {
        if (fn.desc.equals("I")) {
            return switch (intIdx) {
                case 0 -> "id";
                case 1 -> "parentId";
                case 2 -> "type";
                case 3 -> "contentType";
                case 4 -> "x";
                case 5 -> "y";
                case 6 -> "width";
                case 7 -> "height";
                case 8 -> "opacity";
                default -> "intField" + intIdx;
            };
        }
        if (fn.desc.equals("Ljava/lang/String;")) {
            return switch (strIdx) {
                case 0 -> "text";
                case 1 -> "tooltip";
                default -> "stringField" + strIdx;
            };
        }
        if (fn.desc.equals("Z")) {
            return switch (boolIdx) {
                case 0 -> "isHidden";
                case 1 -> "isDraggable";
                default -> "boolField" + boolIdx;
            };
        }
        return null;
    }

    private String suggestBufferField(FieldNode fn, int intIdx) {
        if (fn.desc.equals("[B")) return "payload";
        if (fn.desc.equals("I")) {
            return intIdx == 0 ? "position" : "capacity";
        }
        return null;
    }

    private String suggestNodeField(FieldNode fn, int intIdx) {
        String selfDesc = "L" + fn.name + ";";
        if (fn.desc.contains(";") && !fn.desc.equals("Ljava/lang/String;")) {
            return intIdx == 0 ? "next" : "previous";
        }
        if (fn.desc.equals("J")) return "key";
        return null;
    }

    private String suggestModelField(FieldNode fn, int intIdx, int arrIdx) {
        if (fn.desc.equals("[I")) {
            return switch (arrIdx) {
                case 0 -> "verticesX";
                case 1 -> "verticesY";
                case 2 -> "verticesZ";
                case 3 -> "triangleA";
                case 4 -> "triangleB";
                case 5 -> "triangleC";
                case 6 -> "faceColors";
                default -> "intArray" + arrIdx;
            };
        }
        if (fn.desc.equals("I")) {
            return switch (intIdx) {
                case 0 -> "vertexCount";
                case 1 -> "triangleCount";
                default -> "intField" + intIdx;
            };
        }
        return null;
    }

    private String suggestClientField(FieldNode fn, int intIdx, int strIdx) {
        if (fn.desc.equals("I")) {
            return switch (intIdx) {
                case 0 -> "gameState";
                case 1 -> "loginState";
                case 2 -> "worldId";
                default -> "clientInt" + intIdx;
            };
        }
        if (fn.desc.equals("Ljava/lang/String;")) {
            return switch (strIdx) {
                case 0 -> "username";
                case 1 -> "password";
                default -> "clientString" + strIdx;
            };
        }
        return null;
    }

    private String suggestGenericField(FieldNode fn, int intIdx, int strIdx, int boolIdx) {
        // Generic fallback: give type-based sequential names
        return switch (fn.desc) {
            case "I" -> "intField" + intIdx;
            case "J" -> "longField" + intIdx;
            case "Z" -> "boolField" + boolIdx;
            case "Ljava/lang/String;" -> "stringField" + strIdx;
            default -> null;
        };
    }

    /**
     * Check if a name looks obfuscated (single char or very short nonsensical).
     */
    public static boolean isObfuscatedName(String name) {
        if (name.length() <= 2) return true;
        // Check if it's all lowercase single chars or looks like a/b/c/aa/ab pattern
        return name.matches("^[a-z]{1,3}$");
    }
}
