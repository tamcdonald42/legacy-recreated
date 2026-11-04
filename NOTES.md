# Septerra Core -> native Windows port (SRW static recompilation)

Working tree for recompiling *Septerra Core* into a native 32-bit Windows executable
using [M-HT/SR](https://github.com/M-HT/SR) — specifically its Windows recompiler **SRW**.

**Current state: `SR-Septerra.exe` builds and launches.** Video is stubbed (see
*Known gaps*), so movies do nothing.

## Layout

| Path | What it is |
|---|---|
| `reference/SR` | Git submodule, M-HT/SR @ `ac690dd`. `SRW/` = recompiler, `SRW-games/Septerra Core/SRW/` = recompile config (**upstream-tracked**), `games/Septerra Core/SR-Septerra/` = C glue + build dir |
| `septerra_core/` | Game install (GOG **1.04**) — exe, `*.db` assets, `QuickTime.qts`, plus the built `SR-Septerra.exe` and its runtime DLLs |
| `libs/` | Deps: SDL2 2.30.9, mpg123 1.24.0-x86, libquicktime 1.2.4 src (headers only), `lqt_stubs`, **nasm 2.16.03** |
| `work/` | Scratch: SRW run dirs, probe objects, `a.a`/`b.a`. Gitignored |
| `port/build/` | Reproducible build scripts (below) |
| `port/patches/` | Patch applied to the submodule |
| `ghidra_12.2_PUBLIC/`, `H:\jdk-21` | Ghidra 12.2 + JDK 21 (launcher `H:\run_ghidra.bat`); not used by the build |

## Build — three scripts, in order

```
H:\ghidra\port\build\build_lqt_stub.bat      -> port\build\out\lqt\quicktime.lib
H:\ghidra\port\build\build_mpg123_lib.bat    -> port\build\out\lib\mpg123-0.lib
H:\ghidra\port\build\build.bat               -> SR-Septerra.exe
```

`build.bat` does the whole chain: SRW recompile -> copy generated source into `x86\` ->
`compact_source.py` -> nasm probe pass -> `repair_short_jumps.py` -> `scons` (assemble +
link with MSVC). Give each script something with a console: they call `vcvarsall.bat`.

Prerequisites: MSVC BuildTools 2022 (x86), NASM 2.16.03 at `libs\nasm`, Python
(`C:\Users\Admin\miniconda3\python.exe`), SDL2 2.30.9 + mpg123 dev files under `libs\`.

### The five things that break it

1. **The `.sci` config files are mandatory.** `build-x86.sh` copies
   `SRW-games/Septerra Core/SRW/x86/*.sci` next to `SRW.exe` before running it. Skip that
   and SRW still exits 0 but emits ~3,700 `short jump is out of range` errors and no
   extern declarations (`symbol 'GetTickCount' not defined`, hundreds of them), and the
   generated source is twice the size. Copy the `.sci` files, every time.
2. **`scons` alone fails with `Cannot open include file: 'SDL.h'`.** The include/lib paths
   only reach SCons through the `PATH_INC` / `PATH_LIB` env vars (`SConstruct:125-128`).
3. **`SConstruct` needs `advapi32` and `user32` added to the `cl` LIBS list** (see
   `port/patches/0001-sconstruct-add-advapi32-user32.patch`). Upstream builds with mingw,
   which links those by default; MSVC does not, so `Reg*`/`MessageBoxA` come up unresolved.
   Apply with `git -C reference/SR apply ..\..\port\patches\0001-*.patch`.
4. **mpg123's import library must be regenerated.** Upstream's `libmpg123-0.dll.def` has no
   `LIBRARY` line, so `lib /def:libmpg123-0.dll.def` names the module after the *file* and
   appends `.dll` again — the exe ends up importing `libmpg123-0.dll.dll`. Link succeeds,
   the exe then dies instantly with `0xC0000135` (STATUS_DLL_NOT_FOUND). Prepend
   `LIBRARY libmpg123-0.dll` and rebuild; also keep that `.lib` in its **own** directory,
   because with the mpg123 release dir on `LIBPATH` SCons matches the DLL file instead.
5. **`quicktime.lib` is a stub**, not libquicktime.

NASM 2.15.03–2.15.05 are known-broken with SRW; 2.16.03 works.

## The game executable must be v1.04

Upstream supports only the **English 1.04** build:

```
Septerra104.exe   729088 bytes
md5     94daaeb19c17d9af3e2ae245219037a9
sha1    2dd27a46f41a91fed0e6ee7205e4b9cef61275c9
sha256  f2f6040416589935ccbc206fee4db1ef369f6c9e8d167e631b190919b12fdf91
```

**The GOG build of 1.04 works even though its hashes differ** (md5 `3e518925…`,
sha256 `dac9c8a2…`): same 729088-byte size, `FileVersion 1.04`, `goggame.dll` reports
`1.0.4.0`, different PE timestamp only. Verified by running the recompiler: **all 21,588
relocations in the upstream `relocations.csv` match the GOG binary byte-for-byte, zero
mismatches.** The hash gap is header packaging, not code.

The earlier retail CD was **1.02**: 610362 bytes, PE32, **no `.reloc` section** — hence
`Relocations were stripped from file - need external relocations`, and the relocation table
(derived from 1.04) cannot line up with it. SRW then aborts with
`Error: inconsistent relocation target address` at `SRW_loader.c:529`.

## Do not do this again

`C:\Users\Admin\fix_relocations.py` rewrote the target column by reading the dword at each
fixup address in the 1.02 exe. That does not recover a relocation table — 30.9% of "targets"
land inside the image versus 86.7% in the upstream table, with gems like
`0x401017 -> 0xc0850049` (a `mov` encoding, not an address). Reproduced for the record as
`games/Septerra Core/SR-Septerra/x86/relocations.csv.1.02-derived.bak`; the live
`relocations.csv` there is the pristine upstream copy (sha256 `4f20420a…`).

`patch_srw.py` / `patch_srw_unconditional.py` deleted or neutered that
`inconsistent relocation target address` check in `SRW_loader.c`. That suppresses the symptom
of a version mismatch and ships a binary with silently wrong pointers. `SRW_loader.c` is
pristine — keep it that way.

## Known gaps

- **Video playback is dead.** `port/build/lqt_stub.c` exports the 24 `lqt_*` / `quicktime_*`
  symbols QTML.c needs, all returning 0. The exe links and the game runs, but any QuickTime
  movie plays nothing. Real libquicktime needs MSYS2 + autotools + ffmpeg, or a prebuilt
  Windows `quicktime.lib` from someone who has one.
- **GUI not yet observed.** The smoke test (`port/build/smoke_test.ps1`) launches the exe over
  SSH, i.e. in session 0 with no desktop, and only proves it does not crash immediately
  (stays up >8 s). Whether it renders needs a run in the interactive RDP/console session.
- 32-bit x86 only; the `x64` and `llasm` build dirs are untouched.

## Recompiled artifact

`SR-Septerra.exe`: 439296 bytes, PE32 i386, subsystem WINDOWS, image base `0x400000`,
imports SDL2 / libmpg123-0 / quicktime(stub) / WINMM / ADVAPI32 / USER32 / KERNEL32 /
SHELL32. Runtime needs `SDL2.dll`, `libmpg123-0.dll`, `quicktime.dll` beside it.

## Decompilation (the actual source-port path)

The recomp is not the source port; it is the map plus the oracle. Ghidra work lives in
`port/ghidra/`, project in `work/ghidra-proj/Septerra`, output in `work/ghidra-out/`.

```
port\ghidra\analyze_septerra.bat   -> import + analyze + Inventory.java
port\ghidra\ground_truth.bat       -> GroundTruth.java (seed .sci truth) + labeled inventory
```

Ghidra 12.2 needs **JDK 25** (`H:\jdk-25\jdk-25.0.5+8`); the JDK 21 that
`install_ghidra.py` pulled is too old and fails with "unsupported java version". The project
directory must exist before `analyzeHeadless` runs ("Directory not found" otherwise).

### Inventory of the original 1.04 (measured)

| | count | bytes |
|---|---|---|
| functions recovered | 1335 | 429588 |
| looks like MSVC CRT (skip — link the real CRT instead) | 427 | 87061 (20%) |
| game/engine | 908 | 342527 (80%) |
| of which still unnamed `FUN_*` | 806 | 327180 |
| asm<->C boundary (`*_asm2c`, already C in the shim) | 83 | — |

Median function 114 bytes; 451 functions <=64 bytes; 88 >=1025 bytes; largest 6565 bytes
(`FUN_0043fc30`). Ground truth seeded: 84 renamed, 2 no-return, 5 missed code ranges
disassembled, 31 data ranges cleared.

**Correction to the obvious plan:** the `.db` archive reader is *not* a decompilation target.
`OpenGameDataFiles` / `RecordOpen` / `RecordRead` / `CloseGameDataFiles` are already written in
C in `Game-DataFiles.c` — the generated asm just calls them (`*_asm2c` = asm calls C). The 83
boundary functions are done; the remaining work is the 806 unnamed game/engine functions.

Next sensible step: walk the call graph down from `WinMain_` (0x412b80) to pick subsystems by
boundary rather than by address.

### Call-graph walk (step 2)

```
port\ghidra\make_symbol_map.py         -> out/shim_symbols.csv (241 boundary symbols -> subsystem)
port\ghidra\run_calltree.bat           -> calltree_nodes.csv / calltree_edges.csv
port\ghidra\run_annotate.bat           -> annotation.csv (string/xref evidence for top functions)
```

Result and the caveats that matter: **`port/ghidra/subsystem_map.md`**. Short version: 743 of
1,335 functions are reachable from `WinMain_` (264 KB), 578 of those are pure engine logic
(163 KB), and the walk is blind to indirect calls — so "unreachable" means "unknown route",
not "unused". First decompilation target: `0x430500` (5.8 KB, 38 callees, directly under
`WinMain_`).
