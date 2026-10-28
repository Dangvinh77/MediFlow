@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\demo_25_10\Demo-Launcher.ps1" -Action Frontend %*
set "DEMO_EXIT=%ERRORLEVEL%"
echo.
if not "%MEDIFLOW_NO_PAUSE%"=="1" pause
exit /b %DEMO_EXIT%
