package com.runetek.deobfuscator.engine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight dependency injection / service locator.
 * Inspired by C# IServiceCollection pattern — register services by type,
 * resolve them anywhere in the pipeline.
 */
public class ServiceRegistry {

    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    /**
     * Register a service instance by its type.
     */
    public <T> void register(Class<T> type, T instance) {
        services.put(type, instance);
    }

    /**
     * Resolve a service by type.
     * @throws IllegalStateException if service not registered
     */
    @SuppressWarnings("unchecked")
    public <T> T resolve(Class<T> type) {
        Object svc = services.get(type);
        if (svc == null) {
            throw new IllegalStateException("Service not registered: " + type.getName());
        }
        return (T) svc;
    }

    /**
     * Check if a service is registered.
     */
    public boolean has(Class<?> type) {
        return services.containsKey(type);
    }
}
