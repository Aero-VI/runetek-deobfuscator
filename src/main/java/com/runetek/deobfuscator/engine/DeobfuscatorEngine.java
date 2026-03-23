package com.runetek.deobfuscator.engine;

import com.runetek.deobfuscator.dictionary.ProfileRegistry;
import com.runetek.deobfuscator.dictionary.RevisionProfile;
import com.runetek.deobfuscator.output.ClassWriter;
import com.runetek.deobfuscator.output.JarWriter;
import com.runetek.deobfuscator.output.MappingExporter;
import com.runetek.deobfuscator.output.ProjectGenerator;
import com.runetek.deobfuscator.phase1.HeuristicRenamer;
import com.runetek.deobfuscator.phase1.MappingStore;
import com.runetek.deobfuscator.phase2.HookInjector;
import com.runetek.deobfuscator.phase2.HookRegistry;
import com.runetek.deobfuscator.phase3.DecompilationPhase;
import com.runetek.deobfuscator.util.JarLoader;

import org.objectweb.asm.tree.ClassNode;
import java.nio.file.Files;
import java.util.Map;

/**
 * Main engine orchestrator. Loads input, configures the pipeline,
 * executes all phases, and writes output.
 */
public class DeobfuscatorEngine {

    private final EngineConfig config;
    private final ServiceRegistry services;
    private final TransformPipeline pipeline;

    public DeobfuscatorEngine(EngineConfig config) {
        this.config = config;
        this.services = new ServiceRegistry();
        this.pipeline = new TransformPipeline();

        // Resolve revision profile
        ProfileRegistry profileRegistry = new ProfileRegistry();
        RevisionProfile profile;
        if (config.profileName() != null) {
            profile = profileRegistry.findByName(config.profileName());
            if (profile == null) {
                throw new IllegalArgumentException("Unknown profile: " + config.profileName());
            }
        } else if (config.revision() > 0) {
            profile = profileRegistry.findByRevision(config.revision());
            if (profile == null) {
                System.out.println("Warning: No profile found for revision " + config.revision() + ", using default");
                profile = profileRegistry.getDefault();
            }
        } else {
            profile = profileRegistry.getDefault();
        }
        System.out.println("Using profile: " + profile.name());

        // Register core services
        MappingStore mappingStore = new MappingStore();
        HookRegistry hookRegistry = new HookRegistry();
        services.register(MappingStore.class, mappingStore);
        services.register(HookRegistry.class, hookRegistry);
        services.register(RevisionProfile.class, profile);

        // Load existing mappings if provided
        if (config.mappingsFile() != null && Files.exists(config.mappingsFile())) {
            System.out.println("Loading mappings from: " + config.mappingsFile());
            mappingStore.loadFromFile(config.mappingsFile());
        }

        // Load hook definitions if provided
        if (config.hooksFile() != null && Files.exists(config.hooksFile())) {
            System.out.println("Loading hooks from: " + config.hooksFile());
            hookRegistry.loadFromFile(config.hooksFile());
        }

        // Configure pipeline phases
        if (!config.skipRenaming()) {
            pipeline.addPhase(new HeuristicRenamer());
        }
        if (!config.skipHooks()) {
            pipeline.addPhase(new HookInjector());
        }
        if (config.decompile()) {
            pipeline.addPhase(new DecompilationPhase());
        }
    }

    /**
     * Run the full deobfuscation pipeline.
     */
    public void run() throws Exception {
        // 1. Load classes from input JAR
        System.out.println("Loading classes from JAR...");
        TransformContext context = new TransformContext(config, services);
        JarLoader.loadJar(config.inputJar(), context);
        System.out.println("  Loaded " + context.classes().size() + " classes");

        // 2. Execute transform pipeline
        System.out.println("\nExecuting pipeline (" + pipeline.phaseCount() + " phases)...\n");
        pipeline.execute(context);

        // 3. Write output
        System.out.println("\nWriting output...");
        Files.createDirectories(config.outputDir());

        // Find the applet main class for launcher generation
        String appletClass = findAppletClass(context);

        // Write output JAR if requested (runnable with embedded Launcher)
        if (config.outputJar() != null) {
            JarWriter.writeRunnableJar(context, config.outputJar(), appletClass);
        }

        // Export mappings
        MappingStore mappings = services.resolve(MappingStore.class);
        if (config.mappingsFile() != null) {
            MappingExporter.exportMappings(mappings, config.mappingsFile());
            System.out.println("  Mappings saved to: " + config.mappingsFile());
        }

        // Generate runnable project (with decompiled reference sources if --decompile)
        if (config.decompile()) {
            System.out.println("  Generating runnable project with decompiled sources...");
            ProjectGenerator.generate(context, config.outputDir());
        } else {
            // Just write classes and lib jar without decompilation
            ClassWriter.writeClasses(context, config.outputDir().resolve("classes"));
        }

        // Print stats
        System.out.println("\n--- Statistics ---");
        context.printStats();
    }

    /**
     * Find the most-derived concrete class extending java.applet.Applet.
     */
    private String findAppletClass(TransformContext context) {
        String bestCandidate = null;
        int bestDepth = -1;

        for (Map.Entry<String, ClassNode> entry : context.classes().entrySet()) {
            ClassNode cn = entry.getValue();
            int depth = 0;
            String superName = cn.superName;
            boolean isApplet = false;
            while (superName != null && depth < 20) {
                if (superName.contains("Applet")) {
                    isApplet = true;
                    break;
                }
                ClassNode superNode = context.getClass(superName);
                if (superNode == null) break;
                superName = superNode.superName;
                depth++;
            }
            if (isApplet && depth > bestDepth) {
                bestDepth = depth;
                bestCandidate = cn.name;
            }
        }
        return bestCandidate != null ? bestCandidate : "client";
    }
}
