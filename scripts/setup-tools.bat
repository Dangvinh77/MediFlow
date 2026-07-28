@echo off
:: setup-tools.bat — One-time dev environment setup for MediFlow AI tools
#
# Installs both optional tools:
#   1. codebase-memory-mcp  — fast graph queries for AI agents (native binary)
#   2. Understand-Anything  — visual codebase exploration dashboard (Claude plugin)
#
# Usage:
#   scripts\setup-tools.bat              # interactive, install both
#   scripts\setup-tools.bat --minimal    # only codebase-memory-mcp
#   scripts\setup-tools.bat --full       # both (default)
#
# See: docs/ai/13-codebase-tools.md for full documentation.

@echo off
setlocal enabledelayedexpansion

set "SCOPE=%1"
if "%SCOPE%"=="" set "SCOPE=--full"

echo =============================================
echo  MediFlow — AI Codebase Tools Setup
echo =============================================
echo.

:: ==========================================
:: 1. codebase-memory-mcp (native binary MCP)
:: ==========================================
echo --------------------------------------------
echo  [1/2] codebase-memory-mcp
echo  Fast graph queries for AI coding agents
echo --------------------------------------------
call "%~dp0setup-codebase-memory.bat"
if %ERRORLEVEL% NEQ 0 (
    echo [WARN] codebase-memory-mcp setup had issues.
) else (
    echo [OK] codebase-memory-mcp ready.
)
echo.

if /I "%SCOPE%"=="--minimal" goto :done

:: ==========================================
:: 2. Understand-Anything (Claude Code plugin)
:: ==========================================
echo --------------------------------------------
echo  [2/2] Understand-Anything
echo  Visual codebase exploration dashboard
echo --------------------------------------------
call "%~dp0setup-understand-anything.bat"
if %ERRORLEVEL% NEQ 0 (
    echo [WARN] Understand-Anything setup had issues.
) else (
    echo [OK] Understand-Anything ready.
)
echo.

:done
echo =============================================
echo  Setup complete!
echo =============================================
echo.
echo  Next steps:
echo    1. Restart Claude Code
echo    2. Run  /index-codebase   (graph for agents)
echo    3. Run  /understand       (visual dashboard)
echo.
echo  Full docs: docs/ai/13-codebase-tools.md
echo =============================================

exit /b 0
