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
