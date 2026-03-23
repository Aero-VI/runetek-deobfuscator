package com.aeroverra.deobfuscator.util;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.*;

public class JarIO {

    public static Map<String, ClassNode> loadJar(Path jarPath) throws IOException {
        Map<String, ClassNode> classes = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                try (InputStream is = jar.getInputStream(entry)) {
                    ClassReader cr = new ClassReader(is);
                    ClassNode cn = new ClassNode();
                    cr.accept(cn, ClassReader.SKIP_FRAMES);
                    classes.put(cn.name, cn);
                }
            }
        }
        return classes;
    }

    public static void writeJar(Map<String, ClassNode> classes, Path outputPath) throws IOException {
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(outputPath.toFile()))) {
            for (Map.Entry<String, ClassNode> entry : classes.entrySet()) {
                ClassNode cn = entry.getValue();
                ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                cn.accept(cw);
                byte[] bytes = cw.toByteArray();

                String entryName = cn.name + ".class";
                jos.putNextEntry(new JarEntry(entryName));
                jos.write(bytes);
                jos.closeEntry();
            }
        }
    }
}
