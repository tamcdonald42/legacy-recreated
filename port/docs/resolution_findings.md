# Can this game draw at a higher resolution?

Short answer: **not today.** The engine asks for 640×480 in its own code, and everything it paints
is composed at that size. The window can be bigger, but the picture handed to it is already finished.

## Evidence

**1. The game requests 640×480 explicitly.** In `WinMain_` (`0x412b80`, line ~238 of the decompiled
output):

```c
FUN_00446a20(DAT_004d55a8, 0x280, 0x1e0, (undefined *)0x0);
```

`0x280` = 640, `0x1e0` = 480. Those are literals in the game's own code, not values read from
`Septerra.cfg`. `FUN_00446a20` is the display-mode call chain (DirectDraw `SetDisplayMode`).

**2. The window size is a separate thing.** In the wrapper (`WinApi-ddraw.c`), the window is created
at `Display_Width`/`Display_Height` (line 878), while the renderer is given the *game's* requested
size as its logical size (`SDL_RenderSetLogicalSize(Renderer, dwWidth, dwHeight)`). Presentation
then does `SDL_RenderCopy(..., NULL, NULL)` — the finished 640×480 frame stretched to the window.

**3. Consequence.** With `Display_Width = 1280`, you get a clean 2× enlargement of a 640×480 picture.
Every original pixel becomes a perfect 2×2 block. It is sharp, and it contains exactly the same
information as before: no extra detail exists to show.

## What this means for art packs

| goal | possible today | why |
|---|---|---|
| replace a picture with one of the same size | **yes** | the engine never knows the difference |
| fix colours, palettes, ugly compression | **yes** | same reason |
| supply art at 1280×960 for a 640×480 game | no, not usefully | it is downsampled to the frame before you see it |
| genuinely more visible detail | no | requires the engine to compose at a larger size |

So "hi-res texture pack" in the usual sense is gated, not forbidden. The art half of the work is
still real and still worth doing — it just cannot exceed the frame the engine draws into.

## The one question that decides whether hi-res is cheap or expensive

Sprites have to get onto that 640×480 frame somehow, and there are two possibilities:

- **Through the wrapper's blit function** (`IDirectDrawSurface_Blt`, which the wrapper implements).
  If so, the wrapper sees every sprite draw with its source and destination rectangles — and could
  scale those rectangles into a larger frame without touching the game's code at all.
- **By writing pixels straight into a locked surface.** Then the wrapper sees a memory buffer and
  raw bytes, with no idea where one sprite ends and the next begins. Scaling would require the
  game's drawing code to be understood and replaced.

The wrapper implements both `Blt` and `Lock`, so both paths are live. Which one the engine actually
uses per sprite is not yet established.

**How to settle it (cheap):** log every blit for one minute of real gameplay, with source and
destination rectangles. Hundreds of small blits with sub-640 rectangles means the wrapper sees the
sprite work, and a wrapper-side 2× renderer is plausible. Almost no blits means direct pixel writes,
and hi-res needs the draw path ported first.

## What this changes about the port

It shrinks the gate. Hi-res does not need all ~800 unnamed functions. It needs the **draw and
composite path** — the code that decides where each sprite goes and paints it — which is a subset
in the range of 30–80 functions, plus the wrapper side. That is a months-scale project, not a
years-scale one, and it is the piece worth attacking deliberately rather than the whole engine in
alphabetical order.

## Result (measured, headless menu run)

Instrumented `IDirectDrawSurface_Blt` and `IDirectDrawSurface_Flip` with an env-var log
(`LEGACY_BLIT_LOG`) and ran 12 seconds with no display. 10,127 presents, and the sprite work
**goes through the wrapper**:

```
blit dst=640x480 dstrect=(0,0,36,28) src=36x637 srcrect=(0,0,36,28)
blit dst=640x480 dstrect=(10,10,46,38) src=36x637 srcrect=(0,0,36,28)
frame 2 blits=3 locks=4
```

- Destination surfaces are **640×480**, as predicted.
- Blits carry **explicit source and destination rectangles** — the wrapper sees each sprite's
  position and size individually.
- Locks per frame are low (1–4), so composition is not done by painting pixels directly; the
  engine asks the wrapper to move pictures around.

**Consequence:** a wrapper-side higher-resolution renderer is architecturally plausible —
the wrapper is handed each sprite draw with coordinates it can scale. It is not a small change
(the destination is the engine's own 640×480 buffer, and anything drawn by other means has to be
handled separately), but it does not require porting the game's drawing code first.

**Caveat:** this is the menu, headless, at an unthrottled frame rate. Ratios matter, absolute
numbers do not. In-game counts need a desktop run.

## Instrumentation

