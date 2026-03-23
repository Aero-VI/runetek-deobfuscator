package com.runetek.deobfuscator.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordered pipeline of transform phases.
 * Each phase executes in sequence, transforming the shared context.
 */
public class TransformPipeline {

    private final List<TransformPhase> phases = new ArrayList<>();

    public TransformPipeline addPhase(TransformPhase phase) {
        phases.add(phase);
        return this;
    }

    public void execute(TransformContext context) throws Exception {
        for (int i = 0; i < phases.size(); i++) {
            TransformPhase phase = phases.get(i);
            System.out.printf("[Phase %d/%d] %s%n", i + 1, phases.size(), phase.name());
            long start = System.currentTimeMillis();
            phase.execute(context);
            long elapsed = System.currentTimeMillis() - start;
            System.out.printf("  → completed in %dms%n", elapsed);
        }
    }

    public int phaseCount() {
        return phases.size();
    }
}
