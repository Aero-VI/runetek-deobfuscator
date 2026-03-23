package com.aeroverra.deobfuscator.decompile;

import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;

import java.io.File;
import java.nio.file.Path;

public class VineflowerDecompiler {

    public static void decompile(Path inputJar, Path outputDir) {
        File outDir = outputDir.toFile();
        outDir.mkdirs();

        String[] args = {
                "-din=1",
                "-rbr=1",
                "-dgs=1",
                "-asc=1",
                "-rsy=1",
                "-iec=1",
                "-lit=1",
                "-log=WARN",
                inputJar.toAbsolutePath().toString(),
                outDir.getAbsolutePath()
        };

        System.out.println("  Running Vineflower decompiler...");
        try {
            ConsoleDecompiler.main(args);
        } catch (Exception e) {
            System.err.println("  Vineflower error: " + e.getMessage());
            try {
                String[] fallbackArgs = {
                        "-log=WARN",
                        inputJar.toAbsolutePath().toString(),
                        outDir.getAbsolutePath()
                };
                ConsoleDecompiler.main(fallbackArgs);
            } catch (Exception e2) {
                System.err.println("  Vineflower fallback also failed: " + e2.getMessage());
                throw new RuntimeException("Decompilation failed", e2);
            }
        }

        File outputJar = new File(outDir, inputJar.getFileName().toString());
        if (outputJar.exists()) {
            extractJavaFiles(outputJar, outDir);
            outputJar.delete();
        }

        System.out.println("  Decompilation complete");
    }

    private static void extractJavaFiles(File jarFile, File outputDir) {
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarFile)) {
            java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".java")) continue;

                File outFile = new File(outputDir, entry.getName());
                outFile.getParentFile().mkdirs();
                try (java.io.InputStream is = jar.getInputStream(entry);
                     java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) > 0) {
                        fos.write(buf, 0, len);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("  Error extracting java files: " + e.getMessage());
        }
    }
}
