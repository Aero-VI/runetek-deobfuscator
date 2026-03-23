package com.runetek.deobfuscator.phase1;

import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Analyzes class structure to guess meaningful names based on heuristics.
 * Examines field types, method signatures, superclasses, interfaces,
 * and string constants to identify RuneTek-specific patterns.
 */
public class ClassHeuristicAnalyzer {

    /**
     * Known RuneTek class patterns — each pattern matches structural
     * characteristics and produces a candidate name.
     */
    private final List<HeuristicPattern> patterns = new ArrayList<>();

    public ClassHeuristicAnalyzer() {
        registerDefaultPatterns();
    }

    private void registerDefaultPatterns() {
        // Pattern: Client main class (extends Applet/JFrame, has main method)
        patterns.add(new HeuristicPattern("Client") {
            @Override
            public boolean matches(ClassNode cn) {
                if (cn.superName != null &&
                    (cn.superName.contains("Applet") || cn.superName.contains("Canvas") ||
                     cn.superName.contains("JFrame"))) {
                    return hasMethod(cn, "main", "([Ljava/lang/String;)V");
                }
                return false;
            }
        });

        // Pattern: Widget/Interface component (has x, y, width, height int fields)
        patterns.add(new HeuristicPattern("Widget") {
            @Override
            public boolean matches(ClassNode cn) {
                int intFieldCount = 0;
                boolean hasArrayField = false;
                for (FieldNode fn : cn.fields) {
                    if (fn.desc.equals("I")) intFieldCount++;
                    if (fn.desc.startsWith("[")) hasArrayField = true;
                }
                // Widget-like: many int fields (coords, dimensions) + array children
                return intFieldCount >= 8 && hasArrayField && cn.fields.size() >= 12;
            }
        });

        // Pattern: Node/LinkedList element (has next/prev self-referencing fields)
        patterns.add(new HeuristicPattern("Node") {
            @Override
            public boolean matches(ClassNode cn) {
                int selfRefCount = 0;
                String selfDesc = "L" + cn.name + ";";
                for (FieldNode fn : cn.fields) {
                    if (fn.desc.equals(selfDesc)) selfRefCount++;
                }
                return selfRefCount >= 2 && cn.fields.size() <= 5;
            }
        });

        // Pattern: Cache/Hashtable (has array field + int size + get/put-like methods)
        patterns.add(new HeuristicPattern("Cache") {
            @Override
            public boolean matches(ClassNode cn) {
                boolean hasArray = false;
                boolean hasIntSize = false;
                for (FieldNode fn : cn.fields) {
                    if (fn.desc.startsWith("[L")) hasArray = true;
                    if (fn.desc.equals("I")) hasIntSize = true;
                }
                return hasArray && hasIntSize &&
                       cn.methods.size() >= 3 && cn.methods.size() <= 8;
            }
        });

        // Pattern: Buffer/Packet (has byte[] and int position fields)
        patterns.add(new HeuristicPattern("Buffer") {
            @Override
            public boolean matches(ClassNode cn) {
                boolean hasByteArray = false;
                boolean hasInt = false;
                for (FieldNode fn : cn.fields) {
                    if (fn.desc.equals("[B")) hasByteArray = true;
                    if (fn.desc.equals("I")) hasInt = true;
                }
                // Buffer: byte[] data + int offset, with read/write methods
                return hasByteArray && hasInt && cn.methods.size() >= 5;
            }
        });

        // Pattern: Renderable/Model (has int arrays for vertices)
        patterns.add(new HeuristicPattern("Model") {
            @Override
            public boolean matches(ClassNode cn) {
                int intArrayCount = 0;
                for (FieldNode fn : cn.fields) {
                    if (fn.desc.equals("[I")) intArrayCount++;
                }
                // Model: many int[] for vertices, triangles, colors
                return intArrayCount >= 5 && cn.fields.size() >= 10;
            }
        });

        // Pattern: Definition/Config (loaded from cache, many typed fields)
        patterns.add(new HeuristicPattern("Definition") {
            @Override
            public boolean matches(ClassNode cn) {
                boolean hasStringField = false;
                boolean hasIntFields = false;
                int fieldCount = cn.fields.size();
                for (FieldNode fn : cn.fields) {
                    if (fn.desc.equals("Ljava/lang/String;")) hasStringField = true;
                    if (fn.desc.equals("I")) hasIntFields = true;
                }
                return hasStringField && hasIntFields && fieldCount >= 15;
            }
        });
    }

    /**
     * Analyze a class and return a suggested name, or null if no pattern matches.
     */
    public String analyze(ClassNode cn) {
        for (HeuristicPattern pattern : patterns) {
            if (pattern.matches(cn)) {
                return pattern.suggestedName();
            }
        }
        return null;
    }

    /**
     * Register a custom heuristic pattern.
     */
    public void addPattern(HeuristicPattern pattern) {
        patterns.add(0, pattern); // Custom patterns take priority
    }

    static boolean hasMethod(ClassNode cn, String name, String desc) {
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals(name) && mn.desc.equals(desc)) return true;
        }
        return false;
    }

    /**
     * Base class for heuristic matching patterns.
     */
    public static abstract class HeuristicPattern {
        private final String suggestedName;

        protected HeuristicPattern(String suggestedName) {
            this.suggestedName = suggestedName;
        }

        public abstract boolean matches(ClassNode cn);
        public String suggestedName() { return suggestedName; }
    }
}
