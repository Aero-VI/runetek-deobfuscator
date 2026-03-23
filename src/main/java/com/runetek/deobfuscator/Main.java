package com.runetek.deobfuscator;

import com.runetek.deobfuscator.engine.DeobfuscatorEngine;
import com.runetek.deobfuscator.engine.EngineConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

/**
 * CLI entry point for the RuneTek Universal Deobfuscator.
 *
 * If run with no arguments, launches interactive mode.
 * Otherwise parses command-line flags.
 */
public class Main {

    public static void main(String[] args) {
        EngineConfig config;

        if (args.length == 0) {
            config = interactiveMode();
        } else if (args.length == 1 && ("--help".equals(args[0]) || "-h".equals(args[0]))) {
            printUsage();
            return;
        } else if (args.length < 2) {
            printUsage();
            System.exit(1);
            return;
        } else {
            config = parseArgs(args);
        }

        printBanner();
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

    private static EngineConfig interactiveMode() {
        Scanner scanner = new Scanner(System.in);

        printBanner();
        System.out.println("No arguments provided — entering interactive mode.\n");

        // Input JAR
        Path inputJar;
        while (true) {
            System.out.print("Path to input JAR: ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                System.out.println("  Input JAR is required.");
                continue;
            }
            inputJar = Paths.get(input);
            if (!Files.exists(inputJar)) {
                System.out.println("  File not found: " + inputJar);
                continue;
            }
            break;
        }

        // Output directory
        System.out.print("Output directory [./output]: ");
        String outputStr = scanner.nextLine().trim();
        Path outputDir = Paths.get(outputStr.isEmpty() ? "output" : outputStr);

        // Revision
        System.out.print("Revision number (e.g. 508, 317) [auto-detect]: ");
        String revStr = scanner.nextLine().trim();
        int revision = 0;
        if (!revStr.isEmpty()) {
            try {
                revision = Integer.parseInt(revStr);
            } catch (NumberFormatException e) {
                System.out.println("  Invalid number, will auto-detect.");
            }
        }

        // Decompile
        System.out.print("Decompile to Java source? [Y/n]: ");
        String decompStr = scanner.nextLine().trim().toLowerCase();
        boolean decompile = decompStr.isEmpty() || decompStr.startsWith("y");

        // Output JAR
        System.out.print("Write modified JAR? [Y/n]: ");
        String jarStr = scanner.nextLine().trim().toLowerCase();
        boolean writeJar = jarStr.isEmpty() || jarStr.startsWith("y");
        Path outputJar = null;
        if (writeJar) {
            System.out.print("Output JAR path [./deobfuscated.jar]: ");
            String jarPath = scanner.nextLine().trim();
            outputJar = Paths.get(jarPath.isEmpty() ? "deobfuscated.jar" : jarPath);
        }

        // Mappings
        System.out.print("Export name mappings? [Y/n]: ");
        String mapStr = scanner.nextLine().trim().toLowerCase();
        boolean writeMappings = mapStr.isEmpty() || mapStr.startsWith("y");
        Path mappingsFile = null;
        if (writeMappings) {
            System.out.print("Mappings file path [./mappings.json]: ");
            String mapPath = scanner.nextLine().trim();
            mappingsFile = Paths.get(mapPath.isEmpty() ? "mappings.json" : mapPath);
        }

        // Skip phases
        System.out.print("Skip heuristic renaming? [y/N]: ");
        String skipRename = scanner.nextLine().trim().toLowerCase();
        boolean skipRenaming = skipRename.startsWith("y");

        System.out.print("Skip hook injection? [y/N]: ");
        String skipHook = scanner.nextLine().trim().toLowerCase();
        boolean skipHooks = skipHook.startsWith("y");

        // Verbose
        System.out.print("Verbose output? [y/N]: ");
        String verbStr = scanner.nextLine().trim().toLowerCase();
        boolean verbose = verbStr.startsWith("y");

        System.out.println();

        EngineConfig.Builder builder = EngineConfig.builder()
                .inputJar(inputJar)
                .outputDir(outputDir)
                .decompile(decompile)
                .skipRenaming(skipRenaming)
                .skipHooks(skipHooks)
                .verbose(verbose);

        if (revision > 0) builder.revision(revision);
        if (outputJar != null) builder.outputJar(outputJar);
        if (mappingsFile != null) builder.mappingsFile(mappingsFile);

        return builder.build();
    }

    private static EngineConfig parseArgs(String[] args) {
        EngineConfig.Builder configBuilder = EngineConfig.builder()
                .inputJar(Paths.get(args[0]))
                .outputDir(Paths.get(args[1]));

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
            } else if ("--profile".equals(arg)) {
                configBuilder.profileName(args[++i]);
            } else if ("--revision".equals(arg)) {
                configBuilder.revision(Integer.parseInt(args[++i]));
            } else {
                System.err.println("Unknown option: " + arg);
                printUsage();
                System.exit(1);
            }
        }

        return configBuilder.build();
    }

    private static void printBanner() {
        System.out.println("======================================================");
        System.out.println("     RuneTek Universal Deobfuscator v0.1.0            ");
        System.out.println("======================================================");
        System.out.println();
    }

    private static void printUsage() {
        printBanner();
        System.out.println("Usage: runetek-deobfuscator <input-jar> <output-dir> [options]");
        System.out.println("       runetek-deobfuscator                          (interactive mode)");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --mappings <file>   Load/save name mappings (JSON)");
        System.out.println("  --hooks <file>      Load hook definitions (JSON)");
        System.out.println("  --output-jar <file> Write deobfuscated classes to a JAR");
        System.out.println("  --skip-rename       Skip heuristic renaming phase");
        System.out.println("  --skip-hooks        Skip hook injection phase");
        System.out.println("  --decompile         Decompile to Java source");
        System.out.println("  --verbose           Verbose output");
        System.out.println("  --profile <name>    Use specific profile (e.g. 'RuneTek 3', 'RuneTek 4')");
        System.out.println("  --revision <num>    Auto-detect profile by revision number (e.g. 508, 317)");
        System.out.println("  -h, --help          Show this help");
    }
}
