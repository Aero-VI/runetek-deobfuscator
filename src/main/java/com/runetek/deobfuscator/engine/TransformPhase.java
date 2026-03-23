package com.runetek.deobfuscator.engine;

/**
 * A single phase in the deobfuscation pipeline.
 * Inspired by C# middleware/pipeline pattern — each phase
 * transforms the context and passes it along.
 */
public interface TransformPhase {

    /** Human-readable name for logging */
    String name();

    /** Execute this phase, mutating the context */
    void execute(TransformContext context) throws Exception;
}
