# Authoring a sprite pack

Everything below is confirmed against real files and verified by rendering outside the game.
The goal of this document is that someone can replace one sprite and see it in game.

## What a sprite is

A sprite frame is 8-bit indexed pixels plus a palette. There is no compression and no scaling
anywhere in the engine — the blitter copies one byte to one 16-bit word, clipped only.

## File layout (AM04)

```
+0x00  'AM04'
+0x04  uint32  type            0xFFFFFFFF for images whose record name starts I, S or E
+0x08  uint32  -> A            small table
+0x10  uint32  -> B            table
+0x18  uint32  -> palettes     0x400 bytes each: 256 x 0x00BBGGRR
+0x1C  uint32  palette count
+0x20  uint32  -> D            0x18-byte frame entries
+0x28  uint32  -> E            pixel indices, one byte per pixel
+0x30  uint32  -> F            run descriptors, 8 bytes each
+0x38  uint32  -> G            row table, 8 bytes per row
+0x40  uint32  unused
```

At load the engine adds the file base to each of those eight offsets, then replaces `+0x18` with its
own array of 0x230-byte records (a pointer to the source palette followed by a 512-byte RGB565
conversion at +4). Rendering from the file only needs the 32-bit palettes.

## Structures

```
D entry (0x18)   +0x00 packed (height << 16 | width)
                 +0x10 row index into G
                 +0x14 width

G row (8)        +0 offset of this row's first run
                 +4  number of runs

F run (8)        +0 offset into E
                 +4 skip   (advance x, draw nothing)
                 +6 count  (copy this many indices)
```

Rows are drawn left to right: `x += skip`, then `count` pixels are copied straight from E.

## Reading a file

```
python3 port/tools/am04_sheet.py <file.bin> sheet.png      # every entry, 13 per row
python3 port/tools/render_am04.py <file.bin> --list        # geometry of every entry
python3 port/tools/render_am04.py <file.bin> --entry 7 --out one.png
```

Consecutive D entries are frames of the same object, so an animation shows up as a run of adjacent
cells. Entries that render blank are blank in the file — they are not a decoder bug.

## Replacing a sprite

The smallest honest replacement changes pixels without changing geometry:

1. Render the frame you want to replace and note its width and height.
2. Author a new image at the same size, indexed to 256 colours or fewer.
3. Build a new palette record (0x400 bytes, one `0x00BBGGRR` dword per entry) and new run
   descriptors that cover your image. Row runs are `(skip, count)`; a wide image is one run of
   `count = width` with `skip = 0`.
4. Append your index bytes to E, point your descriptors at them, and repoint the D entry's
   `row index` at your new rows.
5. Write the file back with all eight section offsets updated.

Keeping the dimensions identical means the game's clipping and the object's collision graphics stay
valid; changing them is a separate problem.

## What is still missing before this ships

- A writer, not just a reader: nothing yet emits a valid AM04 file with recomputed section offsets.
- Key-to-artwork naming: we know keys (`0x01001f4b` and friends) but not a human-readable index of
  which file holds which scene.
- Verification in game: the pack layer replaces whole blobs, so an edited file can be dropped in
  today — but it has not been tried end to end.

## Honest limits

- Only files loaded through `FUN_0040f000` follow this layout. Audio (`VSSF`) and character
  (`CH14`) records are documented elsewhere and are untouched by this work.
- The engine converts palettes to RGB565 on load; a 24-bit-authored palette will lose precision at
  conversion, exactly as the original art did.

## The writer exists: first pack built

`port/tools/am04_replace.py` rewrites one entry's index bytes in place, leaving the file
structurally identical — same sections, same offsets, same size, same run descriptors. That matters
because the engine loads it exactly as it loaded the original, with nothing to re-validate.

```
python3 am04_replace.py FILE --entry 6 --pattern stripes --out NEW.bin
```

With `--pattern stripes` only non-background pixels of the entry are recoloured, so the object keeps
its silhouette. `--pattern fill` replaces the whole cell, frame included.

### What was produced

Target: key `0x1001f4b`, entry 6 — the tilted disc icon, 37x37. 1,508 of 2,701 pixels rewritten in
the palette's four most saturated colours. Before/after in `pack_before_after.png`.

Verification: 2,699 bytes differ in a 710,859-byte file, all inside section E. The engine reports

```
legacy-pack: override key 0x1001f4b (710859 bytes) <- ...\pack\blobs\01001f4b.bin
```

so the modified file is genuinely the one being loaded.

### Not yet proven

The disc is an inventory icon, and inventory is not reachable from the opening cutscene, so this has
not been seen on screen. The file loads; the picture has not been watched.

## Visibility test: twelve files, none on that screen

Twelve AM04 files were repainted wholesale (every index of every entry rewritten to palette entries
254/255, which were set to magenta and lime) and loaded one at a time through the pack layer. Each
run confirmed the override fired:

```
01001f40 01001f41 01001f42 01001f43 01001f45 01001f46
01001f47 01001f48 01001f49 01001f4a 01001f4c 08001f57   -> overrides=1, magenta pixels=0
```

Screenshots were taken every 150 frames across the opening cutscene and scanned for magenta; the
baseline run confirmed zero false positives.

**None of these twelve is drawn on that screen.** That is a clean negative result across the whole
`0x01001f4x` family plus one `0x0800` file.

So the cutscene's visible UI comes from somewhere else. Candidates, in order of likelihood:

1. the `TX00` texture records (12 of them, ~600 bytes each) — texture descriptors rather than art;
2. `CH14` character records;
3. the 36x637 surface that gets blitted repeatedly, whose source has not been identified.

The last one is the most tractable: we can dump that surface and search every blob for the matching
pixel run, which identifies its key by content rather than by guessing.

## Correction: the first pack did work, and we misread the evidence

The very first in-game test - the 37x37 disc from `0x0101001f4b`, recoloured with diagonal stripes -
**was visible on screen**. The screenshot showed regular parallel cyan/blue diagonal stripes across
the disc, and that description was written down at the time as "bright light-blue/cyan diagonal
stripes reflecting across its surface, giving it a glossy, metallic appearance" - then dismissed as
the object's natural specular highlight.

It was the replacement pattern. The palette indices chosen for the stripes happened to be the file's
bluest entries, so the result looked like plausible sheen rather than an obvious debug colour.

Two process failures to avoid repeating:

1. **Do not use a colour that already occurs in the art.** A replacement pattern must be a colour
   the original cannot produce (magenta/lime on a blue object), or the change is ambiguous.
2. **A 7040x2160 capture makes a 37x37 icon ~30 pixels wide.** Vision passes over downscaled crops
   described the pattern but could not judge its scale. Crop to the icon and upscale with
   nearest-neighbour before deciding whether art changed.

Consequence: everything after this point that assumed "the pack does not reach the screen" -
the twelve-file sweep, the dark silhouettes investigation - was chasing a solved problem.
