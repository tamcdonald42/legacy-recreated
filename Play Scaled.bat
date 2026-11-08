@echo off
REM Plays with the art pack and the 2x replacement art active.
REM   pack\blobs\<KEY>.bin          whole-file replacements
REM   pack\scale\scale.txt          "KEY ENTRY FILE.scl" per line
set LEGACY_PACK_DIR=%~dp0pack
set LEGACY_SCALE_DIR=%~dp0pack\scale
set LEGACY_BLIT_HOOK=1
cd /d "%~dp0septerra_core"
SR-Septerra.exe 2> "%~dp0scaled.log"
echo.
echo Log: scaled.log
pause