`LEGACY_BLIT_LOG=<path>` writes one line per present (`frame <n> blits=<n> locks=<n>`) plus up to
300 individual blit records. Carried as `port/patches/0003-legacy-blit-log.patch`.

## Correction: in-scene drawing does not go through `Blt`

The menu measurement above only describes the **menu**. Driving the game into an actual scene
(scripted click on New Game, then watching a dialogue in the Doskias chamber) gives a different
picture:

| state | blits/frame | locks/frame |
|---|---|---|
| main menu | up to 98 | 1–4 |
| in-engine dialogue scene | **0** | **7** |

Zero blits, seven locks, sustained over 6,000 frames. The scene — pre-rendered background,
character sprites, portrait, text box — is painted by **writing pixels directly into a locked
surface**. The wrapper is handed a memory buffer, not a list of sprite draws.

So the earlier conclusion ("a wrapper-side scaled renderer is architecturally plausible") is
**wrong for gameplay**. It holds only for menus and UI. Scaling individual sprites would require
the engine's own drawing code, which is the part that has to be understood and replaced.

## What that leaves as the realistic options

1. **Post-process upscaling of the finished 640×480 frame** — cheap, wrapper-only, no new art.
   It cannot add detail, but for pixel art a good upscaler visibly improves the result. This is
   what most "HD" pixel-art re-releases actually do.
2. **Port the engine's drawing path** — the only route to genuinely higher-resolution rendering
   with sharper art. Larger than the 30–80 functions guessed earlier, because the software
   renderer *is* the per-frame bulk of the game.

Option 1 is worth having regardless; option 2 is the real project.

## Harness (why none of this needs a human at the keyboard)

Two additions make the game drivable and observable in a windowless session:

- `LEGACY_SHOT_DIR` + `LEGACY_SHOT_EVERY` — save a BMP of the rendered frame every N presents
  (`LegacyScreenshot`, `WinApi-ddraw.c`).
- `LEGACY_INPUT="6000,click,100,440;9000,key,13"` — replay clicks and keys at millisecond offsets
  (`LegacyInputPump` + `Patch_PushMouseClick`, `Game-Patch.c`). Coordinates are in the game's
  640×480 logical space; SDL's logical-size handling converts real mouse input the same way.

Together these reached the Doskias dialogue scene from a cold start with no display attached.
That is the foundation for automated visual checks of art packs later.

## Post-process upscaler: built, measured, redundant

An EPX/Scale2x filter was implemented in the wrapper (`LegacyUpscaleEPX2`, `LEGACY_UPSCALE=2x`)
and instrumented. Then the diagnostic line answered a question we had been guessing at:

```
legacy-upscale: diag surface=1280x960 bpp=2 | texture=1280x960 fmt=SDL_PIXELFORMAT_RGB565 factor=2
```

**The backbuffer is already 1280×960**, i.e. window resolution, not 640×480. The wrapper hands
the engine a surface at display size, so whatever composite we receive is already at the target
resolution. Doubling it again produced doubled/black scanlines — a correct filter applied to the
wrong stage.

Consequence: a post-process upscaler is not the missing piece. The filter is kept but
dimension-guarded so it only runs when a genuine 2× relationship exists; today it is inert.

## The question this exposes, and why it matters

If the engine composes onto a 1280×960 surface, one of two things is true:

- **It scales its art up** (from 640-wide source art) — a magnifying glass, same detail, larger.
- **It draws art 1:1 on a larger canvas** — more of the world visible, and the "extended view"
  the owner worried about already exists at any window size above 640×480.

The two look similar in a screenshot but mean opposite things for art packs. The test is cheap:
capture the same scene at 640×480 and at 1280×960, then compare how many pixels a character
occupies. Twice as many pixels everywhere = scaling. Same sprite size, more visible surroundings
= extended view.

That single comparison decides what a hi-res pack can even deliver, so it comes before any more
filter work.

## Settled: window size is pure magnification, exactly 2.00×

Same static screen — the main menu — captured at 640×480 and at 1280×960, then the bright-pixel
bounding box measured on both (no eyeballing):

| capture | bbox | width | height | origin |
|---|---|---|---|---|
| 640×480 | (11,11)–(593,458) | 582 | 447 | 11,11 |
| 1280×960 | (22,22)–(1187,916) | 1165 | 894 | 22,22 |

Ratios: width **2.00**, height **2.00**, origin **2.00**. Every element sits at exactly twice the
position and twice the size. Nothing is revealed at the edges.

**Answer: there is no extended view.** Enlarging the window magnifies the same 640×480
composition; it never shows more of the world. The owner's concern about a wider field of view
constituting an unfair advantage does not apply — the capability does not exist.

## The consequence worth testing next

