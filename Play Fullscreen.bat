@echo off
REM Fullscreen, 4:3 preserved. Black bars on the sides of a widescreen monitor.
REM EPX upscaling keeps the pixels crisp; LEGACY_FIT=stretch fills the screen instead.
set LEGACY_MODE=desktop
set LEGACY_WINDOW=1280x960
set LEGACY_UPSCALE=2x
set LEGACY_FIT=box
set LEGACY_SCALE_DIR=%~dp0pack\scale
cd /d "%~dp0septerra_core"
SR-Septerra.exe
