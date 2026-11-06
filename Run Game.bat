@echo off
setlocal
cd /d "%~dp0septerra_core"
if not exist "SR-Septerra.exe" (
  echo Copying the modern build...
  copy /Y "..\reference\SR\games\Septerra Core\SR-Septerra\SR-Septerra.exe" "SR-Septerra.exe" >nul
)
echo ==========================================================
echo  Septerra Core - modern build
echo  window size is set in: %CD%\Septerra.cfg
echo  change Display_Width / Display_Height there to resize
echo ==========================================================
echo.
"SR-Septerra.exe"
echo.
echo Game exited with code %ERRORLEVEL%.
pause
