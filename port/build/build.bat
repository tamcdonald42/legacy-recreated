@echo off
rem ===========================================================================
rem Septerra Core -> SR-Septerra.exe, full pipeline (Windows x86, MSVC + NASM).
rem
rem   step 1  port\build\build_lqt_stub.bat    -> out\lqt\quicktime.lib (STUB)
rem   step 2  port\build\build_mpg123_lib.bat  -> out\lib\mpg123-0.lib
rem   step 3  this script                      -> SR-Septerra.exe
rem
rem Requires (see NOTES.md): Ghidra tree at H:\ghidra, JDK not needed here,
rem NASM 2.16.03 under libs\nasm, MSVC BuildTools 2022, Python for the helper
rem scripts, and septerra_core\septerra.exe = English 1.04 (GOG build accepted).
rem ===========================================================================
setlocal
set "HERE=%~dp0"
set "ROOT=H:\ghidra"
set "SR=%ROOT%\reference\SR"
set "GAME=%SR%\games\Septerra Core\SR-Septerra"
set "XD=%GAME%\x86"
set "RUN=%ROOT%\work\srw-run2"
set "NASMDIR=%ROOT%\libs\nasm\nasm-2.16.03"
set "NASM=%NASMDIR%\nasm.exe"
rem python: override with  set "PY=C:\path\to\python.exe"
if not defined PY set "PY=%USERPROFILE%\miniconda3\python.exe"
if not exist "%PY%" set "PY=python"
set "PATH=%NASMDIR%;%PATH%"

rem ---- 1. static recompile: 1.04 exe + config -> Septerra.asm + seg*.inc ------
if not exist "%RUN%" mkdir "%RUN%"
copy /y "%SR%\SRW\SRW.exe"                             "%RUN%\" >nul
copy /y "%ROOT%\septerra_core\septerra.exe"            "%RUN%\Septerra104.exe" >nul
copy /y "%SR%\SRW-games\Septerra Core\SRW\SR.cfg"        "%RUN%\" >nul
copy /y "%SR%\SRW-games\Septerra Core\SRW\bssborder.csv" "%RUN%\" >nul
copy /y "%SR%\SRW-games\Septerra Core\SRW\relocations.csv" "%RUN%\" >nul
rem The .sci files are MANDATORY: without them SRW emits ~3,700 short-jump
rem errors and drops all the extern declarations (undefined GetTickCount etc).
copy /y "%SR%\SRW-games\Septerra Core\SRW\x86\*.sci"     "%RUN%\" >nul

cd /d "%RUN%"
"%RUN%\SRW.exe" Septerra104.exe Septerra.asm
if errorlevel 1 goto :srwfail

rem ---- 2. generated source into the build dir ------------------------------
rem one copy per file: cmd's copy concatenates multiple sources into one target
copy /y "%RUN%\Septerra.asm"        "%XD%\" >nul
copy /y "%RUN%\seg01.inc"           "%XD%\" >nul
copy /y "%RUN%\seg02.inc"           "%XD%\" >nul
copy /y "%RUN%\seg03.inc"           "%XD%\" >nul
copy /y "%RUN%\seg05.inc"           "%XD%\" >nul
copy /y "%RUN%\Septerra.resdump"    "%XD%\" >nul
cd /d "%XD%"

"%PY%" compact_source.py
if errorlevel 1 goto :fail

rem ---- 3. nasm probe pass, repair out-of-range short jumps, real build -------
rem nasm's stderr is what repair_short_jumps.py parses (build-x86.sh does the
rem same with a.a); the probe object is thrown away, scons assembles for real.
"%NASM%" -fwin32 -O1 -w+orphan-labels -w-number-overflow -ix86/ Septerra.asm -o "%TEMP%\septerra_probe.obj" 2>a.a
"%PY%" repair_short_jumps.py
if errorlevel 1 goto :fail

call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvarsall.bat" x86
set "PATH_INC=%ROOT%\libs\SDL2\SDL2-2.30.9\include;%ROOT%\libs\mpg123\mpg123-1.24.0-x86;%ROOT%\libs\libquicktime-src\include;%ROOT%\libs\lqt_stubs"
set "PATH_LIB=%HERE%out\lqt;%HERE%out\lib;%ROOT%\libs\SDL2\SDL2-2.30.9\lib\x86"

cd /d "%GAME%"
del /q SR-Septerra.exe 2>nul
scons
if errorlevel 1 goto :fail
echo === built: %GAME%\SR-Septerra.exe ===
exit /b 0

:srwfail
echo SRW_RECOMPILE_FAILED
exit /b 1
:fail
echo BUILD_FAILED
exit /b 1
