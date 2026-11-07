@echo off
REM Plays the game with the blitter map switched on.
REM Every sprite drawn is logged as: key, entry, frame, destination rect.
REM Close the game when done, then the log is at blit_keys.log next to this file.
set LEGACY_BLIT_HOOK=1
cd /d "%~dp0septerra_core"
SR-Septerra.exe 2> "%~dp0blit_keys.log"
echo.
echo Log written to blit_keys.log
pause
