@echo off
call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvarsall.bat" x86
set "PATH_INC=H:\ghidra\libs\SDL2\SDL2-2.30.9\include;H:\ghidra\libs\mpg123\mpg123-1.24.0-x86;H:\ghidra\libs\libquicktime-src\include;H:\ghidra\libs\lqt_stubs"
set "PATH_LIB=H:\ghidra\libs\SDL2\SDL2-2.30.9\lib\x86;H:\ghidra\libs\mpg123\mpg123-1.24.0-x86"
cd /d "H:\ghidra\reference\SR\games\Septerra Core\SR-Septerra"
scons
echo SCONS_EXIT=%ERRORLEVEL%
