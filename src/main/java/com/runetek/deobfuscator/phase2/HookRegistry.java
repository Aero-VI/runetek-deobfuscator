package com.runetek.deobfuscator.phase2;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Registry of all hook definitions. Supports loading from JSON config
 * and querying hooks by target class/method.
 */
public class HookRegistry {

    private final List<HookDefinition> hooks = new ArrayList<HookDefinition>();

    public void register(HookDefinition hook) {
        hooks.add(hook);
    }

    public List<HookDefinition> allHooks() {
        return hooks;
    }

    public List<HookDefinition> hooksForClass(String className) {
        List<HookDefinition> result = new ArrayList<HookDefinition>();
        for (HookDefinition h : hooks) {
            if (h.targetClass().equals(className)) {
                result.add(h);
            }
        }
        return result;
    }

    public List<HookDefinition> hooksForMethod(String className, String methodName, String desc) {
        List<HookDefinition> result = new ArrayList<HookDefinition>();
        for (HookDefinition h : hooks) {
            if (h.targetClass().equals(className)
                    && h.targetMember().equals(methodName)
                    && (h.targetDescriptor() == null || h.targetDescriptor().equals(desc))
                    && (h.type() == HookDefinition.HookType.METHOD_ENTRY
                        || h.type() == HookDefinition.HookType.METHOD_EXIT)) {
                result.add(h);
            }
        }
        return result;
    }

    public List<HookDefinition> hooksForField(String className, String fieldName) {
        List<HookDefinition> result = new ArrayList<HookDefinition>();
        for (HookDefinition h : hooks) {
            if (h.targetClass().equals(className)
                    && h.targetMember().equals(fieldName)
                    && (h.type() == HookDefinition.HookType.FIELD_GET
                        || h.type() == HookDefinition.HookType.FIELD_SET)) {
                result.add(h);
            }
        }
        return result;
    }

    /**
     * Load hook definitions from a JSON file.
     */
    public void loadFromFile(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            String json = new String(bytes, StandardCharsets.UTF_8);
            Gson gson = new Gson();
            Type type = new TypeToken<List<Map<String, String>>>() {}.getType();
            List<Map<String, String>> entries = gson.fromJson(json, type);

            for (Map<String, String> entry : entries) {
                String hookType = entry.get("type");
                if (hookType == null) hookType = "METHOD_ENTRY";
                HookDefinition hook = HookDefinition.builder(entry.get("name"))
                        .type(HookDefinition.HookType.valueOf(hookType))
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
