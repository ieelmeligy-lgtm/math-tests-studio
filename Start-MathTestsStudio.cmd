@echo off
rem Builds the app the first time, then starts Math Tests Studio.
setlocal
set "HERE=%~dp0"
if not defined JAVA_HOME for /d %%J in ("C:\Program Files\Microsoft\jdk-21*") do set "JAVA_HOME=%%~J"
set "PATH=%JAVA_HOME%\bin;%PATH%"
if not exist "%HERE%app\target\MathTestsStudio.jar" (
  echo Building Math Tests Studio, please wait...
  set "MVN=mvn"
  if exist "%HERE%..\tools\apache-maven-3.9.9\bin\mvn.cmd" set "MVN=%HERE%..\tools\apache-maven-3.9.9\bin\mvn.cmd"
  pushd "%HERE%app"
  call "%MVN%" -q -B package
  popd
)
start "" "%JAVA_HOME%\bin\javaw.exe" -jar "%HERE%app\target\MathTestsStudio.jar"
