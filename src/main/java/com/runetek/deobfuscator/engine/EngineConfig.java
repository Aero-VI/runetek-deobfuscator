package com.runetek.deobfuscator.engine;

import java.nio.file.Path;

/**
 * Immutable configuration for the deobfuscator engine.
 * Built via the fluent Builder pattern.
 */
public final class EngineConfig {

    private final Path inputJar;
    private final Path outputDir;
    private final Path mappingsFile;
    private final Path hooksFile;
    private final Path outputJar;
    private final boolean skipRenaming;
    private final boolean skipHooks;
    private final boolean decompile;
    private final boolean verbose;

    private EngineConfig(Path inputJar, Path outputDir, Path mappingsFile, Path hooksFile,
                         Path outputJar, boolean skipRenaming, boolean skipHooks,
                         boolean decompile, boolean verbose) {
        this.inputJar = inputJar;
        this.outputDir = outputDir;
        this.mappingsFile = mappingsFile;
        this.hooksFile = hooksFile;
        this.outputJar = outputJar;
        this.skipRenaming = skipRenaming;
        this.skipHooks = skipHooks;
        this.decompile = decompile;
        this.verbose = verbose;
    }

    public Path inputJar() { return inputJar; }
    public Path outputDir() { return outputDir; }
    public Path mappingsFile() { return mappingsFile; }
    public Path hooksFile() { return hooksFile; }
    public Path outputJar() { return outputJar; }
    public boolean skipRenaming() { return skipRenaming; }
    public boolean skipHooks() { return skipHooks; }
    public boolean decompile() { return decompile; }
    public boolean verbose() { return verbose; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Path inputJar;
        private Path outputDir;
        private Path mappingsFile;
        private Path hooksFile;
        private Path outputJar;
        private boolean skipRenaming;
        private boolean skipHooks;
        private boolean decompile;
        private boolean verbose;

        public Builder inputJar(Path p) { this.inputJar = p; return this; }
        public Builder outputDir(Path p) { this.outputDir = p; return this; }
        public Builder mappingsFile(Path p) { this.mappingsFile = p; return this; }
        public Builder hooksFile(Path p) { this.hooksFile = p; return this; }
        public Builder outputJar(Path p) { this.outputJar = p; return this; }
        public Builder skipRenaming(boolean b) { this.skipRenaming = b; return this; }
        public Builder skipHooks(boolean b) { this.skipHooks = b; return this; }
        public Builder decompile(boolean b) { this.decompile = b; return this; }
        public Builder verbose(boolean b) { this.verbose = b; return this; }

        public EngineConfig build() {
            if (inputJar == null) throw new IllegalStateException("inputJar is required");
            if (outputDir == null) throw new IllegalStateException("outputDir is required");
            return new EngineConfig(inputJar, outputDir, mappingsFile, hooksFile, outputJar,
                    skipRenaming, skipHooks, decompile, verbose);
        }
    }
}
