package com.runetek.deobfuscator.phase1;

import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * Analyzes string literals within classes and methods to extract context
 * clues for naming. Many obfuscated RuneTek clients retain string constants
 * that hint at the original purpose of classes and methods.
 */
public class StringLiteralAnalyzer {

    /** Keyword to suggested class name prefix */
    private static final Map<String, String> CLASS_KEYWORDS = new LinkedHashMap<String, String>();
    /** Keyword to suggested method name */
    private static final Map<String, String> METHOD_KEYWORDS = new LinkedHashMap<String, String>();

    static {
        CLASS_KEYWORDS.put("login", "LoginHandler");
        CLASS_KEYWORDS.put("password", "LoginHandler");
        CLASS_KEYWORDS.put("authenticat", "Authenticator");
        CLASS_KEYWORDS.put("socket", "NetworkHandler");
        CLASS_KEYWORDS.put("connection", "Connection");
        CLASS_KEYWORDS.put("packet", "PacketHandler");
        CLASS_KEYWORDS.put("disconnect", "Connection");
        CLASS_KEYWORDS.put("opengl", "GLRenderer");
        CLASS_KEYWORDS.put("rasteriz", "Rasterizer");
        CLASS_KEYWORDS.put("drawpixel", "Rasterizer");
        CLASS_KEYWORDS.put("texture", "TextureManager");
        CLASS_KEYWORDS.put("sprite", "Sprite");
        CLASS_KEYWORDS.put("midi", "MidiPlayer");
        CLASS_KEYWORDS.put("audio", "AudioSystem");
        CLASS_KEYWORDS.put("sound", "SoundPlayer");
        CLASS_KEYWORDS.put("cache", "CacheManager");
        CLASS_KEYWORDS.put("archive", "Archive");
        CLASS_KEYWORDS.put(".idx", "IndexFile");
        CLASS_KEYWORDS.put(".dat", "DataFile");
        CLASS_KEYWORDS.put("region", "Region");
        CLASS_KEYWORDS.put("landscape", "Landscape");
        CLASS_KEYWORDS.put("worldmap", "WorldMap");
        CLASS_KEYWORDS.put("minimap", "Minimap");
        CLASS_KEYWORDS.put("player", "Player");
        CLASS_KEYWORDS.put("npc", "NPC");
        CLASS_KEYWORDS.put("entity", "Entity");
        CLASS_KEYWORDS.put("actor", "Actor");
        CLASS_KEYWORDS.put("inventory", "Inventory");
        CLASS_KEYWORDS.put("equipment", "Equipment");
        CLASS_KEYWORDS.put("ground item", "GroundItem");
        CLASS_KEYWORDS.put("chat", "ChatSystem");
        CLASS_KEYWORDS.put("message", "MessageHandler");

        METHOD_KEYWORDS.put("login", "processLogin");
        METHOD_KEYWORDS.put("logout", "processLogout");
        METHOD_KEYWORDS.put("render", "render");
        METHOD_KEYWORDS.put("draw", "draw");
        METHOD_KEYWORDS.put("decode", "decode");
        METHOD_KEYWORDS.put("encode", "encode");
        METHOD_KEYWORDS.put("load", "load");
        METHOD_KEYWORDS.put("save", "save");
        METHOD_KEYWORDS.put("connect", "connect");
        METHOD_KEYWORDS.put("disconnect", "disconnect");
        METHOD_KEYWORDS.put("update", "update");
        METHOD_KEYWORDS.put("tick", "tick");
        METHOD_KEYWORDS.put("click", "onClick");
        METHOD_KEYWORDS.put("mouse", "handleMouse");
        METHOD_KEYWORDS.put("keyboard", "handleKeyboard");
        METHOD_KEYWORDS.put("key press", "onKeyPress");
    }

    /**
     * Extract all string literals from a class.
     */
    public static List<String> extractStrings(ClassNode cn) {
        List<String> strings = new ArrayList<String>();
        for (MethodNode mn : cn.methods) {
            if (mn.instructions == null) continue;
            for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof LdcInsnNode) {
                    LdcInsnNode ldc = (LdcInsnNode) insn;
                    if (ldc.cst instanceof String) {
                        strings.add((String) ldc.cst);
                    }
                }
            }
        }
        return strings;
    }

    /**
     * Suggest a class name based on string literal analysis.
     * Returns null if no strong match found.
     */
    public static String suggestClassName(ClassNode cn) {
        List<String> strings = extractStrings(cn);
        if (strings.isEmpty()) return null;

        Map<String, Integer> hits = new LinkedHashMap<String, Integer>();
        for (String str : strings) {
            String lower = str.toLowerCase();
            for (Map.Entry<String, String> entry : CLASS_KEYWORDS.entrySet()) {
                if (lower.contains(entry.getKey())) {
                    Integer count = hits.get(entry.getValue());
                    hits.put(entry.getValue(), count == null ? 1 : count + 1);
                }
            }
        }

        if (hits.isEmpty()) return null;

        // Return the most-matched class name
        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : hits.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    /**
     * Suggest method names based on string literals used within each method.
     * Returns a map of "methodName+desc" to suggestedName.
     */
    public static Map<String, String> suggestMethodNames(ClassNode cn) {
        Map<String, String> suggestions = new LinkedHashMap<String, String>();

        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("<init>") || mn.name.equals("<clinit>")) continue;
            if (!FieldPatternMatcher.isObfuscatedName(mn.name)) continue;
            if (mn.instructions == null) continue;

            for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof LdcInsnNode) {
                    LdcInsnNode ldc = (LdcInsnNode) insn;
                    if (ldc.cst instanceof String) {
                        String lower = ((String) ldc.cst).toLowerCase();
                        for (Map.Entry<String, String> entry : METHOD_KEYWORDS.entrySet()) {
                            if (lower.contains(entry.getKey())) {
                                String key = mn.name + "+" + mn.desc;
                                if (!suggestions.containsKey(key)) {
                                    suggestions.put(key, entry.getValue());
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }

        return suggestions;
    }
}
