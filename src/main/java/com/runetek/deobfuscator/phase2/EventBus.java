package com.runetek.deobfuscator.phase2;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Event bus that hook callbacks dispatch to at runtime.
 * This class is generated as bytecode and injected into the target JAR.
 *
 * The generated EventBus supports:
 *   - fire(String eventName, Object[] args) — dispatch to listeners
 *   - register(String eventName, EventListener listener)
 *   - unregister(String eventName, EventListener listener)
 */
public class EventBus {

    public static final String INTERNAL_NAME = "com/runetek/hooks/EventBus";
    public static final String LISTENER_INTERNAL_NAME = "com/runetek/hooks/EventListener";
    public static final String FIRE_DESCRIPTOR = "(Ljava/lang/String;[Ljava/lang/Object;)V";

    /**
     * Generate the bytecode for the EventBus class.
     */
    public static ClassNode generateEventBusClass() {
        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V1_8;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = INTERNAL_NAME;
        cn.superName = "java/lang/Object";

        // Field: private static final Map<String, List<EventListener>> listeners
        cn.fields.add(new FieldNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "listeners",
                "Ljava/util/Map;",
                "Ljava/util/Map<Ljava/lang/String;Ljava/util/List<L" + LISTENER_INTERNAL_NAME + ";>;>;",
                null));

        // Static initializer
        MethodNode clinit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        InsnList clinitInsns = new InsnList();
        clinitInsns.add(new TypeInsnNode(Opcodes.NEW, "java/util/concurrent/ConcurrentHashMap"));
        clinitInsns.add(new InsnNode(Opcodes.DUP));
        clinitInsns.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/util/concurrent/ConcurrentHashMap", "<init>", "()V", false));
        clinitInsns.add(new FieldInsnNode(Opcodes.PUTSTATIC, INTERNAL_NAME,
                "listeners", "Ljava/util/Map;"));
        clinitInsns.add(new InsnNode(Opcodes.RETURN));
        clinit.instructions = clinitInsns;
        clinit.maxStack = 2;
        clinit.maxLocals = 0;
        cn.methods.add(clinit);

        cn.methods.add(generateFireMethod());
        cn.methods.add(generateRegisterMethod());
        cn.methods.add(generateUnregisterMethod());

        // Default constructor
        MethodNode ctor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        InsnList ctorInsn = new InsnList();
        ctorInsn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        ctorInsn.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        ctorInsn.add(new InsnNode(Opcodes.RETURN));
        ctor.instructions = ctorInsn;
        ctor.maxStack = 1;
        ctor.maxLocals = 1;
        cn.methods.add(ctor);

