package com.runetek.deobfuscator;

import com.runetek.deobfuscator.engine.*;
import com.runetek.deobfuscator.phase1.*;
import com.runetek.deobfuscator.phase2.*;
import com.runetek.deobfuscator.util.AsmUtil;
import com.runetek.deobfuscator.util.JarLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the deobfuscator engine.
 * Creates synthetic obfuscated classes to test the pipeline.
 */
class EngineTest {

    @TempDir
    Path tempDir;

    /**
     * Test: MappingStore round-trip (save/load JSON)
     */
    @Test
    void testMappingStoreRoundTrip() {
        MappingStore store = new MappingStore();
        store.mapClass("a", "Client");
        store.mapClass("b", "Widget");
        store.mapField("a", "c", "I", "gameState");
        store.mapMethod("a", "d", "(II)V", "processLogin");

        Path file = tempDir.resolve("mappings.json");
        store.saveToFile(file);

        MappingStore loaded = new MappingStore();
        loaded.loadFromFile(file);

        assertEquals("Client", loaded.resolveClass("a"));
        assertEquals("Widget", loaded.resolveClass("b"));
        assertEquals("gameState", loaded.resolveField("a", "c", "I"));
        assertEquals("processLogin", loaded.resolveMethod("a", "d", "(II)V"));
    }

    /**
     * Test: ClassHeuristicAnalyzer identifies a Buffer-like class
     */
    @Test
    void testHeuristicAnalyzerIdentifiesBuffer() {
        ClassNode cn = createBufferClass();
        ClassHeuristicAnalyzer analyzer = new ClassHeuristicAnalyzer();
        String result = analyzer.analyze(cn);
        assertEquals("Buffer", result);
    }

    /**
     * Test: ClassHeuristicAnalyzer identifies a Node-like class
     */
    @Test
    void testHeuristicAnalyzerIdentifiesNode() {
        ClassNode cn = createNodeClass();
        ClassHeuristicAnalyzer analyzer = new ClassHeuristicAnalyzer();
        String result = analyzer.analyze(cn);
        assertEquals("Node", result);
    }

    /**
     * Test: FieldPatternMatcher detects obfuscated names
     */
    @Test
    void testObfuscatedNameDetection() {
        assertTrue(FieldPatternMatcher.isObfuscatedName("a"));
        assertTrue(FieldPatternMatcher.isObfuscatedName("ab"));
        assertTrue(FieldPatternMatcher.isObfuscatedName("abc"));
        assertFalse(FieldPatternMatcher.isObfuscatedName("gameState"));
        assertFalse(FieldPatternMatcher.isObfuscatedName("processLogin"));
    }

    /**
     * Test: RenamingTransformer applies class renames
     */
    @Test
    void testRenamingTransformer() {
        MappingStore store = new MappingStore();
        store.mapClass("a", "Buffer");
        store.mapField("a", "b", "[B", "payload");
        store.mapField("a", "c", "I", "position");

        ClassNode cn = createBufferClass();

        Map<String, ClassNode> classes = Map.of("a", cn);
        RenamingTransformer transformer = new RenamingTransformer(store);
        Map<String, ClassNode> result = transformer.applyMappings(classes);

        assertTrue(result.containsKey("Buffer"));
        ClassNode renamed = result.get("Buffer");
        assertEquals("Buffer", renamed.name);
    }

    /**
     * Test: HookRegistry loads and queries hooks
     */
    @Test
    void testHookRegistry() {
        HookRegistry registry = new HookRegistry();
        registry.register(HookDefinition.builder("onTick")
                .type(HookDefinition.HookType.METHOD_ENTRY)
                .targetClass("Client")
                .targetMember("tick")
                .targetDescriptor("()V")
                .callbackClass("com/mods/Hooks")
                .callbackMethod("onTick")
                .build());

        assertEquals(1, registry.size());
        assertEquals(1, registry.hooksForClass("Client").size());
        assertEquals(0, registry.hooksForClass("Widget").size());
        assertEquals(1, registry.hooksForMethod("Client", "tick", "()V").size());
    }

    /**
     * Test: MethodHookVisitor injects entry hook
     */
    @Test
    void testMethodEntryHookInjection() {
        // Create a simple method
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
        mn.instructions.add(new InsnNode(Opcodes.RETURN));

        HookDefinition hook = HookDefinition.builder("onTick")
                .type(HookDefinition.HookType.METHOD_ENTRY)
                .targetClass("Client")
                .targetMember("tick")
                .targetDescriptor("()V")
                .build();

        int injected = MethodHookVisitor.injectEntryHooks(mn, List.of(hook));
        assertEquals(1, injected);

        // Verify the instructions were added (should have more than just RETURN now)
        assertTrue(mn.instructions.size() > 1);
    }

