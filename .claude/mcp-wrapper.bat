@echo off
where codebase-memory-mcp.exe >nul 2>&1
if errorlevel 1 (
  echo [FAIL] codebase-memory-mcp.exe is not on PATH. Run scripts\setup-codebase-memory.bat. 1>&2
  exit /b 1
)
codebase-memory-mcp.exe %*
