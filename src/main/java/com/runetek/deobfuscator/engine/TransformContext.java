package com.runetek.deobfuscator.engine;

import org.objectweb.asm.tree.ClassNode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutable context passed through the transform pipeline.
 * Holds all loaded classes (as ASM ClassNodes) and shared state.
 */
public class TransformContext {

    private final EngineConfig config;
    private final ServiceRegistry services;

    /** Map of internal class name → ClassNode (mutable during transforms) */
    private final Map<String, ClassNode> classes = new LinkedHashMap<>();

    /** Accumulated statistics */
    private int classesRenamed;
    private int fieldsRenamed;
    private int methodsRenamed;
    private int hooksInjected;

    public TransformContext(EngineConfig config, ServiceRegistry services) {
        this.config = config;
        this.services = services;
    }

    public EngineConfig config() { return config; }
    public ServiceRegistry services() { return services; }

    public void addClass(String name, ClassNode node) {
        classes.put(name, node);
    }

    public ClassNode getClass(String name) {
        return classes.get(name);
    }

    public Map<String, ClassNode> classes() {
        return classes;
    }

    public Map<String, ClassNode> classesUnmodifiable() {
        return Collections.unmodifiableMap(classes);
    }

    // Stats
    public void incrementClassesRenamed() { classesRenamed++; }
    public void incrementFieldsRenamed() { fieldsRenamed++; }
    public void incrementMethodsRenamed() { methodsRenamed++; }
    public void incrementHooksInjected() { hooksInjected++; }
    public void addHooksInjected(int count) { hooksInjected += count; }

    public int classesRenamed() { return classesRenamed; }
    public int fieldsRenamed() { return fieldsRenamed; }
    public int methodsRenamed() { return methodsRenamed; }
    public int hooksInjected() { return hooksInjected; }

    public void printStats() {
        System.out.println("  Classes renamed:  " + classesRenamed);
        System.out.println("  Fields renamed:   " + fieldsRenamed);
        System.out.println("  Methods renamed:  " + methodsRenamed);
        System.out.println("  Hooks injected:   " + hooksInjected);
        System.out.println("  Total classes:    " + classes.size());
    }
}