    /**
     * Test: MethodHookVisitor injects exit hooks before all return instructions
     */
    @Test
    void testMethodExitHookInjection() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "process", "()V", null, null);
        // Two return paths
        LabelNode label = new LabelNode();
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.add(new JumpInsnNode(Opcodes.IFNULL, label));
        mn.instructions.add(new InsnNode(Opcodes.RETURN)); // early return
        mn.instructions.add(label);
        mn.instructions.add(new InsnNode(Opcodes.RETURN)); // normal return

        HookDefinition hook = HookDefinition.builder("onProcessExit")
                .type(HookDefinition.HookType.METHOD_EXIT)
                .targetClass("Client")
                .targetMember("process")
                .targetDescriptor("()V")
                .build();

        int injected = MethodHookVisitor.injectExitHooks(mn, List.of(hook));
        assertEquals(2, injected); // One hook injected before each RETURN
    }

    /**
     * Test: MethodHookVisitor injects field SET hooks
     */
    @Test
    void testFieldSetHookInjection() {
        // Create a method that sets a field
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "update", "()V", null, null);
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.add(new InsnNode(Opcodes.ICONST_1));
        mn.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, "Client", "gameState", "I"));
        mn.instructions.add(new InsnNode(Opcodes.RETURN));

        HookDefinition hook = HookDefinition.builder("onGameStateChange")
                .type(HookDefinition.HookType.FIELD_SET)
                .targetClass("Client")
                .targetMember("gameState")
                .targetDescriptor("I")
                .build();

        int injected = MethodHookVisitor.injectFieldSetHooks(mn, "gameState", "I", List.of(hook));
        assertEquals(1, injected);
        assertTrue(mn.instructions.size() > 4); // More instructions now
    }

    /**
     * Test: MethodHookVisitor injects field GET hooks
     */
    @Test
    void testFieldGetHookInjection() {
        // Create a method that reads a field
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "getState", "()I", null, null);
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, "Client", "gameState", "I"));
        mn.instructions.add(new InsnNode(Opcodes.IRETURN));

        HookDefinition hook = HookDefinition.builder("onGameStateRead")
                .type(HookDefinition.HookType.FIELD_GET)
                .targetClass("Client")
                .targetMember("gameState")
                .targetDescriptor("I")
                .build();

        int injected = MethodHookVisitor.injectFieldGetHooks(mn, "gameState", "I", List.of(hook));
        assertEquals(1, injected);
        assertTrue(mn.instructions.size() > 3);
    }

    /**
     * Test: EventBus class generation produces valid bytecode with listener support
     */
    @Test
    void testEventBusGeneration() {
        ClassNode eventBus = EventBus.generateEventBusClass();
        assertEquals("com/runetek/hooks/EventBus", eventBus.name);

        // Should have fire, register, unregister, <init>, <clinit> methods
        assertTrue(eventBus.methods.size() >= 4);

        // Verify fire method exists
        boolean hasFire = eventBus.methods.stream()
                .anyMatch(m -> m.name.equals("fire") && m.desc.equals(EventBus.FIRE_DESCRIPTOR));
        assertTrue(hasFire, "EventBus should have fire method");

        // Verify register method exists
        boolean hasRegister = eventBus.methods.stream()
                .anyMatch(m -> m.name.equals("register"));
        assertTrue(hasRegister, "EventBus should have register method");

        // Verify unregister method exists
        boolean hasUnregister = eventBus.methods.stream()
                .anyMatch(m -> m.name.equals("unregister"));
        assertTrue(hasUnregister, "EventBus should have unregister method");

        // Should compile to valid bytes
        byte[] bytes = AsmUtil.toBytesNoFrames(eventBus);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        // Should be loadable back
        ClassNode loaded = JarLoader.loadClass(bytes);
        assertEquals("com/runetek/hooks/EventBus", loaded.name);
    }

    /**
     * Test: EventListener interface generation
     */
    @Test
    void testEventListenerGeneration() {
        ClassNode listener = EventBus.generateListenerInterface();
        assertEquals("com/runetek/hooks/EventListener", listener.name);
        assertTrue((listener.access & Opcodes.ACC_INTERFACE) != 0);
        assertTrue((listener.access & Opcodes.ACC_ABSTRACT) != 0);

        // Should have the onEvent method
        boolean hasOnEvent = listener.methods.stream()
                .anyMatch(m -> m.name.equals("onEvent"));
        assertTrue(hasOnEvent, "EventListener should have onEvent method");

        // Should compile to valid bytes
        byte[] bytes = AsmUtil.toBytesNoFrames(listener);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
    }

    /**
     * Test: Full pipeline with synthetic JAR
     */
    @Test
    void testFullPipeline() throws Exception {
        // Create a synthetic JAR with obfuscated classes
        Path inputJar = createSyntheticJar();
        Path outputDir = tempDir.resolve("output");

        EngineConfig config = EngineConfig.builder()
                .inputJar(inputJar)
                .outputDir(outputDir)
                .build();

        DeobfuscatorEngine engine = new DeobfuscatorEngine(config);
        engine.run();

        // Verify output was created
        assertTrue(outputDir.resolve("classes").toFile().exists());
    }

    /**
     * Test: Full pipeline with hook injection
     */
    @Test
    void testFullPipelineWithHooks() throws Exception {
        // Create synthetic JAR
        Path inputJar = createSyntheticJar();
        Path outputDir = tempDir.resolve("output-hooks");

        // Write hook definitions
        Path hooksFile = tempDir.resolve("hooks.json");
        String hooksJson = """
                [
                  {
                    "name": "onBufferRead",
                    "type": "METHOD_ENTRY",
                    "targetClass": "Buffer",
                    "targetMember": "read",
                    "targetDescriptor": "()I"
                  }
                ]
                """;
        Files.writeString(hooksFile, hooksJson);

        EngineConfig config = EngineConfig.builder()
                .inputJar(inputJar)
                .outputDir(outputDir)
                .hooksFile(hooksFile)
                .build();

        DeobfuscatorEngine engine = new DeobfuscatorEngine(config);
        engine.run();

        assertTrue(outputDir.resolve("classes").toFile().exists());
    }

    /**
     * Test: Hook definitions JSON round-trip
     */
    @Test
    void testHookDefinitionsFromJson() throws Exception {
        Path hooksFile = tempDir.resolve("hooks.json");
        String json = """
                [
                  {
                    "name": "onLogin",
                    "type": "METHOD_ENTRY",
                    "targetClass": "Client",
                    "targetMember": "processLogin",
                    "targetDescriptor": "(II)V"
                  },
                  {
                    "name": "onGameStateChange",
                    "type": "FIELD_SET",
                    "targetClass": "Client",
                    "targetMember": "gameState",
                    "targetDescriptor": "I"
                  }
                ]
                """;
        Files.writeString(hooksFile, json);

        HookRegistry registry = new HookRegistry();
        registry.loadFromFile(hooksFile);

        assertEquals(2, registry.size());
        assertEquals(1, registry.hooksForMethod("Client", "processLogin", "(II)V").size());
        assertEquals(1, registry.hooksForField("Client", "gameState").size());
    }

    // ---- Helper methods to create synthetic obfuscated classes ----

    private ClassNode createBufferClass() {
        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V21;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "a";
        cn.superName = "java/lang/Object";

        // byte[] field (data)
        cn.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "b", "[B", null, null));
        // int field (position)
        cn.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "c", "I", null, null));

        // Multiple read/write methods
        for (int i = 0; i < 6; i++) {
            MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "m" + i, "()I", null, null);
            mn.instructions.add(new InsnNode(Opcodes.ICONST_0));
            mn.instructions.add(new InsnNode(Opcodes.IRETURN));
            cn.methods.add(mn);
        }

        return cn;
    }

    private ClassNode createNodeClass() {
        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V21;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "b";
        cn.superName = "java/lang/Object";

        // Self-referencing fields (next, previous)
        cn.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "a", "Lb;", null, null));
        cn.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "b", "Lb;", null, null));
        // Key field
        cn.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "c", "J", null, null));

        return cn;
    }

    private Path createSyntheticJar() throws Exception {
        Path jarPath = tempDir.resolve("obfuscated.jar");

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
            // Add Buffer class
            ClassNode bufferClass = createBufferClass();
            byte[] bytes = AsmUtil.toBytesNoFrames(bufferClass);
            jos.putNextEntry(new JarEntry("a.class"));
            jos.write(bytes);
            jos.closeEntry();

            // Add Node class
            ClassNode nodeClass = createNodeClass();
            bytes = AsmUtil.toBytesNoFrames(nodeClass);
            jos.putNextEntry(new JarEntry("b.class"));
            jos.write(bytes);
            jos.closeEntry();

            // Add a generic class
            ClassNode genericClass = new ClassNode();
            genericClass.version = Opcodes.V21;
            genericClass.access = Opcodes.ACC_PUBLIC;
            genericClass.name = "c";
            genericClass.superName = "java/lang/Object";
            genericClass.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "x", "I", null, null));
            MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                    "java/lang/Object", "<init>", "()V", false));
            mn.instructions.add(new InsnNode(Opcodes.RETURN));
            genericClass.methods.add(mn);
            bytes = AsmUtil.toBytesNoFrames(genericClass);
            jos.putNextEntry(new JarEntry("c.class"));
            jos.write(bytes);
            jos.closeEntry();
        }

        return jarPath;
    }
}
