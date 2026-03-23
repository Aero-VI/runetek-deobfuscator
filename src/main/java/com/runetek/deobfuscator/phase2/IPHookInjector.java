package com.runetek.deobfuscator.phase2;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.Map;

/**
 * Phase 2 Hook: IP Redirection
 *
 * Locates Socket constructor calls (new Socket(host, port)) in the client
 * and replaces the host string argument with "127.0.0.1" so the client
 * connects to localhost instead of Jagex servers.
 *
 * Detection strategy:
 *   1. Scan all methods for java/net/Socket.&lt;init&gt;(Ljava/lang/String;I)V
 *   2. Before the INVOKESPECIAL, the stack has: socket, host, port
 *   3. We insert a POP for the host string and push "127.0.0.1"
 *
 *   Also handles InetAddress.getByName(String) patterns.
 */
public class IPHookInjector {

    private static final String LOCALHOST = "127.0.0.1";

    /**
     * Scan all classes and redirect socket connections to localhost.
     * @return number of injection points modified
     */
    public static int inject(Map<String, ClassNode> classes) {
        int injected = 0;

        for (Map.Entry<String, ClassNode> entry : classes.entrySet()) {
            ClassNode cn = entry.getValue();
            for (MethodNode mn : cn.methods) {
                if (mn.instructions == null) continue;
                injected += processMethod(mn);
            }
        }

        return injected;
    }

    private static int processMethod(MethodNode mn) {
        int count = 0;

        for (int i = 0; i < mn.instructions.size(); i++) {
            AbstractInsnNode insn = mn.instructions.get(i);

            if (insn instanceof MethodInsnNode) {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;

                // Pattern 1: new Socket(String host, int port)
                if ("java/net/Socket".equals(methodInsn.owner)
                        && "<init>".equals(methodInsn.name)
                        && "(Ljava/lang/String;I)V".equals(methodInsn.desc)) {

                    // Find the LDC that pushes the host string before this call
                    // Walk backwards to find where the String argument is pushed
                    AbstractInsnNode hostPush = findStringArgBeforeSocketInit(mn, insn);
                    if (hostPush != null) {
                        // Replace the host string with localhost
                        mn.instructions.set(hostPush, new LdcInsnNode(LOCALHOST));
                        count++;
                        System.out.println("    [IP Hook] Redirected Socket(String,int) in " +
                                mn.name + mn.desc);
                    }
                }

                // Pattern 2: InetAddress.getByName(String)
                if ("java/net/InetAddress".equals(methodInsn.owner)
                        && "getByName".equals(methodInsn.name)
                        && "(Ljava/lang/String;)Ljava/net/InetAddress;".equals(methodInsn.desc)) {

                    // The string argument is on top of stack before this call
                    AbstractInsnNode prev = findPreviousRealInsn(insn);
                    if (prev instanceof LdcInsnNode) {
                        mn.instructions.set(prev, new LdcInsnNode(LOCALHOST));
                        count++;
                        System.out.println("    [IP Hook] Redirected InetAddress.getByName() in " +
                                mn.name + mn.desc);
                    } else {
                        // If it's not a simple LDC, insert POP + LDC before the call
                        InsnList patch = new InsnList();
                        patch.add(new InsnNode(Opcodes.POP)); // pop original host
                        patch.add(new LdcInsnNode(LOCALHOST)); // push localhost
                        mn.instructions.insertBefore(insn, patch);
                        count++;
                        System.out.println("    [IP Hook] Forced InetAddress.getByName() to localhost in " +
                                mn.name + mn.desc);
                    }
                }

                // Pattern 3: Socket(InetAddress, int) — less common but possible
                if ("java/net/Socket".equals(methodInsn.owner)
                        && "<init>".equals(methodInsn.name)
                        && "(Ljava/net/InetAddress;I)V".equals(methodInsn.desc)) {
                    // This is handled if InetAddress.getByName was already patched upstream
                    // Log it for visibility
                    System.out.println("    [IP Hook] Found Socket(InetAddress,int) in " +
                            mn.name + mn.desc + " — ensure InetAddress.getByName is also patched");
                }
            }
        }

        return count;
    }

    /**
     * Walk backwards from a Socket init call to find the LDC that pushes the host String.
     * For Socket(String, int), the stack order is: ..., socket, host_string, port_int
     * So we need to skip past the int argument to find the string.
     */
    private static AbstractInsnNode findStringArgBeforeSocketInit(MethodNode mn, AbstractInsnNode socketInit) {
        // Walk backwards: first skip the int (port) push, then find the String (host) push
        AbstractInsnNode current = findPreviousRealInsn(socketInit);

        // Skip the port argument (could be ICONST, BIPUSH, SIPUSH, LDC(int), ILOAD, GETFIELD returning int, etc.)
        if (current != null) {
            current = findPreviousRealInsn(current);
        }

        // Now we should be at the host string push
        if (current instanceof LdcInsnNode) {
            LdcInsnNode ldc = (LdcInsnNode) current;
            if (ldc.cst instanceof String) {
                return current;
            }
        }

        // If it's not a simple LDC, return null — we can't safely patch it
        return null;
    }

    /**
     * Find the previous non-label, non-line-number instruction.
     */
    private static AbstractInsnNode findPreviousRealInsn(AbstractInsnNode insn) {
        AbstractInsnNode prev = insn.getPrevious();
        while (prev != null && (prev instanceof LabelNode || prev instanceof LineNumberNode
                || prev instanceof FrameNode)) {
            prev = prev.getPrevious();
        }
        return prev;
    }
}
