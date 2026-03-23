package com.runetek.deobfuscator;

import com.runetek.deobfuscator.engine.DeobfuscatorEngine;
import com.runetek.deobfuscator.engine.EngineConfig;

import java.nio.file.Paths;

/**
 * CLI entry point for the RuneTek Universal Deobfuscator.
 *
 * Usage: java -jar runetek-deobfuscator.jar &lt;input-jar&gt; &lt;output-dir&gt; [options]
 *
 * Options:
 *   --mappings &lt;file&gt;     Load/save mappings from/to JSON file
 *   --hooks &lt;file&gt;        Load hook definitions from JSON file
 *   --skip-rename         Skip Phase 1 (heuristic renaming)
 *   --skip-hooks          Skip Phase 2 (hook injection)
 *   --decompile           Decompile output to Java source (Phase 3)
 *   --verbose             Enable verbose logging
 */
public class Main {

    public static void main(String[] args) {
        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }

        EngineConfig.Builder configBuilder = EngineConfig.builder()
                .inputJar(Paths.get(args[0]))
                .outputDir(Paths.get(args[1]));

        // Parse optional arguments
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            if ("--mappings".equals(arg)) {
                configBuilder.mappingsFile(Paths.get(args[++i]));
            } else if ("--hooks".equals(arg)) {
                configBuilder.hooksFile(Paths.get(args[++i]));
            } else if ("--output-jar".equals(arg)) {
                configBuilder.outputJar(Paths.get(args[++i]));
            } else if ("--skip-rename".equals(arg)) {
                configBuilder.skipRenaming(true);
            } else if ("--skip-hooks".equals(arg)) {
                configBuilder.skipHooks(true);
            } else if ("--decompile".equals(arg)) {
                configBuilder.decompile(true);
            } else if ("--verbose".equals(arg)) {
                configBuilder.verbose(true);
            } else {
                System.err.println("Unknown option: " + arg);
                printUsage();
                System.exit(1);
            }
        }

        EngineConfig config = configBuilder.build();

        System.out.println("======================================================");
        System.out.println("     RuneTek Universal Deobfuscator v0.1.0            ");
        System.out.println("======================================================");
        System.out.println();
        System.out.println("Input:  " + config.inputJar());
        System.out.println("Output: " + config.outputDir());
        System.out.println();

        try {
            DeobfuscatorEngine engine = new DeobfuscatorEngine(config);
            engine.run();
            System.out.println("\n[OK] Deobfuscation complete.");
        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
            if (config.verbose()) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Usage: runetek-deobfuscator <input-jar> <output-dir> [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --mappings <file>   Load/save name mappings (JSON)");
        System.out.println("  --hooks <file>      Load hook definitions (JSON)");
        System.out.println("  --output-jar <file> Write deobfuscated classes to a JAR");
        System.out.println("  --skip-rename       Skip heuristic renaming phase");
        System.out.println("  --skip-hooks        Skip hook injection phase");
        System.out.println("  --decompile         Decompile to Java source");
        System.out.println("  --verbose           Verbose output");
    }
}
