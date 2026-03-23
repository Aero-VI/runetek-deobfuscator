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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the deobfuscator engine.
 */
class EngineTest {

    @TempDir
    Path tempDir;

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

    @Test
    void testHeuristicAnalyzerIdentifiesBuffer() {
        ClassNode cn = createBufferClass();
        ClassHeuristicAnalyzer analyzer = new ClassHeuristicAnalyzer();
        String result = analyzer.analyze(cn);
        assertEquals("Buffer", result);
    }

    @Test
    void testHeuristicAnalyzerIdentifiesNode() {
        ClassNode cn = createNodeClass();
        ClassHeuristicAnalyzer analyzer = new ClassHeuristicAnalyzer();
        String result = analyzer.analyze(cn);
        assertEquals("Node", result);
    }

    @Test
    void testObfuscatedNameDetection() {
        assertTrue(FieldPatternMatcher.isObfuscatedName("a"));
        assertTrue(FieldPatternMatcher.isObfuscatedName("ab"));
        assertTrue(FieldPatternMatcher.isObfuscatedName("abc"));
        assertFalse(FieldPatternMatcher.isObfuscatedName("gameState"));
        assertFalse(FieldPatternMatcher.isObfuscatedName("processLogin"));
    }

    @Test
    void testRenamingTransformer() {
        MappingStore store = new MappingStore();
        store.mapClass("a", "Buffer");
        store.mapField("a", "b", "[B", "payload");
        store.mapField("a", "c", "I", "position");

        ClassNode cn = createBufferClass();

        Map<String, ClassNode> classes = new LinkedHashMap<String, ClassNode>();
        classes.put("a", cn);
        RenamingTransformer transformer = new RenamingTransformer(store);
        Map<String, ClassNode> result = transformer.applyMappings(classes);

        assertTrue(result.containsKey("Buffer"));
        ClassNode renamed = result.get("Buffer");
        assertEquals("Buffer", renamed.name);
    }

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

    @Test
    void testMethodEntryHookInjection() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
        mn.instructions.add(new InsnNode(Opcodes.RETURN));

        HookDefinition hook = HookDefinition.builder("onTick")
                .type(HookDefinition.HookType.METHOD_ENTRY)
                .targetClass("Client")
                .targetMember("tick")
                .targetDescriptor("()V")
                .build();

