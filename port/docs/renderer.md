# The painter, located

The chain that puts a scene on screen, with addresses in the original 1.04 binary. Every claim
below is from decompiled code or runtime instrumentation, and each step was reached by following
data rather than guessing at call sites.

## The chain

```
FUN_0040b650   scene driver      walks g_levelObjs, tests each object's rect on screen
   └─ FUN_00407d00   placement    works out the destination rect, clips it to 640 x height
        └─ FUN_00404b20   blitter  reads the 0x230-byte frame record, writes the pixels
```

And underneath all of it, the screen itself:

```
FUN_00402850   lock screen    ->  DAT_004ae944 = pixel base
                                  DAT_004ae94c = base + (pitch/2) * 0x300   (a line far down)
FUN_004028e0   unlock screen
```

## How each piece was found

**Screen globals.** The wrapper's `Lock` is handed the surface; scanning the engine's `.data` for a
stored pointer to that surface produced exactly one hit, at `0x4A91A4` in the recompiled binary.
The linker map names that slot `loc_4DE56C`, i.e. original address **0x4DE56C**. Fourteen functions
read it; the writer is `FUN_00406090`.

**Lock/unlock pair.** `FUN_00402850` does the work: it fetches the surface description, stores the
pixel base in `DAT_004ae944`, and computes `DAT_004ae94c = (pitch/2)*0x300 + base`. `FUN_004028e0`
calls the matching unlock and clears both. That is the classic lock → pointer + stride pattern, and
it explains the lock counts measured at runtime.

**Scene driver.** Of the 40 functions reading the pixel base, `FUN_0040b650` (353 lines) is the one
that walks `g_levelObjs`, tests `PtInRect` for visibility, and issues draws per object.

**Placement.** `FUN_00407d00` computes source and destination, clips with the literal `0x280` (640)
and `DAT_004d4ea8` (screen height), then calls the blitter.

**Blitter.** `FUN_00404b20`, 163 lines. It performs the frame-record lookup we predicted from
`StartAnimation`:

```c
local_2c = *(ushort *)(param_3 + 2) * 0x230 + *(int *)*param_1;
```

Index × 0x230 plus a base — the same stride established from the animation side. It then walks
scanlines with `ushort *` pointers (16-bit colour, matching the RGB565 surfaces measured at
runtime).

## Inside the blitter

```c
*dst = *(uint16 *)(record + 4 + *src * 2);
```

One byte in, one 16-bit word out: the byte is an index into a 256-entry table of 16-bit colours at
`record + 4`. The copy advances source by one byte and destination by one word per pixel, with
clipping only — **no step, no factor, no second sample anywhere in the routine**.

That settles the resolution question: **no scaling exists in this engine in any form.** The 2.00x
window magnification measured earlier is the whole of it.

Rows are not flat bitmaps. Each row is a sequence of `(skip, count)` runs walked from a descriptor
array, and the code handles mirrored drawing with `iVar7 = +/-1`.

*Correction:* an earlier version of this note said "the art is RGB565, not palettized". That was
measured on the composited screen surface. Source art is 8-bit indexed; the screen is RGB565. Both
are true of different ends of the same operation.

## The AM04 file format

The loader `FUN_0040f000` opens a blob by key, reads it whole, checks the magic `AM04`
(`0x34304d41`), then adds the blob base to **eight stored offsets**, turning them into pointers:

```
+0x00  char[4]  'AM04'          (overwritten with the key at load)
+0x04  uint32   type            (forced to 0xFFFFFFFF for I/S/E names)
+0x08  uint32   -> section A
+0x10  uint32   -> section B
+0x18  uint32   -> palette array        (0x400 bytes per frame)
+0x1C  uint32   frame count
+0x20  uint32   -> section D
+0x28  uint32   -> section E   pixel indices
+0x30  uint32   -> section F   run descriptors
+0x38  uint32   -> section G   run jump table
+0x40  uint32   -> section H   (unused in the files checked)
```

