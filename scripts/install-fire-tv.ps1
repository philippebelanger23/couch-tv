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

$Py = Join-Path $Root ".venv\Scripts\python.exe"
if (-not (Test-Path $Py)) {
    Write-Host "Creating .venv"
    $launcher = Get-Command py -ErrorAction SilentlyContinue
    if ($launcher) { py -3 -m venv .venv } else { python -m venv .venv }
    & (Join-Path $Root ".venv\Scripts\python.exe") -m pip install -U pip
    & (Join-Path $Root ".venv\Scripts\python.exe") -m pip install -r (Join-Path $Root "bridge\requirements.txt")
    $Py = Join-Path $Root ".venv\Scripts\python.exe"
}

$script = Join-Path $Root "scripts\install-fire-tv.py"
$argList = @()
if ($Prep) { $argList += "--prep" }
if ($Undo) { $argList += "--undo" }
if ($TvIp) { $argList += $TvIp }

Write-Host "If Windows Firewall asks, Allow access for Python (private networks)."
& $Py $script @argList
exit $LASTEXITCODE
