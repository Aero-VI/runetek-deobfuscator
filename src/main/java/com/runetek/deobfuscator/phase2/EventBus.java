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
 *
 * The EventListener interface is also injected alongside.
 *
 * At deobfuscation time, calls to EventBus.fire() are injected at hook points.
 * At runtime, users register listeners to react to events.
 */
public class EventBus {

    /**
     * The internal name for the EventBus class in bytecode.
     */
    public static final String INTERNAL_NAME = "com/runetek/hooks/EventBus";

    /**
     * The internal name for the EventListener interface.
     */
    public static final String LISTENER_INTERNAL_NAME = "com/runetek/hooks/EventListener";

    /**
     * Descriptor for the fire method: void fire(String eventName, Object... args)
     */
    public static final String FIRE_DESCRIPTOR = "(Ljava/lang/String;[Ljava/lang/Object;)V";

    /**
     * Generate the bytecode for the EventBus class.
     *
     * Generated class structure:
     * <pre>
     * public class EventBus {
     *     private static final Map&lt;String, List&lt;EventListener&gt;&gt; listeners = new ConcurrentHashMap&lt;&gt;();
     *
     *     public static void fire(String eventName, Object[] args) {
     *         List&lt;EventListener&gt; list = listeners.get(eventName);
     *         if (list != null) {
     *             for (EventListener l : list) { l.onEvent(eventName, args); }
     *         }
     *     }
     *
     *     public static void register(String eventName, EventListener listener) { ... }
     *     public static void unregister(String eventName, EventListener listener) { ... }
     * }
     * </pre>
     */
    public static ClassNode generateEventBusClass() {
        var cn = new ClassNode();
        cn.version = Opcodes.V21;
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

        // Static initializer: listeners = new ConcurrentHashMap<>()
        var clinit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        var clinitInsns = new InsnList();
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

        // fire(String, Object[]) — iterates listeners and dispatches
        cn.methods.add(generateFireMethod());

        // register(String, EventListener)
        cn.methods.add(generateRegisterMethod());

        // unregister(String, EventListener)
        cn.methods.add(generateUnregisterMethod());

        // Default constructor
        var ctor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        var ctorInsn = new InsnList();
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
     * Generate the EventListener interface:
     * <pre>
     * public interface EventListener {
     *     void onEvent(String eventName, Object[] args);
     * }
     * </pre>
     */
    public static ClassNode generateListenerInterface() {
        var cn = new ClassNode();
        cn.version = Opcodes.V21;
        cn.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT;
        cn.name = LISTENER_INTERNAL_NAME;
        cn.superName = "java/lang/Object";

        var mn = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                "onEvent",
                "(Ljava/lang/String;[Ljava/lang/Object;)V",
                null, null);
        cn.methods.add(mn);

        return cn;
    }

    /**
     * Generate the fire method bytecode.
     *
     * void fire(String eventName, Object[] args) {
     *     List list = (List) listeners.get(eventName);
     *     if (list == null) return;
     *     Iterator it = list.iterator();
     *     while (it.hasNext()) {
     *         EventListener l = (EventListener) it.next();
     *         l.onEvent(eventName, args);
     *     }
     * }
     */
    private static MethodNode generateFireMethod() {
        var mn = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "fire", FIRE_DESCRIPTOR, null, null);

        var insns = new InsnList();
        LabelNode nullCheck = new LabelNode();
        LabelNode loopStart = new LabelNode();
        LabelNode loopEnd = new LabelNode();

