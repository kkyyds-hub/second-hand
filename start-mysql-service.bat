@echo off
set MYSQL_BIN=D:\mysql\mysql-8.4.6-winx64\bin
set MYSQL_SVC=MySQL84

echo ========================================
echo   MySQL Service - Install and Start
echo   (Run as Administrator if denied)
echo ========================================
echo.

if not exist "%MYSQL_BIN%\mysqld.exe" (
    echo [ERROR] mysqld.exe not found. Check path: %MYSQL_BIN%
    pause
    exit /b 1
)

echo [1/2] Register Windows service: %MYSQL_SVC%
"%MYSQL_BIN%\mysqld.exe" --install %MYSQL_SVC%
if errorlevel 1 (
    echo.
    echo If "Install/Remove of the Service Denied": Right-click this file - Run as administrator
    echo If service already exists: run "net start %MYSQL_SVC%" instead.
    echo.
) else (
    echo Service registered.
)

echo.
echo [2/2] Starting service: %MYSQL_SVC%
net start %MYSQL_SVC%
if errorlevel 1 (
    echo Start failed. Check if service exists or port 3306 is in use.
) else (
    echo MySQL started successfully.
)

echo.
pause
