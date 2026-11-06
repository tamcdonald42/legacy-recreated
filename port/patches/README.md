# Shim patches

`reference/SR` is a pinned third-party submodule. Every edit we make to it lives here as a patch,
so the submodule can be reset at any time and our overlay rebuilt from files that are actually
committed to this repo.

## Which patch to apply

**`0008-current-overlay.patch` is the one that matters.** It is the complete diff of the working
tree against the pinned submodule commit, verified with `git apply --check --reverse` (the check
passing means the patch reproduces our tree exactly). Apply it to a pristine submodule and you get
the exact build environment used in `port/docs/renderer.md`.

```
cd reference/SR
git apply ../../port/patches/0008-current-overlay.patch
```

## Historical increments

`0001`-`0007` are kept for the record: each one added a single capability at the time it was
built. They are *not* a clean sequence to reapply — they were written against earlier trees.
Read them for history, apply `0008` to build.

| Patch | What it added |
|---|---|
| 0001 | `advapi32` + `user32` link libraries in SConstruct |
| 0002 | blob-level pack override in `Game-DataFiles.c` (`LEGACY_PACK_DIR`) |
| 0003 | blit logging (`LEGACY_BLIT_LOG`) |
| 0004 | frame capture + scripted input (`LEGACY_SHOT_*`, `LEGACY_INPUT`) |
| 0005 | mouse click injection (`Patch_PushMouseClick`) |
| 0006 | surface dump (`LEGACY_DUMP_SURFACES`) |
| 0007 | whole-surface art override (`LEGACY_ART`) — proved the shortcut does not exist |

## What 0008 adds on top

- `/MAP` link flag — produces `SR-Septerra.map`, 12,399 named symbols, the address/name bridge
  between runtime values and original functions.
- `LEGACY_CALLERS` / `LEGACY_BT` — direct-caller and stack reporting at the DirectDraw entry points.
- `LEGACY_FIND_GLOBAL` — scans engine data for whoever stored the screen surface pointer.
- `LEGACY_IMAGE_DIR` / `LEGACY_FIND_IMAGE` — live image-struct dump and key search, used to solve
  the AM04 palette format.
- `LEGACY_CACHE_DUMP`, `LEGACY_SLOTS` — image cache and slot draining.
