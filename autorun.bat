@echo off
echo Starting changeme...

:: Try JAVA_HOME first
set "JAVA_PATH=%JAVA_HOME%\bin\java.exe"

:: If JAVA_HOME doesn't work, try hardcoded path
if not exist "%JAVA_PATH%" (
    set "JAVA_PATH=C:\Program Files\Java\jdk-25\bin\java.exe"
)

:: Final check
if not exist "%JAVA_PATH%" (
    echo Java not found. Please install Java or set JAVA_HOME.
    pause
    exit /b 1
)

:: Launch the server with the proper path
"%JAVA_PATH%" --enable-native-access=ALL-UNNAMED -jar changeme.jar
pause
