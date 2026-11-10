@echo off
REM Presents the 640x480 frame at 1920x1440 (3x). Layout untouched.
set LEGACY_WINDOW=1920x1440
set LEGACY_PACK_DIR=%~dp0pack
cd /d "%~dp0septerra_core"
SR-Septerra.exe 2> "%~dp0window_3x.log"
