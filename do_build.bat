@echo off
set "JAVA_HOME=C:\Program Files\Java\jdk-17"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "MVN_DIR=%TEMP%\apache-maven-3.9.9"
set "OUTFILE=c:\Users\lolre\OneDrive\Desktop\Project\java-project\chloe-manager\build-output.txt"

echo Building... > "%OUTFILE%"
cd /d "c:\Users\lolre\OneDrive\Desktop\Project\java-project\chloe-manager"
call "%MVN_DIR%\bin\mvn.cmd" clean package -B >> "%OUTFILE%" 2>&1
echo BUILD_EXIT_CODE=%ERRORLEVEL% >> "%OUTFILE%"
