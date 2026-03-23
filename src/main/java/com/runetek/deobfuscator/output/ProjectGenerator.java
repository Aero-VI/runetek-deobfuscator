package com.runetek.deobfuscator.output;

import com.runetek.deobfuscator.engine.TransformContext;
import com.runetek.deobfuscator.util.AsmUtil;
import com.strobel.decompiler.Decompiler;
import com.strobel.decompiler.DecompilerSettings;
import com.strobel.decompiler.PlainTextOutput;
import com.strobel.assembler.metadata.ITypeLoader;
import com.strobel.assembler.metadata.Buffer;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Generates a re-compilable Maven project from deobfuscated classes.
 * Uses Procyon decompiler to produce .java sources from transformed bytecode,
 * then writes a complete Maven project structure.
 */
public class ProjectGenerator {

    /**
     * Generate a complete Maven project structure in the output directory.
     */
    public static void generate(TransformContext context, Path outputDir) throws IOException {
        Path projectDir = outputDir.resolve("deobfuscated-project");
        Path sourcesDir = projectDir.resolve("src/main/java");
        Files.createDirectories(sourcesDir);

        // Write pom.xml
        writePom(projectDir);

        // Prepare class bytes for the type loader
        Map<String, byte[]> classBytesMap = new HashMap<>();
        for (Map.Entry<String, ClassNode> entry : context.classes().entrySet()) {
            try {
                byte[] bytes = AsmUtil.toBytesNoFrames(entry.getValue());
                classBytesMap.put(entry.getKey(), bytes);
            } catch (Exception e) {
                System.err.println("  Warning: failed to serialize " + entry.getKey() + ": " + e.getMessage());
            }
        }

        // Also write .class files for reference
        Path classesDir = projectDir.resolve("target/classes");
        Files.createDirectories(classesDir);
        for (Map.Entry<String, byte[]> entry : classBytesMap.entrySet()) {
            Path classFile = classesDir.resolve(entry.getKey() + ".class");
            Files.createDirectories(classFile.getParent());
            Files.write(classFile, entry.getValue());
        }

        // Decompile each class to .java using Procyon
        int decompiled = 0;
        int errors = 0;

        DecompilerSettings settings = DecompilerSettings.javaDefaults();
        settings.setTypeLoader(new InMemoryTypeLoader(classBytesMap));
        settings.setForceExplicitImports(true);

        for (Map.Entry<String, byte[]> entry : classBytesMap.entrySet()) {
            String className = entry.getKey();
            try {
                // Decompile to string
                StringWriter sw = new StringWriter();
                PlainTextOutput output = new PlainTextOutput(sw);
                Decompiler.decompile(className, output, settings);
                String source = sw.toString();

                if (source != null && !source.isBlank()) {
                    // Write .java file
                    Path javaFile = sourcesDir.resolve(className + ".java");
                    Files.createDirectories(javaFile.getParent());
                    Files.writeString(javaFile, source);
                    decompiled++;
                } else {
                    errors++;
                }
            } catch (Exception e) {
                System.err.println("  Warning: failed to decompile " + className + ": " + e.getMessage());
                errors++;
            }
        }

        System.out.println("  Decompiled " + decompiled + " classes to Java source" +
                (errors > 0 ? " (" + errors + " errors)" : ""));
        System.out.println("  Project generated at: " + projectDir);
    }

    /**
     * In-memory type loader backed by our class bytes map.
     * Allows Procyon to resolve classes without writing to disk first.
     */
    private static class InMemoryTypeLoader implements ITypeLoader {
        private final Map<String, byte[]> classBytesMap;

        InMemoryTypeLoader(Map<String, byte[]> classBytesMap) {
            this.classBytesMap = classBytesMap;
        }

        @Override
        public boolean tryLoadType(String internalName, Buffer buffer) {
            byte[] bytes = classBytesMap.get(internalName);
            if (bytes != null) {
                buffer.reset(bytes.length);
                buffer.putByteArray(bytes, 0, bytes.length);
                buffer.position(0);
                return true;
            }
            return false;
        }
    }

    private static void writePom(Path projectDir) throws IOException {
        String pom = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                
                    <groupId>com.runetek</groupId>
                    <artifactId>deobfuscated-client</artifactId>
                    <version>1.0.0</version>
                    <packaging>jar</packaging>
                
                    <name>Deobfuscated RuneTek Client</name>
                    <description>Auto-generated deobfuscated client project</description>
                
                    <properties>
                        <maven.compiler.source>21</maven.compiler.source>
                        <maven.compiler.target>21</maven.compiler.target>
                        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    </properties>
                </project>
                """;
        Files.writeString(projectDir.resolve("pom.xml"), pom);
    }
}
