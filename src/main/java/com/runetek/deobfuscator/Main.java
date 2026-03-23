package com.runetek.deobfuscator;

import com.runetek.deobfuscator.engine.DeobfuscatorEngine;
import com.runetek.deobfuscator.engine.EngineConfig;

import java.nio.file.Path;

/**
 * CLI entry point for the RuneTek Universal Deobfuscator.
 *
 * Usage: java -jar runetek-deobfuscator.jar <input-jar> <output-dir> [options]
 *
 * Options:
 *   --mappings <file>     Load/save mappings from/to JSON file
 *   --hooks <file>        Load hook definitions from JSON file
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

        Path inputJar = Path.of(args[0]);
        Path outputDir = Path.of(args[1]);

        EngineConfig.Builder configBuilder = EngineConfig.builder()
                .inputJar(inputJar)
                .outputDir(outputDir);

        // Parse optional arguments
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--mappings" -> configBuilder.mappingsFile(Path.of(args[++i]));
                case "--hooks" -> configBuilder.hooksFile(Path.of(args[++i]));
                case "--skip-rename" -> configBuilder.skipRenaming(true);
                case "--skip-hooks" -> configBuilder.skipHooks(true);
                case "--decompile" -> configBuilder.decompile(true);
                case "--verbose" -> configBuilder.verbose(true);
                default -> {
                    System.err.println("Unknown option: " + args[i]);
                    printUsage();
                    System.exit(1);
                }
            }
        }

        EngineConfig config = configBuilder.build();

        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║     RuneTek Universal Deobfuscator v0.1.0       ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Input:  " + config.inputJar());
        System.out.println("Output: " + config.outputDir());
        System.out.println();

        try {
            DeobfuscatorEngine engine = new DeobfuscatorEngine(config);
            engine.run();
            System.out.println("\n✓ Deobfuscation complete.");
        } catch (Exception e) {
            System.err.println("✗ Error: " + e.getMessage());
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
        System.out.println("  --skip-rename       Skip heuristic renaming phase");
        System.out.println("  --skip-hooks        Skip hook injection phase");
        System.out.println("  --decompile         Decompile to Java source");
        System.out.println("  --verbose           Verbose output");
    }
}
