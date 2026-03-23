package com.aeroverra.deobfuscator.transform;

import org.objectweb.asm.tree.ClassNode;
import java.util.Map;

public class TransformPipeline {

    public void run(Map<String, ClassNode> classes) {
        System.out.println("  [Step 1] Renaming obfuscated classes...");
        ClassRenamer renamer = new ClassRenamer();
        Map<String, ClassNode> renamed = renamer.rename(classes);
        classes.clear();
        classes.putAll(renamed);
        System.out.println("    Renamed " + renamer.getRenameCount() + " classes");

        System.out.println("  [Step 2] Removing opaque predicates...");
        int removed = OpaquePredicateRemover.process(classes);
        System.out.println("    Removed " + removed + " opaque predicates");

        System.out.println("  [Step 3] Fixing method/field collisions...");
        int collisions = CollisionFixer.fix(classes);
        System.out.println("    Fixed " + collisions + " collisions");

        System.out.println("  [Step 4] Cleaning dead code...");
        int cleaned = DeadCodeCleaner.clean(classes);
        System.out.println("    Cleaned " + cleaned + " methods");

        // Note: Return-type overloading is handled in post-processing (SourceFixer)
        // rather than bytecode renaming, as renaming confuses Vineflower's decompiler.
    }
}
