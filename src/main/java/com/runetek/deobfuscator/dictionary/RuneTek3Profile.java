package com.runetek.deobfuscator.dictionary;

import com.runetek.deobfuscator.phase1.ClassHeuristicAnalyzer;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * Revision profile for RuneTek 3 engine (revision 317 era).
 * Placeholder — ready to be filled in when 317 support is added.
 *
 * RuneTek 3 characteristics:
 *   - Simpler obfuscation than RT4 (still ZKM but less aggressive)
 *   - Applet-based client
 *   - Different class hierarchy layout
 *   - Different packet structure
 *   - Typically smaller class count
 */
public class RuneTek3Profile implements RevisionProfile {

    @Override
    public String name() {
        return "RuneTek 3 (317)";
    }

    @Override
    public String engineName() {
        return "RuneTek 3";
    }

    @Override
    public int minRevision() {
        return 300;
    }

    @Override
    public int maxRevision() {
        return 377;
    }

    @Override
    public List<ClassHeuristicAnalyzer.HeuristicPattern> getClassPatterns() {
        List<ClassHeuristicAnalyzer.HeuristicPattern> patterns =
                new ArrayList<ClassHeuristicAnalyzer.HeuristicPattern>();

        // 317-era specific: Stream class (the Buffer equivalent)
        // In 317, it's commonly called "Stream" rather than "Buffer"
        patterns.add(new ClassHeuristicAnalyzer.HeuristicPattern("Stream") {
            @Override
            public boolean matches(ClassNode cn) {
                boolean hasByteArray = false;
                boolean hasInt = false;
                int methodCount = cn.methods.size();
                for (FieldNode fn : cn.fields) {
                    if ("[B".equals(fn.desc)) hasByteArray = true;
                    if ("I".equals(fn.desc)) hasInt = true;
                }
                // Stream in 317 has more methods than RT4 Buffer
                return hasByteArray && hasInt && methodCount >= 10;
            }
        });

        // 317-era Signlink class — handles file I/O and threading
        patterns.add(new ClassHeuristicAnalyzer.HeuristicPattern("Signlink") {
            @Override
            public boolean matches(ClassNode cn) {
                boolean hasThread = false;
                boolean hasFile = false;
                for (FieldNode fn : cn.fields) {
                    if (fn.desc.contains("Thread")) hasThread = true;
                    if (fn.desc.contains("File") || fn.desc.contains("RandomAccessFile")) hasFile = true;
                }
                return hasThread && hasFile;
            }
        });

        return patterns;
    }

    @Override
    public List<String> getKnownHostPatterns() {
        return Arrays.asList(
                "runescape.com",
                "jagex.com"
        );
    }

    @Override
    public int getExpectedRSAModulusLength() {
        return 128; // 512-bit RSA in early revisions
    }

    @Override
    public String getMainClassSuperPattern() {
        return "Applet";
    }

    @Override
    public Map<String, String> getBaseClassMappings() {
        return Collections.emptyMap();
    }

    @Override
    public Map<String, String> getBaseFieldMappings() {
        return Collections.emptyMap();
    }
}
