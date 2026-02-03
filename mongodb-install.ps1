# MongoDB 6.0 - Install to D: from Downloads zip, config + auth (root/1234)
# Run: run-mongodb-install.bat   or   PowerShell -ExecutionPolicy Bypass -File mongodb-install.ps1
# Admin right needed for Windows service.

$MongoVersion = "6.0.27"
$ZipName = "mongodb-windows-x86_64-$MongoVersion.zip"
$ZipPath = "C:\Users\kk\Downloads\$ZipName"
$InstallRoot = "D:\mongodb"
$MongoDir = "$InstallRoot\mongodb-windows-x86_64-$MongoVersion"
$DataDir = "$InstallRoot\data"
$LogDir = "$InstallRoot\log"
$LogFile = "$LogDir\mongod.log"
$CfgPath = "$InstallRoot\mongod.cfg"
$MongoUser = "root"
$MongoPwd = "1234"

Write-Host "=========================================="
Write-Host "  MongoDB $MongoVersion - D:\mongodb"
Write-Host "  Zip: $ZipPath"
Write-Host "  Data: $DataDir   Log: $LogFile"
Write-Host "  Auth: user=$MongoUser  password=$MongoPwd"
Write-Host "=========================================="
Write-Host ""

# 1. Check zip
if (-not (Test-Path $ZipPath)) {
    Write-Host "[ERROR] Zip not found: $ZipPath"
    Write-Host "        Put $ZipName in Downloads folder and run again."
    exit 1
}
Write-Host "[1/6] Zip OK: $ZipPath"

# 2. Create D:\mongodb and dirs
Write-Host "[2/6] Creating D:\mongodb (data, log)..."
New-Item -ItemType Directory -Path $InstallRoot -Force | Out-Null
New-Item -ItemType Directory -Path $DataDir -Force | Out-Null
New-Item -ItemType Directory -Path $LogDir -Force | Out-Null

# 3. Extract
$MongodExe = "$MongoDir\bin\mongod.exe"
if (-not (Test-Path $MongodExe)) {
    Write-Host "[3/6] Extracting to D:\mongodb..."
    try {
        Expand-Archive -Path $ZipPath -DestinationPath $InstallRoot -Force
    } catch {
        Write-Host "      Extract failed (zip may be broken). Check: $ZipPath"
        exit 1
    }
    $extracted = Get-ChildItem $InstallRoot -Directory | Where-Object { $_.Name -like "mongodb*" } | Select-Object -First 1
    if ($extracted) { $MongoDir = $extracted.FullName; $MongodExe = "$MongoDir\bin\mongod.exe" }
    if (-not (Test-Path $MongodExe)) {
        $MongodExe = Get-ChildItem -Path $InstallRoot -Recurse -Filter "mongod.exe" -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
    }
}
if (-not $MongodExe -or -not (Test-Path $MongodExe)) {
    Write-Host "      mongod.exe not found after extract."
    exit 1
}
Write-Host "      mongod.exe: $MongodExe"

# 4. Config (no auth first, so we can create user)
Write-Host "[4/6] Writing mongod.cfg (no auth for first start)..."
$CfgNoAuth = @"
systemLog:
  destination: file
  path: $LogFile
  logAppend: true
storage:
  dbPath: $DataDir
net:
  port: 27017
  bindIp: 127.0.0.1
"@
Set-Content -Path $CfgPath -Value $CfgNoAuth -Encoding UTF8

# 5. Create user root/1234: start mongod without auth, run mongosh to add user, then enable auth
Write-Host "[5/6] Creating user $MongoUser / **** ..."
$proc = Start-Process -FilePath $MongodExe -ArgumentList "--config", $CfgPath -PassThru -WindowStyle Hidden
Start-Sleep -Seconds 5
$mongoshExe = $null
if (Get-Command mongosh -ErrorAction SilentlyContinue) { $mongoshExe = "mongosh" }
if (-not $mongoshExe -and (Test-Path "D:\mongodb\mongosh\bin\mongosh.exe")) { $mongoshExe = "D:\mongodb\mongosh\bin\mongosh.exe" }
$authEnabled = $false
if ($mongoshExe) {
    $createUserJs = "db.getSiblingDB('admin').createUser({user:'$MongoUser',pwd:'$MongoPwd',roles:['root']})"
    & $mongoshExe "mongodb://localhost:27017/admin" --eval $createUserJs 2>&1 | Out-Null
    $authEnabled = $true
    Write-Host "      User root created (or already exists)."
} else {
    Write-Host "      mongosh not found. Install from: https://www.mongodb.com/try/download/shell"
    Write-Host "      After install, run once: mongosh mongodb://localhost:27017/admin --eval `"db.getSiblingDB('admin').createUser({user:'root',pwd:'1234',roles:['root']})`""
    Write-Host "      Then add under net in $CfgPath : security: / authorization: enabled  and restart MongoDB."
}
Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2

# Config: enable auth only if we created user (or assume user exists)
if ($authEnabled) {
    $CfgWithAuth = @"
systemLog:
  destination: file
  path: $LogFile
  logAppend: true
storage:
  dbPath: $DataDir
net:
  port: 27017
  bindIp: 127.0.0.1
security:
  authorization: enabled
"@
    Set-Content -Path $CfgPath -Value $CfgWithAuth -Encoding UTF8
} else {
    Write-Host "      Using config without auth (connect: mongodb://localhost:27017/demo)."
}

# 6. Install Windows service
Write-Host "[6/6] Installing Windows service 'MongoDB'..."
& $MongodExe --config $CfgPath --install --serviceName "MongoDB"
if ($LASTEXITCODE -ne 0) {
    Write-Host "      Service install failed. Run this script as Administrator (right-click -> Run as admin)."
    Write-Host "      Or start manually: $MongodExe --config $CfgPath"
} else {
    Start-Service -Name "MongoDB" -ErrorAction SilentlyContinue
    Write-Host "      Service 'MongoDB' installed and started."
}

Write-Host ""
if ($authEnabled) {
    Write-Host "Done. Connection: mongodb://root:1234@localhost:27017/demo"
    Write-Host "Project yml: demo.mongodb.uri = mongodb://root:1234@localhost:27017/demo"
} else {
    Write-Host "Done. Connection (no auth): mongodb://localhost:27017/demo"
    Write-Host "After creating user root/1234, set project yml: mongodb://root:1234@localhost:27017/demo"
}
