@echo off
rem ---------------------------------------------------------------------------
rem Step 2 of 3: regenerate mpg123's import library with an explicit DLL name.
rem Upstream's libmpg123-0.dll.def has NO "LIBRARY" line, so 'lib /def:' names the
rem module after the FILE (libmpg123-0.dll) and adds .dll again, producing an
rem import for "libmpg123-0.dll.dll". The exe then links fine but dies at load
rem with 0xC0000135 (DLL not found). Fix: prepend the LIBRARY line.
rem ---------------------------------------------------------------------------
set "HERE=%~dp0"
set "OUT=%HERE%out\lib"
set "SRC=H:\ghidra\libs\mpg123\mpg123-1.24.0-x86\libmpg123-0.dll.def"
call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvarsall.bat" x86
if not exist "%OUT%" mkdir "%OUT%"
cd /d "%OUT%"

> mpg123-0.def echo LIBRARY libmpg123-0.dll
>>mpg123-0.def type "%SRC%"

del /q mpg123-0.lib 2>nul
lib /nologo /def:mpg123-0.def /out:mpg123-0.lib /machine:x86
if errorlevel 1 goto :fail
echo === mpg123 import lib ok: %OUT%\mpg123-0.lib ===
goto :eof
:fail
echo MPG123_LIB_FAILED
exit /b 1
