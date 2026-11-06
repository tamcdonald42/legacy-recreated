@echo off
REM Runs Septerra Core with the art pack in the pack\blobs folder.
set LEGACY_PACK_DIR=%~dp0pack
cd /d "%~dp0septerra_core"
start "" SR-Septerra.exe
