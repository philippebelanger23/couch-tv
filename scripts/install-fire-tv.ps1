# Sideload couch-tv onto a Fire TV from Windows. No Git Bash.
# Usage, from the repo root in PowerShell:
#   .\scripts\install-fire-tv.ps1 -Prep
#   .\scripts\install-fire-tv.ps1 -TvIp 192.168.1.110
#   .\scripts\install-fire-tv.ps1 -Undo -TvIp 192.168.1.110
param(
    [string]$TvIp = $env:TV_IP,
    [switch]$Prep,
    [switch]$Undo
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

function Get-HostPython {
    foreach ($name in @("py", "python", "python3")) {
        $cmd = Get-Command $name -ErrorAction SilentlyContinue
        if ($cmd) { return $cmd.Source }
    }
    foreach ($guess in @(
        "$env:LOCALAPPDATA\Programs\Python\Python312\python.exe",
        "$env:LOCALAPPDATA\Programs\Python\Launcher\py.exe",
        "$env:ProgramFiles\Python312\python.exe"
    )) {
        if (Test-Path $guess) { return $guess }
    }
    return $null
}

$Py = Join-Path $Root ".venv\Scripts\python.exe"
if (-not (Test-Path $Py)) {
    $hostPy = Get-HostPython
    if (-not $hostPy) {
        Write-Host "Python is not on PATH. Download the official 64-bit installer:"
        Write-Host "  https://www.python.org/ftp/python/3.12.10/python-3.12.10-amd64.exe"
        Write-Host "On the first screen tick Add python.exe to PATH, then Install Now."
        Write-Host "Close this PowerShell window, open a new one, cd to this folder, and run this script again."
        exit 1
    }
    Write-Host "Creating .venv with $hostPy"
    if ((Split-Path -Leaf $hostPy) -eq "py.exe") {
        & $hostPy -3 -m venv .venv
    } else {
        & $hostPy -m venv .venv
    }
    if (-not (Test-Path $Py)) {
        Write-Host "venv was not created. Close PowerShell, open a new window after installing Python, and try again."
        exit 1
    }
    & $Py -m pip install -U pip
    & $Py -m pip install -r (Join-Path $Root "bridge\requirements.txt")
}

$script = Join-Path $Root "scripts\install-fire-tv.py"
$argList = @()
if ($Prep) { $argList += "--prep" }
if ($Undo) { $argList += "--undo" }
if ($TvIp) { $argList += $TvIp }

Write-Host "If Windows Firewall asks, Allow access for Python (private networks)."
& $Py $script @argList
exit $LASTEXITCODE
