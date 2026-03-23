package com.aeroverra.deobfuscator.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ScriptGenerator {

    public static void generate(Path outputDir, String mainClass) throws IOException {
        String compile = "@echo off\r\n"
                + "echo Compiling RuneTek 508 client...\r\n"
                + "dir /s /b src\\*.java > sources.txt\r\n"
                + "javac -source 1.8 -target 1.8 -d bin @sources.txt\r\n"
                + "del sources.txt\r\n"
                + "if %ERRORLEVEL% EQU 0 (\r\n"
                + "    echo Compilation successful!\r\n"
                + ") else (\r\n"
                + "    echo Compilation failed with errors.\r\n"
                + ")\r\n"
                + "pause\r\n";
        Files.write(outputDir.resolve("#compile.bat"), compile.getBytes(StandardCharsets.UTF_8));

        String run = "@echo off\r\n"
                + "echo Launching RuneTek 508 client...\r\n"
                + "java -cp bin " + mainClass + "\r\n"
                + "pause\r\n";
        Files.write(outputDir.resolve("#run.bat"), run.getBytes(StandardCharsets.UTF_8));

        System.out.println("  Generated #compile.bat and #run.bat");
    }
}
