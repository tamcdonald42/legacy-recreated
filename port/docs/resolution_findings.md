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
