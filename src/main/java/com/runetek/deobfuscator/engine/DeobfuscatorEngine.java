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

import java.nio.file.Files;

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

        // Write transformed classes
        ClassWriter.writeClasses(context, config.outputDir().resolve("classes"));

        // Write output JAR if requested
        if (config.outputJar() != null) {
            JarWriter.writeJar(context, config.outputJar(), null);
        }

        // Export mappings
        MappingStore mappings = services.resolve(MappingStore.class);
        if (config.mappingsFile() != null) {
            MappingExporter.exportMappings(mappings, config.mappingsFile());
            System.out.println("  Mappings saved to: " + config.mappingsFile());
        }

        // Generate re-compilable project if decompile is enabled
        if (config.decompile()) {
            System.out.println("  Generating Maven project with decompiled sources...");
            ProjectGenerator.generate(context, config.outputDir());
        }

        // Print stats
        System.out.println("\n--- Statistics ---");
        context.printStats();
    }
}
