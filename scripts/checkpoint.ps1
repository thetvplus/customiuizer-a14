#!/usr/bin/env powershell
#Requires -Version 7.2
<#
.SYNOPSIS
    Orchestrate an A14 qualifying checkpoint transaction.
.DESCRIPTION
    This script runs the v4 transaction sequence for a single checkpoint:
    code/tests/docs -> checkers -> Fast/Full -> stage -> staged checker -> commit -> push.
    It does not auto-select business changes, does not create branches, and never runs destructive Git.
#>
[CmdletBinding()]
param(
    [Parameter()]
    [switch]$Full,

    [Parameter()]
    [string]$Message,

    [Parameter()]
    [switch]$Qualifying
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepoRoot = (git -C $PSScriptRoot rev-parse --show-toplevel) | Out-String | ForEach-Object { $_.Trim() }

function Invoke-Native($Name, $Path, $Args) {
    Write-Host "=== $Name" -ForegroundColor Cyan
    & $Path @Args
    if ($LASTEXITCODE -ne 0) {
        throw "$Name failed (exit $LASTEXITCODE)"
    }
}

function Assert-ControlState() {
    Invoke-Native "Automation state checker" (Get-Command python).Source @("tools/check_automation_state.py")
}

function Assert-DocumentContract() {
    Invoke-Native "Document contract checker" (Get-Command python).Source @("tools/check_document_contracts.py")
}

# Run checkers against working tree.
Assert-ControlState
Assert-DocumentContract

$Mode = if ($Full) { "Full" } else { "Fast" }
$Verify = Join-Path $RepoRoot "scripts" "verify.ps1"
if (-not (Test-Path -LiteralPath $Verify -PathType Leaf)) {
    throw "verify.ps1 not found"
}

Invoke-Native "Repository verifier ($Mode)" "powershell" @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $Verify, "-Mode", $Mode)

if (-not $Message) {
    throw "Commit message is required. Use -Message '...'"
}

# Stage current changes.
Invoke-Native "Git add" "git" @("-C", $RepoRoot, "add", "-A")

# Staged snapshot checks.
$QualifyingArg = if ($Qualifying) { @("--qualifying") } else { @() }
Invoke-Native "Staged snapshot checker" (Get-Command python).Source @("tools/check_staged_snapshot.py") + $QualifyingArg

# Re-run checkers against staged snapshot.
Assert-ControlState
Assert-DocumentContract

# Commit.
Invoke-Native "Git commit" "git" @("-C", $RepoRoot, "commit", "-m", $Message)

# Push exact branch.
Invoke-Native "Git push" "git" @("-C", $RepoRoot, "push", "origin", "devin/a14-rom-intelligence-audit")

Write-Host "Checkpoint transaction complete." -ForegroundColor Green
