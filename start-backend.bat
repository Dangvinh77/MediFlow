@echo off
rem SMTP configuration and Wi-Fi demo access are handled by the shared launcher.
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\demo_25_10\Demo-Launcher.ps1" -Action Backend %*
set "DEMO_EXIT=%ERRORLEVEL%"
echo.
if not "%MEDIFLOW_NO_PAUSE%"=="1" pause
exit /b %DEMO_EXIT%
