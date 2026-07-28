@echo off
:: setup-codebase-memory.bat — Download and install codebase-memory-mcp binary
::
:: Run this ONCE on each developer machine after cloning the repo.
:: Requires: curl, a directory in PATH (default: %USERPROFILE%\.local\bin)
::
:: Usage:  scripts\setup-codebase-memory.bat
::         scripts\setup-codebase-memory.bat C:\custom\path
::
:: After installing, restart Claude Code so .mcp.json picks up the new binary,
:: then run  /index-codebase  to build the knowledge graph.

setlocal enabledelayedexpansion

set "INSTALL_DIR=%~1"
if "%INSTALL_DIR%"=="" (
    set "INSTALL_DIR=%USERPROFILE%\.local\bin"
)

set "BIN_PATH=%INSTALL_DIR%\codebase-memory-mcp.exe"

:: Check if already installed
where codebase-memory-mcp.exe >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    where codebase-memory-mcp.exe
    echo [OK] codebase-memory-mcp already on PATH.
    goto :check_version
)
if exist "%BIN_PATH%" (
    echo [OK] codebase-memory-mcp found at %BIN_PATH%
    goto :check_version
)

echo [..] Downloading codebase-memory-mcp (Windows amd64)...

:: Ensure install dir exists
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"

:: Download latest Windows binary
curl -fsSL -o "%BIN_PATH%" ^
    "https://github.com/DeusData/codebase-memory-mcp/releases/latest/download/codebase-memory-mcp-windows-amd64.exe"
if %ERRORLEVEL% NEQ 0 (
    echo [FAIL] Download failed. Check your internet connection.
    exit /b 1
)

:: Verify it's a real binary
if not exist "%BIN_PATH%" (
    echo [FAIL] Binary not found after download.
    exit /b 1
)

echo [OK] Downloaded to %BIN_PATH%

:: Add to PATH if not already there
echo.
echo [INFO] You may need to add "%INSTALL_DIR%" to your PATH:
echo        set PATH=%%PATH%%;%INSTALL_DIR%
echo        Or run Claude Code from a new terminal.

:check_version
echo.
echo [..] Verifying version...
"%BIN_PATH%" --version 2>nul || "%BIN_PATH%" version 2>nul || echo [WARN] Could not verify version (binary may need different flags).

echo.
echo =============================================
echo  Setup complete!
echo =============================================
echo  1. Restart Claude Code
echo  2. Run  /index-codebase  to build the graph
echo     (first index takes 1-3 min for MediFlow)
echo  3. Start coding — agents will use graph tools
echo =============================================

exit /b 0
