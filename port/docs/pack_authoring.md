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
