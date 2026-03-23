package com.runetek.deobfuscator.output;

import com.runetek.deobfuscator.engine.TransformContext;
import com.runetek.deobfuscator.util.AsmUtil;
import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.SinkReturns;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Generates a re-compilable Maven project from deobfuscated classes.
 * Uses CFR decompiler to produce .java sources from transformed bytecode,
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

        // Prepare class bytes for the decompiler
        final Map<String, byte[]> classBytesMap = new HashMap<String, byte[]>();
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

        // Decompile each class to .java using CFR
        int decompiled = 0;
        int errors = 0;

        for (Map.Entry<String, byte[]> entry : classBytesMap.entrySet()) {
            final String className = entry.getKey();
            try {
                String source = decompileClass(className, classBytesMap);

                if (source != null && source.trim().length() > 0) {
                    Path javaFile = sourcesDir.resolve(className + ".java");
                    Files.createDirectories(javaFile.getParent());
                    Files.write(javaFile, source.getBytes(StandardCharsets.UTF_8));
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
     * Decompile a single class using CFR with in-memory class bytes.
     */
    private static String decompileClass(final String className, final Map<String, byte[]> classBytesMap) {
        final StringBuilder result = new StringBuilder();

        OutputSinkFactory sinkFactory = new OutputSinkFactory() {
            @Override
            public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> collection) {
                return Arrays.asList(SinkClass.STRING);
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
                if (sinkType == SinkType.JAVA && sinkClass == SinkClass.STRING) {
                    return new Sink<T>() {
                        @Override
                        public void write(T t) {
                            result.append(t.toString());
                        }
                    };
                }
                return new Sink<T>() {
                    @Override
                    public void write(T t) {
                        // discard non-java output
                    }
                };
            }
        };

        // Write the target class to a temp file for CFR
        File tempFile = null;
        try {
            tempFile = File.createTempFile("cfr_", ".class");
            byte[] bytes = classBytesMap.get(className);
            if (bytes == null) return null;

            FileOutputStream fos = new FileOutputStream(tempFile);
            fos.write(bytes);
            fos.close();

            Map<String, String> options = new HashMap<String, String>();
            options.put("showversion", "false");
            options.put("silent", "true");

            CfrDriver driver = new CfrDriver.Builder()
                    .withOutputSink(sinkFactory)
                    .withOptions(options)
                    .build();

            driver.analyse(Arrays.asList(tempFile.getAbsolutePath()));
        } catch (IOException e) {
            System.err.println("  Warning: CFR temp file error for " + className + ": " + e.getMessage());
            return null;
        } finally {
            if (tempFile != null) {
                tempFile.delete();
            }
        }

        return result.toString();
    }

    private static void writePom(Path projectDir) throws IOException {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
                + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
                + "    <modelVersion>4.0.0</modelVersion>\n"
                + "\n"
                + "    <groupId>com.runetek</groupId>\n"
                + "    <artifactId>deobfuscated-client</artifactId>\n"
                + "    <version>1.0.0</version>\n"
                + "    <packaging>jar</packaging>\n"
                + "\n"
                + "    <name>Deobfuscated RuneTek Client</name>\n"
                + "    <description>Auto-generated deobfuscated client project</description>\n"
                + "\n"
                + "    <properties>\n"
                + "        <maven.compiler.source>1.8</maven.compiler.source>\n"
                + "        <maven.compiler.target>1.8</maven.compiler.target>\n"
                + "        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n"
                + "    </properties>\n"
                + "</project>\n";
        Files.write(projectDir.resolve("pom.xml"), pom.getBytes(StandardCharsets.UTF_8));
    }
}
