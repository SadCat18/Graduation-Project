param(
  [string]$RootPassword = "Root@123456",
  [string]$AppUser = "appuser",
  [string]$AppPassword = "App@123456",
  [int]$Port = 3306
)

$ErrorActionPreference = "Stop"
if ($null -ne (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue)) {
  $PSNativeCommandUseErrorActionPreference = $false
}

function Write-Section {
  param([string]$Title)
  Write-Host ""
  Write-Host "========== $Title ==========" -ForegroundColor Cyan
}

function Test-Login {
  param(
    [string]$HostName,
    [string]$UserName,
    [string]$Password,
    [string]$Label
  )

  $mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
  if (-not (Test-Path $mysql)) {
    Write-Host "[ERROR] mysql.exe not found: $mysql" -ForegroundColor Red
    return
  }

  $cmd = "`"$mysql`" -h$HostName -P$Port -u$UserName -p$Password --connect-timeout=5 --execute=`"SELECT CURRENT_USER(), USER();`" 2>nul"
  $output = cmd /c $cmd
  if ($LASTEXITCODE -eq 0) {
    Write-Host "[OK] $Label" -ForegroundColor Green
    $output | ForEach-Object { Write-Host $_ }
  } else {
    Write-Host "[FAIL] $Label" -ForegroundColor Yellow
    $output | ForEach-Object { Write-Host $_ }
  }
}

Write-Section "MySQL Service"
Get-Service *mysql* -ErrorAction SilentlyContinue | Format-Table -Auto Name, DisplayName, Status

Write-Section "Port Check"
cmd /c "netstat -ano | findstr :$Port"

Write-Section "Root Login"
Test-Login -HostName "localhost" -UserName "root" -Password $RootPassword -Label "root@localhost"
Test-Login -HostName "127.0.0.1" -UserName "root" -Password $RootPassword -Label "root@127.0.0.1"

Write-Section "App User Login"
Test-Login -HostName "localhost" -UserName $AppUser -Password $AppPassword -Label "$AppUser@localhost"
Test-Login -HostName "127.0.0.1" -UserName $AppUser -Password $AppPassword -Label "$AppUser@127.0.0.1"

Write-Section "Done"
Write-Host "If all checks show [OK], MySQL connection is healthy." -ForegroundColor Green
