@echo off
:: setup-hooks.bat — enable the MediFlow git hooks on this machine (Windows)
::
:: The hook scripts are committed at scripts/git-hooks/, but git only runs them
:: when core.hooksPath points there — and that setting lives in .git/config,
:: which is LOCAL to each machine and NEVER travels with `git clone`.
:: Run this once after cloning (bootstrap.ps1 does it automatically).
::
:: Usage:
::   scripts\setup-hooks.bat

if not exist "scripts\git-hooks" (
    echo [ERROR] scripts\git-hooks\ not found — run this from the repo root.
    exit /b 1
)

git config core.hooksPath scripts/git-hooks
if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%

echo [ok] Git hooks enabled: core.hooksPath = scripts/git-hooks
echo       - prepare-commit-msg strips "Co-Authored-By:" lines
echo       - commit-msg rejects any commit that still carries one (single-author policy)
echo       - post-commit / post-merge maintain the changelog (.changelog/)

for /f "delims=" %%v in ('git config --get core.hooksPath') do set "HP=%%v"
echo Active hooksPath: %HP%
exit /b 0
