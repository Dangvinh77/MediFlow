@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0Initialize-Video.ps1" -Preview
if errorlevel 1 pause
