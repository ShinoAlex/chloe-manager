@echo off
set "JAVA_HOME=C:\Program Files\Java\jdk-25"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "MVN=D:\Tool\apache-maven-3.10.2\bin\mvn.cmd"
set "PROJECT=d:\GAMEMODS\MC\server\chloe-manager"
set "OUTFILE=%PROJECT%\build-output.txt"

echo Building... > "%OUTFILE%"
cd /d "%PROJECT%"
call "%MVN%" clean package -B >> "%OUTFILE%" 2>&1
echo BUILD_EXIT_CODE=%ERRORLEVEL% >> "%OUTFILE%"
exit /b %ERRORLEVEL%
