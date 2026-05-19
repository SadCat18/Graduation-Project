param(
  [ValidateSet("menu", "check", "fix-root", "restart-check", "all")]
  [string]$Mode = "menu",
  [string]$RootPassword = "Root@123456",
  [string]$AppUser = "appuser",
  [string]$AppPassword = "App@123456",
  [int]$Port = 3306
)

$ErrorActionPreference = "Stop"
if ($null -ne (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue)) {
  $PSNativeCommandUseErrorActionPreference = $false
}

$mysqlExe = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"

function Write-Section {
  param([string]$Title)
  Write-Host ""
  Write-Host "========== $Title ==========" -ForegroundColor Cyan
}

function Invoke-Mysql {
  param(
    [string]$HostName = "localhost",
    [string]$UserName,
    [string]$Password,
    [string]$Sql
  )

  if (-not (Test-Path $mysqlExe)) {
    throw "mysql.exe not found: $mysqlExe"
  }

  $cmd = "`"$mysqlExe`" -h$HostName -P$Port -u$UserName -p$Password --connect-timeout=5 --default-character-set=utf8mb4 --execute=`"$Sql`" 2>nul"
  $output = cmd /c $cmd
  return @{
    ExitCode = $LASTEXITCODE
    Output = ($output | Out-String).Trim()
  }
}

function Test-Login {
  param(
    [string]$HostName,
    [string]$UserName,
    [string]$Password,
    [string]$Label
  )

  $result = Invoke-Mysql -HostName $HostName -UserName $UserName -Password $Password -Sql "SELECT CURRENT_USER(), USER();"
  if ($result.ExitCode -eq 0) {
    Write-Host "[OK] $Label" -ForegroundColor Green
    if ($result.Output) {
      $result.Output -split "`r?`n" | ForEach-Object { Write-Host $_ }
    }
    return $true
  }

  Write-Host "[FAIL] $Label" -ForegroundColor Yellow
  if ($result.Output) {
    $result.Output -split "`r?`n" | ForEach-Object { Write-Host $_ }
  }
  return $false
}

function Show-ServiceAndPort {
  Write-Section "MySQL Service"
  Get-Service *mysql* -ErrorAction SilentlyContinue | Format-Table -Auto Name, DisplayName, Status

  Write-Section "Port Check"
  cmd /c "netstat -ano | findstr :$Port"
}

function Run-QuickCheck {
  Show-ServiceAndPort

  Write-Section "Root Login"
  $rootLocal = Test-Login -HostName "localhost" -UserName "root" -Password $RootPassword -Label "root@localhost"
  $rootIp = Test-Login -HostName "127.0.0.1" -UserName "root" -Password $RootPassword -Label "root@127.0.0.1"

  Write-Section "App User Login"
  $appLocal = Test-Login -HostName "localhost" -UserName $AppUser -Password $AppPassword -Label "$AppUser@localhost"
  $appIp = Test-Login -HostName "127.0.0.1" -UserName $AppUser -Password $AppPassword -Label "$AppUser@127.0.0.1"

  Write-Section "Done"
  if ($rootLocal -and $rootIp -and $appLocal -and $appIp) {
    Write-Host "All checks passed." -ForegroundColor Green
    return $true
  }

  Write-Host "Some checks failed." -ForegroundColor Yellow
  return $false
}

function Fix-RootPrivileges {
  Write-Section "Fix Root Privileges"
  $sql = @"
CREATE USER IF NOT EXISTS 'root'@'127.0.0.1' IDENTIFIED BY '$RootPassword';
ALTER USER 'root'@'localhost' IDENTIFIED BY '$RootPassword';
ALTER USER 'root'@'127.0.0.1' IDENTIFIED BY '$RootPassword';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'localhost' WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON *.* TO 'root'@'127.0.0.1' WITH GRANT OPTION;
FLUSH PRIVILEGES;
SELECT user, host, plugin, account_locked FROM mysql.user WHERE user='root' ORDER BY host;
"@

  $result = Invoke-Mysql -HostName "localhost" -UserName "root" -Password $RootPassword -Sql $sql
  if ($result.ExitCode -eq 0) {
    Write-Host "[OK] Root privilege repair finished." -ForegroundColor Green
    if ($result.Output) {
      $result.Output -split "`r?`n" | ForEach-Object { Write-Host $_ }
    }
    return $true
  }

  Write-Host "[FAIL] Root privilege repair failed." -ForegroundColor Red
  if ($result.Output) {
    $result.Output -split "`r?`n" | ForEach-Object { Write-Host $_ }
  }
  return $false
}

function Restart-ServiceSafe {
  param([string]$Name)
  $svc = Get-Service -Name $Name -ErrorAction SilentlyContinue
  if (-not $svc) {
    Write-Host "[WARN] Service not found: $Name"
    return $false
  }
  try {
    Write-Host "[INFO] Restarting service: $Name"
    Restart-Service -Name $Name -Force -ErrorAction Stop
    Start-Sleep -Seconds 3
    $svc.Refresh()
    if ($svc.Status -eq "Running") {
      Write-Host "[OK] Service running: $Name"
      return $true
    }
    Write-Host "[FAIL] Service not running: $Name ($($svc.Status))"
    return $false
  } catch {
    Write-Host "[FAIL] Restart failed: $Name -> $($_.Exception.Message)"
    return $false
  }
}

function Run-RestartCheck {
  Write-Section "Restart And Check"
  $targets = @("mysql", "MySQL80")
  $restarted = $false
  foreach ($name in $targets) {
    if (Restart-ServiceSafe -Name $name) {
      $restarted = $true
    }
  }

  if (-not $restarted) {
    Write-Host "[WARN] No MySQL service restarted."
  }

  return (Run-QuickCheck)
}

function Show-Menu {
  Write-Host ""
  Write-Host "MySQL Toolkit" -ForegroundColor Cyan
  Write-Host "1. Quick check"
  Write-Host "2. Fix root privileges"
  Write-Host "3. Restart service and check"
  Write-Host "4. Run all"
  Write-Host "0. Exit"
  $choice = Read-Host "Select"
  switch ($choice) {
    "1" { return "check" }
    "2" { return "fix-root" }
    "3" { return "restart-check" }
    "4" { return "all" }
    default { return "exit" }
  }
}

if ($Mode -eq "menu") {
  $Mode = Show-Menu
}

switch ($Mode) {
  "check" {
    if (Run-QuickCheck) { exit 0 } else { exit 1 }
  }
  "fix-root" {
    if (Fix-RootPrivileges) { exit 0 } else { exit 1 }
  }
  "restart-check" {
    if (Run-RestartCheck) { exit 0 } else { exit 1 }
  }
  "all" {
    $fixed = Fix-RootPrivileges
    $checked = Run-QuickCheck
    if ($fixed -and $checked) { exit 0 } else { exit 1 }
  }
  default {
    Write-Host "Exit."
    exit 0
  }
}
