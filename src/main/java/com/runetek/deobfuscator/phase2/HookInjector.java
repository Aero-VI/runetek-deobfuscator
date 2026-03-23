package com.runetek.deobfuscator.phase2;

import com.runetek.deobfuscator.engine.TransformContext;
import com.runetek.deobfuscator.engine.TransformPhase;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.Map;

/**
 * Phase 2: ASM Bytecode Injection Hooks.
 *
 * Injects event dispatch calls into target methods and field accesses
 * based on HookDefinitions from the HookRegistry. Also injects the
 * EventBus utility class into the output.
 *
 * Supports four hook types:
 *   - METHOD_ENTRY: fires at method start
 *   - METHOD_EXIT: fires before each return instruction
 *   - FIELD_GET: fires after each GETFIELD/GETSTATIC of the target field
 *   - FIELD_SET: fires before each PUTFIELD/PUTSTATIC of the target field
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

            // Process method hooks
            for (MethodNode mn : cn.methods) {
                if (mn.name.equals("<init>") || mn.name.equals("<clinit>")) continue;

                List<HookDefinition> methodHooks = registry.hooksForMethod(
                        className, mn.name, mn.desc);

                if (!methodHooks.isEmpty()) {
                    // Inject entry hooks
                    int entryCount = MethodHookVisitor.injectEntryHooks(mn, methodHooks);
                    // Inject exit hooks
                    int exitCount = MethodHookVisitor.injectExitHooks(mn, methodHooks);

                    int methodTotal = entryCount + exitCount;
                    if (methodTotal > 0) {
                        System.out.println("      " + mn.name + mn.desc +
                                " → " + methodTotal + " method hooks injected");
                        totalInjected += methodTotal;
                    }
                }

                // Process field hooks — scan all methods for field access instructions
                for (FieldNode fn : cn.fields) {
                    List<HookDefinition> fieldHooks = registry.hooksForField(className, fn.name);
                    if (fieldHooks.isEmpty()) continue;

                    int getCount = MethodHookVisitor.injectFieldGetHooks(
                            mn, fn.name, fn.desc, fieldHooks);
                    int setCount = MethodHookVisitor.injectFieldSetHooks(
                            mn, fn.name, fn.desc, fieldHooks);

                    int fieldTotal = getCount + setCount;
                    if (fieldTotal > 0) {
                        System.out.println("      " + mn.name + " → " + fieldTotal +
                                " field hooks for " + fn.name);
                        totalInjected += fieldTotal;
                    }
                }
            }
        }

        // Always inject the EventBus and EventListener classes
        ClassNode listenerInterface = EventBus.generateListenerInterface();
        context.addClass(EventBus.LISTENER_INTERNAL_NAME, listenerInterface);
        ClassNode eventBusClass = EventBus.generateEventBusClass();
        context.addClass(EventBus.INTERNAL_NAME, eventBusClass);
        System.out.println("  EventBus class injected: " + EventBus.INTERNAL_NAME);
        System.out.println("  EventListener interface injected: " + EventBus.LISTENER_INTERNAL_NAME);

        context.addHooksInjected(totalInjected);
        System.out.println("  Total hooks injected: " + totalInjected);
    }
}
