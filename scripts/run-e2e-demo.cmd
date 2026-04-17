@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-e2e-demo.ps1" %*
endlocal
