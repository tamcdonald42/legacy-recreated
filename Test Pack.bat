@echo off
REM Diagnostic run: every candidate art file painted magenta.
REM Open the inventory, take a screenshot, then close the game.
set LEGACY_PACK_DIR=H:\ghidra\pack_test
set LEGACY_PACK_TRACE=1
cd /d "H:\ghidra\septerra_core"
SR-Septerra.exe 2> "H:\ghidra\pack_test_log.txt"
echo.
echo Trace written to H:\ghidra\pack_test_log.txt
pause
