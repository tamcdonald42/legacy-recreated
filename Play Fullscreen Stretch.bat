@echo off
REM Fullscreen, image stretched to fill a widescreen monitor. No black bars,
REM at the cost of horizontal distortion - the game was drawn for 4:3.
set LEGACY_MODE=desktop
set LEGACY_WINDOW=1280x960
set LEGACY_UPSCALE=2x
set LEGACY_FIT=stretch
set LEGACY_SCALE_DIR=%~dp0pack\scale
cd /d "%~dp0septerra_core"
SR-Septerra.exe
