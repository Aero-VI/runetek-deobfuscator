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
     * @return Map of original field name to suggested name
     */
    public Map<String, String> analyzeFields(ClassNode cn, String className) {
        Map<String, String> suggestions = new LinkedHashMap<String, String>();

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
            if ("I".equals(fn.desc) || "J".equals(fn.desc)) {
                intIndex++;
            } else if ("Ljava/lang/String;".equals(fn.desc)) {
                stringIndex++;
            } else if ("Z".equals(fn.desc)) {
                boolIndex++;
            } else if (fn.desc.startsWith("[")) {
                arrayIndex++;
            }
        }

        return suggestions;
    }

    private String suggestFieldName(FieldNode fn, String className, int intIdx, int strIdx, int boolIdx, int arrIdx) {
        if ("Widget".equals(className)) return suggestWidgetField(fn, intIdx, strIdx, boolIdx);
        if ("Buffer".equals(className)) return suggestBufferField(fn, intIdx);
        if ("Node".equals(className)) return suggestNodeField(fn, intIdx);
        if ("Model".equals(className)) return suggestModelField(fn, intIdx, arrIdx);
        if ("Client".equals(className)) return suggestClientField(fn, intIdx, strIdx);
        return suggestGenericField(fn, intIdx, strIdx, boolIdx);
    }

    private String suggestWidgetField(FieldNode fn, int intIdx, int strIdx, int boolIdx) {
        if ("I".equals(fn.desc)) {
            switch (intIdx) {
                case 0: return "id";
                case 1: return "parentId";
                case 2: return "type";
                case 3: return "contentType";
                case 4: return "x";
                case 5: return "y";
                case 6: return "width";
                case 7: return "height";
                case 8: return "opacity";
                default: return "intField" + intIdx;
            }
        }
        if ("Ljava/lang/String;".equals(fn.desc)) {
            switch (strIdx) {
                case 0: return "text";
                case 1: return "tooltip";
                default: return "stringField" + strIdx;
            }
        }
        if ("Z".equals(fn.desc)) {
            switch (boolIdx) {
                case 0: return "isHidden";
                case 1: return "isDraggable";
                default: return "boolField" + boolIdx;
            }
        }
        return null;
    }

    private String suggestBufferField(FieldNode fn, int intIdx) {
        if ("[B".equals(fn.desc)) return "payload";
        if ("I".equals(fn.desc)) {
            return intIdx == 0 ? "position" : "capacity";
        }
        return null;
    }

    private String suggestNodeField(FieldNode fn, int intIdx) {
        if (fn.desc.contains(";") && !"Ljava/lang/String;".equals(fn.desc)) {
            return intIdx == 0 ? "next" : "previous";
        }
        if ("J".equals(fn.desc)) return "key";
        return null;
    }

    private String suggestModelField(FieldNode fn, int intIdx, int arrIdx) {
        if ("[I".equals(fn.desc)) {
            switch (arrIdx) {
                case 0: return "verticesX";
                case 1: return "verticesY";
                case 2: return "verticesZ";
                case 3: return "triangleA";
                case 4: return "triangleB";
                case 5: return "triangleC";
                case 6: return "faceColors";
                default: return "intArray" + arrIdx;
            }
        }
        if ("I".equals(fn.desc)) {
            switch (intIdx) {
                case 0: return "vertexCount";
                case 1: return "triangleCount";
                default: return "intField" + intIdx;
            }
        }
        return null;
    }

    private String suggestClientField(FieldNode fn, int intIdx, int strIdx) {
        if ("I".equals(fn.desc)) {
            switch (intIdx) {
                case 0: return "gameState";
                case 1: return "loginState";
                case 2: return "worldId";
                default: return "clientInt" + intIdx;
            }
        }
        if ("Ljava/lang/String;".equals(fn.desc)) {
            switch (strIdx) {
                case 0: return "username";
                case 1: return "password";
                default: return "clientString" + strIdx;
            }
        }
        return null;
    }

    private String suggestGenericField(FieldNode fn, int intIdx, int strIdx, int boolIdx) {
        if ("I".equals(fn.desc)) return "intField" + intIdx;
        if ("J".equals(fn.desc)) return "longField" + intIdx;
        if ("Z".equals(fn.desc)) return "boolField" + boolIdx;
        if ("Ljava/lang/String;".equals(fn.desc)) return "stringField" + strIdx;
        return null;
    }

    // JDK/JRE method and field names that happen to be short but must NOT be renamed
    private static final java.util.Set<String> RESERVED_NAMES = new java.util.HashSet<String>(
            java.util.Arrays.asList(
                // Thread/Runnable
                "run", "get", "set", "put", "add", "map",
                // Object
                "equals", "hashCode", "toString", "clone", "finalize", "notify", "notifyAll", "wait",
                // Applet / AWT / Swing
                "init", "start", "stop", "destroy", "paint", "update", "repaint",
                // Collections
                "size", "clear", "remove", "contains", "isEmpty", "iterator", "next", "hasNext",
                // IO
                "read", "write", "close", "flush", "reset", "skip", "mark", "available",
                // Comparable/Comparator
                "compareTo", "compare",
                // Iterable
                "forEach",
                // Map
                "key", "keys", "values", "entrySet", "keySet",
                // Other common short JDK names
                "abs", "min", "max", "sum", "and", "or", "xor", "not", "log", "sin", "cos", "tan",
                "gc", "exit", "exec", "load", "open", "seek", "tell", "eof", "end"
            ));

    /**
     * Check if a name looks obfuscated (single char or very short nonsensical).
     * Excludes known JDK method/field names to prevent AbstractMethodErrors.
     */
    public static boolean isObfuscatedName(String name) {
        if (RESERVED_NAMES.contains(name)) return false;
        if (name.length() <= 2) return true;
        return name.matches("^[a-z]{1,3}$");
    }
}
