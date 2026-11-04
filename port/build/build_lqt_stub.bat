@echo off
rem ---------------------------------------------------------------------------
rem Step 1 of 3: build the link-time libquicktime stub (quicktime.dll + .lib).
rem SR-Septerra's QTML.c calls libquicktime (lqt_*/quicktime_*). Real libquicktime
rem for Windows needs MSYS2 + autotools + ffmpeg, so this stub exists only to let
rem the exe LINK. Every entry point returns 0 at runtime: movie playback does
rem nothing. Test builds only.
rem ---------------------------------------------------------------------------
set "HERE=%~dp0"
set "OUT=%HERE%out\lqt"
call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvarsall.bat" x86
if not exist "%OUT%" mkdir "%OUT%"
cd /d "%HERE%"

del /q "%OUT%\quicktime.dll" "%OUT%\quicktime.lib" "%OUT%\quicktime.exp" "%OUT%\lqt_stub.obj" 2>nul
cl /nologo /O2 /c lqt_stub.c /Fo:"%OUT%\lqt_stub.obj"
if errorlevel 1 goto :fail
link /nologo /DLL /MACHINE:X86 /OUT:"%OUT%\quicktime.dll" /DEF:quicktime.def "%OUT%\lqt_stub.obj"
if errorlevel 1 goto :fail
echo === stub ok: %OUT%\quicktime.dll ===
dir /b "%OUT%\quicktime.dll" "%OUT%\quicktime.lib"
goto :eof
:fail
echo STUB_BUILD_FAILED
exit /b 1
