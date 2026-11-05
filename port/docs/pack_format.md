# Asset pack format — draft v0.1

What the engine actually does with art, what a pack can therefore override, and the format
those overrides take. Everything marked *(evidence)* was read out of the 1.04 binary; everything
marked *(open)* is not yet established and the spec stays vague on purpose.

## 1. How art enters the engine *(evidence)*

All content comes out of the `.db` archives through one API, implemented in
`Game-DataFiles.c` on the recomp side:

| function | address | role |
|---|---|---|
| `RecordTryOpen_asm2c` | 0x445ae0 | open by key, `0xffffffff` on miss |
| `RecordGetSize_asm2c` | 0x445f30 | blob length |
| `RecordRead_asm2c` | 0x445c80 | read blob into a `malloc` buffer |
| `RecordClose_asm2c` | 0x445ec0 | release handle |

Twelve functions call `RecordRead_asm2c`. Each follows the same shape:

```c
uVar4 = RecordTryOpen_asm2c(key);
_Size = RecordGetSize_asm2c(uVar4);
buf   = malloc_asm2c(_Size);
RecordRead_asm2c(uVar4, buf, _Size);
RecordClose_asm2c(uVar4);
```

Loaded blobs carry a magic. `FUN_0040e2a0` reads one and checks `0x30305647` (`"GV00"`), then
fixes up internal offsets in place — so records are relocatable blobs with an internal pointer
table, not flat bitmaps.

## 2. The animation frame record, 0x230 bytes *(evidence)*

`StartAnimation` (0x40e900) computes the address of frame *n* as:

```c
frame = *(int *)(animRoot + 0x18) + *(ushort *)(anim + 0x190) * 0x230;
```

and `CreateAnimFrame` (`FUN_0040e6d0`) allocates and initialises that record:

```c
_Size = 0x230;                       /* no palette  */
if (hasPalette) _Size = 0x630;       /* 0x230 + 0x400 = 256 * 4 palette entries */
...
for (iVar3 = 0x8c; iVar3 != 0; ...)  /* copy 0x8C dwords = 0x230 bytes */
    *puVar5 = *puVar4;
param_1[0x8b] = hasPalette ? param_1 + 0x8c : 0;   /* +0x22C -> palette at +0x230 */
```

Two independent confirmations of 0x230: the `malloc` size, and the `0x8C` dword copy.

Known field offsets inside the record:

| offset | contents |
|---|---|
| `+0x208` | mode, `param_6` ∈ {0, 1, 2} — selects the scroll-rate formula |
| `+0x20C` | origin offset X *(zeroed on create)* |
| `+0x210` | origin offset Y *(zeroed on create)* |
| `+0x214` | origin offset Z *(zeroed on create)* |
| `+0x218` | `param_5` (duration/frame-time passed to `StartAnimation`) |
| `+0x219` | byte, feeds the X scroll-rate |
| `+0x21A` | byte, feeds the Y scroll-rate |
| `+0x21B` | byte, feeds the Z scroll-rate |
| `+0x21C` | float, X rate `((K - b21B) - off20C) / duration` |
| `+0x220` | float, Y rate |
| `+0x224` | float, Z rate |
| `+0x228` | 0 |
| `+0x22C` | pointer to the 256-entry palette, or 0 |
| `+0x230` | palette (only when `+0x22C != 0`), 256 × 4 bytes |

The bytes at `+0x219..0x21B` and the `K` constant at `_DAT_00495e48` drive per-frame scroll
rates — these are the tiling/parallax parameters, and they are why a replaced frame must keep
the original record's timing bytes intact even if its pixels change.

## 3. Is there a scaling blit? No *(evidence)*

`WhoCalls` over the GDI/DDraw imports finds exactly one renderer entry point, `FUN_00418ce0`,
calling `BitBlt`, `CreateCompatibleDC`, `SelectObject` and `DeleteDC`. `StretchBlt`,
`CreateCompatibleBitmap`, `CreateDIBitmap` and `SetDIBits` are **not imported at all**. DirectDraw
is COM, so its surface calls are indirect vtable dispatches with no symbol xrefs — which is why
the call-graph walk reported `graphics = 1`.

Consequence, and it constrains the whole format: **the shipped engine cannot scale a blit.**
A replaced frame either keeps its original pixel dimensions, or the shim must gain a scaling
blit before larger art can be used.

## 4. Pack layout

```
packs/<packname>/
  manifest.json
  blobs/<record-key>.bin           # blob-level: whole record replaced, opaque
  frames/<set>/<index>.png         # frame-level: one animation frame replaced
```