Verified against the live engine: for key `0x1001f41` the loaded copy and the file differ in
**39 bytes out of 8,192**, and every one is accounted for (magic → key, type → 0xFFFFFFFF, seven
relocations, and `+0x18` replaced with the engine's converted array). Everything else is identical.

### Record size

`(next section - palette array) / frame count = 0x400` exactly, on a 7-frame and a 16-frame file.
0x400 = 256 entries of 4 bytes, stored as `0x00BBGGRR`. Decoded as 32-bit colour the entries run
smoothly (0.80–0.99 by frame) with 256 distinct values: these are palettes.

**So keys like `0x1001f41` are palette banks, not pictures.** Seven palettes, probably lighting or
time-of-day variants for one scene. An earlier attempt to find palettes "inside frame records" read
noise because there are no pixel records in those files at all.

### The runtime conversion

The engine allocates its own array and rewrites `+0x18` to point at it. That array walks at
**0x230 per frame** — the same stride the blitter uses — and each record is:

```
record+0x000  pointer to the file's 32-bit palette
record+0x004  512-byte RGB565 palette (256 entries)   <- what the blitter reads
record+0x204  header: mode, origin, duration, motion
```

The chain closes: 32-bit palettes in the file, converted to 16-bit at load, read at +4 by the
blitter, with 0x230 as the stride everywhere.

## Section map across three files

| section | 01001f41 | 01001f4b | 01000fa2 | contents |
|---|---|---|---|---|
| A (+0x08) | 760 | 2,432 | 1,900 | small table |
| B (+0x10) | 520 | 1,352 | 17,420 | table |
| palettes (+0x18) | 7,168 | 12,288 | 16,384 | 7 / 12 / 16 palettes |
| D (+0x20) | 480 | 1,248 | 4,944 | small table |
| **E (+0x28)** | **110,065** | **636,131** | **766,636** | **8-bit pixel indices** |
| F (+0x30) | 10,184 | 32,624 | 289,096 | run descriptors |
| G (+0x38) | 6,672 | 24,712 | 107,568 | run jump table |
| H (+0x40) | 0 | 0 | 0 | unused here |

Section E spans 0–255 with 2.8–5.7 bits/byte entropy: 8-bit artwork.

### Rendering attempt

Feeding section E to a flat 640-wide bitmap **shears but shows real content** — a regular grid,
digit strings, star-shaped icons, silhouettes. Not noise, and not the wrong section: rows are run
described (`skip, count`), so a flat read misaligns them. Transcribing the blitter's run walk
offline is the remaining step, and needs no new discovery.

## Open questions

- The exact run-walk semantics (descriptor indexing through sections F and G), needed for a clean
  offline render.
- What `0x300` in the stride computation represents — probably room below the visible frame.
- Which keys are sprite sheets versus palette banks versus scene compositions.

## Next steps, in order

1. Transcribe the blitter's run walk into `port/tools/` and render section E cleanly.
2. Use the result to identify which key holds which artwork.
3. Then a pack can substitute pixels, which is the first real art pack.

## Instrumentation available

Runtime switches in the wrapper (inert unless set):

| variable | what it does |
|---|---|
| `LEGACY_PACK_DIR` / `_TRACE` / `_DUMP` | blob override and extraction |
| `LEGACY_BLIT_LOG` | log every blit with source and destination rects |
| `LEGACY_SHOT_DIR` / `_SHOT_EVERY` | screenshot every Nth presented frame |
| `LEGACY_INPUT` | scripted clicks and keys, `t,action,x,y;...` |
| `LEGACY_DUMP_SURFACES` | write every blitted surface to BMP |
| `LEGACY_ART` | replace a blitted surface with a BMP |
| `LEGACY_FIND_GLOBAL` | locate the engine's screen-surface pointer |
| `LEGACY_FIND_IMAGE` | enumerate image structs in `.data` by shape |
| `LEGACY_IMAGE_DIR` | dump a loaded image struct and its sections |
| `LEGACY_CACHE_DUMP` | dump the engine's image cache table |
| `LEGACY_CALLERS` / `LEGACY_BT` | report call sites reaching the wrapper |

## Where the offline render stands

Not done, and the reason is specific rather than mysterious. The run-walk is transcribed from the
blitter, but it needs `param_1[4]` — the pixel pool, taken from image field `+0x40` — and **that
field is zero for every image loaded in the headless runs**: all twelve are the `0x01001f4x`
palette-bank family.

Two candidate explanations, both testable:

1. the pixel pool is built later, during scene loading, and the runs never got that far;
2. the scripted click did not actually reach gameplay this time.

Either way the next move is the same and cheap: confirm visually that the game is in a scene, then
dump the live image struct at that moment and look for a non-zero `+0x40`. Until that field is
non-zero, no offline render is possible — the indices have no base to index from.

## Gameplay confirmed, and what that rules out

The scripted click does reach the game: a screenshot at frame 24,600 shows the opening cutscene —
throne room, character portrait, dialogue box reading *"Doskias: It is not heresy. The artifacts are
meant to unlock the secrets of the Core..."*.

So the scene renderer is running with real artwork on screen, while the image structs visible in
`.data` still report `+0x40 = 0` and still belong to the `0x01001f4x` family only.

That means the scene does **not** draw from those image structs. The blitter's five-pointer input
must come from a different structure — most likely the per-object animation records (0x1C8 `Obj`,
0x230 frame records) rather than the image cache.

**Consequence:** hunting image keys was the wrong thread for scene art. The palette banks are a
real finding, but they are not what paints a scene.

## Offline sprite render: working

The five pointers passed to the blitter were one slot off in my earlier reading. `FUN_00409050`
assembles them on the stack, so `&local_18` hands over six consecutive dwords:

```
param_1[0] = &image[0x18]   -> *param_1 = palette records (0x230 stride)
param_1[1] = image[0x20]    -> section D: 0x18-byte entries
param_1[2] = image[0x38]    -> section G: row table, 8 bytes per row
param_1[3] = image[0x30]    -> section F: run descriptors, 8 bytes per run
param_1[4] = image[0x28]    -> section E: pixel indices
```

**That is the whole mystery of the zeroed `+0x40`**: the pixel pool is section E, which is inside
the file. The `+0x40` field is simply unused, and I had been reading the struct one field late.

Section layouts, confirmed by inspection:

```
D entry  (0x18 bytes): +0x10 row index, +0x14 width
G row    (8 bytes):    +0 count-run index, +4 runs in this row
F run    (8 bytes):    +0 offset into E, +4 skip, +6 count
```

`port/tools/render_am04.py` implements this and renders entries from a real file. First runs on
`01001f4b.bin`: 43x43 and 73x43 sprites appear, and they are **recognisable game art** — inventory
item icons (equipment, a chest harness, a circular disc), each inside its selection frame. Rendered
entirely outside the game, from the file on disk.

Not perfect yet: entries 0–2 look like near-duplicate frames of the same item, which suggests the
D-entry index is doing something more subtle than "one entry = one sprite" (probably animation
frames of a single item, or a per-frame table). But the pixels, palettes, runs and row addresses are
all correct — the image is the artwork, not noise.

## What this means for art packs

A pack can now be authored against a format we can read, write and verify offline: 256-colour
palette plus run-described index data. The remaining work is packaging, not discovery.

## Blitter map: which image is drawn where

`LEGACY_BLIT_HOOK=1` installs a detour at the sprite blitter and logs every distinct draw:

```
blit-key: key=08001F58 entry=10 frame=11 rect=233,198 414x376
blit-key: key=01001F40 entry=0  frame=0  rect=0,0 28x28
```

How the key is recovered: `FUN_00409050` builds the blitter's five pointers on the stack from
`&local_18`, so `p1[0]` is `&image[0x18]`. The image base is therefore `p1[0] - 0x18`, and the
loader stamps the record key at the image's offset `0` (see the live-vs-file diff for `0x1001f41`,
where `AM04` in the file becomes the key in memory). `entry` is `*(u16*)(param_3+4)` and `frame` is
`*(u16*)(param_3+2)`.

