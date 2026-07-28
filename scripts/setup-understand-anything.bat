@echo off
:: setup-understand-anything.bat — Register + install Understand-Anything plugin
::
:: Run ONCE on each developer machine after cloning the repo.
:: Requires: Claude Code CLI on PATH.
::
:: Usage:
::   scripts\setup-understand-anything.bat
::
:: After installing:
::   - Run  /understand  to analyze the codebase (takes ~2-5 min, uses tokens)
::   - Run  /understand-dashboard  to explore the visual graph
::
:: NOTE: The first /understand run uses significant tokens (LLM analysis).
::       Subsequent runs are incremental (only changed files).

echo [..] Adding Understand-Anything marketplace...
claude plugin marketplace add Egonex-AI/Understand-Anything
if %ERRORLEVEL% NEQ 0 (
    echo [FAIL] Could not register marketplace. Is Claude Code on PATH?
    echo        Try manually:  /plugin marketplace add Egonex-AI/Understand-Anything
    exit /b 1
)

echo [..] Installing Understand-Anything plugin...
claude plugin install understand-anything
if %ERRORLEVEL% NEQ 0 (
    echo [FAIL] Plugin install failed.
    echo        Try manually:  /plugin install understand-anything
    exit /b 1
)

echo.
echo =============================================
echo  Plugin installed!
echo =============================================
echo  Commands:
echo    /understand              Analyze codebase
echo    /understand-dashboard    Open visual graph
echo    /understand-chat         Ask about code
echo    /understand-diff         Impact analysis
echo    /understand-explain      Deep-dive into file
echo    /understand-domain       Business domain view
echo    /understand-onboard      Onboarding guide
echo.
echo  First run:  /understand
echo  (will auto-open dashboard when done)
echo =============================================

exit /b 0
