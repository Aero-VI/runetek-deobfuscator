package com.aeroverra.deobfuscator.transform;

import org.objectweb.asm.tree.*;

import java.util.*;

public class CollisionFixer {

    public static int fix(Map<String, ClassNode> classes) {
        int fixed = 0;
        for (ClassNode cn : classes.values()) {
            fixed += fixMethodCollisions(cn);
            fixed += fixFieldCollisions(cn);
        }
        return fixed;
    }

    private static int fixMethodCollisions(ClassNode cn) {
        int fixed = 0;
        Set<String> seen = new HashSet<>();

        for (MethodNode mn : cn.methods) {
            if (mn.name.startsWith("<")) continue;
            String key = mn.name + mn.desc;
            if (seen.contains(key)) {
                String newName = mn.name + "_" + (fixed + 1);
                mn.name = newName;
                fixed++;
            }
            seen.add(key);
        }

        return fixed;
    }

    private static int fixFieldCollisions(ClassNode cn) {
        int fixed = 0;
        Set<String> seen = new HashSet<>();

        for (FieldNode fn : cn.fields) {
            String key = fn.name + ":" + fn.desc;
            if (seen.contains(key)) {
                fn.name = fn.name + "_" + (fixed + 1);
                fixed++;
            }
            seen.add(key);
        }

        return fixed;
    }
}
