package com.runetek.deobfuscator.phase2;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Registry of all hook definitions. Supports loading from JSON config
 * and querying hooks by target class/method.
 */
public class HookRegistry {

    private final List<HookDefinition> hooks = new ArrayList<>();

    public void register(HookDefinition hook) {
        hooks.add(hook);
    }

    public List<HookDefinition> allHooks() {
        return hooks;
    }

    /**
     * Get all hooks targeting a specific class.
     */
    public List<HookDefinition> hooksForClass(String className) {
        return hooks.stream()
                .filter(h -> h.targetClass().equals(className))
                .collect(Collectors.toList());
    }

    /**
     * Get hooks targeting a specific method in a class.
     */
    public List<HookDefinition> hooksForMethod(String className, String methodName, String desc) {
        return hooks.stream()
                .filter(h -> h.targetClass().equals(className)
                        && h.targetMember().equals(methodName)
                        && (h.targetDescriptor() == null || h.targetDescriptor().equals(desc)))
                .filter(h -> h.type() == HookDefinition.HookType.METHOD_ENTRY
                        || h.type() == HookDefinition.HookType.METHOD_EXIT)
                .collect(Collectors.toList());
    }

    /**
     * Get field hooks for a specific field.
     */
    public List<HookDefinition> hooksForField(String className, String fieldName) {
        return hooks.stream()
                .filter(h -> h.targetClass().equals(className)
                        && h.targetMember().equals(fieldName))
                .filter(h -> h.type() == HookDefinition.HookType.FIELD_GET
                        || h.type() == HookDefinition.HookType.FIELD_SET)
                .collect(Collectors.toList());
    }

    /**
     * Load hook definitions from a JSON file.
     * Expected format:
     * [
     *   {
     *     "name": "onLogin",
     *     "type": "METHOD_ENTRY",
     *     "targetClass": "Client",
     *     "targetMember": "processLogin",
     *     "targetDescriptor": "(II)V",
     *     "callbackClass": "com/mods/Hooks",
     *     "callbackMethod": "onLogin"
     *   }
     * ]
     */
    public void loadFromFile(Path path) {
        try {
            String json = Files.readString(path);
            Gson gson = new Gson();
            Type type = new TypeToken<List<Map<String, String>>>() {}.getType();
            List<Map<String, String>> entries = gson.fromJson(json, type);

            for (Map<String, String> entry : entries) {
                HookDefinition hook = HookDefinition.builder(entry.get("name"))
                        .type(HookDefinition.HookType.valueOf(entry.getOrDefault("type", "METHOD_ENTRY")))
                        .targetClass(entry.get("targetClass"))
                        .targetMember(entry.get("targetMember"))
                        .targetDescriptor(entry.get("targetDescriptor"))
                        .callbackClass(entry.get("callbackClass"))
                        .callbackMethod(entry.get("callbackMethod"))
                        .build();
                register(hook);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load hooks from " + path, e);
        }
    }

    public int size() { return hooks.size(); }
}
