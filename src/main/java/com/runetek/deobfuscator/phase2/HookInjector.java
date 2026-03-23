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
 * Performs three types of injection:
 *   1. IP Hook — redirects socket connections to 127.0.0.1
 *   2. RSA Lobotomy — removes BigInteger.modPow() RSA encryption
 *   3. EventBus hooks — user-defined method/field event dispatching
 */
public class HookInjector implements TransformPhase {

    @Override
    public String name() {
        return "ASM Bytecode Injection Hooks";
    }

    @Override
    public void execute(TransformContext context) {
        HookRegistry registry = context.services().resolve(HookRegistry.class);

        // Phase 2a: IP Hook — redirect socket connections to localhost
        System.out.println("  [Phase 2a] IP Hook — scanning for socket connections...");
        int ipHooks = IPHookInjector.inject(context.classes());
        System.out.println("    IP hooks injected: " + ipHooks);

        // Phase 2b: RSA Lobotomy — neutralize RSA encryption
        System.out.println("  [Phase 2b] RSA Lobotomy — scanning for modPow calls...");
        int rsaPatches = RSALobotomizer.inject(context.classes());
        System.out.println("    RSA patches applied: " + rsaPatches);

        // Phase 2c: EventBus user-defined hooks
        if (registry.size() == 0) {
            System.out.println("  [Phase 2c] No user-defined hooks, injecting EventBus class only");
        } else {
            System.out.println("  [Phase 2c] Processing " + registry.size() + " user-defined hook definitions...");
        }

        int totalInjected = 0;

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

                if (!methodHooks.isEmpty()) {
                    int entryCount = MethodHookVisitor.injectEntryHooks(mn, methodHooks);
                    int exitCount = MethodHookVisitor.injectExitHooks(mn, methodHooks);

                    int methodTotal = entryCount + exitCount;
                    if (methodTotal > 0) {
                        System.out.println("      " + mn.name + mn.desc +
                                " -> " + methodTotal + " method hooks injected");
                        totalInjected += methodTotal;
                    }
                }

                for (FieldNode fn : cn.fields) {
                    List<HookDefinition> fieldHooks = registry.hooksForField(className, fn.name);
                    if (fieldHooks.isEmpty()) continue;

                    int getCount = MethodHookVisitor.injectFieldGetHooks(
                            mn, fn.name, fn.desc, fieldHooks);
                    int setCount = MethodHookVisitor.injectFieldSetHooks(
                            mn, fn.name, fn.desc, fieldHooks);

                    int fieldTotal = getCount + setCount;
                    if (fieldTotal > 0) {
                        System.out.println("      " + mn.name + " -> " + fieldTotal +
                                " field hooks for " + fn.name);
                        totalInjected += fieldTotal;
                    }
                }
            }
        }

        // Inject EventBus and EventListener classes
        ClassNode listenerInterface = EventBus.generateListenerInterface();
        context.addClass(EventBus.LISTENER_INTERNAL_NAME, listenerInterface);
        ClassNode eventBusClass = EventBus.generateEventBusClass();
        context.addClass(EventBus.INTERNAL_NAME, eventBusClass);
        System.out.println("  EventBus class injected: " + EventBus.INTERNAL_NAME);

        context.addHooksInjected(totalInjected + ipHooks + rsaPatches);
        System.out.println("  Total hooks/patches: " + (totalInjected + ipHooks + rsaPatches));
    }
}
