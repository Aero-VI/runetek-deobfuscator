package com.runetek.deobfuscator.phase2;

/**
 * Defines a single hook injection point.
 * Hooks can be injected at method entry, exit, or at specific instruction patterns.
 */
public class HookDefinition {

    public enum HookType {
        /** Inject at method entry (before first instruction) */
        METHOD_ENTRY,
        /** Inject at method exit (before every return) */
        METHOD_EXIT,
        /** Inject before field read */
        FIELD_GET,
        /** Inject after field write */
        FIELD_SET
    }

    private final String name;
    private final HookType type;
    private final String targetClass;      // Internal class name
    private final String targetMember;     // Method or field name
    private final String targetDescriptor; // Method/field descriptor
    private final String callbackClass;    // Class containing the callback
    private final String callbackMethod;   // Static callback method name

    public HookDefinition(String name, HookType type, String targetClass,
                          String targetMember, String targetDescriptor,
                          String callbackClass, String callbackMethod) {
        this.name = name;
        this.type = type;
        this.targetClass = targetClass;
        this.targetMember = targetMember;
        this.targetDescriptor = targetDescriptor;
        this.callbackClass = callbackClass;
        this.callbackMethod = callbackMethod;
    }

    public String name() { return name; }
    public HookType type() { return type; }
    public String targetClass() { return targetClass; }
    public String targetMember() { return targetMember; }
    public String targetDescriptor() { return targetDescriptor; }
    public String callbackClass() { return callbackClass; }
    public String callbackMethod() { return callbackMethod; }

    @Override
    public String toString() {
        return String.format("Hook[%s: %s.%s%s → %s.%s]",
                name, targetClass, targetMember, targetDescriptor,
                callbackClass, callbackMethod);
    }

    /** Builder for convenience */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private HookType type = HookType.METHOD_ENTRY;
        private String targetClass;
        private String targetMember;
        private String targetDescriptor;
        private String callbackClass;
        private String callbackMethod;

        Builder(String name) { this.name = name; }

        public Builder type(HookType t) { this.type = t; return this; }
        public Builder targetClass(String c) { this.targetClass = c; return this; }
        public Builder targetMember(String m) { this.targetMember = m; return this; }
        public Builder targetDescriptor(String d) { this.targetDescriptor = d; return this; }
        public Builder callbackClass(String c) { this.callbackClass = c; return this; }
        public Builder callbackMethod(String m) { this.callbackMethod = m; return this; }

        public HookDefinition build() {
            return new HookDefinition(name, type, targetClass, targetMember,
                    targetDescriptor, callbackClass, callbackMethod);
        }
    }
}
