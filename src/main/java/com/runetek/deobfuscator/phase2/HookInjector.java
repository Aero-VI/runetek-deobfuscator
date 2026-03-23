package com.runetek.deobfuscator.phase2;

import com.runetek.deobfuscator.engine.TransformContext;
import com.runetek.deobfuscator.engine.TransformPhase;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.Map;

/**
 * Phase 2: ASM Bytecode Injection Hooks.
 *
 * Injects event dispatch calls into target methods based on
 * HookDefinitions from the HookRegistry. Also injects the
 * EventBus utility class into the output.
 */
public class HookInjector implements TransformPhase {

    @Override
    public String name() {
        return "ASM Bytecode Injection Hooks";
    }

    @Override
    public void execute(TransformContext context) {
        HookRegistry registry = context.services().resolve(HookRegistry.class);

        if (registry.size() == 0) {
            System.out.println("  No hooks defined, injecting EventBus class only");
        } else {
            System.out.println("  Processing " + registry.size() + " hook definitions...");
        }

        int totalInjected = 0;

        // Process each class for applicable hooks
        for (Map.Entry<String, ClassNode> entry : context.classes().entrySet()) {
            String className = entry.getKey();
            ClassNode cn = entry.getValue();

            List<HookDefinition> classHooks = registry.hooksForClass(className);
            if (classHooks.isEmpty()) continue;

            System.out.println("    Hooks for class: " + className);

            for (MethodNode mn : cn.methods) {
                if (mn.name.equals("<init>") || mn.name.equals("<clinit>")) continue;

                List<HookDefinition> methodHooks = registry.hooksForMethod(
                        className, mn.name, mn.desc);

                if (methodHooks.isEmpty()) continue;

                // Inject entry hooks
                int entryCount = MethodHookVisitor.injectEntryHooks(mn, methodHooks);
                // Inject exit hooks
                int exitCount = MethodHookVisitor.injectExitHooks(mn, methodHooks);

                int methodTotal = entryCount + exitCount;
                if (methodTotal > 0) {
                    System.out.println("      " + mn.name + mn.desc +
                            " → " + methodTotal + " hooks injected");
                    totalInjected += methodTotal;
                }
            }
        }

        // Always inject the EventBus class so hooks have a target
        ClassNode eventBusClass = EventBus.generateEventBusClass();
        context.addClass(EventBus.INTERNAL_NAME, eventBusClass);
        System.out.println("  EventBus class injected: " + EventBus.INTERNAL_NAME);

        context.addHooksInjected(totalInjected);
        System.out.println("  Total hooks injected: " + totalInjected);
    }
}
