@echo off
set "JAVA_HOME=H:\jdk-25\jdk-25.0.5+8"
set "PATH=%JAVA_HOME%\bin;%PATH%"
call "H:\ghidra\ghidra_12.2_PUBLIC\support\analyzeHeadless.bat" ^
  H:\ghidra\work\ghidra-proj Septerra ^
  -process "septerra.exe" ^
  -noanalysis ^
  -scriptPath H:\ghidra\work\ghidra-scripts ^
  -postScript Xrefs.java 0x4ae960,0x4af8cc,0x4af8d0,0x4af8c4,0x4af8c8
echo CALLTREE_EXIT=%ERRORLEVEL%
