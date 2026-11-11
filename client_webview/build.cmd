@echo off
REM ============================================================
REM One-click build for the WebView client (client_webview).
REM Purpose : run build.ps1 even when PowerShell execution policy
REM           blocks unsigned local scripts (AllSigned / RemoteSigned).
REM Params  : passed through, e.g. build.cmd -SkipDeps -SmokeTest
REM ============================================================
setlocal
pushd "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1" %*
set "RC=%ERRORLEVEL%"
popd
if not "%RC%"=="0" (
    echo.
    echo [FAIL] build.ps1 exited with code %RC%
)
exit /b %RC%
