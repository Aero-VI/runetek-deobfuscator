# RuneTek Universal Deobfuscator

A Java/ASM-based framework for deobfuscating and modifying RuneTek game clients. Produces clean, re-compilable Maven projects with meaningful names and injectable event hooks.

## Features

- **Phase 1: Heuristic Mapping & Renaming** — Structural pattern matching identifies obfuscated classes (Client, Widget, Buffer, Node, Model, etc.) and applies meaningful names to classes, fields, and methods
- **Phase 2: ASM Bytecode Injection Hooks** — Injects EventBus dispatch calls at method entry/exit and field get/set points, configurable via JSON
- **Pipeline Architecture** — Modular, extensible transform phases with dependency injection
- **Mapping Persistence** — Export/import name mappings as JSON for reuse across revisions
- **Procyon Decompilation** — Optional decompilation to readable Java source in a Maven project
- **Re-compilable Output** — Generated Maven project compiles out of the box

## Quick Start

```bash
# Build
mvn clean package

# Run (basic deobfuscation)
java -jar target/runetek-deobfuscator-0.1.0-SNAPSHOT.jar input.jar output/

# With mappings and hooks
java -jar target/runetek-deobfuscator-0.1.0-SNAPSHOT.jar input.jar output/ \
  --mappings mappings.json \
  --hooks hooks.json \
  --decompile \
  --verbose
```

## CLI Options

| Option | Description |
|--------|-------------|
| `--mappings <file>` | Load/save name mappings (JSON) |
| `--hooks <file>` | Load hook definitions (JSON) |
| `--skip-rename` | Skip Phase 1 (heuristic renaming) |
| `--skip-hooks` | Skip Phase 2 (hook injection) |
| `--decompile` | Decompile output to Java source |
| `--verbose` | Verbose logging |

## Hook Definition Format

```json
[
  {
    "name": "onLogin",
    "type": "METHOD_ENTRY",
    "targetClass": "Client",
    "targetMember": "processLogin",
    "targetDescriptor": "(II)V"
  },
  {
    "name": "onGameStateChange",
    "type": "FIELD_SET",
    "targetClass": "Client",
    "targetMember": "gameState",
    "targetDescriptor": "I"
  }
]
```

Hook types: `METHOD_ENTRY`, `METHOD_EXIT`, `FIELD_GET`, `FIELD_SET`

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full design document.

## Requirements

- Java 21+
- Maven 3.9+

## Building

```bash
mvn clean package        # Build fat JAR
mvn test                 # Run tests
```
