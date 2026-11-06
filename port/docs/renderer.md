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

## Why this matters

- **The 0x230 frame record is not just bookkeeping.** It is what the blitter reads to find pixels.
  The record layout decoded earlier is therefore the pack key: replace what the record points at
  and the blitter draws something else.
- **The blitter is small.** 163 lines of decompiled C for the routine that paints every sprite.
  The "30–80 functions" estimate for the render path now has a concrete centre: `FUN_00404b20`,
  `FUN_00407d00`, `FUN_0040b650`, plus the lock/unlock pair.
- **A wrapper-side or pack-side scaling step belongs here.** If art resolution is ever to matter,
  this is the loop where source pixels become screen pixels — the only place where sampling
  decisions are made.

## Open questions

- Where the pixels actually live inside the 0x230 record (the blitter reads a pointer from it; the
  field has not been identified yet).
- Whether the blitter does any scaling of its own, or only 1:1 copies. The clip maths suggests 1:1
  with clipping, which would confirm art can never exceed native detail today.
- What `0x300` in the stride computation represents — probably room below the visible frame for
  off-screen rows.

## Next steps, in order

1. Identify the pixel pointer field inside the 0x230 record (blitter side).
2. Confirm whether the blit is 1:1 (no sampling) — settles the art-resolution ceiling for good.
3. Then a pack can substitute pixels at the record level, which is the first real art pack.

## Inside the blitter: the sprite format, and the resolution answer

Reading `FUN_00404b20` end to end settles both open questions.

### The pixel write

```c
*puVar6 = *(undefined2 *)(local_2c + 4 + (uint)*pbVar9 * 2);
puVar6 = puVar6 + 1;
pbVar9 = pbVar9 + 1;
```

Source is a **byte** (`*pbVar9`); destination is a **16-bit word**. The byte is used as an index
into a table of 16-bit entries at `local_2c + 4`. In other words:

- **Sprite art is 8-bit palettized**, one byte per pixel.
- **Each frame carries its own palette**: 256 entries of 16-bit colour (RGB565), starting at
  offset +4 of the frame's data structure.
- The screen is 16-bit RGB565, which is why the *composited* surface we dumped looked direct-colour.
  Both statements are true; they describe different ends of the same operation.

*Correction to an earlier note:* `resolution_findings.md` recorded "art is RGB565, not palettized".
That was measured on the presented surface, not on the source art. For sprite art the truth is
**8-bit indexed with a per-frame palette**.

### The copy is one-to-one

The inner loop advances the source pointer by exactly one byte per pixel written, and the
destination by exactly one word. There is no step, no factor, no second sample anywhere in the
routine. Vertical walk adds a constant stride per row.

**No scaling exists, in any form, anywhere in this engine.** Not in the wrapper, not in the
placeholder routine, not in the blitter. The 2.00× window magnification really is all there is.

### The pack hook

The frame data pointer is:

```c
local_2c = *(ushort *)(record + 2) * 0x230 + *base;   /* the record */
iVar4    = *(int *)(local_2c + 0x204);                 /* an override */
if (iVar4) local_2c = iVar4;                           /* use it instead */
```

Field **+0x204** is a pointer the engine prefers when it is non-zero: substitute it and the blitter
draws from whatever it points at, requiring only that the target begins with a 512-byte RGB565
palette followed by index data reachable through the row descriptors. That is the cleanest possible
insertion point — no patching of engine code, just a pointer.

### Row structure

Rows are described by pairs of 16-bit values walking a descriptor array: `(skip, count)`. The
descriptor's other fields give the row's position in the pixel pool (`param_1[4] + offset`) and the
run length. Clipping against the destination rectangle happens before the copy, per run, on both
edges and both orientations (the code handles mirrored drawing with `iVar7 = ±1`).
