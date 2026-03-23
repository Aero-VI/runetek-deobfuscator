package com.runetek.deobfuscator.phase1;

import com.runetek.deobfuscator.dictionary.RevisionProfile;
import com.runetek.deobfuscator.engine.TransformContext;
import com.runetek.deobfuscator.engine.TransformPhase;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 1: Heuristic Mapping &amp; Renaming.
 *
 * Analyzes all loaded classes to identify obfuscated names and
 * applies heuristic-based renaming using structural pattern matching
 * and string literal analysis.
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

        // Load profile-specific heuristic patterns
        if (context.services().has(RevisionProfile.class)) {
            RevisionProfile profile = context.services().resolve(RevisionProfile.class);
            List<ClassHeuristicAnalyzer.HeuristicPattern> profilePatterns = profile.getClassPatterns();
            for (ClassHeuristicAnalyzer.HeuristicPattern pattern : profilePatterns) {
                classAnalyzer.addPattern(pattern);
            }
            System.out.println("  Loaded " + profilePatterns.size() + " profile-specific patterns from " + profile.name());

            // Apply base mappings from profile
            Map<String, String> baseMappings = profile.getBaseClassMappings();
            for (Map.Entry<String, String> entry : baseMappings.entrySet()) {
                if (!mappings.hasClassMapping(entry.getKey())) {
                    mappings.mapClass(entry.getKey(), entry.getValue());
                }
            }
        }

        Map<String, Integer> nameUsage = new HashMap<String, Integer>();

        // Step 1: Analyze all classes for class-level renames
        System.out.println("  Analyzing class patterns...");
        for (Map.Entry<String, ClassNode> entry : context.classesUnmodifiable().entrySet()) {
            String originalName = entry.getKey();
            ClassNode cn = entry.getValue();

            if (mappings.hasClassMapping(originalName)) continue;

            String suggestedName = classAnalyzer.analyze(cn);

            if (suggestedName == null) {
                suggestedName = StringLiteralAnalyzer.suggestClassName(cn);
            }

            if (suggestedName != null) {
                Integer count = nameUsage.get(suggestedName);
                int countVal = count == null ? 0 : count;
                nameUsage.put(suggestedName, countVal + 1);
                String finalName = countVal == 0 ? suggestedName : suggestedName + countVal;

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

            String simpleName = resolvedClassName;
            int slash = simpleName.lastIndexOf('/');
            if (slash >= 0) simpleName = simpleName.substring(slash + 1);

            // Analyze fields
            Map<String, String> fieldSuggestions = fieldMatcher.analyzeFields(cn, simpleName);
            for (Map.Entry<String, String> fs : fieldSuggestions.entrySet()) {
                for (FieldNode f : cn.fields) {
                    if (f.name.equals(fs.getKey())) {
                        mappings.mapField(className, f.name, f.desc, fs.getValue());
                        context.incrementFieldsRenamed();
                        break;
                    }
                }
            }

            // Analyze methods via signature matching
            Map<String, String> methodSuggestions = methodMatcher.analyzeMethods(cn, simpleName);

            // Also try string literal analysis
            Map<String, String> stringMethodSuggestions = StringLiteralAnalyzer.suggestMethodNames(cn);
            for (Map.Entry<String, String> sms : stringMethodSuggestions.entrySet()) {
                if (!methodSuggestions.containsKey(sms.getKey())) {
                    methodSuggestions.put(sms.getKey(), sms.getValue());
                }
            }

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

            context.classes().clear();
            context.classes().putAll(renamedClasses);
            System.out.println("    Applied " + mappings.totalMappings() + " total mappings");

            // Step 4: Fix Class.forName("obfuscated") string constants
            int forNameFixes = ClassForNameFixer.fix(context.classes(), mappings.classMappings());
            if (forNameFixes > 0) {
                System.out.println("    Fixed " + forNameFixes + " Class.forName() string constants");
            }
        } else {
            System.out.println("    No mappings to apply");
        }
    }
}
