package com.runetek.deobfuscator.phase1;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stores obfuscated → deobfuscated name mappings for classes, fields, and methods.
 * Supports JSON import/export for reuse across runs.
 */
public class MappingStore {

    private final Map<String, String> classMappings = new LinkedHashMap<>();
    private final Map<String, String> fieldMappings = new LinkedHashMap<>();   // "owner.name:desc" → newName
    private final Map<String, String> methodMappings = new LinkedHashMap<>();  // "owner.name+desc" → newName

    // Class mappings
    public void mapClass(String obfuscated, String deobfuscated) {
        classMappings.put(obfuscated, deobfuscated);
    }

    public String resolveClass(String name) {
        return classMappings.getOrDefault(name, name);
    }

    public boolean hasClassMapping(String name) {
        return classMappings.containsKey(name);
    }

    // Field mappings: key = "ownerClass.fieldName:descriptor"
    public void mapField(String owner, String name, String desc, String newName) {
        fieldMappings.put(owner + "." + name + ":" + desc, newName);
    }

    public String resolveField(String owner, String name, String desc) {
        String key = owner + "." + name + ":" + desc;
        return fieldMappings.getOrDefault(key, name);
    }

    // Method mappings: key = "ownerClass.methodName+descriptor"
    public void mapMethod(String owner, String name, String desc, String newName) {
        methodMappings.put(owner + "." + name + "+" + desc, newName);
    }

    public String resolveMethod(String owner, String name, String desc) {
        String key = owner + "." + name + "+" + desc;
        return methodMappings.getOrDefault(key, name);
    }

    public Map<String, String> classMappings() { return classMappings; }
    public Map<String, String> fieldMappings() { return fieldMappings; }
    public Map<String, String> methodMappings() { return methodMappings; }

    /**
     * Load mappings from a JSON file.
     */
    public void loadFromFile(Path path) {
        try {
            String json = Files.readString(path);
            Gson gson = new Gson();
            Type type = new TypeToken<MappingData>() {}.getType();
            MappingData data = gson.fromJson(json, type);
            if (data.classes != null) classMappings.putAll(data.classes);
            if (data.fields != null) fieldMappings.putAll(data.fields);
            if (data.methods != null) methodMappings.putAll(data.methods);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load mappings from " + path, e);
        }
    }

    /**
     * Save mappings to a JSON file.
     */
    public void saveToFile(Path path) {
        try {
            MappingData data = new MappingData();
            data.classes = classMappings;
            data.fields = fieldMappings;
            data.methods = methodMappings;
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(path, gson.toJson(data));
        } catch (IOException e) {
            throw new RuntimeException("Failed to save mappings to " + path, e);
        }
    }

    private static class MappingData {
        Map<String, String> classes;
        Map<String, String> fields;
        Map<String, String> methods;
    }

    public int totalMappings() {
        return classMappings.size() + fieldMappings.size() + methodMappings.size();
    }
}
