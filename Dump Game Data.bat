@echo off
setlocal
cd /d "%~dp0septerra_core"
if not exist "..\reference\SR\games\Septerra Core\SR-Septerra\SR-Septerra.exe" (
  echo Build not found. Build it first.
  pause
  exit /b 1
)
copy /Y "..\reference\SR\games\Septerra Core\SR-Septerra\SR-Septerra.exe" "SR-Septerra.exe" >nul
set LEGACY_PACK_DUMP=%~dp0dumped_data
set LEGACY_PACK_TRACE=1
if not exist "%LEGACY_PACK_DUMP%" mkdir "%LEGACY_PACK_DUMP%"
echo ==========================================================
echo  Extracting game records to:
echo    %LEGACY_PACK_DUMP%
echo.
echo  Let the game get to the main menu (or a bit further),
echo  then close the window. A log is written next to this file.
echo ==========================================================
echo.
"SR-Septerra.exe" 2> "%~dp0dump_log.txt"
echo.
echo Finished. %ERRORLEVEL%
dir /b "%LEGACY_PACK_DUMP%" | find /c /v "" > "%~dp0dump_count.txt"
echo Files extracted:
type "%~dp0dump_count.txt"
pause
