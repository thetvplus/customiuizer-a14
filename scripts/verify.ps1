[CmdletBinding()]
param(
    [ValidateSet("fast", "full")]
    [string]$Mode = "fast"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $RepoRoot

$Python = Get-Command python -ErrorAction SilentlyContinue
if (-not $Python) {
    throw "Python 3 was not found."
}

& $Python.Source tools/verify.py $Mode
if ($LASTEXITCODE -ne 0) {
    throw "verify.py $Mode failed with exit code $LASTEXITCODE"
}
