package com.runetek.deobfuscator.dictionary;

import com.runetek.deobfuscator.phase1.ClassHeuristicAnalyzer;

import java.util.List;

/**
 * Abstract profile for a RuneTek revision range.
 *
 * Each profile defines:
 *   - The revision range it supports
 *   - Custom heuristic patterns specific to that era's obfuscation style
 *   - Known structural signatures (superclass patterns, field layouts)
 *   - RSA key patterns and socket connection patterns for injection
 *
 * Phase 4 modularity: implement this interface for each RuneTek generation
 * to support different obfuscation schemes without changing core logic.
 */
public interface RevisionProfile {

    /**
     * Human-readable name for this profile (e.g. "RuneTek 4 (508)")
     */
    String name();

    /**
     * The engine generation this profile targets.
     */
    String engineName();

    /**
     * Minimum revision this profile supports (inclusive).
     */
    int minRevision();

    /**
     * Maximum revision this profile supports (inclusive).
     */
    int maxRevision();

    /**
     * Check if this profile supports a given revision number.
     */
    default boolean supportsRevision(int revision) {
        return revision >= minRevision() && revision <= maxRevision();
    }

    /**
     * Return custom heuristic patterns for class identification.
     * These patterns are added to the ClassHeuristicAnalyzer with priority.
     */
    List<ClassHeuristicAnalyzer.HeuristicPattern> getClassPatterns();

    /**
     * Return known socket host patterns to look for during IP hooking.
     * For RuneTek 4: "world*.runescape.com", for 317: "127.0.0.1" or custom.
     */
    List<String> getKnownHostPatterns();

    /**
     * Return known RSA modulus string length range.
     * Helps identify RSA constants more precisely.
     */
    int getExpectedRSAModulusLength();

    /**
     * Return the expected main class superclass pattern.
     * RuneTek 4: extends Applet; RuneTek 3: extends Applet
     */
    String getMainClassSuperPattern();

    /**
     * Optional: return a base set of known mappings for this revision.
     * These are applied before heuristic analysis.
     */
    java.util.Map<String, String> getBaseClassMappings();

    /**
     * Optional: return known field mappings for well-understood classes.
     */
    java.util.Map<String, String> getBaseFieldMappings();
}
