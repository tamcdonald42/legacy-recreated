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
