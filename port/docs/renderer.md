

## The section map, and where the pixels are

Reading the eight section offsets on three different files gives a consistent shape:

| section | 01001f41 | 01001f4b | 01000fa2 | contents |
|---|---|---|---|---|
| A (+0x08) | 760 | 2,432 | 1,900 | small table |
| B (+0x10) | 520 | 1,352 | 17,420 | table |
| records (+0x18) | 7,168 | 12,288 | 16,384 | palettes (÷0x400 = 7, 12, 16 ✓) |
| D (+0x20) | 480 | 1,248 | 4,944 | small table |
| **E (+0x28)** | **110,065** | **636,131** | **766,636** | **8-bit pixel indices** |
| F (+0x30) | 10,184 | 32,624 | 289,096 | row/run descriptors |
| G (+0x38) | 6,672 | 24,712 | 107,568 | run jump table |
| H (+0x40) | 0 | 0 | 0 | unused in these files |

Section E is the pixel data: byte values span 0–255 with entropy 2.8–5.7 bits/byte, exactly what
8-bit indexed artwork looks like.

### The converted record, at runtime

The engine allocates its own array for the palettes and rewrites `+0x18` to point at it. That array
walks at **0x230 per frame** — the same stride the blitter uses — and each record begins with a
pointer back to the source palette in the file, followed by the RGB565 conversion:

```
record+0x000  pointer to the file's 32-bit palette
record+0x004  512-byte RGB565 palette  (256 entries)  <- what the blitter reads
record+0x204  ... header fields (mode, origin, duration, motion) ...
```

The whole chain now closes: file palettes are 32-bit, the loader converts to 16-bit, the blitter
reads them at +4, and every stride in the engine is 0x230.

### First attempt at rendering, and why it sheared

Feeding section E to a flat 640-wide bitmap produces **sheared but recognisable content** — a
regular grid, digit strings, star-shaped icons, silhouettes. Not noise, and not a wrong section, but
wrong row addressing: rows are described by runs of `(skip, count)` in the descriptor tables, not
laid out flat. The blitter walks exactly those runs (`piVar11`, advancing by `(skip,count)` pairs,
copying `count` indices per run).

So the last mile is implementing that run walk offline — no new discovery required, just transcribing
the loop we already have in decompiled form.
