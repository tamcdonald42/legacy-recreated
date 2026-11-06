@echo off
setlocal
cd /d "%~dp0septerra_core"
if not exist "..\reference\SR\games\Septerra Core\SR-Septerra\SR-Septerra.exe" (
  echo Build not found.
  pause
  exit /b 1
)
copy /Y "..\reference\SR\games\Septerra Core\SR-Septerra\SR-Septerra.exe" "SR-Septerra.exe" >nul
set LEGACY_BLIT_LOG=%~dp0blit_log.txt
echo ==========================================================
echo  Recording how the game draws.
echo.
echo  Play for about a minute - walk around, open a menu,
echo  get into a fight if you like. Then close the window.
echo.
echo  Nothing else to do. The log writes itself to:
echo    %LEGACY_BLIT_LOG%
echo ==========================================================
echo.
"SR-Septerra.exe"
echo.
echo Recorded. Tell Hermes it's done.
pause
