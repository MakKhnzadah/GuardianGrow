@echo off
setlocal
REM Repo-root shim to run the real Gradle build located in backend/
pushd "%~dp0backend" || exit /b 1
call ".\gradlew.bat" %*
set EXIT_CODE=%ERRORLEVEL%
popd
endlocal & exit /b %EXIT_CODE%
