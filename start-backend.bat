@echo off
rem SMTP configuration and Wi-Fi demo access are handled by the shared launcher.
rem -Fast reuses verified JARs and demo data; -Rebuild forces Maven; -ResetData resets retained data.
rem -StartupParallelism 1..3 bounds simultaneous application startup (default 2).
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\demo_25_10\Demo-Launcher.ps1" -Action Backend %*
set "DEMO_EXIT=%ERRORLEVEL%"
echo.
if not "%MEDIFLOW_NO_PAUSE%"=="1" pause
exit /b %DEMO_EXIT%
