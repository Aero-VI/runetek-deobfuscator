package com.runetek.deobfuscator.dictionary;

import com.runetek.deobfuscator.phase1.ClassHeuristicAnalyzer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * Revision profile for RuneTek 4 engine (revisions 503-554).
 * This is the baseline dictionary using revision 508 as reference.
 *
 * RuneTek 4 characteristics:
 *   - ZKM obfuscator (Zelix KlassMaster) — single-letter class/method names
 *   - Applet-based client entry point
 *   - Uses java.net.Socket for game connections
 *   - RSA encryption with BigInteger.modPow()
 *   - Class hierarchy: Node → Renderable → Entity/Actor
 *   - Buffer/Packet class with byte[] payload and int position
 *   - Widget system with deeply nested int fields
 */
public class RuneTek4Profile implements RevisionProfile {

    @Override
    public String name() {
        return "RuneTek 4 (508)";
    }

    @Override
    public String engineName() {
        return "RuneTek 4";
    }

    @Override
    public int minRevision() {
        return 503;
    }

    @Override
    public int maxRevision() {
        return 554;
    }

    @Override
    public List<ClassHeuristicAnalyzer.HeuristicPattern> getClassPatterns() {
        List<ClassHeuristicAnalyzer.HeuristicPattern> patterns =
                new ArrayList<ClassHeuristicAnalyzer.HeuristicPattern>();

        // RuneTek 4 specific: ISAACCipher — has int[] array and specific method patterns
        patterns.add(new ClassHeuristicAnalyzer.HeuristicPattern("ISAACCipher") {
            @Override
            public boolean matches(ClassNode cn) {
                int intArrayFields = 0;
                int intFields = 0;
                for (FieldNode fn : cn.fields) {
                    if ("[I".equals(fn.desc)) intArrayFields++;
                    if ("I".equals(fn.desc)) intFields++;
                }
                // ISAAC: has 2-3 int[] arrays (randTable, result, memory) and several int counters
                return intArrayFields >= 2 && intFields >= 3
                        && cn.fields.size() <= 8 && cn.methods.size() <= 5;
            }
        });

        // Linked list structure — has a Node head/sentinel + size counter
        patterns.add(new ClassHeuristicAnalyzer.HeuristicPattern("LinkedList") {
            @Override
            public boolean matches(ClassNode cn) {
                boolean hasSentinel = false;
                boolean hasInt = false;
                for (FieldNode fn : cn.fields) {
                    // The sentinel field is a reference to the Node class
                    if (fn.desc.startsWith("L") && !fn.desc.equals("Ljava/lang/String;")) {
                        hasSentinel = true;
                    }
                    if ("I".equals(fn.desc)) hasInt = true;
                }
                // Small class with one reference field + maybe a size int
                return hasSentinel && cn.fields.size() <= 3
                        && cn.methods.size() >= 3 && cn.methods.size() <= 8;
            }
        });

        // Graphics 2D Toolkit — contains BufferedImage or drawImage references
        patterns.add(new ClassHeuristicAnalyzer.HeuristicPattern("GraphicsToolkit") {
            @Override
            public boolean matches(ClassNode cn) {
                for (MethodNode mn : cn.methods) {
                    if (mn.instructions == null) continue;
                    for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                        if (insn instanceof MethodInsnNode) {
                            MethodInsnNode mi = (MethodInsnNode) insn;
                            if ("java/awt/image/BufferedImage".equals(mi.owner)
                                    || ("java/awt/Graphics".equals(mi.owner) && "drawImage".equals(mi.name))) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        });

        return patterns;
    }

    @Override
    public List<String> getKnownHostPatterns() {
        return Arrays.asList(
                "world",
                "runescape.com",
                "jagex.com",
                "game.openrs2.org"
        );
    }

    @Override
    public int getExpectedRSAModulusLength() {
        return 256; // 1024-bit RSA → 256 hex chars
    }

    @Override
    public String getMainClassSuperPattern() {
        return "Applet";
    }

    @Override
    public Map<String, String> getBaseClassMappings() {
        // No static mappings — heuristics handle everything
        return Collections.emptyMap();
    }

    @Override
    public Map<String, String> getBaseFieldMappings() {
        return Collections.emptyMap();
    }
}
