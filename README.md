# RuneTek Universal Deobfuscator & Modification Framework

An automated Java bytecode deobfuscator, injector, and decompiler framework targeting the RuneScape RuneTek 4 engine (Revisions 503–554), with scalable architecture to support RuneTek 3 (Revisions 317+).

## Goal
Input any raw, obfuscated `.jar` client from the OpenRS2 archive and output a cleanly mapped, modified, and highly readable Java source code project that can be recompiled and run out of the box.

## Phases

### Phase 1: Heuristic Mapping & Deobfuscation
- ASM-based bytecode analyzer that maps the obfuscated client before decompilation.
- Reference data: widely documented 508 revision as baseline dictionary.
- Heuristic signatures to identify core engine components (Player, PacketParser, Stream/Buffer, Network/Socket) regardless of ZKM renaming.
- Renaming engine to dynamically rename obfuscated classes, methods, and fields into readable English.

### Phase 2: Automated Bytecode Injection
- IP Interception: locate socket connection method and inject hook to redirect to `127.0.0.1` (or user-defined IP).
- RSA Lobotomy: locate login block encryption method and strip `BigInteger.modPow()` call, sending login blocks in plain text.

### Phase 3: Decompilation Pipeline
- Pipe modified `.jar` through headless industry-standard decompiler (Fernflower or CFR).
- Output as a standard Java project (Maven/Ant) with all necessary dependencies (32‑bit JOGL for HD clients).

### Phase 4: Expansion to RuneTek 3
- Modular design allowing adaptation of heuristic signatures using 317 revision baseline.

## Architecture
- Core engine written in C# for performance and cross‑platform heuristics.
- Java ASM for bytecode manipulation and renaming.
- Maven‑based build for the final deobfuscated Java project.

## Usage
*(To be populated after initial release)*

## License
Proprietary – see `LICENSE`.