## 5. `manifest.json` — `legacy-recreated-pack/1`

```json
{
  "format": "legacy-recreated-pack/1",
  "name": "Example Pack",
  "version": "0.1.0",
  "author": "you",
  "requires": ["frame-override"],
  "frames": [
    {
      "set": "sprite-set-key",
      "index": 12,
      "file": "frames/sprite-set-key/12.png",
      "source_size": [64, 96],
      "mode": "same-size",
      "notes": "upscaled 4x then downsampled to source size"
    }
  ],
  "blobs": []
}
```

Rules the loader enforces:

- `format` must match; unknown fields ignored, unknown `requires` → pack refused, not half-loaded.
- `source_size` must equal the original frame's dimensions while `mode` is `same-size`.
- Missing `file` or a size mismatch is a pack error, never a silent fallback to a half-replaced
  frame — a visibly broken sprite is better than a mysteriously wrong one.
- Loading is all-or-nothing per frame; packs can be disabled by config without touching files.

## 6. v0 implemented — `port/patches/0002-legacy-pack-blob-override.patch`

The blob layer is real code in `Game-DataFiles.c` now, not a proposal. Three environment
variables drive it, all read once at first record open:

| variable | effect |
|---|---|
| `LEGACY_PACK_DIR` | directory to read overrides from; unset = stock behaviour |
| `LEGACY_PACK_TRACE=1` | log every record key the game opens, with size and whether it was overridden |
| `LEGACY_PACK_DUMP` | write every opened record to `<dir>/<key>.bin` — this is how you get originals to build a pack from |

Override lookup is `<dir>/blobs/<key>.bin`, key as 8 lowercase hex digits, zero-padded
(`05000001.bin`). Unpadded names are tried as a fallback. Quoted values and trailing
separators are tolerated, because `set LEGACY_PACK_DIR="D:\packs\"` is what people type.

### What is verified

- **Substitution happens.** Overriding key `0x5000001` with a 4,630-byte file produced
  `legacy-pack: override key 0x5000001 (4630 bytes) <- ...\05000001.bin` and the process kept
  running. The record came from the pack, not the archive.
- **Extraction works.** A 12-second headless run with `LEGACY_PACK_DUMP` wrote **225 records,
  56 MB**, with sizes matching the archive index (`0x2000009` → 2,502,586 bytes,
  `0x200000b` → 19,476,566 bytes).

### What is not verified

- **Visual correctness.** Everything above ran in a session with no display. Nobody has yet
  eyeballed a replaced sprite; that needs a desktop run with a real pack.
- **Key → content mapping.** The trace gives keys and sizes, not names. The dumped files are
  raw blobs; which one is which sprite is still an open question (see section 7).

### Practical loop this enables

```
LEGACY_PACK_DUMP=D:\dump   SR-Septerra.exe     # 1. extract what the game loads
                                # 2. find the blob you want (by size, by eye, by editing)
                                # 3. edit it, write D:\pack\blobs\<key>.bin
LEGACY_PACK_DIR=D:\pack    SR-Septerra.exe     # 4. run with the override, trace confirms it
```

## 7. Staged capability — what ships when

| stage | override point | needs |
|---|---|---|
| v0 | whole blob by record key | nothing but the loader — works on the recomp today |
| v1 | per animation frame | a hook at the frame-table fill (`CreateAnimFrame` / `StartAnimation`) |
| v2 | arbitrary size, filtered/scaled | a scaling blit in the shim; then internal resolution work |

v0 is deliberately first: it needs no decompiled code, only a lookup in the record reader, and
it proves the key scheme against real content.

## 8. Open questions *(open)*

- **Record key stability.** Keys arrive as integers (`RecordTryOpen_asm2c(key)`); how they are
  derived from names (`GetRecordName_`, 0x4106e0) is not established. Until it is, blob-level
  packs must ship explicit integer keys and be validated per game version.
- **Which record holds which sprite set.** Twelve loaders read records; only the graphics ones
  matter, and their mapping to visual content is unconfirmed.
- **Full 0x230 header.** Offsets above are the ones the code provably touches; the rest of the
  record is believed to hold the source surface description and is not yet decoded.
- **Palette identity.** Whether a frame's palette is authoritative per frame or shared per sprite
  set decides whether v1 packs may recolour independently.

## 9. Legal

This document specifies a **format**, not assets. Any pack, upscaled or original, is derived from
the game's art and cannot be redistributed. The project ships the loader and, eventually, the
pack-building tool; end users generate packs from their own copy.
