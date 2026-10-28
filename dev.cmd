@echo off
rem dev.cmd build      :: compile + package -> build\libs\aicompanion-<ver>.jar
rem dev.cmd runClient  :: launch Minecraft 1.20.1 + Forge with the mod
rem TEMP must not be the 8.3 path (MSIGAM~1): it breaks NIO Selector in JDK 17/21.
if not exist "C:\Temp" mkdir "C:\Temp"
set "TEMP=C:\Temp"
set "TMP=C:\Temp"
set "TEMURIN=%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
if exist "%TEMURIN%" (
	set "JAVA_HOME=%TEMURIN%"
	set "PATH=%TEMURIN%\bin;%PATH%"
)
rem gradlew takes the project from the current dir, so run it from the mod folder wherever dev.cmd is called from
pushd "%~dp0"
call "%~dp0gradlew.bat" %*
set "RC=%ERRORLEVEL%"
popd
exit /b %RC%
