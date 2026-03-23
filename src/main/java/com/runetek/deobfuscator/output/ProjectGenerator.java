package com.runetek.deobfuscator.output;

import com.runetek.deobfuscator.engine.TransformContext;
import com.runetek.deobfuscator.util.AsmUtil;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Generates a re-compilable Maven project from deobfuscated classes.
 * Writes transformed .class files and generates a pom.xml so the
 * output can be built standalone.
 */
public class ProjectGenerator {

    /**
     * Generate a complete Maven project structure in the output directory.
     */
    public static void generate(TransformContext context, Path outputDir) throws IOException {
        Path projectDir = outputDir.resolve("deobfuscated-project");
        Path classesDir = projectDir.resolve("src/main/java");
        Files.createDirectories(classesDir);

        // Write pom.xml
        writePom(projectDir);

        // Write classes as .class files in a 'classes' directory
        // (Decompilation to .java would require Procyon/CFR integration)
        Path compiledDir = projectDir.resolve("target/classes");
        Files.createDirectories(compiledDir);

        for (Map.Entry<String, ClassNode> entry : context.classes().entrySet()) {
            String name = entry.getKey();
            ClassNode cn = entry.getValue();

            Path classFile = compiledDir.resolve(name + ".class");
            Files.createDirectories(classFile.getParent());

            try {
                byte[] bytes = AsmUtil.toBytesNoFrames(cn);
                Files.write(classFile, bytes);
            } catch (Exception e) {
                System.err.println("  Warning: failed to write " + name + " to project: " + e.getMessage());
            }
        }

        System.out.println("  Project generated at: " + projectDir);
        System.out.println("  Note: Use a decompiler (CFR/Procyon) on target/classes/ to generate .java sources");
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
