package com.aeroverra.deobfuscator.transform;

import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

import java.util.*;

public class ClassRenamer {

    private static final Set<String> JAVA_KEYWORDS = new HashSet<>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new",
            "package", "private", "protected", "public", "return", "short", "static",
            "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while",
            "null", "true", "false", "var", "record", "sealed", "permits", "yield"
    ));

    private final Map<String, String> renameMap = new HashMap<>();
    private int renameCount = 0;

    public Map<String, ClassNode> rename(Map<String, ClassNode> classes) {
        for (String name : classes.keySet()) {
            String simpleName = name.contains("/") ? name.substring(name.lastIndexOf('/') + 1) : name;
            if (shouldRename(simpleName)) {
                String newName;
                if (name.contains("/")) {
                    String pkg = name.substring(0, name.lastIndexOf('/') + 1);
                    newName = pkg + "Class_" + simpleName;
                } else {
                    newName = "Class_" + simpleName;
                }
                renameMap.put(name, newName);
                renameCount++;
            }
        }

        if (renameMap.isEmpty()) return classes;

        Remapper remapper = new Remapper() {
            @Override
            public String map(String internalName) {
                return renameMap.getOrDefault(internalName, internalName);
            }
        };

        Map<String, ClassNode> result = new LinkedHashMap<>();
        for (Map.Entry<String, ClassNode> entry : classes.entrySet()) {
            ClassNode original = entry.getValue();
            ClassNode renamed = new ClassNode();
            ClassRemapper cr = new ClassRemapper(renamed, remapper);
            original.accept(cr);
            String newName = remapper.map(entry.getKey());
            result.put(newName, renamed);
        }

        return result;
    }

    private boolean shouldRename(String simpleName) {
        if (JAVA_KEYWORDS.contains(simpleName)) return true;
        if (simpleName.matches("^[a-z]{1,3}$")) return true;
        return false;
    }

    public int getRenameCount() { return renameCount; }
    public Map<String, String> getRenameMap() { return Collections.unmodifiableMap(renameMap); }
}
