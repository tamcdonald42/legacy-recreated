@echo off
set "JAVA_HOME=H:\jdk-25\jdk-25.0.5+8"
set "PATH=%JAVA_HOME%\bin;%PATH%"
call "H:\ghidra\ghidra_12.2_PUBLIC\support\analyzeHeadless.bat" ^
  H:\ghidra\work\ghidra-proj Septerra ^
  -process "septerra.exe" ^
  -noanalysis ^
  -scriptPath H:\ghidra\work\ghidra-scripts ^
  -postScript Decompile.java 0x428ec0,0x4299c0,0x428390,0x428370,0x42a530
echo CALLTREE_EXIT=%ERRORLEVEL%
