package com.runetek.deobfuscator;

import com.runetek.deobfuscator.phase2.IPHookInjector;
import com.runetek.deobfuscator.phase2.RSALobotomizer;
import com.runetek.deobfuscator.util.AsmUtil;
import com.runetek.deobfuscator.util.JarLoader;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Phase 2: IP Hook and RSA Lobotomy.
 */
class Phase2Test {

    @Test
    void testIPHookRedirectsSocketConstructor() {
        // Create a method that does: new Socket("world1.runescape.com", 43594)
        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V1_8;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "NetworkClient";
        cn.superName = "java/lang/Object";

        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "connect", "()V", null, null);
        mn.instructions.add(new TypeInsnNode(Opcodes.NEW, "java/net/Socket"));
        mn.instructions.add(new InsnNode(Opcodes.DUP));
        mn.instructions.add(new LdcInsnNode("world1.runescape.com")); // host
        mn.instructions.add(new IntInsnNode(Opcodes.SIPUSH, 43594));   // port
        mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/net/Socket", "<init>", "(Ljava/lang/String;I)V", false));
        mn.instructions.add(new InsnNode(Opcodes.RETURN));
        cn.methods.add(mn);

        Map<String, ClassNode> classes = new LinkedHashMap<String, ClassNode>();
        classes.put("NetworkClient", cn);

        int injected = IPHookInjector.inject(classes);
        assertEquals(1, injected);

        // Verify the host string was replaced
        boolean foundLocalhost = false;
        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof LdcInsnNode) {
                LdcInsnNode ldc = (LdcInsnNode) insn;
                if ("127.0.0.1".equals(ldc.cst)) {
                    foundLocalhost = true;
                }
                if ("world1.runescape.com".equals(ldc.cst)) {
                    fail("Original host string should have been replaced");
                }
            }
        }
        assertTrue(foundLocalhost, "Should have injected 127.0.0.1");
    }

    @Test
    void testIPHookRedirectsInetAddress() {
        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V1_8;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "NetHandler";
        cn.superName = "java/lang/Object";

        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "resolve", "()V", null, null);
        mn.instructions.add(new LdcInsnNode("game.runescape.com"));
        mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "java/net/InetAddress", "getByName",
                "(Ljava/lang/String;)Ljava/net/InetAddress;", false));
        mn.instructions.add(new InsnNode(Opcodes.POP));
        mn.instructions.add(new InsnNode(Opcodes.RETURN));
        cn.methods.add(mn);

        Map<String, ClassNode> classes = new LinkedHashMap<String, ClassNode>();
        classes.put("NetHandler", cn);

        int injected = IPHookInjector.inject(classes);
        assertEquals(1, injected);

        // Verify replacement
        boolean foundLocalhost = false;
        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof LdcInsnNode) {
                LdcInsnNode ldc = (LdcInsnNode) insn;
                if ("127.0.0.1".equals(ldc.cst)) foundLocalhost = true;
            }
        }
        assertTrue(foundLocalhost, "Should have redirected InetAddress.getByName to localhost");
    }

    @Test
    void testRSALobotomyRemovesModPow() {
        // Create a method that does:
        // new BigInteger(data).modPow(exponent, modulus)
        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V1_8;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "LoginEncoder";
        cn.superName = "java/lang/Object";

        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "encrypt",
                "([B)Ljava/math/BigInteger;", null, null);

        // new BigInteger(byte[])
        mn.instructions.add(new TypeInsnNode(Opcodes.NEW, "java/math/BigInteger"));
        mn.instructions.add(new InsnNode(Opcodes.DUP));
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1)); // byte[] data
        mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/math/BigInteger", "<init>", "([B)V", false));

        // Push exponent (another BigInteger)
        mn.instructions.add(new TypeInsnNode(Opcodes.NEW, "java/math/BigInteger"));
        mn.instructions.add(new InsnNode(Opcodes.DUP));
        mn.instructions.add(new LdcInsnNode("65537"));
        mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/math/BigInteger", "<init>", "(Ljava/lang/String;)V", false));

        // Push modulus (another BigInteger)
        mn.instructions.add(new TypeInsnNode(Opcodes.NEW, "java/math/BigInteger"));
        mn.instructions.add(new InsnNode(Opcodes.DUP));
        mn.instructions.add(new LdcInsnNode("abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"));
        mn.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 16));
        mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/math/BigInteger", "<init>", "(Ljava/lang/String;I)V", false));

        // modPow call
        mn.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/math/BigInteger", "modPow",
                "(Ljava/math/BigInteger;Ljava/math/BigInteger;)Ljava/math/BigInteger;", false));

        mn.instructions.add(new InsnNode(Opcodes.ARETURN));
        cn.methods.add(mn);

        Map<String, ClassNode> classes = new LinkedHashMap<String, ClassNode>();
        classes.put("LoginEncoder", cn);

        int neutralized = RSALobotomizer.inject(classes);
        assertEquals(1, neutralized);

        // Verify modPow was removed
        boolean foundModPow = false;
        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode mInsn = (MethodInsnNode) insn;
                if ("modPow".equals(mInsn.name)) {
                    foundModPow = true;
                }
            }
        }
        assertFalse(foundModPow, "modPow should have been removed");

        // Verify the bytecode is still valid (can be serialized)
        byte[] bytes = AsmUtil.toBytesNoFrames(cn);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
    }

    @Test
    void testRSALobotomyNoModPowNoChange() {
        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V1_8;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "SafeClass";
        cn.superName = "java/lang/Object";

        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "doNothing", "()V", null, null);
        mn.instructions.add(new InsnNode(Opcodes.RETURN));
        cn.methods.add(mn);

        Map<String, ClassNode> classes = new LinkedHashMap<String, ClassNode>();
        classes.put("SafeClass", cn);

        int neutralized = RSALobotomizer.inject(classes);
        assertEquals(0, neutralized);
    }
}
