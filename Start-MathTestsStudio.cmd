@echo off
rem Builds the app when it is missing or older than its source code, then starts Math Tests Studio.
setlocal EnableDelayedExpansion
set "HERE=%~dp0"
if not defined JAVA_HOME for /d %%J in ("C:\Program Files\Microsoft\jdk-21*") do set "JAVA_HOME=%%~J"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "JAR=%HERE%app\target\MathTestsStudio.jar"
set "MVN=mvn"
if exist "%HERE%..\tools\apache-maven-3.9.9\bin\mvn.cmd" set "MVN=%HERE%..\tools\apache-maven-3.9.9\bin\mvn.cmd"

set "BUILD="
if not exist "%JAR%" set "BUILD=1"
rem After an update: rebuild when any file in app\src or app\pom.xml is newer than the jar
if not defined BUILD powershell -NoProfile -Command "$j=(Get-Item -LiteralPath $env:JAR).LastWriteTime; $s=@(Get-ChildItem -LiteralPath ($env:HERE+'app\src') -Recurse -File)+@(Get-Item -LiteralPath ($env:HERE+'app\pom.xml')); if($s|Where-Object{$_.LastWriteTime -gt $j}){exit 1}"
if errorlevel 1 set "BUILD=1"

if defined BUILD (
  echo Building Math Tests Studio, please wait...
  pushd "%HERE%app"
  call "!MVN!" -q -B package
  set "RC=!errorlevel!"
  popd
  if not "!RC!"=="0" (
    echo.
    echo The build failed. See the messages above.
    pause
    exit /b 1
  )
)
start "" "%JAVA_HOME%\bin\javaw.exe" -jar "%JAR%"
