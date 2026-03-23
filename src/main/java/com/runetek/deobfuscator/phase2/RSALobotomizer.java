package com.runetek.deobfuscator.phase2;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.Map;

/**
 * Phase 2 Hook: RSA Lobotomy
 *
 * Locates the BigInteger.modPow() call used for RSA encryption in the login
 * block and surgically removes it so the client sends plaintext credentials.
 *
 * In RuneTek 4 (revisions 503-554), the login block is encrypted using RSA:
 *   BigInteger encrypted = new BigInteger(loginBlock).modPow(rsaExponent, rsaModulus);
 *
 * This transformer:
 *   1. Finds BigInteger.modPow(BigInteger, BigInteger) calls
 *   2. Removes the modPow call entirely so the BigInteger passes through unencrypted
 *   3. Effectively: result = new BigInteger(data) instead of new BigInteger(data).modPow(exp, mod)
 *
 * The stack before modPow is: ..., bigint, exponent, modulus
 * After modPow: ..., encrypted_bigint
 * We want: ..., bigint (just pop the exponent and modulus, skip modPow)
 */
public class RSALobotomizer {

    /**
     * Scan all classes and remove RSA encryption (modPow calls).
     * @return number of modPow calls neutralized
     */
    public static int inject(Map<String, ClassNode> classes) {
        int neutralized = 0;

        for (Map.Entry<String, ClassNode> entry : classes.entrySet()) {
            ClassNode cn = entry.getValue();
            for (MethodNode mn : cn.methods) {
                if (mn.instructions == null) continue;
                neutralized += processMethod(mn, entry.getKey());
            }
        }

        return neutralized;
    }

    private static int processMethod(MethodNode mn, String className) {
        int count = 0;

        for (int i = 0; i < mn.instructions.size(); i++) {
            AbstractInsnNode insn = mn.instructions.get(i);

            if (insn instanceof MethodInsnNode) {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;

                // BigInteger.modPow(BigInteger exponent, BigInteger modulus) → BigInteger
                if ("java/math/BigInteger".equals(methodInsn.owner)
                        && "modPow".equals(methodInsn.name)
                        && "(Ljava/math/BigInteger;Ljava/math/BigInteger;)Ljava/math/BigInteger;".equals(methodInsn.desc)) {

                    // Replace modPow with: POP, POP (remove exponent and modulus from stack)
                    // This leaves the original BigInteger on the stack unchanged
                    InsnList replacement = new InsnList();
                    replacement.add(new InsnNode(Opcodes.POP)); // pop modulus
                    replacement.add(new InsnNode(Opcodes.POP)); // pop exponent
                    // Original BigInteger remains on stack

                    mn.instructions.insertBefore(insn, replacement);
                    mn.instructions.remove(insn);

                    count++;
                    System.out.println("    [RSA Lobotomy] Neutralized modPow in " +
                            className + "." + mn.name + mn.desc);

                    // Also try to find and neutralize the RSA key constants
                    neutralizeRSAConstants(mn);
                }
            }
        }

        return count;
    }

    /**
     * Optionally neutralize RSA key constants (large hex strings used for modulus/exponent).
     * These are typically loaded as LDC strings and passed to new BigInteger(String, 16).
     * We don't need to remove them (modPow is already gone), but logging them is useful.
     */
    private static void neutralizeRSAConstants(MethodNode mn) {
        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof LdcInsnNode) {
                LdcInsnNode ldc = (LdcInsnNode) insn;
                if (ldc.cst instanceof String) {
                    String str = (String) ldc.cst;
                    // RSA keys are typically long hex strings (128+ chars)
                    if (str.length() >= 64 && str.matches("[0-9a-fA-F]+")) {
                        System.out.println("    [RSA Lobotomy] Found RSA constant: " +
                                str.substring(0, 16) + "... (" + str.length() + " chars)");
                    }
                }
            }
        }
    }
}