If the engine scales its drawing to fill the surface (which the 2.00× factor proves it does), then
it must be *sampling* art scaled rather than pixel-doubling it — and art supplied at twice the
resolution could survive at native detail. That would make high-resolution packs viable on the
current build, without porting anything.

Test: substitute one sprite record with a 2× version of itself, run at 1280×960, and compare.

That single experiment decides whether the overhaul needs the port at all.

## Pipeline settled (per-copy instrumentation)

The single-shot diagnostic was misleading me; a periodic log (every 400th copy) is unambiguous, at
a 1280×960 window:

```
copy#1    surface=1280x960 bpp=2 | texture=1280x960 factor=2
copy#401  surface=1280x960 bpp=2 | texture=1280x960 factor=2
```

The presented backbuffer **is** the window size; the texture matches it; every frame is composed at
1280×960. The `dst=640x480` lines in the earlier blit log were the engine's own offscreen canvases,
not the presentation target. Two different surfaces, which is what made the two measurements look
contradictory.

Combined with the exactly-2.00× menu geometry, the model is: **the engine composes at output
resolution and multiplies every drawing operation by a global factor** (window size ÷ 640×480).

Consequence for the hypothesis in the previous section: supplying art at twice the resolution would
most likely be drawn at twice the size again, not at higher detail — the scale factor comes from the
output size, not from the asset. That makes "hi-res packs work today" unlikely, and it must not be
claimed until a real replacement has been tried.

## What actually blocks all art work

Not resolution — **format**. The dumped records are a family of containers:

| magic | count | looks like |
|---|---|---|
| `VSSF` | 155 | audio |
| `AM04` | 40 | animation/sprite data, 47 KB–1.2 MB, header 0x48 with offset tables |
| `TX00` | 12 | texture descriptor tables, ~600 B |
| `CH14` | 12 | character records |
| `GV00`, `IL00` | 1 each | globals, level index |

Until the `AM04` sprite container is decoded — dimensions, palette, pixel layout — no pack can
replace a single picture, at any resolution. That is the gate, and it comes before every other art
task.

## First artwork extracted (surface dump)

Rather than reverse-engineer `AM04` blind, the wrapper now dumps the source surface of every
distinct blit: `LEGACY_DUMP_SURFACES=<dir>` writes `surf_<w>x<h>_<n>.bmp`, capped at 200.

First run (title screen + a click into the game) produced three:

```
640x480 bpp=2   617 KB   the complete title/splash artwork, with publisher logos
36x637  bpp=2    67 KB   a narrow UI strip
640x480 bpp=2   617 KB   a second full-screen layer
```

Findings:

- Surface pixel format is **RGB565, 16 bpp** — not palettized, contrary to the assumption carried
  from the 1999-era art discussion. Any replacement artwork must arrive in that format or be
  converted to it.
- Full-screen art is recoverable **as an image**, complete and uncut. This is the first time the
  project has produced artwork rather than numbers, and it is exactly the kind of asset a pack
  would override.
- In-scene sprites are *not* captured by this route, because they are painted by direct pixel
  writes rather than blitted (see the zero-blits measurement earlier).

So there are two distinct asset paths, and they need different treatment:

| asset kind | how it reaches the screen | pack approach |
|---|---|---|
| blitted layers (splash, UI strips, menu art) | `Blt` with a source surface | interceptable at blit time |
| in-scene sprites | painted pixel by pixel by engine code | needs `AM04`/engine understanding |

The `AM04` decode is still required for sprite work, but it is no longer the only route to
replacing artwork.

## Art override tested: the shortcut does not exist

`LEGACY_ART=<bmp>` was built to replace the contents of any blitted surface matching an image's
size, as a proof that packs could work at the wrapper level. A deliberately altered 640×480 image
(hue-rotated, flipped, magenta bars) was supplied. The log confirmed the swap:

```
legacy-art: loaded H:\ghidra\work\art_override.bmp (640x480)
legacy-art: replaced surface 640x480
```

The screen did not change. Counting blits over the title screen explains why:

| measurement | value |
|---|---|
| blits per frame at the title | 1 |
| blits of the 36×637 UI strip (12 s total) | 10,537 |
| blits of a 640×480 surface (12 s total) | **1** |
| locks per frame | 3 |

The single 640×480 blit happens once at startup; the strip is the only thing blitted repeatedly.
The score, the logo, the menu — all of it is painted by **direct pixel writes** through three locks
per frame.

**Conclusion: there is no wrapper-level art replacement for anything visible.** Only narrow UI
strips pass through `Blt`. Every substantial image reaches the screen through the engine's own
software painter, which means:

- the blob-level pack layer is still the correct insertion point;
- making it work for real art requires reading the code that paints, i.e. `AM04` plus the painter
  itself;
- the "two asset paths" table earlier was too optimistic — it is one important path and a few
  UI strips.
