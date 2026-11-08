@echo off
REM Presents the 640x480 frame at 1280x960 (2x). Layout untouched.
set LEGACY_WINDOW=1280x960
set LEGACY_PACK_DIR=%~dp0pack
cd /d "%~dp0septerra_core"
SR-Septerra.exe
