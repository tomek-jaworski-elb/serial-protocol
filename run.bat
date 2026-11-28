@echo off
REM Batch Script to Check Java Version and Run the serial-protocol application

SET JAVA_TOOL_OPTIONS=-Xms256m -Xmx512m
SET APP_FILE=serial-protocol
REM Minimum Java major version; keep in sync with java.version in pom.xml.
SET REQUIRED_JAVA=25
REM The version comes from the jar name, so a new <version> in pom.xml needs no change here.
REM Exactly one jar may match (as in the deploy scripts): with several, the FOR would pick by name, not by version.
SET JAR_FILE=
SET JAR_COUNT=0
FOR %%F IN (target\%APP_FILE%-*.jar) DO (
    SET JAR_FILE=%%F
    SET /A JAR_COUNT+=1
)

echo =====================================
echo Checking for Java %REQUIRED_JAVA% or newer ...
echo =====================================
REM Use the first java that is new enough: JAVA_HOME, then java from PATH.
REM Paths are echoed in quotes and outside parenthesised blocks, so "&" or ")" in them is safe.
SET "HOME_NOTE=not set"
IF NOT DEFINED JAVA_HOME GOTO try_path
SET "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
SET "HOME_NOTE=no bin\java.exe there"
IF NOT EXIST "%JAVA_EXE%" GOTO try_path
CALL :probe
SET "HOME_NOTE=%PROBE_NOTE%"
IF DEFINED PROBE_OK GOTO java_ok

:try_path
REM where lists PATH matches in the order cmd searches them, so its first line is the java that would run.
SET PATH_JAVA=
FOR /F "delims=" %%P IN ('where java 2^>nul') DO IF NOT DEFINED PATH_JAVA SET "PATH_JAVA=%%P"
SET "PATH_NOTE=not found"
IF NOT DEFINED PATH_JAVA GOTO no_java
SET "JAVA_EXE=%PATH_JAVA%"
CALL :probe
SET "PATH_NOTE=%PROBE_NOTE%"
IF DEFINED PROBE_OK GOTO java_ok

:no_java
echo Error: %APP_FILE% needs Java %REQUIRED_JAVA% or newer, but none was found.
echo   JAVA_HOME = "%JAVA_HOME%" -^> %HOME_NOTE%
echo   PATH java = "%PATH_JAVA%" -^> %PATH_NOTE%
echo Please install a JDK %REQUIRED_JAVA% or newer and set JAVA_HOME to it or put it on PATH.
pause
exit /b 1

:java_ok
echo Using "%JAVA_EXE%" (Java %JAVA_MAJOR%)
echo =====================================
IF NOT DEFINED JAR_FILE (
    echo Error: no target\%APP_FILE%-*.jar found. Build it first with build-jar.bat.
    pause
    exit /b 1
)
IF %JAR_COUNT% GTR 1 (
    echo Error: %JAR_COUNT% jars match target\%APP_FILE%-*.jar, expected one:
    dir /b target\%APP_FILE%-*.jar
    echo Rebuild with build-jar.bat, which cleans target first.
    pause
    exit /b 1
)
echo Running %JAR_FILE% ...
echo =====================================
REM JAVA_TOOL_OPTIONS is picked up by the JVM itself, so it is not repeated on the command line.
"%JAVA_EXE%" -jar %JAR_FILE%

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
exit /b 0

:probe
REM Runs %JAVA_EXE% and sets PROBE_NOTE ("Java N" or "did not run") and PROBE_OK when N >= REQUIRED_JAVA.
REM java.specification.version is a bare major on every vendor: "25", "26" (also for 26-ea), "1.8".
REM The ^"" ... ^" wrapper keeps the quotes around a java.exe path with spaces.
SET PROBE_OK=
SET JAVA_MAJOR=
SET "PROBE_NOTE=did not run"
FOR /F "tokens=2 delims==" %%V IN ('^""%JAVA_EXE%" -XshowSettings:properties -version 2^>^&1 ^| findstr /C:"java.specification.version"^"') DO SET JAVA_MAJOR=%%V
IF NOT DEFINED JAVA_MAJOR EXIT /B 0
SET JAVA_MAJOR=%JAVA_MAJOR: =%
SET "PROBE_NOTE=Java %JAVA_MAJOR%"
IF "%JAVA_MAJOR:~0,2%"=="1." EXIT /B 0
IF %JAVA_MAJOR% LSS %REQUIRED_JAVA% EXIT /B 0
SET PROBE_OK=1
EXIT /B 0
