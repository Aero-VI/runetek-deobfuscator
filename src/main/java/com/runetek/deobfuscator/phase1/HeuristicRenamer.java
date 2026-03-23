package com.runetek.deobfuscator.phase1;

import com.runetek.deobfuscator.engine.TransformContext;
import com.runetek.deobfuscator.engine.TransformPhase;
import org.objectweb.asm.tree.ClassNode;

import java.util.HashMap;
import java.util.Map;

/**
 * Phase 1: Heuristic Mapping & Renaming.
 *
 * Analyzes all loaded classes to identify obfuscated names and
 * applies heuristic-based renaming using structural pattern matching.
 *
 * Pipeline:
 *   1. Analyze each class with ClassHeuristicAnalyzer
 *   2. Analyze fields with FieldPatternMatcher
 *   3. Analyze methods with MethodSignatureMatcher
 *   4. Build complete mapping set
 *   5. Apply mappings via RenamingTransformer (ASM Remapper)
 */
public class HeuristicRenamer implements TransformPhase {

    @Override
    public String name() {
        return "Heuristic Mapping & Renaming";
    }

    @Override
    public void execute(TransformContext context) {
        MappingStore mappings = context.services().resolve(MappingStore.class);
        ClassHeuristicAnalyzer classAnalyzer = new ClassHeuristicAnalyzer();
        FieldPatternMatcher fieldMatcher = new FieldPatternMatcher();
        MethodSignatureMatcher methodMatcher = new MethodSignatureMatcher();

        // Track name collisions
        Map<String, Integer> nameUsage = new HashMap<>();

        // Step 1: Analyze all classes for class-level renames
        System.out.println("  Analyzing class patterns...");
        for (Map.Entry<String, ClassNode> entry : context.classesUnmodifiable().entrySet()) {
            String originalName = entry.getKey();
            ClassNode cn = entry.getValue();

            // Skip already-mapped classes
            if (mappings.hasClassMapping(originalName)) continue;

            // Try heuristic analysis
            String suggestedName = classAnalyzer.analyze(cn);
            if (suggestedName != null) {
                // Handle name collisions by appending a counter
                int count = nameUsage.getOrDefault(suggestedName, 0);
                nameUsage.put(suggestedName, count + 1);
                String finalName = count == 0 ? suggestedName : suggestedName + count;

                // Preserve package structure if any
                String pkg = "";
                int lastSlash = originalName.lastIndexOf('/');
                if (lastSlash >= 0) {
                    pkg = originalName.substring(0, lastSlash + 1);
                }

                mappings.mapClass(originalName, pkg + finalName);
                context.incrementClassesRenamed();
            }
        }
        System.out.println("    Classes identified: " + context.classesRenamed());

        // Step 2: Analyze fields and methods per class
        System.out.println("  Analyzing field and method patterns...");
        for (Map.Entry<String, ClassNode> entry : context.classesUnmodifiable().entrySet()) {
            String className = entry.getKey();
            ClassNode cn = entry.getValue();
            String resolvedClassName = mappings.resolveClass(className);

            // Extract just the simple name for context
            String simpleName = resolvedClassName;
            int slash = simpleName.lastIndexOf('/');
            if (slash >= 0) simpleName = simpleName.substring(slash + 1);

            // Analyze fields
            Map<String, String> fieldSuggestions = fieldMatcher.analyzeFields(cn, simpleName);
            for (Map.Entry<String, String> fs : fieldSuggestions.entrySet()) {
                // Find the field descriptor
                cn.fields.stream()
                    .filter(f -> f.name.equals(fs.getKey()))
                    .findFirst()
                    .ifPresent(f -> {
                        mappings.mapField(className, f.name, f.desc, fs.getValue());
                        context.incrementFieldsRenamed();
                    });
            }

            // Analyze methods
            Map<String, String> methodSuggestions = methodMatcher.analyzeMethods(cn, simpleName);
            for (Map.Entry<String, String> ms : methodSuggestions.entrySet()) {
                String[] parts = ms.getKey().split("\\+", 2);
                if (parts.length == 2) {
                    mappings.mapMethod(className, parts[0], parts[1], ms.getValue());
                    context.incrementMethodsRenamed();
                }
            }
        }
        System.out.println("    Fields renamed: " + context.fieldsRenamed());
        System.out.println("    Methods renamed: " + context.methodsRenamed());

        // Step 3: Apply all mappings via ASM Remapper
        System.out.println("  Applying mappings...");
        if (mappings.totalMappings() > 0) {
            RenamingTransformer transformer = new RenamingTransformer(mappings);
            Map<String, ClassNode> renamedClasses = transformer.applyMappings(context.classes());

            // Replace classes in context
            context.classes().clear();
            context.classes().putAll(renamedClasses);
            System.out.println("    Applied " + mappings.totalMappings() + " total mappings");
        } else {
            System.out.println("    No mappings to apply");
        }
    }
}
