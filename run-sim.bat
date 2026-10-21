@echo off
REM Runs the ship-model data simulator (simulator/), building it first when the JAR is missing.
REM Usage:  run-sim.bat replay --from docs\LogTerminal.txt --loop
REM         run-sim.bat raw --bytes "119,49,...,13,10" --to stdout
REM         run-sim.bat --help
REM The simulator needs Java 25, the same JDK the application is built with. JAVA_HOME wins over PATH.

SET SIM_JAR=simulator\target\ship-sim.jar

IF DEFINED JAVA_HOME (
    SET "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) ELSE (
    SET "JAVA_EXE=java"
)

IF NOT EXIST "%SIM_JAR%" (
    echo %SIM_JAR% not found, building it ...
    CALL mvnw.cmd -q -f simulator\pom.xml -DskipTests package
    IF ERRORLEVEL 1 (
        echo Error: the simulator build failed.
        exit /b 1
    )
)

"%JAVA_EXE%" -jar "%SIM_JAR%" %*
