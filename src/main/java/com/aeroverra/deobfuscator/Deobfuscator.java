package com.aeroverra.deobfuscator;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Deobfuscator {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar deobfuscator.jar <input.jar>");
            return;
        }

        String inputJar = args[0];
        System.out.println("[*] Starting RuneTek Deobfuscator Framework (Java 8/ASM)");
        System.out.println("[*] Analyzing target: " + inputJar);

        try (JarFile jarFile = new JarFile(inputJar)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    analyzeClass(jarFile, entry);
                }
            }
            
            System.out.println("[*] Phase 1: Initial Heuristic Mapping scan complete.");
        } catch (IOException e) {
            System.err.println("[!] I/O Error reading JAR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void analyzeClass(JarFile jarFile, JarEntry entry) throws IOException {
        try (InputStream is = jarFile.getInputStream(entry)) {
            ClassReader classReader = new ClassReader(is);
            ClassVisitor heuristicVisitor = new HeuristicClassVisitor(Opcodes.ASM9);
            // We use 0 for flags to just read basic info without full frame calculation yet
            classReader.accept(heuristicVisitor, 0);
        }
    }
}

class HeuristicClassVisitor extends ClassVisitor {
    private String className;

    public HeuristicClassVisitor(int api) {
        super(api);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name;
        // Future Phase 1: identify Applet subclasses, network streams
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        // Map methods looking for PacketParser, Stream logic, RSA
        return new HeuristicMethodVisitor(api, mv, className, name, descriptor);
    }
}

class HeuristicMethodVisitor extends MethodVisitor {
    private final String className;
    private final String methodName;
    private final String descriptor;

    public HeuristicMethodVisitor(int api, MethodVisitor mv, String className, String methodName, String descriptor) {
        super(api, mv);
        this.className = className;
        this.methodName = methodName;
        this.descriptor = descriptor;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        // Phase 2 target: RSA Lobotomy signature check
        if (owner.equals("java/math/BigInteger") && name.equals("modPow")) {
            System.out.println("[+] Found potential RSA encryption block (modPow) in: " + className + "." + methodName);
        }
        
        // Phase 2 target: IP connection logic signature check
        if (owner.equals("java/net/Socket") && name.equals("<init>")) {
            System.out.println("[+] Found socket initialization in: " + className + "." + methodName);
        }
        
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }
}
