@echo off
setlocal
rem Export every mediflow_* database of the seeded demo to plain SQL.
rem Needs only Docker; output goes to scripts\demo_25_10\exports\<time> (gitignored).
rem Options: -Container <name>  -OutputDir <folder>  -Force
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\demo_25_10\Export-DemoDatabase.ps1" %*
set "DEMO_EXIT=%ERRORLEVEL%"
echo.
if not "%MEDIFLOW_NO_PAUSE%"=="1" pause
exit /b %DEMO_EXIT%
