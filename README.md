# The Legacy Recreated

An unofficial decompilation workbench for **Septerra Core 1.04** (1999, GOG build) — working
notes and Ghidra tooling for turning the shipped executable into a readable, eventually
portable code base. The runnable target is the static-recompilation build
from [M-HT/SR](https://github.com/M-HT/SR) (`SR-Septerra.exe`); the decompilation work happens
on the **original** `septerra.exe`, which is the only thing worth decompiling.

This repo contains **no game data and no decompiled game code**. You need your own copy of the
game; nothing here redistributes it.

## Why this exists

I played Septerra Core as a demo, in the window after Valkyrie had lost their publisher and
before they'd found another. The developers asked for feedback, so I wrote back saying a
console-style JRPG on PC was exactly what I wanted and I'd buy it on release. They asked to
quote my email in their pitch. I agreed — which means that in a very small way, I helped put
this game in a box.

That's the honest reason this repo exists. Not preservation in the abstract: I want to play the
game I helped get made, at a resolution that isn't 1999.

The timeline behind that — Viacom New Media closing, Valkyrie reforming, Monolith signing — is
in [`port/docs/history.md`](port/docs/history.md), with sources.

## Approach

The recompilation is not a source port — it is a translation of a translation. Two facts make
it useful anyway:

1. `SRW` proves you have the right binary: all 21,588 relocations resolve byte-for-byte.
2. The ~16,000 lines of C shim in `reference/SR` (SDL2, Win32, DirectDraw, DirectSound) is the
   portability layer. Decompiled code that links against it runs on modern hardware.

So: keep the recomp as a **behavioural oracle**, decompile the original for **readable C**, and
replace generated assembly function-by-function. The build stays runnable at every step.

## Requirements

| | |
|---|---|
| Game | Septerra Core 1.04, `septerra.exe` = 729,088 bytes, PE32 i386, image base `0x400000` |
| Hash | md5 `94daaeb19c17d9af3e2ae245219037a9` — the GOG repack differs but content matches (proven by relocation match) |
| Ghidra | 12.2 (needs **JDK 25**; it refuses 21) |
| Build | NASM 2.16, MSVC BuildTools 2022 x86, Python for `scons` |
| Submodule | `reference/SR` (M-HT/SR, pinned) — provides SRW and the shim |

## Pipeline

```
septerra.exe ──SRW──> Septerra.asm + seg*.inc ──nasm──> .obj ──MSVC──> SR-Septerra.exe  (oracle)
      │
      └──Ghidra──> project ──Inventory──> function inventory (1,335 functions / 429,588 bytes)
                       │
                       ├── GroundTruth.java   seed names from SRW's .sci files
                       ├── CallTree.java      reachability + subsystem tagging from WinMain_
                       ├── Decompile.java     decompile one or many functions + metrics
                       ├── TypeState.java     apply every established name, type and struct
                       ├── Structs.java       create ObjSlot/Effect/Obj + emit objslot.h
                       └── Xrefs.java         who reads/writes this global
```

`port/build/build.bat` is the single entry point for the recomp; each Ghidra script has a
matching `run_*.bat`.

## Findings

Each of these is backed by disassembly or by code in the binary, not by pattern-guessing:

- **Two-level state machine.** `WinMain_` dispatches on `g_gameState` (states 3/4/5 →
  `FUN_00430500`); that function dispatches on `g_subState` (phases `SUB_0..SUB_12`).
  `SUB_8` is the level-entry spawn pass, `SUB_9` the per-frame update.
- **Object slots, 0xBE8 bytes.** `BuildObjTables` proves it: `memset(slot,0,0xbe8)`,
  `slot[0] = object`, selector at `slot+0xB10`, stride `0x2fa` ints. Five 0x1C8 effect
  sub-structs at `slot+0x228`.
- **The object header is the `Effect` layout** — every object offset touched is below `0x1C8`.
- **Combat lists, named by the game's own assert strings.** `AddCombatant` errors with
  *"Tell Jim you ran out of slots in the Allies Combat List"* / *"…the Enemies Combat List"*,
  both capped at 10. Allies occupy slots 3–12 (0–2 reserved), enemies get a second slot array.
- **`g_flag_ae960` is a dud.** 19 references, all reads, zero writes, initial value 0 — it was
  named `g_shutdown` in an earlier pass and the name was wrong. Negative results are recorded
  because they cost the most time to rediscover.

`port/ghidra/finding_430500_state_machine.md` has the full write-up, including two things that
**did not** work: retyping a switch's *global* never clears `case (int *)0x0:` labels, and
`case` labels survive retyping the operand's variable because Ghidra emits the switch expression
inline.

## Asset packs

`port/docs/pack_format.md` specifies the replacement-art format (draft v0.1): where art enters
the engine (`RecordTryOpen` → `RecordRead`), the 0x230-byte animation frame record, and the
staged capability plan — whole-blob overrides work on the current build, per-frame overrides
need a hook, and larger-than-original art needs a scaling blit that the engine does not have.
The format ships; the art never does.

## Layout

```
NOTES.md                  environment, build chain, every trap hit so far
port/build/               recomp pipeline: stub libs, mpg123 import lib, build.bat
port/ghidra/              Ghidra scripts + .bat wrappers + findings
port/include/             engine headers derived from the binary (objslot.h)
port/docs/                format specs (pack_format.md)
port/patches/             the single SConstruct patch needed by the shim
reference/SR              upstream M-HT/SR (submodule)
work/                     scratch: SRW runs, Ghidra project, decompiled output (gitignored)
```

## Status

Readable so far: the state machine, object/effect/slot layout, spawn and combat registration.
Not started: graphics, audio, input, script interpreter, menus. No function has been ported to C
yet — the immediate goal is coverage of the per-frame path, not source yet.

## Trademark & legal

Unofficial fan research project. Not affiliated with, endorsed by, or sponsored by the rights
holders of Septerra Core. "Septerra Core" is used only to identify the software being studied.

This repository ships **no game data, no game executable, and no decompiled game code**. You
must own a copy of the game to use any of it. Nothing here enables playing the game without
one.

## Licence

MIT for the tooling and notes (see `LICENSE`). It covers neither the game, its data, nor any
decompiled output — those are not distributed here.
