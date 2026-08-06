@echo off
:: MCP wrapper for codebase-memory-mcp — runs native binary directly.
:: This bypasses the npm Node.js wrapper which is incompatible with MCP stdio.
:: Adjust this path if your npm global install location differs.
"%USERPROFILE%\AppData\Roaming\npm\node_modules\codebase-memory-mcp\bin\codebase-memory-mcp.exe" %*
