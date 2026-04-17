@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-e2e-cross-stack.ps1" %*
endlocal
