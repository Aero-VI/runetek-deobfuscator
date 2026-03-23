package com.runetek.deobfuscator.output;

import com.runetek.deobfuscator.phase1.MappingStore;

import java.nio.file.Path;

/**
 * Exports mapping data to various formats.
 */
public class MappingExporter {

    /**
     * Export mappings to a JSON file.
     */
    public static void exportMappings(MappingStore mappings, Path outputPath) {
        mappings.saveToFile(outputPath);
    }
}