        return cn;
    }

    /**
     * Generate the EventListener interface.
     */
    public static ClassNode generateListenerInterface() {
        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V1_8;
        cn.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT;
        cn.name = LISTENER_INTERNAL_NAME;
        cn.superName = "java/lang/Object";

        MethodNode mn = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                "onEvent",
                "(Ljava/lang/String;[Ljava/lang/Object;)V",
                null, null);
        cn.methods.add(mn);

        return cn;
    }

    private static MethodNode generateFireMethod() {
        MethodNode mn = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "fire", FIRE_DESCRIPTOR, null, null);

        InsnList insns = new InsnList();
        LabelNode nullCheck = new LabelNode();
        LabelNode loopStart = new LabelNode();
        LabelNode loopEnd = new LabelNode();

        insns.add(new FieldInsnNode(Opcodes.GETSTATIC, INTERNAL_NAME, "listeners", "Ljava/util/Map;"));
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insns.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true));
        insns.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/util/List"));
        insns.add(new VarInsnNode(Opcodes.ASTORE, 2));

        insns.add(new VarInsnNode(Opcodes.ALOAD, 2));
        insns.add(new JumpInsnNode(Opcodes.IFNULL, loopEnd));

        insns.add(new VarInsnNode(Opcodes.ALOAD, 2));
        insns.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "java/util/List", "iterator", "()Ljava/util/Iterator;", true));
        insns.add(new VarInsnNode(Opcodes.ASTORE, 3));

        insns.add(loopStart);
        insns.add(new VarInsnNode(Opcodes.ALOAD, 3));
        insns.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "java/util/Iterator", "hasNext", "()Z", true));
        insns.add(new JumpInsnNode(Opcodes.IFEQ, loopEnd));

        insns.add(new VarInsnNode(Opcodes.ALOAD, 3));
        insns.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "java/util/Iterator", "next", "()Ljava/lang/Object;", true));
        insns.add(new TypeInsnNode(Opcodes.CHECKCAST, LISTENER_INTERNAL_NAME));

        insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insns.add(new VarInsnNode(Opcodes.ALOAD, 1));
        insns.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                LISTENER_INTERNAL_NAME, "onEvent",
                "(Ljava/lang/String;[Ljava/lang/Object;)V", true));
        insns.add(new JumpInsnNode(Opcodes.GOTO, loopStart));

        insns.add(loopEnd);
        insns.add(new InsnNode(Opcodes.RETURN));

        mn.instructions = insns;
        mn.maxStack = 4;
        mn.maxLocals = 4;
        return mn;
    }

    private static MethodNode generateRegisterMethod() {
        MethodNode mn = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "register",
                "(Ljava/lang/String;L" + LISTENER_INTERNAL_NAME + ";)V",
                null, null);

        InsnList insns = new InsnList();
        LabelNode hasListLabel = new LabelNode();

        insns.add(new FieldInsnNode(Opcodes.GETSTATIC, INTERNAL_NAME, "listeners", "Ljava/util/Map;"));
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insns.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true));
        insns.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/util/List"));
        insns.add(new VarInsnNode(Opcodes.ASTORE, 2));

        insns.add(new VarInsnNode(Opcodes.ALOAD, 2));
        insns.add(new JumpInsnNode(Opcodes.IFNONNULL, hasListLabel));

        insns.add(new TypeInsnNode(Opcodes.NEW, "java/util/concurrent/CopyOnWriteArrayList"));
        insns.add(new InsnNode(Opcodes.DUP));
        insns.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/util/concurrent/CopyOnWriteArrayList", "<init>", "()V", false));
        insns.add(new VarInsnNode(Opcodes.ASTORE, 2));

        insns.add(new FieldInsnNode(Opcodes.GETSTATIC, INTERNAL_NAME, "listeners", "Ljava/util/Map;"));
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insns.add(new VarInsnNode(Opcodes.ALOAD, 2));
        insns.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true));
        insns.add(new InsnNode(Opcodes.POP));

        insns.add(hasListLabel);
        insns.add(new VarInsnNode(Opcodes.ALOAD, 2));
        insns.add(new VarInsnNode(Opcodes.ALOAD, 1));
        insns.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "java/util/List", "add", "(Ljava/lang/Object;)Z", true));
        insns.add(new InsnNode(Opcodes.POP));

        insns.add(new InsnNode(Opcodes.RETURN));

        mn.instructions = insns;
        mn.maxStack = 3;
        mn.maxLocals = 3;
        return mn;
    }

    private static MethodNode generateUnregisterMethod() {
        MethodNode mn = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "unregister",
                "(Ljava/lang/String;L" + LISTENER_INTERNAL_NAME + ";)V",
                null, null);

        InsnList insns = new InsnList();
        LabelNode endLabel = new LabelNode();

        insns.add(new FieldInsnNode(Opcodes.GETSTATIC, INTERNAL_NAME, "listeners", "Ljava/util/Map;"));
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insns.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true));
        insns.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/util/List"));
        insns.add(new VarInsnNode(Opcodes.ASTORE, 2));

        insns.add(new VarInsnNode(Opcodes.ALOAD, 2));
        insns.add(new JumpInsnNode(Opcodes.IFNULL, endLabel));

        insns.add(new VarInsnNode(Opcodes.ALOAD, 2));
        insns.add(new VarInsnNode(Opcodes.ALOAD, 1));
        insns.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "java/util/List", "remove", "(Ljava/lang/Object;)Z", true));
        insns.add(new InsnNode(Opcodes.POP));

        insns.add(endLabel);
        insns.add(new InsnNode(Opcodes.RETURN));

        mn.instructions = insns;
        mn.maxStack = 3;
        mn.maxLocals = 3;
        return mn;
    }
}
