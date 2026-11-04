# Subsystem map — original Septerra Core 1.04

Produced from the Ghidra program `work/ghidra-proj/Septerra` (1,335 functions, 429,588 bytes)
with `port/ghidra/CallTree.java`, walking the call graph down from `WinMain_` @ `0x412b80`.
Raw data: `work/ghidra-out/calltree_nodes.csv`, `calltree_edges.csv`, `annotation.csv`.

## Headline numbers

| | functions | bytes |
|---|---|---|
| whole program | 1,335 | 429,588 |
| reachable from `WinMain_`, depth ≤ 6 | 743 | 264,380 |
| — of those, calling **no** boundary shim (pure engine logic) | **578** | **163,531** |
| not reached by the walk | 592 | — |

Depth profile: `0=1, 1=83, 2=213, 3=176, 4=167, 5=79, 6=24`.

## Where the boundary calls go

Functions (among the 743) whose *direct* callees touch each subsystem:

| subsystem | functions | bytes |
|---|---|---|
| crt | 104 | 63,564 |
| ui (`user32`) | 29 | 50,002 |
| os (`kernel32`: files, registry, threads) | 30 | 13,627 |
| assets (`Game-DataFiles`) | 14 | 11,619 |
| gdi | 10 | 7,042 |
| audio (`dsound`, `MSS`, SoundEngine) | 6 | 2,264 |
| video (`QTML`) | 6 | 1,357 |
| graphics (`ddraw`) | 1 | 768 |
| config, timer | 2, 2 | 470, 213 |

## Reading this correctly — two caveats

1. **`graphics=1` is not the truth.** DirectDraw must be the game's main output path, so the
   call graph is missing most of it. `getCalledFunctions` only sees *direct* `call` targets;
   the engine dispatches through function pointers and virtual calls (`call [eax+8]`), so
   entire subsystems are invisible to the walk.
2. **That also explains the 592 unreachable functions.** They are not all dead code or CRT —
   a chunk will be indirect-call targets reachable only through pointer tables. Any work plan
   built on this map must treat "unreachable" as "unknown route", not "not needed".

## The 83 direct callees of `WinMain_` (top level)

The largest ones, i.e. the entry points into the real engine:

| address | size | callees | boundary | note |
|---|---|---|---|---|
| `00430500` | 5,822 B | 38 | — | biggest pure-engine function, called straight from `WinMain_` |
| `004156a0` | 3,486 B | 14 | ui | UI/dispatch heavy |
| `0040fdf0` | 1,941 B | 30 | assets, crt | asset/manifest init path |
| `00418ce0` | 1,731 B | 35 | gdi, ui | text/UI drawing |
| `00443f50` | 1,727 B | 12 | — | uses `sprintf_asm2c` (`%dx`) |
| `004073f0` | 1,549 B | 17 | — | |
| `00445460` | 1,042 B | 0 | — | `OpenGameDataFiles_asm2c` — already C |
| `00401bc0` | 935 B | 15 | crt, os | startup/IO |
| `00464bb0` | 917 B | 12 | audio, crt, os | sound init |

## Biggest pure-engine functions (the actual decompilation targets)

| address | bytes | callees | note |
|---|---|---|---|
| `00430500` | 5,822 | 38 | depth 1 — strongest first target |
| `0043b180` | 4,660 | 25 | |
| `00437e40` | 4,567 | 25 | |
| `0042b0f0` | 4,099 | 6 | |
| `0042d950` | 4,093 | 11 | uses `_CIpow` |
| `00435f90` | 3,677 | 15 | |
| `0043da10` | 3,556 | 13 | references `"%02d"` (number formatting) |
| `00424350` | 2,947 | 2 | uses `_CIsqrt` |

## What did *not* work, so nobody repeats it

`Annotate.java` tried to name these functions from referenced string literals. Yield was
almost nil — `0043da10 → "%02d"`, `00443f50 → "%dx"`, everything else empty. These are
game-logic and math routines that build data rather than format text, so **string xrefs are
not a viable naming strategy here.** Naming will need data-flow analysis (what globals they
touch, what structs they index) or behaviour diffing against the running recomp.

## Suggested work order

1. `0x430500` first — 5.8 KB, 38 callees, directly under `WinMain_`, no boundary calls: most
   likely the main loop or the game-state machine, and the richest structural anchor.
2. Follow its callees before widening: 38 edges is enough to define a subsystem boundary.
3. Only then look at `0x43b180` / `0x437e40` (4.6 KB each).
4. Resolve the indirect-call blind spot early — export pointer tables from `.data`
   (`relocations.csv` already marks 21,588 pointer locations) so the map stops lying.
