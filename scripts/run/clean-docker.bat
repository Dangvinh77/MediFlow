@echo off
setlocal
rem Clean also stops orphan demo JVMs holding this repository's demo ports.
rem Use -Preview to review process and Docker cleanup without making changes.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\demo_25_10\Demo-Launcher.ps1" -Action Clean %*
set "DEMO_EXIT=%ERRORLEVEL%"
echo.
if not "%MEDIFLOW_NO_PAUSE%"=="1" pause
exit /b %DEMO_EXIT%
