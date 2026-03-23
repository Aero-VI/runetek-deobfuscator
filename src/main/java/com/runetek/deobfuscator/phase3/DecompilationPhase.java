package com.runetek.deobfuscator.phase3;

import com.runetek.deobfuscator.engine.TransformContext;
import com.runetek.deobfuscator.engine.TransformPhase;

/**
 * Phase 3: Decompilation Pipeline.
 *
 * Takes the ASM-mapped and hooked .jar and pipes it through CFR decompiler
 * to produce a clean, recompilable Maven project.
 *
 * This phase is only executed when --decompile is specified.
 * The actual decompilation is handled by ProjectGenerator, which is called
 * from the engine after all phases complete.
 *
 * This phase serves as a validation step: it verifies all classes can be
 * serialized to valid bytecode before decompilation.
 */
public class DecompilationPhase implements TransformPhase {

    @Override
    public String name() {
        return "Decompilation Validation";
    }

    @Override
    public void execute(TransformContext context) {
        System.out.println("  Validating " + context.classes().size() + " classes for decompilation...");

        int valid = 0;
        int invalid = 0;

        for (String className : context.classes().keySet()) {
            try {
                org.objectweb.asm.tree.ClassNode cn = context.getClass(className);
                // Test serialization
                com.runetek.deobfuscator.util.AsmUtil.toBytesNoFrames(cn);
                valid++;
            } catch (Exception e) {
                System.err.println("    Warning: " + className + " has invalid bytecode: " + e.getMessage());
                invalid++;
            }
        }

        System.out.println("    Valid:   " + valid);
        if (invalid > 0) {
            System.out.println("    Invalid: " + invalid + " (will be skipped during decompilation)");
        }
    }
}
