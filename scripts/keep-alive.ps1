# Keeps the local backend stack (Docker Desktop + db/app containers + the
# ngrok tunnel) up and reachable. Safe to run repeatedly — every check is
# idempotent, it only starts things that aren't already running.
#
# Intended use: registered as a Scheduled Task that runs at log-on and then
# repeats every few minutes, so it both brings the stack up when the machine
# starts and self-heals anything that crashed later. See
# scripts/register-keep-alive-task.ps1 to set that up.

$ErrorActionPreference = "Continue"

$RepoPath      = "C:\Users\HomePC\Documents\work\t-coop-backend"
$DockerExe     = "$env:LOCALAPPDATA\Programs\DockerDesktop\Docker Desktop.exe"
$NgrokExe      = "C:\Users\HomePC\AppData\Local\Microsoft\WinGet\Packages\Ngrok.Ngrok_Microsoft.Winget.Source_8wekyb3d8bbwe\ngrok.exe"
$NgrokDomain   = "hamster-probiotic-compile.ngrok-free.dev"
$LogFile       = "$RepoPath\scripts\keep-alive.log"
$NgrokLog      = "$RepoPath\scripts\ngrok.log"

function Write-Log($Message) {
    $line = "{0}  {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $Message
    Add-Content -Path $LogFile -Value $line
    Write-Output $line
}

function Test-DockerEngine {
    docker info *> $null
    return ($LASTEXITCODE -eq 0)
}

function Ensure-DockerDesktop {
    if (Test-DockerEngine) {
        return $true
    }

    $running = Get-Process "Docker Desktop" -ErrorAction SilentlyContinue
    if (-not $running) {
        Write-Log "Docker Desktop not running - launching it."
        Start-Process $DockerExe
    } else {
        Write-Log "Docker Desktop process is up but engine isn't responding yet - waiting."
    }

    for ($i = 0; $i -lt 36; $i++) {
        Start-Sleep -Seconds 5
        if (Test-DockerEngine) {
            Write-Log "Docker engine is ready."
            return $true
        }
    }
    Write-Log "WARNING: Docker engine still not ready after 3 minutes."
    return $false
}

function Ensure-Containers {
    Push-Location $RepoPath
    try {
        $status = docker compose ps --format json 2>$null
        Write-Log "Ensuring containers are up (docker compose up -d)."
        docker compose up -d 2>&1 | ForEach-Object { Write-Log "  $_" }
    } finally {
        Pop-Location
    }
}

function Ensure-NgrokTunnel {
    $existing = Get-CimInstance Win32_Process -Filter "Name = 'ngrok.exe'" -ErrorAction SilentlyContinue
    if ($existing) {
        Write-Log "ngrok is already running (PID $($existing.ProcessId | Select-Object -First 1))."
        return
    }

    Write-Log "ngrok is not running - starting tunnel on $NgrokDomain."
    Start-Process -FilePath $NgrokExe `
        -ArgumentList "http --url=$NgrokDomain 8080 --log stdout" `
        -WindowStyle Hidden `
        -RedirectStandardOutput $NgrokLog `
        -RedirectStandardError "$RepoPath\scripts\ngrok.err.log"
}

Write-Log "=== keep-alive check starting ==="

if (Ensure-DockerDesktop) {
    Ensure-Containers
} else {
    Write-Log "Skipping container check - Docker engine unavailable."
}

Ensure-NgrokTunnel

Write-Log "=== keep-alive check complete ==="
