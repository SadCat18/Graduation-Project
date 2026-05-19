param(
  [string]$User = "root",
  [string]$Password = "123456",
  [int]$Port = 3306
)

$ErrorActionPreference = "Stop"
if ($null -ne (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue)) {
  $PSNativeCommandUseErrorActionPreference = $false
}

function Test-MySQLLogin {
  param(
    [string]$HostName,
    [string]$UserName,
    [string]$Pwd,
    [int]$DbPort
  )
  try {
    $cmd = "mysql --host=$HostName --port=$DbPort --user=$UserName --password=$Pwd --connect-timeout=5 --execute=""SELECT 1 AS ok;"""
    $output = (cmd /c $cmd 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -eq 0) {
      Write-Host "[OK] Login success: ${HostName}:$DbPort ($UserName)"
      return $true
    } else {
      Write-Host "[ERROR] Login failed: ${HostName}:$DbPort ($UserName)"
      Write-Host $output
      return $false
    }
  } catch {
    Write-Host "[ERROR] Test exception: ${HostName}:$DbPort -> $($_.Exception.Message)"
    return $false
  }
}

function Test-IsAdmin {
  try {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
  } catch {
    return $false
  }
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
    } else {
      Write-Host "[ERROR] Service not running: $Name ($($svc.Status))"
      return $false
    }
  } catch {
    Write-Host "[ERROR] Restart failed: $Name -> $($_.Exception.Message)"
    return $false
  }
}

Write-Host "========== MySQL Self Check =========="
if (-not (Test-IsAdmin)) {
  Write-Host "[WARN] Current shell is not elevated. Service restart may fail."
}

$targets = @("mysql", "MySQL80")
$restarted = $false
foreach ($name in $targets) {
  if (Restart-ServiceSafe -Name $name) {
    $restarted = $true
  }
}

if (-not $restarted) {
  Write-Host "[WARN] No MySQL service restarted. Check service names."
}

Write-Host "[INFO] Verifying root login..."
$ok1 = Test-MySQLLogin -HostName "127.0.0.1" -UserName $User -Pwd $Password -DbPort $Port
$ok2 = Test-MySQLLogin -HostName "localhost" -UserName $User -Pwd $Password -DbPort $Port

if ($ok1 -or $ok2) {
  Write-Host "[OK] MySQL is available. You can start Spring Boot."
  exit 0
} else {
  Write-Host "[ERROR] MySQL is still unavailable. Check credentials/privileges."
  exit 1
}
