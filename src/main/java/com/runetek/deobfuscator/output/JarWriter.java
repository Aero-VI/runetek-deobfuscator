package com.runetek.deobfuscator.output;

import com.runetek.deobfuscator.engine.TransformContext;
import com.runetek.deobfuscator.util.AsmUtil;
import org.objectweb.asm.tree.ClassNode;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.jar.Attributes;

/**
 * Writes all transformed classes back into a JAR file.
 * Useful for producing a directly-runnable deobfuscated JAR.
 */
public class JarWriter {

    /**
     * Write all classes from the context into a JAR file.
     *
     * @param context    The transform context with all classes
     * @param outputPath Path for the output JAR
     * @param mainClass  Optional main class for the manifest (internal name format, e.g. "com/runetek/Client")
     */
    public static void writeJar(TransformContext context, Path outputPath, String mainClass) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (mainClass != null) {
            // Convert internal name to binary name
            manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass.replace('/', '.'));
        }

        int written = 0;
        int errors = 0;

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(outputPath.toFile()), manifest)) {
            for (Map.Entry<String, ClassNode> entry : context.classes().entrySet()) {
                String name = entry.getKey();
                ClassNode cn = entry.getValue();

                try {
                    byte[] bytes = AsmUtil.toBytesNoFrames(cn);
                    jos.putNextEntry(new JarEntry(name + ".class"));
                    jos.write(bytes);
                    jos.closeEntry();
                    written++;
                } catch (Exception e) {
                    System.err.println("  Warning: failed to write " + name + " to JAR: " + e.getMessage());
                    errors++;
                }
            }
        }

        System.out.println("  Written " + written + " classes to " + outputPath.getFileName() +
                (errors > 0 ? " (" + errors + " errors)" : ""));
    }
}
