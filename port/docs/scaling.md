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

### Whole-frame 2x (LEGACY_WINDOW)

`LEGACY_WINDOW=1280x960` makes the window 1280x960 while the engine keeps rendering 640x480.
SDL_RenderCopy stretches the finished frame, so every element scales together and the layout stays
intact. Nearest sampling by default (crisp pixels); `Scaling=linear` in the ini smooths it.

This is the safe "it runs bigger" mode. It adds no detail - it is the same 640x480 image, larger.
True added detail still needs either per-element replacement art with a layout-aware engine, or the
native port.

### EPX upscale (LEGACY_UPSCALE=2x) - the one that actually looks better

`Play Sharp.bat` runs `LEGACY_WINDOW=1280x960` with `LEGACY_UPSCALE=2x`.

What has to be true for this to work, and was not true before:

- the **backbuffer must stay at 640x480**, so the engine keeps drawing in its native coordinate
  space. The texture is still `W * factor` (1280x960), so `CopyBackbufferSurfaceToTexture` finds a
  matching pair and runs `LegacyUpscaleEPX2` over the finished frame.
- the backbuffer used to be created at the *texture* size, so with factor 2 the engine drew
  640x480 of content into a 1280x960 buffer, the EPX condition never matched, and the result was a
  small image in the top-left quarter. Fixed by dividing the drawable size by the factor when EPX
  is on.

Why EPX beats the plain stretch: with no upscale the engine renders its own 1280x960 frame by
scaling every sprite, which goes through the renderer's linear filter and smears edges - measured
on the main menu, the menu text has colour bleed along the letterforms. EPX renders the frame at
640x480 first (crisp source pixels), then rebuilds corners and diagonals at 2x. Same layout, same
timing, visibly cleaner text and sprite edges.

Cost: one extra full-frame pass over 307,200 pixels per present. Measured fine on the 4060 box; the
menu loop stayed uncapped.

Art packs work under EPX, with one rule: **author pack art at native size** (43x43 for the
inventory icons, 73x37 for the disc). EPX draws at 640x480 and doubles the finished frame, so art
authored at 2x gets downsampled into the native cell and then doubled again - a wasted round trip
that softens it. Native-sized replacement art comes out crisp and edge-rebuilt like the rest of the
frame. Verified: with `LEGACY_SCALE_DIR` and `LEGACY_UPSCALE=2x` both set, the loader reports the
same `scale: key ... entry ...` lines and the frame log reports `EPX 640x480 -> 1280x960`.

What this still cannot do is add detail. EPX sharpens the existing art; it does not invent pixels.
Replacement art changes style at native resolution. Resolution itself is the native port's job.

### Window fit and fullscreen (LEGACY_MODE / LEGACY_FIT)

`LEGACY_MODE=window|desktop|fullscreen` chooses the display mode without editing `Septerra.cfg`.
`desktop` is borderless fullscreen at the desktop resolution; `fullscreen` is exclusive mode.

Placement is done by hand in `LegacyPresentTexture()`, not by `SDL_RenderSetLogicalSize`. SDL anchors
its logical viewport to the left instead of centring it - measured at 1024x576 with a 1280x960
texture: content occupied columns 0-767 with a 256-pixel bar on the right only. The helper clears to
black and copies the frame into a computed destination rect instead.

| `LEGACY_FIT` | result on a 16:9 screen |
|---|---|
| `box` (default) | 4:3 preserved, bars either side - 1440x1080 content on 1080p |
| `stretch` | fills the screen, horizontal distortion |
| `crop` | fills the screen, loses the top and bottom |

Verified at 1024x576: `box` produced 128-pixel bars both sides with 768 pixels of content;
`stretch` produced 1024 pixels of content and no bars.

Launchers: `Play Fullscreen.bat` (box), `Play Fullscreen Stretch.bat` (stretch).

Widescreen that shows *more of the scene* is not a fit mode - the game's backgrounds are 640x480
paintings with nothing beyond the frame, so hor+ needs a wider canvas plus wider art. That is
milestone-one territory, and even then the art has to exist.

### Verification pitfalls (cost hours, worth remembering)

**Testing a window-size change over RDP is meaningless.** An RDP session reports its own desktop
size to Windows, so every window is clamped to it. A 640x480 window, a 1280x960 window and a
1920x1440 window all look identical on a phone-sized remote session. Test display changes at the
physical desktop, or check the resolution first: `Get-CimInstance Win32_VideoController`.

The same applies to a headless host: the virtual display reports 1024x768, so window sizes above
that are clamped there too and screenshots of the desktop come back black.

**Practical ceiling on a 1080p screen is 2x.** 1920x1440 is taller than 1080 and gets cut off at the
bottom edge, so 3x is only usable on a taller display. Septerra.cfg already pins the game to
1280x960, which is why LEGACY_WINDOW=1280x960 looks identical to a normal launch: normal *is* 2x.
