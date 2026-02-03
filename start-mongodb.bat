@echo off
set MONGO_ROOT=D:\mongodb
set MONGO_VER=6.0.27
set MONGOD=%MONGO_ROOT%\mongodb-windows-x86_64-%MONGO_VER%\bin\mongod.exe
set CFG=%MONGO_ROOT%\mongod.cfg
if not exist "%MONGOD%" (
    set MONGOD=%MONGO_ROOT%\mongodb-windows-x86_64-6.0.27\bin\mongod.exe
)
if not exist "%MONGOD%" (
    echo mongod.exe not found. Run run-mongodb-install.bat first.
    pause
    exit /b 1
)

echo Starting MongoDB (D:\mongodb)...
start "" "%MONGOD%" --config "%CFG%"
echo MongoDB started. URI: mongodb://root:1234@localhost:27017/demo
pause
