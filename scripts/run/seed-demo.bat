@echo off
setlocal
rem Re-seed the demo patients on the running backend (start-backend.bat first, wait for READY).
rem start-backend.bat already seeds at startup; run this when it started outside 07:00-16:45
rem Vietnam time, or to refill a case. Same-day reruns never create a second visit.
rem -ExamChargeNow creates Loc's unpaid exam fee right before he presents (15-minute slot hold).
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\demo_25_10\Demo-Launcher.ps1" -Action Seed %*
set "DEMO_EXIT=%ERRORLEVEL%"
echo.
if not "%MEDIFLOW_NO_PAUSE%"=="1" pause
exit /b %DEMO_EXIT%
