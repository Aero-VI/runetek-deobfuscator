# RuneTek Universal Deobfuscator

Automated Java bytecode deobfuscator, injector, and decompiler for RuneTek game clients (revisions 300–554).

**Input:** Raw obfuscated `.jar` from OpenRS2  
**Output:** Clean, mapped, recompilable Maven project

## Requirements

- **Java 8+** (compiled as Java 8 bytecode)
- **Maven 3.6+**

## Build

```bash
mvn clean package
```

Produces a fat JAR at `target/runetek-deobfuscator-0.1.0-SNAPSHOT.jar`.

## Usage

```bash
java -jar runetek-deobfuscator.jar <input.jar> <output-dir> [options]
```

### Options

| Flag | Description |
|------|-------------|
| `--mappings <file>` | Load/save name mappings (JSON) |
| `--hooks <file>` | Load hook definitions (JSON) |
| `--output-jar <file>` | Write deobfuscated classes to a JAR |
| `--skip-rename` | Skip Phase 1 (heuristic renaming) |
| `--skip-hooks` | Skip Phase 2 (hook injection) |
| `--decompile` | Decompile to Java source (Phase 3) |
| `--profile <name>` | Use specific profile (e.g. `"RuneTek 3"`) |
| `--revision <num>` | Auto-detect profile by revision (e.g. `508`, `317`) |
| `--verbose` | Verbose output |

### Example

```bash
# Full pipeline: rename + inject + decompile
java -jar runetek-deobfuscator.jar gamepack_508.jar output/ \
    --revision 508 \
    --mappings mappings.json \
    --output-jar deobfuscated.jar \
    --decompile

# Just rename and export mappings
java -jar runetek-deobfuscator.jar gamepack.jar output/ \
    --skip-hooks \
    --mappings mappings.json
```

## Architecture

### Phase 1: Heuristic Mapping (ASM)
- Structural pattern matching identifies Client, Buffer, Node, Widget, Model, etc.
- String literal analysis finds Login, Network, Cache classes
- Field/method pattern matching suggests meaningful names
- ASM Remapper applies all renames consistently across the classpath

### Phase 2: Bytecode Injection
- **IP Hook:** Redirects `Socket` connections and `InetAddress.getByName()` to `127.0.0.1`
- **RSA Lobotomy:** Removes `BigInteger.modPow()` RSA encryption from login blocks
- **EventBus Hooks:** User-defined method entry/exit and field get/set event dispatching

### Phase 3: Decompilation Pipeline
- Validates bytecode integrity
- Pipes through CFR decompiler
- Generates a complete Maven project with decompiled Java sources

### Phase 4: Modular Profiles
- `RevisionProfile` interface for swappable engine dictionaries
- Built-in profiles: RuneTek 4 (503–554), RuneTek 3 (300–377)
- Custom profiles can be registered for any revision range
- Auto-detection by revision number

## Project Structure

```
src/main/java/com/runetek/deobfuscator/
├── Main.java                    # CLI entry point
├── dictionary/                  # Modular revision profiles
│   ├── RevisionProfile.java     # Profile interface
│   ├── ProfileRegistry.java     # Profile auto-detection
│   ├── RuneTek4Profile.java     # RT4 (503-554) dictionary
│   └── RuneTek3Profile.java     # RT3 (300-377) dictionary
├── engine/                      # Core pipeline engine
│   ├── DeobfuscatorEngine.java  # Main orchestrator
│   ├── EngineConfig.java        # Configuration
│   ├── ServiceRegistry.java     # Dependency injection
│   ├── TransformContext.java    # Shared transform state
│   ├── TransformPhase.java      # Phase interface
│   └── TransformPipeline.java   # Phase executor
├── output/                      # Output writers
│   ├── ClassWriter.java         # .class file writer
│   ├── JarWriter.java           # JAR assembler
│   ├── MappingExporter.java     # Mapping JSON export
│   └── ProjectGenerator.java    # Maven project + CFR decompilation
├── phase1/                      # Heuristic mapping
│   ├── ClassHeuristicAnalyzer.java
│   ├── FieldPatternMatcher.java
│   ├── HeuristicRenamer.java
│   ├── MappingStore.java
│   ├── MethodSignatureMatcher.java
│   ├── RenamingTransformer.java
│   └── StringLiteralAnalyzer.java
├── phase2/                      # Bytecode injection
│   ├── EventBus.java
│   ├── HookDefinition.java
│   ├── HookInjector.java
│   ├── HookRegistry.java
│   ├── IPHookInjector.java
│   ├── MethodHookVisitor.java
│   └── RSALobotomizer.java
├── phase3/                      # Decompilation
│   └── DecompilationPhase.java
└── util/                        # Utilities
    ├── AsmUtil.java
    └── JarLoader.java
```

## Dependencies

- **ASM 9.7.1** — bytecode reading, transformation, and remapping
- **CFR 0.152** — headless decompilation to Java source
- **Gson 2.11** — JSON mapping import/export
- **SLF4J 2.0** — logging
- **JUnit 5** — testing

## License

MIT
