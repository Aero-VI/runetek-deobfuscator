package com.runetek.deobfuscator.output;

import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Generates Launcher.class bytecode using ASM.
 * This creates a simple main() that wraps the Applet client in a JFrame
 * with default parameters for a 508 client.
 */
public class LauncherGenerator {

    /**
     * Generate Launcher.class bytecode that launches the given applet class.
     */
    public static byte[] generate(String appletClassName) {
        org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_FRAMES | org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                "Launcher", null, "java/lang/Object", null);

        // Static fields for configuration
        visitField(cw, "SERVER_IP", "Ljava/lang/String;", "127.0.0.1");
        visitIntField(cw, "SERVER_PORT", 43594);
        visitIntField(cw, "WORLD_ID", 1);
        visitIntField(cw, "WIDTH", 765);
        visitIntField(cw, "HEIGHT", 503);

        // Generate main method
        generateMain(cw, appletClassName);

        // Default constructor
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void visitField(org.objectweb.asm.ClassWriter cw, String name, String desc, String value) {
        FieldVisitor fv = cw.visitField(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                name, desc, null, value);
        fv.visitEnd();
    }

    private static void visitIntField(org.objectweb.asm.ClassWriter cw, String name, int value) {
        FieldVisitor fv = cw.visitField(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                name, "I", null, value);
        fv.visitEnd();
    }

    private static void generateMain(org.objectweb.asm.ClassWriter cw, String appletClassName) {
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "main", "([Ljava/lang/String;)V", null,
                new String[]{"java/lang/Exception"});
        mv.visitCode();

