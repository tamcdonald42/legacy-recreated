@echo off
REM Plays with the art pack and 2x replacement art active.
REM   pack\blobs\<KEY>.bin              whole-file replacements
REM   pack\scale\scale.txt + *.scl     scaled replacement art (KEY ENTRY FILE)
set LEGACY_PACK_DIR=%~dp0pack
set LEGACY_SCALE_DIR=%~dp0pack\scale
cd /d "%~dp0septerra_core"
start "" SR-Septerra.exe
