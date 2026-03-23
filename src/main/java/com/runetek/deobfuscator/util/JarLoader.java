package com.runetek.deobfuscator.util;

import com.runetek.deobfuscator.engine.TransformContext;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Loads all .class files from a JAR into the TransformContext as ASM ClassNodes.
 */
public class JarLoader {

    /**
     * Load all classes from a JAR file into the context.
     */
    public static void loadJar(Path jarPath, TransformContext context) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;

                try (InputStream is = jar.getInputStream(entry)) {
                    ClassReader cr = new ClassReader(is);
                    ClassNode cn = new ClassNode();
                    cr.accept(cn, ClassReader.EXPAND_FRAMES);
                    context.addClass(cn.name, cn);
                }
            }
        }
    }

    /**
     * Load a single class from bytes.
     */
    public static ClassNode loadClass(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, ClassReader.EXPAND_FRAMES);
        return cn;
    }
}