        // HashMap params = new HashMap();
        mv.visitTypeInsn(Opcodes.NEW, "java/util/HashMap");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);
        mv.visitVarInsn(Opcodes.ASTORE, 1); // params

        // params.put("worldid", String.valueOf(WORLD_ID))
        putParam(mv, 1, "worldid", "1");
        putParam(mv, 1, "modewhat", "0");
        putParam(mv, 1, "modewhere", "0");
        putParam(mv, 1, "safemode", "0");
        putParam(mv, 1, "members", "1");
        putParam(mv, 1, "lang", "0");
        putParam(mv, 1, "game", "0");
        putParam(mv, 1, "js", "1");
        putParam(mv, 1, "plug", "0");
        putParam(mv, 1, "affid", "0");

        // System.out.println("Launching 508 client...")
        mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        mv.visitLdcInsn("Launching 508 client...");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println",
                "(Ljava/lang/String;)V", false);

        // URL codeBase = new URL("http://127.0.0.1/")
        mv.visitTypeInsn(Opcodes.NEW, "java/net/URL");
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn("http://127.0.0.1/");
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/net/URL", "<init>",
                "(Ljava/lang/String;)V", false);
        mv.visitVarInsn(Opcodes.ASTORE, 2); // codeBase

        // Applet applet = (Applet) Class.forName("client").newInstance()
        mv.visitLdcInsn(appletClassName);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Class", "forName",
                "(Ljava/lang/String;)Ljava/lang/Class;", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "newInstance",
                "()Ljava/lang/Object;", false);
        mv.visitTypeInsn(Opcodes.CHECKCAST, "java/applet/Applet");
        mv.visitVarInsn(Opcodes.ASTORE, 3); // applet

        // applet.setStub(new LauncherStub(params, codeBase))
        mv.visitVarInsn(Opcodes.ALOAD, 3);
        mv.visitTypeInsn(Opcodes.NEW, "LauncherStub");
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, 1); // params
        mv.visitVarInsn(Opcodes.ALOAD, 2); // codeBase
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "LauncherStub", "<init>",
                "(Ljava/util/Map;Ljava/net/URL;)V", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/applet/Applet", "setStub",
                "(Ljava/applet/AppletStub;)V", false);

        // JFrame frame = new JFrame("RuneTek 508 Client")
        mv.visitTypeInsn(Opcodes.NEW, "javax/swing/JFrame");
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn("RuneTek 508 Client");
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "javax/swing/JFrame", "<init>",
                "(Ljava/lang/String;)V", false);
        mv.visitVarInsn(Opcodes.ASTORE, 4); // frame

        // frame.setDefaultCloseOperation(EXIT_ON_CLOSE)
        mv.visitVarInsn(Opcodes.ALOAD, 4);
        mv.visitInsn(Opcodes.ICONST_3); // EXIT_ON_CLOSE = 3
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "javax/swing/JFrame",
                "setDefaultCloseOperation", "(I)V", false);

        // frame.setSize(781, 542)
        mv.visitVarInsn(Opcodes.ALOAD, 4);
        mv.visitIntInsn(Opcodes.SIPUSH, 781);
        mv.visitIntInsn(Opcodes.SIPUSH, 542);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "javax/swing/JFrame",
                "setSize", "(II)V", false);

        // frame.setLocationRelativeTo(null)
        mv.visitVarInsn(Opcodes.ALOAD, 4);
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "javax/swing/JFrame",
                "setLocationRelativeTo", "(Ljava/awt/Component;)V", false);

        // applet.setPreferredSize(new Dimension(765, 503))
        mv.visitVarInsn(Opcodes.ALOAD, 3);
        mv.visitTypeInsn(Opcodes.NEW, "java/awt/Dimension");
        mv.visitInsn(Opcodes.DUP);
        mv.visitIntInsn(Opcodes.SIPUSH, 765);
        mv.visitIntInsn(Opcodes.SIPUSH, 503);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/awt/Dimension", "<init>",
                "(II)V", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/applet/Applet",
                "setPreferredSize", "(Ljava/awt/Dimension;)V", false);

        // frame.add(applet)
        mv.visitVarInsn(Opcodes.ALOAD, 4);
        mv.visitVarInsn(Opcodes.ALOAD, 3);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "javax/swing/JFrame",
                "add", "(Ljava/awt/Component;)Ljava/awt/Component;", false);
        mv.visitInsn(Opcodes.POP);

        // frame.pack()
        mv.visitVarInsn(Opcodes.ALOAD, 4);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "javax/swing/JFrame",
                "pack", "()V", false);

        // frame.setVisible(true)
        mv.visitVarInsn(Opcodes.ALOAD, 4);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "javax/swing/JFrame",
                "setVisible", "(Z)V", false);

        // applet.init()
        mv.visitVarInsn(Opcodes.ALOAD, 3);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/applet/Applet",
                "init", "()V", false);

        // applet.start()
        mv.visitVarInsn(Opcodes.ALOAD, 3);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/applet/Applet",
                "start", "()V", false);

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /**
     * Generate the LauncherStub inner class (implements AppletStub).
     */
    public static byte[] generateStub() {
        org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_FRAMES | org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                "LauncherStub", null, "java/lang/Object",
                new String[]{"java/applet/AppletStub"});

        // Fields
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                "params", "Ljava/util/Map;", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                "codeBase", "Ljava/net/URL;", null, null).visitEnd();

        // Constructor(Map, URL)
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                "(Ljava/util/Map;Ljava/net/URL;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitFieldInsn(Opcodes.PUTFIELD, "LauncherStub", "params", "Ljava/util/Map;");
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitFieldInsn(Opcodes.PUTFIELD, "LauncherStub", "codeBase", "Ljava/net/URL;");
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // isActive() -> true
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "isActive", "()Z", null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // getDocumentBase() -> codeBase
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "getDocumentBase", "()Ljava/net/URL;", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, "LauncherStub", "codeBase", "Ljava/net/URL;");
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // getCodeBase() -> codeBase
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "getCodeBase", "()Ljava/net/URL;", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, "LauncherStub", "codeBase", "Ljava/net/URL;");
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // getParameter(String) -> params.get(name)
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "getParameter",
                "(Ljava/lang/String;)Ljava/lang/String;", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, "LauncherStub", "params", "Ljava/util/Map;");
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "get",
                "(Ljava/lang/Object;)Ljava/lang/Object;", true);
        mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String");
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // getAppletContext() -> null
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "getAppletContext",
                "()Ljava/applet/AppletContext;", null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // appletResize(int, int) -> noop
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "appletResize", "(II)V", null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void putParam(MethodVisitor mv, int mapVar, String key, String value) {
        mv.visitVarInsn(Opcodes.ALOAD, mapVar);
        mv.visitLdcInsn(key);
        mv.visitLdcInsn(value);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
        mv.visitInsn(Opcodes.POP);
    }
}
