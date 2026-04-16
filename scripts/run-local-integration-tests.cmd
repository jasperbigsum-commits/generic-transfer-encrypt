@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-local-integration-tests.ps1" %*
endlocal
