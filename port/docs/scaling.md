# Scaling: what was tried and what it proved

The goal of this thread was to make the game look better on a 1920x1080 desktop without authoring
art. Three approaches were tested.

## 1. Window magnification (works, and is all we have today)

The engine renders 640x480 into a window sized by `Septerra.cfg`:

```
Display_Mode = window
Display_Width = 1280
Display_Height = 960
```

2x is the largest exact integer multiple that fits a 1080p desktop; 3x (1920x1440) does not fit
vertically. The picture is the same 640x480 frame, magnified - no detail is added.

## 2. EPX2 upscaling (built, and broken for this engine)

`LEGACY_UPSCALE=epx` was implemented in the shim and wired into the present path. It does not work,
and the reason is structural:

```
legacy-upscale: EPX 2x enabled
legacy-upscale: copy#1 surface=1280x960 bpp=2 | texture=1280x960 factor=2
```

The shim sets `SDL_RenderSetLogicalSize(640*2, 480*2)`. The engine asks the renderer for its output
size and then **creates its backbuffer at that size** - 1280x960 instead of 640x480. Two
consequences:

1. the engine draws its 640x480 picture into a double-size canvas, so the image occupies a quarter
   of the window ("tiny");
2. the EPX guard (`surface == 640x480`) can never fire, because the surface is already 1280x960.

So the filter was never applied. Any real implementation has to keep the backbuffer at 640x480
while presenting at 1280x960, which means decoupling the two sizes in the shim.

## 3. The game's own `Scaling` config key (inconclusive)

`Game-Config.c` recognises `Scaling = basic` (bilinear) and `Scaling = basicnb` (nearest). Two runs
with each variant produced **pixel-identical** frames (0 differing pixels), so either the key is
reset before use or it has no effect at an exact 2x scale. Not resolved; lower priority than it
looks, because it is a filter, not resolution.

## What is left

Filtering can only smooth what is already there. Detail requires drawing art **larger than its
original pixel grid**, which means the blitter has to scale an override image instead of copying
1 byte to 1 word. That is a change in the renderer, not in a launcher.

### Status: scaled replacement art works

Verified on screen: the inventory disc slot (01001F4B entries 6 and 7) is drawn from a 2x
replacement at 146x74 in a 73x37 cell, in both the idle and hovered states.

Two findings that cost several failed rounds and are worth not repeating:

1. **A sprite slot can be several entries, used as alternatives.** 01001F4B entry 7 draws while the
   slot is idle, entry 6 while it is hovered. Replacing one leaves the other on screen, so the change
   looks like it did nothing. Always check the draw counts per entry in a trace before concluding.

2. **Colour-coded markers beat art for identification.** Replacing a 37-pixel icon with an upscaled
   copy of itself is nearly invisible in a screenshot; a solid block of a known colour is not.
   Marking several candidate entries in different colours in one run identifies all of them at once.

Draw order: overlay at blit time is not enough - later draws in the same frame paint over it. The hook
queues the request and paints during CopyBackbufferSurfaceToTexture, so the replacement is the last
thing written each frame.

Limit: the engine positions every element by its original size, so a doubled element spills out of
its cell. That is a layout constraint, not a hook bug.