        // List list = listeners.get(eventName)
        insns.add(new FieldInsnNode(Opcodes.GETSTATIC, INTERNAL_NAME, "listeners", "Ljava/util/Map;"));
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0)); // eventName
        insns.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true));
        insns.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/util/List"));
        insns.add(new VarInsnNode(Opcodes.ASTORE, 2)); // list → local 2

        // if (list == null) return
        insns.add(new VarInsnNode(Opcodes.ALOAD, 2));
        insns.add(new JumpInsnNode(Opcodes.IFNULL, loopEnd));

        // Iterator it = list.iterator()
        insns.add(new VarInsnNode(Opcodes.ALOAD, 2));
        insns.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "java/util/List", "iterator", "()Ljava/util/Iterator;", true));
        insns.add(new VarInsnNode(Opcodes.ASTORE, 3)); // iterator → local 3

        // Loop: while (it.hasNext())
        insns.add(loopStart);
        insns.add(new VarInsnNode(Opcodes.ALOAD, 3));
        insns.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "java/util/Iterator", "hasNext", "()Z", true));
        insns.add(new JumpInsnNode(Opcodes.IFEQ, loopEnd));

        // EventListener l = (EventListener) it.next()
        insns.add(new VarInsnNode(Opcodes.ALOAD, 3));
        insns.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "java/util/Iterator", "next", "()Ljava/lang/Object;", true));
        insns.add(new TypeInsnNode(Opcodes.CHECKCAST, LISTENER_INTERNAL_NAME));

        // l.onEvent(eventName, args)
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0)); // eventName
        insns.add(new VarInsnNode(Opcodes.ALOAD, 1)); // args
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

    /**
     * Generate the register method bytecode.
     *
     * static void register(String eventName, EventListener listener) {
     *     listeners.computeIfAbsent(eventName, k -> new CopyOnWriteArrayList<>()).add(listener);
     * }
     *
     * We use a simpler approach: check if list exists, create if not, then add.
     */
    private static MethodNode generateRegisterMethod() {
        var mn = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "register",
                "(Ljava/lang/String;L" + LISTENER_INTERNAL_NAME + ";)V",
                null, null);

        var insns = new InsnList();
        LabelNode hasListLabel = new LabelNode();

        // Get existing list
        insns.add(new FieldInsnNode(Opcodes.GETSTATIC, INTERNAL_NAME, "listeners", "Ljava/util/Map;"));
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0)); // eventName
        insns.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true));
        insns.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/util/List"));
        insns.add(new VarInsnNode(Opcodes.ASTORE, 2)); // list → local 2

        // if (list != null) goto addToList
        insns.add(new VarInsnNode(Opcodes.ALOAD, 2));
        insns.add(new JumpInsnNode(Opcodes.IFNONNULL, hasListLabel));

        // list = new CopyOnWriteArrayList<>()
        insns.add(new TypeInsnNode(Opcodes.NEW, "java/util/concurrent/CopyOnWriteArrayList"));
        insns.add(new InsnNode(Opcodes.DUP));
        insns.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/util/concurrent/CopyOnWriteArrayList", "<init>", "()V", false));
        insns.add(new VarInsnNode(Opcodes.ASTORE, 2));

        // listeners.put(eventName, list)
        insns.add(new FieldInsnNode(Opcodes.GETSTATIC, INTERNAL_NAME, "listeners", "Ljava/util/Map;"));
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0)); // eventName
        insns.add(new VarInsnNode(Opcodes.ALOAD, 2)); // list
        insns.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true));
        insns.add(new InsnNode(Opcodes.POP)); // discard old value

        // addToList:
        insns.add(hasListLabel);
        // list.add(listener)
        insns.add(new VarInsnNode(Opcodes.ALOAD, 2));
        insns.add(new VarInsnNode(Opcodes.ALOAD, 1)); // listener
        insns.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "java/util/List", "add", "(Ljava/lang/Object;)Z", true));
        insns.add(new InsnNode(Opcodes.POP)); // discard boolean

        insns.add(new InsnNode(Opcodes.RETURN));

        mn.instructions = insns;
        mn.maxStack = 3;
        mn.maxLocals = 3;
        return mn;
    }

    /**
     * Generate the unregister method bytecode.
     *
     * static void unregister(String eventName, EventListener listener) {
     *     List list = listeners.get(eventName);
     *     if (list != null) list.remove(listener);
     * }
     */
    private static MethodNode generateUnregisterMethod() {
        var mn = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "unregister",
                "(Ljava/lang/String;L" + LISTENER_INTERNAL_NAME + ";)V",
                null, null);

        var insns = new InsnList();
        LabelNode endLabel = new LabelNode();

        // List list = listeners.get(eventName)
        insns.add(new FieldInsnNode(Opcodes.GETSTATIC, INTERNAL_NAME, "listeners", "Ljava/util/Map;"));
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0)); // eventName
        insns.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true));
        insns.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/util/List"));
        insns.add(new VarInsnNode(Opcodes.ASTORE, 2)); // list → local 2

        // if (list == null) return
        insns.add(new VarInsnNode(Opcodes.ALOAD, 2));
        insns.add(new JumpInsnNode(Opcodes.IFNULL, endLabel));

        // list.remove(listener)
        insns.add(new VarInsnNode(Opcodes.ALOAD, 2));
        insns.add(new VarInsnNode(Opcodes.ALOAD, 1)); // listener
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
