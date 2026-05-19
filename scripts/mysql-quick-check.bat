@echo off
setlocal
powershell -ExecutionPolicy Bypass -File "%~dp0mysql-toolkit.ps1" -Mode check
echo.
pause
