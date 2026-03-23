# RuneTek Universal Deobfuscator & Modification Framework

## Overview

A Java-based framework using ASM bytecode manipulation to deobfuscate RuneTek game clients and produce clean, re-compilable Java source projects. The engine uses a pipeline architecture inspired by C# enterprise patterns (dependency injection, strategy pattern, pipeline middleware).

## Architecture

```
┌─────────────────────────────────────────────────┐
│              RuneTek Deobfuscator                │
├─────────────────────────────────────────────────┤
│  CLI Entry Point (Main.java)                    │
├─────────────────────────────────────────────────┤
│  Engine Core (Pipeline Architecture)            │
│  ┌───────────────────────────────────────────┐  │
│  │  DeobfuscatorEngine                       │  │
│  │  ├── TransformPipeline                    │  │
│  │  │   ├── Phase1: Heuristic Renaming       │  │
│  │  │   ├── Phase2: Bytecode Injection Hooks │  │
│  │  │   ├── Phase3: Control Flow Recovery    │  │
│  │  │   └── Phase4: Dead Code Removal        │  │
│  │  ├── ServiceRegistry (DI Container)       │  │
│  │  └── ConfigurationManager                 │  │
│  └───────────────────────────────────────────┘  │
├─────────────────────────────────────────────────┤
│  Phase 1: Heuristic Mapping & Renaming          │
│  ├── ClassHeuristicAnalyzer                     │
│  ├── FieldPatternMatcher                        │
│  ├── MethodSignatureMatcher                     │
│  ├── MappingStore (JSON export/import)          │
│  └── RenamingTransformer (ASM ClassVisitor)     │
├─────────────────────────────────────────────────┤
│  Phase 2: ASM Bytecode Injection Hooks          │
│  ├── HookDefinition (annotation-based)          │
│  ├── HookInjector (ASM MethodVisitor)           │
│  ├── EventBus (hook dispatch)                   │
│  └── HookRegistry                               │
├─────────────────────────────────────────────────┤
│  Output Layer                                   │
│  ├── DecompiledSourceWriter (Procyon/CFR)       │
│  ├── MavenProjectGenerator                      │
│  └── MappingExporter                            │
└─────────────────────────────────────────────────┘
```

## Technology Stack

- **Language:** Java 21
- **Build:** Maven
- **Bytecode:** ASM 9.7 (for reading/writing/transforming .class files)
- **Decompilation:** Procyon or CFR (for final source output)
- **Patterns:** Pipeline, Strategy, Service Locator/DI
- **Output:** Re-compilable Maven project with deobfuscated sources

## Phase 1: Heuristic Mapping & Renaming

Analyzes obfuscated classes to identify patterns and rename them meaningfully:
- Field types/access patterns → field names (e.g., `int a` in a class with x,y,width,height pattern → `Widget.x`)
- Method signatures → method names (e.g., `void a(int, int, int, int)` with drawing calls → `drawRect`)
- Class hierarchy/interface patterns → class names
- String literal analysis for context clues
- Superclass/interface matching for known RuneTek base classes

## Phase 2: ASM Bytecode Injection Hooks

Injects event hooks into deobfuscated bytecode:
- Method entry/exit hooks
- Field read/write interceptors
- Custom injection points via config
- Event bus for hook consumers
- All hooks are removable/configurable

## Project Structure

```
runetek-deobfuscator/
├── pom.xml
├── src/main/java/com/runetek/deobfuscator/
│   ├── Main.java
│   ├── engine/
│   │   ├── DeobfuscatorEngine.java
│   │   ├── TransformPipeline.java
│   │   ├── TransformPhase.java
│   │   ├── TransformContext.java
│   │   ├── ServiceRegistry.java
│   │   └── EngineConfig.java
│   ├── phase1/
│   │   ├── HeuristicRenamer.java
│   │   ├── ClassHeuristicAnalyzer.java
│   │   ├── FieldPatternMatcher.java
│   │   ├── MethodSignatureMatcher.java
│   │   ├── MappingStore.java
│   │   └── RenamingTransformer.java
│   ├── phase2/
│   │   ├── HookInjector.java
│   │   ├── HookDefinition.java
│   │   ├── HookRegistry.java
│   │   ├── EventBus.java
│   │   └── MethodHookVisitor.java
│   ├── output/
│   │   ├── SourceWriter.java
│   │   ├── ProjectGenerator.java
│   │   └── MappingExporter.java
│   └── util/
│       ├── ClassUtil.java
│       └── AsmUtil.java
├── src/test/java/com/runetek/deobfuscator/
│   └── EngineTest.java
└── ARCHITECTURE.md
```