        int injected = MethodHookVisitor.injectEntryHooks(mn, Arrays.asList(hook));
        assertEquals(1, injected);
        assertTrue(mn.instructions.size() > 1);
    }

    @Test
    void testMethodExitHookInjection() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "process", "()V", null, null);
        LabelNode label = new LabelNode();
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.add(new JumpInsnNode(Opcodes.IFNULL, label));
        mn.instructions.add(new InsnNode(Opcodes.RETURN));
        mn.instructions.add(label);
        mn.instructions.add(new InsnNode(Opcodes.RETURN));

        HookDefinition hook = HookDefinition.builder("onProcessExit")
                .type(HookDefinition.HookType.METHOD_EXIT)
                .targetClass("Client")
                .targetMember("process")
                .targetDescriptor("()V")
                .build();

        int injected = MethodHookVisitor.injectExitHooks(mn, Arrays.asList(hook));
        assertEquals(2, injected);
    }

    @Test
    void testFieldSetHookInjection() {
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

        int injected = MethodHookVisitor.injectFieldSetHooks(mn, "gameState", "I", Arrays.asList(hook));
        assertEquals(1, injected);
        assertTrue(mn.instructions.size() > 4);
    }

    @Test
    void testFieldGetHookInjection() {
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

        int injected = MethodHookVisitor.injectFieldGetHooks(mn, "gameState", "I", Arrays.asList(hook));
        assertEquals(1, injected);
        assertTrue(mn.instructions.size() > 3);
    }

    @Test
    void testEventBusGeneration() {
        ClassNode eventBus = EventBus.generateEventBusClass();
        assertEquals("com/runetek/hooks/EventBus", eventBus.name);
        assertTrue(eventBus.methods.size() >= 4);

        boolean hasFire = false;
        boolean hasRegister = false;
        boolean hasUnregister = false;
        for (MethodNode m : eventBus.methods) {
            if ("fire".equals(m.name) && EventBus.FIRE_DESCRIPTOR.equals(m.desc)) hasFire = true;
            if ("register".equals(m.name)) hasRegister = true;
            if ("unregister".equals(m.name)) hasUnregister = true;
        }
        assertTrue(hasFire, "EventBus should have fire method");
        assertTrue(hasRegister, "EventBus should have register method");
        assertTrue(hasUnregister, "EventBus should have unregister method");

        byte[] bytes = AsmUtil.toBytesNoFrames(eventBus);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        ClassNode loaded = JarLoader.loadClass(bytes);
        assertEquals("com/runetek/hooks/EventBus", loaded.name);
    }

    @Test
    void testEventListenerGeneration() {
        ClassNode listener = EventBus.generateListenerInterface();
        assertEquals("com/runetek/hooks/EventListener", listener.name);
        assertTrue((listener.access & Opcodes.ACC_INTERFACE) != 0);
        assertTrue((listener.access & Opcodes.ACC_ABSTRACT) != 0);

        boolean hasOnEvent = false;
        for (MethodNode m : listener.methods) {
            if ("onEvent".equals(m.name)) hasOnEvent = true;
        }
        assertTrue(hasOnEvent, "EventListener should have onEvent method");

        byte[] bytes = AsmUtil.toBytesNoFrames(listener);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
    }

    @Test
    void testFullPipeline() throws Exception {
        Path inputJar = createSyntheticJar();
        Path outputDir = tempDir.resolve("output");

        EngineConfig config = EngineConfig.builder()
                .inputJar(inputJar)
                .outputDir(outputDir)
                .build();

        DeobfuscatorEngine engine = new DeobfuscatorEngine(config);
        engine.run();

        assertTrue(outputDir.resolve("classes").toFile().exists());
    }

    @Test
    void testFullPipelineWithHooks() throws Exception {
        Path inputJar = createSyntheticJar();
        Path outputDir = tempDir.resolve("output-hooks");

        Path hooksFile = tempDir.resolve("hooks.json");
        String hooksJson = "[\n"
                + "  {\n"
                + "    \"name\": \"onBufferRead\",\n"
                + "    \"type\": \"METHOD_ENTRY\",\n"
                + "    \"targetClass\": \"Buffer\",\n"
                + "    \"targetMember\": \"read\",\n"
                + "    \"targetDescriptor\": \"()I\"\n"
                + "  }\n"
                + "]";
        Files.write(hooksFile, hooksJson.getBytes(StandardCharsets.UTF_8));

        EngineConfig config = EngineConfig.builder()
                .inputJar(inputJar)
                .outputDir(outputDir)
                .hooksFile(hooksFile)
                .build();

        DeobfuscatorEngine engine = new DeobfuscatorEngine(config);
        engine.run();

        assertTrue(outputDir.resolve("classes").toFile().exists());
    }

    @Test
    void testHookDefinitionsFromJson() throws Exception {
        Path hooksFile = tempDir.resolve("hooks.json");
        String json = "[\n"
                + "  {\n"
                + "    \"name\": \"onLogin\",\n"
                + "    \"type\": \"METHOD_ENTRY\",\n"
                + "    \"targetClass\": \"Client\",\n"
                + "    \"targetMember\": \"processLogin\",\n"
                + "    \"targetDescriptor\": \"(II)V\"\n"
                + "  },\n"
                + "  {\n"
                + "    \"name\": \"onGameStateChange\",\n"
                + "    \"type\": \"FIELD_SET\",\n"
                + "    \"targetClass\": \"Client\",\n"
                + "    \"targetMember\": \"gameState\",\n"
                + "    \"targetDescriptor\": \"I\"\n"
                + "  }\n"
                + "]";
        Files.write(hooksFile, json.getBytes(StandardCharsets.UTF_8));

        HookRegistry registry = new HookRegistry();
        registry.loadFromFile(hooksFile);

        assertEquals(2, registry.size());
        assertEquals(1, registry.hooksForMethod("Client", "processLogin", "(II)V").size());
        assertEquals(1, registry.hooksForField("Client", "gameState").size());
    }

    // ---- Helper methods ----

    private ClassNode createBufferClass() {
        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V1_8;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "a";
        cn.superName = "java/lang/Object";

        cn.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "b", "[B", null, null));
        cn.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "c", "I", null, null));

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
        cn.version = Opcodes.V1_8;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "b";
        cn.superName = "java/lang/Object";

        cn.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "a", "Lb;", null, null));
        cn.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "b", "Lb;", null, null));
        cn.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "c", "J", null, null));

        return cn;
    }

    private Path createSyntheticJar() throws Exception {
        Path jarPath = tempDir.resolve("obfuscated.jar");

        JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()));
        try {
            ClassNode bufferClass = createBufferClass();
            byte[] bytes = AsmUtil.toBytesNoFrames(bufferClass);
            jos.putNextEntry(new JarEntry("a.class"));
            jos.write(bytes);
            jos.closeEntry();

            ClassNode nodeClass = createNodeClass();
            bytes = AsmUtil.toBytesNoFrames(nodeClass);
            jos.putNextEntry(new JarEntry("b.class"));
            jos.write(bytes);
            jos.closeEntry();

            ClassNode genericClass = new ClassNode();
            genericClass.version = Opcodes.V1_8;
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
        } finally {
            jos.close();
        }

        return jarPath;
    }
}
