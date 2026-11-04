@echo off
rem Headless Ghidra analysis of the original 1.04 executable.
rem Project: H:\ghidra\work\ghidra-proj\Septerra  (work\ is gitignored)
rem Output : H:\ghidra\work\ghidra-out\function_inventory.csv
set "JAVA_HOME=H:\jdk-25\jdk-25.0.5+8"
set "PATH=%JAVA_HOME%\bin;%PATH%"
if not exist "H:\ghidra\work\ghidra-out" mkdir "H:\ghidra\work\ghidra-out"

call "H:\ghidra\ghidra_12.2_PUBLIC\support\analyzeHeadless.bat" ^
  H:\ghidra\work\ghidra-proj Septerra ^
  -import "H:\ghidra\septerra_core\septerra.exe" ^
  -scriptPath H:\ghidra\work\ghidra-scripts ^
  -postScript Inventory.java ^
  -analysisTimeoutPerFile 7200
echo ANALYZE_EXIT=%ERRORLEVEL%
