package com.runetek.deobfuscator.phase1;

import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

import java.util.*;

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
     * Tracks used names per class+descriptor to prevent duplicate method names.
     */
    private static class MappingRemapper extends Remapper {
        private final MappingStore mappings;
        // Track used method names per "owner+desc" to avoid duplicates
        private final Map<String, Set<String>> usedMethodNames = new HashMap<String, Set<String>>();
        // Cache resolved names so the same (owner,name,desc) always maps to the same result
        private final Map<String, String> methodNameCache = new HashMap<String, String>();

        MappingRemapper(MappingStore mappings) {
            this.mappings = mappings;
        }

        @Override
        public String map(String internalName) {
            return mappings.resolveClass(internalName);
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            String cacheKey = "F:" + owner + "." + name + ":" + descriptor;
            String cached = methodNameCache.get(cacheKey);
            if (cached != null) return cached;

            String resolved = mappings.resolveField(owner, name, descriptor);

            // Deduplicate fields with same name in same class
            String usageKey = "F:" + owner;
            Set<String> used = usedMethodNames.get(usageKey);
            if (used == null) {
                used = new HashSet<String>();
                usedMethodNames.put(usageKey, used);
            }

            String finalName = resolved;
            if (used.contains(resolved)) {
                int suffix = 1;
                while (used.contains(resolved + suffix)) suffix++;
                finalName = resolved + suffix;
            }
            used.add(finalName);
            methodNameCache.put(cacheKey, finalName);
            return finalName;
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            if (name.startsWith("<")) return name;

            // Check cache first for consistency
            String cacheKey = owner + "." + name + "+" + descriptor;
            String cached = methodNameCache.get(cacheKey);
            if (cached != null) return cached;

            String resolved = mappings.resolveMethod(owner, name, descriptor);

            // Deduplicate: if this name+desc is already used in this class, suffix it
            String usageKey = owner + "+" + descriptor;
            Set<String> used = usedMethodNames.get(usageKey);
            if (used == null) {
                used = new HashSet<String>();
                usedMethodNames.put(usageKey, used);
            }

            String finalName = resolved;
            if (used.contains(resolved)) {
                // Append incrementing suffix until unique
                int suffix = 1;
                while (used.contains(resolved + suffix)) {
                    suffix++;
                }
                finalName = resolved + suffix;
            }
            used.add(finalName);
            methodNameCache.put(cacheKey, finalName);
            return finalName;
        }
    }
}
