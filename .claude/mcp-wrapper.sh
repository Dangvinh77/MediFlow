#!/usr/bin/env sh
# MCP wrapper for codebase-memory-mcp — runs native binary directly.
# This wrapper exists because the npm Node.js shim (bin.js with spawnSync)
# is incompatible with the persistent bidirectional stdio that MCP requires.
exec "/c/Users/hp/AppData/Roaming/npm/node_modules/codebase-memory-mcp/bin/codebase-memory-mcp.exe" "$@"
