package com.runetek.deobfuscator.phase1;

import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Applies name mappings to all classes using ASM's Remapper infrastructure.
 * This ensures all references (field accesses, method calls, type descriptors)
 * are consistently updated across the entire classpath.
 */
public class RenamingTransformer {

    private final MappingStore mappings;

    public RenamingTransformer(MappingStore mappings) {
        this.mappings = mappings;
    }

    /**
     * Apply all mappings to the given set of classes.
     * Returns a new map with renamed classes.
     */
    public Map<String, ClassNode> applyMappings(Map<String, ClassNode> classes) {
        // Build a Remapper from our mappings
        Remapper remapper = new MappingRemapper(mappings);

        Map<String, ClassNode> result = new LinkedHashMap<>();

        for (Map.Entry<String, ClassNode> entry : classes.entrySet()) {
            ClassNode original = entry.getValue();
            ClassNode renamed = new ClassNode();

            // ClassRemapper applies the remapper to all names in the class
            ClassRemapper classRemapper = new ClassRemapper(renamed, remapper);
            original.accept(classRemapper);

            // Store under the new name
            String newName = remapper.map(entry.getKey());
            if (newName == null) newName = entry.getKey();
            result.put(newName, renamed);
        }

        return result;
    }

    /**
     * Custom Remapper that uses our MappingStore.
     */
    private static class MappingRemapper extends Remapper {
        private final MappingStore mappings;

        MappingRemapper(MappingStore mappings) {
            this.mappings = mappings;
        }

        @Override
        public String map(String internalName) {
            return mappings.resolveClass(internalName);
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            return mappings.resolveField(owner, name, descriptor);
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            // Don't remap constructors or special methods
            if (name.startsWith("<")) return name;
            return mappings.resolveMethod(owner, name, descriptor);
        }
    }
}
