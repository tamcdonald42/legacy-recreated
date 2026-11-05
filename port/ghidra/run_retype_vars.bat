@echo off
set "JAVA_HOME=H:\jdk-25\jdk-25.0.5+8"
set "PATH=%JAVA_HOME%\bin;%PATH%"
call "H:\ghidra\ghidra_12.2_PUBLIC\support\analyzeHeadless.bat" ^
  H:\ghidra\work\ghidra-proj Septerra ^
  -process "septerra.exe" ^
  -noanalysis ^
  -scriptPath H:\ghidra\work\ghidra-scripts ^
  -postScript RetypeVars.java 0x428ec0 in_EAX:ObjSlot* iVar1:Obj*
echo CALLTREE_EXIT=%ERRORLEVEL%
