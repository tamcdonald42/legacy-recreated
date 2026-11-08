@echo off
REM Diagnostic run for the scaled-art path. Logs what it matches and where it draws.
REM Play into the inventory, hover the disc, then close the game.
set LEGACY_PACK_DIR=%~dp0pack
set LEGACY_SCALE_DIR=%~dp0pack\scale
set LEGACY_BLIT_HOOK=1
cd /d "%~dp0septerra_core"
SR-Septerra.exe 2> "%~dp0scale_debug.log"
echo.
echo Log written to scale_debug.log
pause
