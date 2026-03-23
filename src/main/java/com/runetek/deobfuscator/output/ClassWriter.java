package com.runetek.deobfuscator.output;

import com.runetek.deobfuscator.engine.TransformContext;
import com.runetek.deobfuscator.util.AsmUtil;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Writes transformed ClassNodes back to .class files.
 */
public class ClassWriter {

    /**
     * Write all classes from the context to the output directory.
     */
    public static void writeClasses(TransformContext context, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);

        int written = 0;
        int errors = 0;

        for (Map.Entry<String, ClassNode> entry : context.classes().entrySet()) {
            String name = entry.getKey();
            ClassNode cn = entry.getValue();

            Path classFile = outputDir.resolve(name + ".class");
            Files.createDirectories(classFile.getParent());

            try {
                // Use toBytesNoFrames for safety with obfuscated code
                // COMPUTE_FRAMES can fail on invalid bytecode
                byte[] bytes = AsmUtil.toBytesNoFrames(cn);
                Files.write(classFile, bytes);
                written++;
            } catch (Exception e) {
                System.err.println("  Warning: failed to write " + name + ": " + e.getMessage());
                errors++;
            }
        }

        System.out.println("  Written " + written + " class files" +
                (errors > 0 ? " (" + errors + " errors)" : ""));
    }
}
