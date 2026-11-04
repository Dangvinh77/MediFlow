@echo off
setlocal
rem Stop a -Fast/-Rebuild/-ResetData session while retaining its database and broker.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\demo_25_10\Demo-Launcher.ps1" -Action Stop %*
set "DEMO_EXIT=%ERRORLEVEL%"
echo.
if not "%MEDIFLOW_NO_PAUSE%"=="1" pause
exit /b %DEMO_EXIT%
