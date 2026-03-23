package com.runetek.deobfuscator.phase2;

/**
 * Simple event bus that hook callbacks dispatch to.
 * This class gets injected into the target JAR so hooks can call
 * EventBus.fire() at runtime.
 *
 * At deobfuscation time, we inject calls to this class.
 * At runtime, users register listeners on this bus.
 */
public class EventBus {

    /**
     * The internal name for use in ASM bytecode injection.
     */
    public static final String INTERNAL_NAME = "com/runetek/hooks/EventBus";

    /**
     * Descriptor for the fire method: void fire(String eventName, Object... args)
     */
    public static final String FIRE_DESCRIPTOR = "(Ljava/lang/String;[Ljava/lang/Object;)V";

    /**
     * Generate the bytecode for the EventBus class that will be injected
     * into the output JAR. This is a standalone utility class.
     */
    public static org.objectweb.asm.tree.ClassNode generateEventBusClass() {
        var cn = new org.objectweb.asm.tree.ClassNode();
        cn.version = org.objectweb.asm.Opcodes.V21;
        cn.access = org.objectweb.asm.Opcodes.ACC_PUBLIC;
        cn.name = INTERNAL_NAME;
        cn.superName = "java/lang/Object";

        // Add a static fire(String, Object...) method
        var mn = new org.objectweb.asm.tree.MethodNode(
                org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_STATIC,
                "fire",
                FIRE_DESCRIPTOR,
                null, null);

        // For now, the default implementation just prints the event (debug mode).
        // Users will replace this class or add listeners via ServiceLoader.
        var insnList = new org.objectweb.asm.tree.InsnList();

        // System.out.println("Event: " + eventName);
        insnList.add(new org.objectweb.asm.tree.FieldInsnNode(
                org.objectweb.asm.Opcodes.GETSTATIC,
                "java/lang/System", "out", "Ljava/io/PrintStream;"));
        insnList.add(new org.objectweb.asm.tree.TypeInsnNode(
                org.objectweb.asm.Opcodes.NEW, "java/lang/StringBuilder"));
        insnList.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.DUP));
        insnList.add(new org.objectweb.asm.tree.LdcInsnNode("[Hook] Event: "));
        insnList.add(new org.objectweb.asm.tree.MethodInsnNode(
                org.objectweb.asm.Opcodes.INVOKESPECIAL,
                "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false));
        insnList.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, 0));
        insnList.add(new org.objectweb.asm.tree.MethodInsnNode(
                org.objectweb.asm.Opcodes.INVOKEVIRTUAL,
                "java/lang/StringBuilder", "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false));
        insnList.add(new org.objectweb.asm.tree.MethodInsnNode(
                org.objectweb.asm.Opcodes.INVOKEVIRTUAL,
                "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false));
        insnList.add(new org.objectweb.asm.tree.MethodInsnNode(
                org.objectweb.asm.Opcodes.INVOKEVIRTUAL,
                "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false));
        insnList.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.RETURN));

        mn.instructions = insnList;
        mn.maxStack = 4;
        mn.maxLocals = 2;
        cn.methods.add(mn);

        // Default constructor
        var ctor = new org.objectweb.asm.tree.MethodNode(
                org.objectweb.asm.Opcodes.ACC_PUBLIC,
                "<init>", "()V", null, null);
        var ctorInsn = new org.objectweb.asm.tree.InsnList();
        ctorInsn.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, 0));
        ctorInsn.add(new org.objectweb.asm.tree.MethodInsnNode(
                org.objectweb.asm.Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        ctorInsn.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.RETURN));
        ctor.instructions = ctorInsn;
        ctor.maxStack = 1;
        ctor.maxLocals = 1;
        cn.methods.add(ctor);

        return cn;
    }
}
