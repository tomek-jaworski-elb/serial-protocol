@echo off
REM Batch Script to Check Java Version and Run the serial-protocol application

SET JAR_FILE_VERSION=1.8
SET JAVA_TOOL_OPTIONS=-Xms256m -Xmx512m
SET APP_FILE=serial-protocol

echo =====================================
echo Checking for Java installation ...
echo =====================================
java -version

IF %ERRORLEVEL% NEQ 0 (
    echo Error: Java is not installed or not configured correctly.
    echo Please install Java or check your environment variables.
    pause
    exit /b 1
)

echo =====================================
echo Running %APP_FILE%-%JAR_FILE_VERSION% ...
echo =====================================
java -jar target/%APP_FILE%-%JAR_FILE_VERSION%.jar %JAVA_TOOL_OPTIONS%

IF %ERRORLEVEL% NEQ 0 (
    echo Error: Failed to start %APP_FILE%.
    echo Please check the jar file and try again.
    pause
    exit /b 1
)

echo =====================================
echo %APP_FILE% is running successfully.
echo =====================================
pause
