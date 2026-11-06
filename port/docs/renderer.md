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

## Reading AM04 offline: header confirmed, record base wrong

`FUN_0040f000` is the image loader. It opens a record by key, reads the whole blob, then adds the
blob base to **eight offsets** stored at `+0x08, +0x10, +0x18, +0x20, +0x28, +0x30, +0x38, +0x40` —
turning file offsets into pointers. It checks the magic `'AM04'` (as the dword `0x34304d41`) and
keeps a reference-counted cache keyed by blob key.

So every AM04 file is a small relocatable image with named sections. Verified on
`01000fa2.bin` (1,204,020 bytes): every one of those eight fields holds an offset inside the file.

**What did not survive testing.** I assumed the section at `+0x18` was an array of 0x230-byte frame
records with the palette at record+4 — that reading follows from the blitter. Decoding 16 palettes
on that basis produced colour noise, and the neighbouring mode fields decoded as implausible values
(10496, 35840). Two independent checks failing the same way means the base is wrong, not the idea.

`port/tools/am04_info.py` reads the header and sections and will happily dump palette strips; the
tool is correct about the header and, on current evidence, unreliable past it. Its output should
not be trusted until the record base is settled.

**The reliable way to settle it** is memory, not files: the engine relocates and interprets these
blobs before drawing, so dumping the live image struct shows what the fields actually point at,
with no guessing. The wrapper can read that memory — it already reads engine globals to find the
surface.

## AM04: solved for palette banks

The live-versus-file comparison settled it. Dumping the loaded structure for key `0x1001f41` and
diffing against the file gives **39 differing bytes out of 8,192**, and every one of them is
accounted for:

| offset | file | live | why |
|---|---|---|---|
| +0x00 | `AM04` | `0x01001f41` | loader overwrites the magic with the key |
| +0x04 | `0` | `0xFFFFFFFF` | loader tags images whose names begin I/S/E |
| +0x08, +0x10, +0x20, +0x28, +0x30, +0x38, +0x40 | file offsets | `base + offset` | exactly the relocation seen in `FUN_0040f000` |
| +0x18 | `0x548` | another allocation | engine swaps in its own converted copy |

Everything else in those 8 KB is byte-identical, which proves the file is the image.

### The record size

`(next section - frame array) / frame count` = `(0x2148 - 0x548) / 7` = **0x400 exactly**, and the
same division lands on 0x400 for the 16-frame file checked earlier. Not 0x230.

0x400 = 1,024 bytes = **256 entries of 4 bytes**. Decoded as 32-bit colour, consecutive entries are
smooth (0.80–0.99 by frame, 256 distinct values), and entry 0 is pure green. These are palettes.

**So key `0x1001f41` is a palette bank, not a picture.** Seven palettes, probably lighting or
time-of-day variants for one scene. That explains the earlier failure exactly: I was hunting for
pixel records inside a file that contains no pixels.

The engine converts each palette to 16-bit RGB565 at load — visible in the live dump, which holds
16-bit pairs where the file holds 32-bit entries.

### What is still unknown

Where the *index* data lives. Sections at +0x08, +0x10, +0x20, +0x28, +0x30, +0x38 are still
unnamed, and the pixel pool the blitter reads (`param_1[4]`) was zero in the files checked so far,
which means the pixels for this key come from somewhere else entirely — most likely a different
blob that shares the palette.

`port/tools/am04_info.py` now reads this layout correctly: 0x400 records, 32-bit palettes, and it
prints frame count, sections and a palette strip.
