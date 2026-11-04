@echo off
rem Apply SRW ground truth (.sci files) to the existing analysis, then dump a labeled inventory.
set "JAVA_HOME=H:\jdk-25\jdk-25.0.5+8"
set "PATH=%JAVA_HOME%\bin;%PATH%"
call "H:\ghidra\ghidra_12.2_PUBLIC\support\analyzeHeadless.bat" ^
  H:\ghidra\work\ghidra-proj Septerra ^
  -process "septerra.exe" ^
  -noanalysis ^
  -scriptPath H:\ghidra\work\ghidra-scripts ^
  -postScript GroundTruth.java
echo GT_EXIT=%ERRORLEVEL%
