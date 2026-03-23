package com.runetek.deobfuscator.util;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

/**
 * ASM utility methods.
 */
public class AsmUtil {

    /**
     * Convert a ClassNode to raw bytes.
     */
    public static byte[] toBytes(ClassNode cn) {
        org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(
                org.objectweb.asm.ClassWriter.COMPUTE_MAXS | org.objectweb.asm.ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        return cw.toByteArray();
    }

    /**
     * Convert a ClassNode to bytes without computing frames (safer for obfuscated code).
     */
    public static byte[] toBytesNoFrames(ClassNode cn) {
        org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(
                org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    /**
     * Check if a class access flag indicates it's an interface.
     */
    public static boolean isInterface(int access) {
        return (access & Opcodes.ACC_INTERFACE) != 0;
    }

    /**
     * Check if a class access flag indicates it's abstract.
     */
    public static boolean isAbstract(int access) {
        return (access & Opcodes.ACC_ABSTRACT) != 0;
    }

    /**
     * Get a human-readable access string.
     */
    public static String accessToString(int access) {
        StringBuilder sb = new StringBuilder();
        if ((access & Opcodes.ACC_PUBLIC) != 0) sb.append("public ");
        if ((access & Opcodes.ACC_PRIVATE) != 0) sb.append("private ");
        if ((access & Opcodes.ACC_PROTECTED) != 0) sb.append("protected ");
        if ((access & Opcodes.ACC_STATIC) != 0) sb.append("static ");
        if ((access & Opcodes.ACC_FINAL) != 0) sb.append("final ");
        if ((access & Opcodes.ACC_ABSTRACT) != 0) sb.append("abstract ");
        return sb.toString().trim();
    }
}
