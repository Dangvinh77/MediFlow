@echo off
setlocal
rem Start the MediFlow mobile app against the running demo backend (start-backend.bat first).
rem -Target chrome (default), android (emulator) or device (phone on the same Wi-Fi, optional -Device id).
rem Demo UI switches are applied automatically; nothing else needs to be run.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\demo_25_10\Demo-Launcher.ps1" -Action Mobile %*
set "DEMO_EXIT=%ERRORLEVEL%"
echo.
if not "%MEDIFLOW_NO_PAUSE%"=="1" pause
exit /b %DEMO_EXIT%