This turns "guess which file holds this art" into a lookup: play to the screen you care about, close
the game, read `blit_keys.log`. The `Trace Blits.bat` launcher does exactly that.

First map, from the title/menu sequence:

| Key | Entries drawn | Notes |
|---|---|---|
| `01001F40` | 0-15, each once, stacked rects | startup sequence, 24-31 px wide |
| `08001F58` | 0-12 cycling | scene/panel art, rects up to 594x237 |
| `08001F57` | 20 | side panel at 21,418 (161x461) |

Note how much of the "scene" is one key drawn repeatedly at different entries - which is exactly the
per-frame behaviour that made file-level guessing fail.

### First full map, from a real play session

`Trace Blits.bat`, inventory opened, 56,371 logged draws, deduplicated by key:

| Key | Draws | Role |
|---|---|---|
| `08001F58` | 14,950 | scene / panel art, cycles entries per frame |
| `01001F4B` | 9,954 | inventory + HUD panels |
| `08001F4D` | 7,964 | never examined before this map |
| `01001F47` | 7,956 | UI |
| `01001F43` | 6,723 | UI |
| `01001F41` | 4,819 | palette-bank / UI |
| `01001F45` | 2,159 | UI |
| `08001F57` | 1,324 | side panel (161x461) |
| `01001F4C` | 336 | small icons |
| `01001F42` | 168 | UI |
| `01001F40` | 18 | startup sequence |

`01001F4B` by entry: `0`, `1`, `5`, `7`, `9`, `20` are drawn ~2,000 times each at large clip windows
(panel furniture), while **entry `6` is drawn 94 times with clip bounds `16,342 -> 89,379`** - a
37-pixel-tall strip in the HUD area. That is the disc icon.

So the very first replacement did target the correct entry. The map confirms it independently of the
screenshot argument, which is the point: from here, identification is a lookup, not a guess.
