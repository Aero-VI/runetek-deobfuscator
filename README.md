# RuneTek Universal Deobfuscator & Modification Framework

An automated Java bytecode deobfuscator, decompiler, and source fixer targeting the RuneScape RuneTek 4 engine (Revisions 503–554), producing **fully recompilable** Java source code.

## What It Does

Input any raw, ZKM-obfuscated `.jar` or `.dat` client from the OpenRS2 archive → output a clean Java source project that compiles and runs.

### Pipeline

1. **ASM Bytecode Analysis** – Load all classes via ASM tree API
2. **Class Renaming** – Rename obfuscated single-letter classes (`a` → `Class_a`) to avoid Java keyword conflicts
3. **Opaque Predicate Removal** – Detect and fold ZKM dummy-parameter conditional branches
4. **Collision Fixing** – Resolve method/field name collisions from renaming
5. **Dead Code Cleaning** – Remove NOP padding and unreachable code
6. **Vineflower Decompilation** – High-quality decompilation via Vineflower (modern Fernflower fork)
7. **Post-Processing Source Fixes**:
   - Disambiguate null arguments in overloaded method calls
   - Cast Object-typed variables to correct AWT types (Container/Component)
   - Replace JSObject.getWindow() with reflection (removed in modern Java)
   - Fix Vineflower's synthetic class-literal field patterns
   - Fix `initCause()` throw chains
   - Add return stubs for methods Vineflower couldn't decompile
   - Generate nativeadvert.browsercontrol stub library

## Usage

```bash
# Build
mvn clean package

# Run (produces output/508/src/ with compilable Java)
java -jar target/runetek-deobfuscator-1.0-SNAPSHOT.jar input/508sd.dat

# Compile the output
cd output/508/src
javac --release 21 -d ../bin $(find . -name "*.java")

# Run (needs a display for the AWT/Applet client)
cd ../bin
java -cp . client 1 live live software members english game0
```

## Input Sources

- OpenRS2 Archive: https://archive.openrs2.org/
- The 508 SD client: `https://archive.openrs2.org/clients/30983.dat`

## Results (508 SD Client)

- **267** classes loaded and deobfuscated
- **267** `.java` files generated
- **0** compilation errors
- **12** deprecation warnings (expected: Applet API removed in modern Java)
- Client runs and prints usage; GUI requires a display

## Requirements

- Java 21+
- Maven 3.8+

## Architecture

```
src/main/java/com/aeroverra/deobfuscator/
├── Deobfuscator.java           # Main entry point & pipeline orchestration
├── transform/
│   ├── TransformPipeline.java   # Orchestrates ASM transforms
│   ├── ClassRenamer.java        # Renames obfuscated short class names
│   ├── OpaquePredicateRemover.java  # Removes ZKM opaque predicates
│   ├── CollisionFixer.java      # Fixes name collisions
│   ├── DeadCodeCleaner.java     # Removes dead code / NOP padding
│   └── ReturnTypeOverloadFixer.java # (experimental, disabled)
├── decompile/
│   └── VineflowerDecompiler.java    # Vineflower decompilation wrapper
├── postprocess/
│   └── SourceFixer.java         # Fixes decompiled source for compilation
└── util/
    ├── JarIO.java               # JAR loading/writing utilities
    └── ScriptGenerator.java     # Batch script generator
```

## License

Proprietary – see `LICENSE`.
