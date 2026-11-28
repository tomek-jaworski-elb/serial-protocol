@echo off
REM Batch Script to Check Java Version and Run the serial-protocol application

SET JAVA_TOOL_OPTIONS=-Xms256m -Xmx512m
SET APP_FILE=serial-protocol
REM The version comes from the jar name, so a new <version> in pom.xml needs no change here.
SET JAR_FILE=
FOR %%F IN (target\%APP_FILE%-*.jar) DO SET JAR_FILE=%%F

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
IF NOT DEFINED JAR_FILE (
    echo Error: no target\%APP_FILE%-*.jar found. Build it first with build-jar.bat.
    pause
    exit /b 1
)
echo Running %JAR_FILE% ...
echo =====================================
java -jar %JAR_FILE% %JAVA_TOOL_OPTIONS%

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
