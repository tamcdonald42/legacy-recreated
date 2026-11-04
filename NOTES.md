# Septerra Core -> native Windows port (SRW static recompilation)

Working tree for recompiling *Septerra Core* into a native 32-bit Windows executable
using [M-HT/SR](https://github.com/M-HT/SR) — specifically its Windows recompiler **SRW**.

## Layout

| Path | What it is |
|---|---|
| `reference/SR` | Git submodule, M-HT/SR @ `ac690dd`. Contains `SRW/` (recompiler), `SRW-games/Septerra Core/SRW/` (recompile config, **upstream-tracked**), `games/Septerra Core/SR-Septerra/` (C glue + build dirs) |
| `septerra_core/` | Retail CD contents (v**1.02**) — game exe, `*.DB` assets, `SETUP.EXE` |
| `septerra_extracted/` | Scratch for `unshield` output (so far just `data1.cab`, 3.7 KB) |
| `libs/` | Runtime deps: SDL2 2.30.9 (+SDL3), mpg123 1.24.0-x86, libquicktime 1.2.4 src, `lqt_stubs`, `unshield.exe/.dll` |
| `ghidra_12.2_PUBLIC/`, `H:\jdk-21` | Ghidra 12.2 + JDK 21 (launcher: `H:\run_ghidra.bat`) |

Driver scripts live outside the repo, in `C:\Users\Admin\`: `install_ghidra.py`, `build_srw.py`,
`get_mpg123.py`, `get_lqt.py`, `build_septerra.py`, `generate_septerra_asm.py`,
`fix_relocations.py`, `patch_srw.py`, `patch_srw_unconditional.py`, `download_unshield.py`.
`build_*.py` shell out to `vcvarsall.bat x86 && scons`.

## HARD REQUIREMENT: the game executable must be v1.04

Upstream `SRW-games/README.md` supports only the **English 1.04** build:

```
Septerra104.exe   729088 bytes
md5     94daaeb19c17d9af3e2ae245219037a9
sha1    2dd27a46f41a91fed0e6ee7205e4b9cef61275c9
sha256  f2f6040416589935ccbc206fee4db1ef369f6c9e8d167e631b190919b12fdf91
```

The CD in `septerra_core/` is **1.02**: `Septerra.exe`, 610362 bytes, PE32 i386, image base
`0x400000`, sections `.text .rdata .data .rsrc` — **no `.reloc` section**, so SRW reports
*"Relocations were stripped from file - need external relocations"* and depends entirely on the
external `relocations.csv`.

`relocations.csv` is supplied by upstream (`SRW-games/Septerra Core/SRW/relocations.csv`,
21588 entries, sha256 `4f20420ae56c3c63805313a206878d766d851d262ef945fb75f9f83b907c0714`)
and was derived from the **1.04** image. Its target addresses do not line up against 1.02,
so SRW aborts with `Error: inconsistent relocation target address` at `SRW_loader.c:529`.

## Do not do this again

`C:\Users\Admin\fix_relocations.py` rewrote the target column by reading the dword at each
fixup address in the 1.02 exe. That does not recover a relocation table — most fixup addresses
land on ordinary instruction bytes in a build they were never computed for. Result: 30.9% of
"targets" fall inside `0x400000-0x4d4c9c`, versus 86.7% in the upstream table, with gems like
`0x401017 -> 0xc0850049` (a `mov` encoding, not an address). Reproduced for the record as
`games/Septerra Core/SR-Septerra/x86/relocations.csv.1.02-derived.bak`. The live
`relocations.csv` in that directory is the pristine upstream copy.

Equally: `patch_srw.py` / `patch_srw_unconditional.py` deleted or neutered the
`inconsistent relocation target address` check in `SRW_loader.c`. That suppresses the symptom of
the version mismatch and would ship a binary with silently wrong pointers. That file is
currently pristine — keep it that way.

## Status

- [x] Ghidra 12.2 + JDK 21 installed, launcher works
- [x] `SRW.exe` built (384512 bytes, scons + MSVC BuildTools 2022 x86)
- [x] `SR-Septerra` C glue compiles (19 `.obj`) — no link, no `.exe` yet;
      verified by full rebuild 2026-11-04, stops at `x86\Septerra.asm' not found`
- [x] Runtime libs staged and `mpg123-0.lib` generated from `.def`
- [ ] **Obtain English 1.04 `Septerra.exe`** <- blocker
- [ ] Run `SRW.exe Septerra104.exe Septerra.asm`, then `scons` to link `SR-Septerra.exe`
- [ ] Ghidra project for interactive analysis (none exists yet — `AppData\Roaming\ghidra`
      is absent, so Ghidra has never been launched)

## Build order once 1.04 is in hand

```
cd H:\ghidra\reference\SR\SRW-games\Septerra Core\SRW
copy <1.04 exe> Septerra104.exe
SRW.exe Septerra104.exe Septerra.asm        # writes .asm/.inc alongside
move the generated .asm/.inc into ..\..\..\games\Septerra Core\SR-Septerra\x86\
cd H:\ghidra\reference\SR\games\Septerra Core\SR-Septerra
"C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvarsall.bat" x86 && scons
```

`rebuild-srsepterra.bat` in the repo root does the `vcvarsall` + `PATH_INC`/`PATH_LIB` + `scons`
sequence. A bare `scons` in `SR-Septerra` fails with `Cannot open include file: 'SDL.h'` — the
include/lib paths only reach SCons through those env vars (SConstruct:125-128).

NASM is **not** on PATH on the host (`Get-Command nasm` -> empty); `build-x86.sh` needs it, and
versions 2.15.03-2.15.05 are known-broken with SRW — install an earlier or later one.
