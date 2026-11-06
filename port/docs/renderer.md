

## Instrumentation available

Runtime switches now in the wrapper (all read from the environment, all inert unless set):

| variable | what it does |
|---|---|
| `LEGACY_PACK_DIR` / `_TRACE` / `_DUMP` | blob override and extraction |
| `LEGACY_BLIT_LOG` | log every blit with source/destination rects |
| `LEGACY_SHOT_DIR` / `_SHOT_EVERY` | screenshot every Nth presented frame |
| `LEGACY_INPUT` | scripted clicks and keys, `t,action,x,y;...` |
| `LEGACY_DUMP_SURFACES` | write every blitted surface to BMP |
| `LEGACY_ART` | replace a blitted surface with a BMP |
| `LEGACY_FIND_GLOBAL` | locate the engine's screen-surface pointer |
| `LEGACY_FIND_IMAGE` | enumerate image structs in `.data` by shape |
| `LEGACY_IMAGE_DIR` | dump a loaded image struct and its sections |
| `LEGACY_CACHE_DUMP` | dump the engine's image cache table |
| `LEGACY_CALLERS` / `LEGACY_BT` | report call sites reaching the wrapper |

## Where the offline render stands

Not done. The run-walk itself is transcribed from the blitter, but one input is missing: the
blitter reads pixel indices from `param_1[4]`, which comes from image field `+0x40`, and **that
field is zero for every image loaded in the headless runs** — all twelve of them are the
`0x01001f4x` palette-bank family.

So either the pixel pool is built later in scene loading, or the runs never reached real gameplay.
Both are testable and neither is a mystery: drive the game to a scene, dump the struct live at that
moment, and look for a non-zero `+0x40`.
