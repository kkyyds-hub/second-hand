@echo off
echo Running MongoDB install script (PowerShell Bypass)...
powershell -ExecutionPolicy Bypass -File "%~dp0mongodb-install.ps1"
pause
