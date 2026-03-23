package com.runetek.deobfuscator.dictionary;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry of available revision profiles.
 * Supports auto-detection of the correct profile based on revision number,
 * or explicit profile selection via CLI.
 */
public class ProfileRegistry {

    private final List<RevisionProfile> profiles = new ArrayList<RevisionProfile>();

    public ProfileRegistry() {
        // Register built-in profiles
        profiles.add(new RuneTek4Profile());
        profiles.add(new RuneTek3Profile());
    }

    /**
     * Register a custom profile.
     */
    public void register(RevisionProfile profile) {
        profiles.add(0, profile); // Custom profiles take priority
    }

    /**
     * Find a profile by engine name (e.g. "RuneTek 4", "RuneTek 3").
     */
    public RevisionProfile findByName(String name) {
        for (RevisionProfile profile : profiles) {
            if (profile.engineName().equalsIgnoreCase(name)
                    || profile.name().equalsIgnoreCase(name)) {
                return profile;
            }
        }
        return null;
    }

    /**
     * Find a profile that supports a given revision number.
     */
    public RevisionProfile findByRevision(int revision) {
        for (RevisionProfile profile : profiles) {
            if (profile.supportsRevision(revision)) {
                return profile;
            }
        }
        return null;
    }

    /**
     * Get the default profile (RuneTek 4).
     */
    public RevisionProfile getDefault() {
        return profiles.get(0);
    }

    /**
     * List all registered profiles.
     */
    public List<RevisionProfile> allProfiles() {
        return profiles;
    }
}
