@echo off
REM Plays with the art pack, the 2x replacement art, and EPX upscaling.
REM   pack\blobs\<KEY>.bin          whole-file replacements
REM   pack\scale\scale.txt          "KEY ENTRY FILE.scl" per line
REM
REM EPX makes the engine draw at its native 640x480 and rebuilds edges at 2x,
REM so replacement art must be authored at NATIVE size (43x43, 73x37 ...), not
REM doubled. Art drawn oversized gets stepped on by the upscale.
set LEGACY_PACK_DIR=%~dp0pack
set LEGACY_SCALE_DIR=%~dp0pack\scale
set LEGACY_BLIT_HOOK=1
set LEGACY_WINDOW=1280x960
set LEGACY_UPSCALE=2x
cd /d "%~dp0septerra_core"
SR-Septerra.exe
