package com.aeroverra.deobfuscator.transform;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * Fixes return-type overloading: methods with the same name and parameter types
 * but different return types. Valid in JVM bytecode but not in Java source.
 * 
 * When two methods differ only by return type, we rename the less-used one
 * (appending the return type descriptor) and update all call sites.
 */
public class ReturnTypeOverloadFixer {

    public static int fix(Map<String, ClassNode> classes) {
        int fixed = 0;
        
        // Build a map of all renames: owner+oldName+oldDesc -> newName
        Map<String, String> renames = new HashMap<>();
        
        for (ClassNode cn : classes.values()) {
            // Group methods by name + parameter descriptor (ignoring return type)
            Map<String, List<MethodNode>> groups = new HashMap<>();
            for (MethodNode mn : cn.methods) {
                if (mn.name.startsWith("<")) continue;
                String paramDesc = getParamDescriptor(mn.desc);
                String key = mn.name + ":" + paramDesc;
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(mn);
            }
            
            for (Map.Entry<String, List<MethodNode>> entry : groups.entrySet()) {
                List<MethodNode> methods = entry.getValue();
                if (methods.size() <= 1) continue;
                
                // Multiple methods with same name+params but different return types
                // Keep the first one, rename the rest
                for (int i = 1; i < methods.size(); i++) {
                    MethodNode mn = methods.get(i);
                    String oldName = mn.name;
                    Type retType = Type.getReturnType(mn.desc);
                    String suffix = sanitizeTypeName(retType);
                    String newName = oldName + "_rt" + suffix;
                    
                    String renameKey = cn.name + "." + oldName + mn.desc;
                    renames.put(renameKey, newName);
                    mn.name = newName;
                    fixed++;
                }
            }
        }
        
        // Update all call sites
        if (!renames.isEmpty()) {
            for (ClassNode cn : classes.values()) {
                for (MethodNode mn : cn.methods) {
                    if (mn.instructions == null) continue;
                    for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                        if (insn instanceof MethodInsnNode) {
                            MethodInsnNode mi = (MethodInsnNode) insn;
                            String key = mi.owner + "." + mi.name + mi.desc;
                            String newName = renames.get(key);
                            if (newName != null) {
                                mi.name = newName;
                            }
                        }
                    }
                }
            }
        }
        
        return fixed;
    }
    
    private static String getParamDescriptor(String desc) {
        int endParen = desc.indexOf(')');
        return desc.substring(0, endParen + 1);
    }
    
    private static String sanitizeTypeName(Type type) {
        switch (type.getSort()) {
            case Type.VOID: return "V";
            case Type.BOOLEAN: return "Z";
            case Type.BYTE: return "B";
            case Type.CHAR: return "C";
            case Type.SHORT: return "S";
            case Type.INT: return "I";
            case Type.LONG: return "J";
            case Type.FLOAT: return "F";
            case Type.DOUBLE: return "D";
            case Type.ARRAY: return "Arr";
            case Type.OBJECT:
                String className = type.getClassName();
                int dot = className.lastIndexOf('.');
                return dot >= 0 ? className.substring(dot + 1) : className;
            default: return "X";
        }
    }
}
