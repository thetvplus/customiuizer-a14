[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$SourceRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ExpectedRepository = "github.com/tomthenpc/customiuizer-a14"
$ExpectedBranch = "devin/a14-rom-intelligence-audit"
$ExpectedUpstream = "origin/$ExpectedBranch"

function Normalize-GitHubRemote {
    param([string]$RemoteUrl)

    $Value = $RemoteUrl.Trim()

    if ($Value -match '^git@github\.com:(.+)$') {
        $Value = "github.com/" + $Matches[1]
    }
    elseif ($Value -match '^ssh://git@github\.com/(.+)$') {
        $Value = "github.com/" + $Matches[1]
    }
    elseif ($Value -match '^https?://github\.com/(.+)$') {
        $Value = "github.com/" + $Matches[1]
    }

    $Value = $Value.TrimEnd("/")
    if ($Value.EndsWith(".git", [System.StringComparison]::OrdinalIgnoreCase)) {
        $Value = $Value.Substring(0, $Value.Length - 4)
    }

    return $Value.ToLowerInvariant()
}

function Get-GitText {
    param(
        [string[]]$Arguments,
        [switch]$AllowFailure
    )

    $Output = & git @Arguments 2>$null
    $ExitCode = $LASTEXITCODE

    if (($ExitCode -ne 0) -and (-not $AllowFailure)) {
        throw "git $($Arguments -join ' ') failed with exit code $ExitCode"
    }

    if ($null -eq $Output) {
        return ""
    }

    return (($Output | Out-String).Trim())
}

$SourceRoot = (Resolve-Path $SourceRoot).Path
$RepoRoot = (Get-GitText @("rev-parse", "--show-toplevel"))
$RepoRoot = (Resolve-Path $RepoRoot).Path
Set-Location $RepoRoot

$Origin = Get-GitText @("remote", "get-url", "origin")
$NormalizedOrigin = Normalize-GitHubRemote $Origin
if ($NormalizedOrigin -ne $ExpectedRepository) {
    throw "Wrong repository '$Origin'. Expected '$ExpectedRepository'."
}

$Branch = Get-GitText @("symbolic-ref", "--quiet", "--short", "HEAD") -AllowFailure
if ([string]::IsNullOrWhiteSpace($Branch)) {
    throw "Detached HEAD is forbidden."
}
if ($Branch -ne $ExpectedBranch) {
    throw "Wrong branch '$Branch'. Expected '$ExpectedBranch'."
}

$GitDirText = Get-GitText @("rev-parse", "--git-dir")
$GitDir = if ([System.IO.Path]::IsPathRooted($GitDirText)) {
    $GitDirText
}
else {
    Join-Path $RepoRoot $GitDirText
}

$Markers = @(
    (Join-Path $GitDir "MERGE_HEAD"),
    (Join-Path $GitDir "CHERRY_PICK_HEAD"),
    (Join-Path $GitDir "REVERT_HEAD"),
    (Join-Path $GitDir "rebase-apply"),
    (Join-Path $GitDir "rebase-merge")
)

$Active = @($Markers | Where-Object { Test-Path $_ })
if ($Active.Count -gt 0) {
    throw "Unfinished Git operation: $($Active -join ', ')"
}

$Files = @(
    "GOAL.md",
    "AGENTS.md",
    "TASK_STATE.md",
    "DEVIN_START_PROMPT.md",
    "INSTALL_A14_CONTROL_PLANE.md",
    "scripts\verify.ps1",
    "scripts\bootstrap-and-start.ps1"
)

foreach ($RelativePath in $Files) {
    $Source = Join-Path $SourceRoot $RelativePath
    if (-not (Test-Path -LiteralPath $Source -PathType Leaf)) {
        throw "Missing source file: $Source"
    }
}

Write-Host "Repository: $RepoRoot"
Write-Host "Origin:     $NormalizedOrigin"
Write-Host "Branch:     $Branch"
Write-Host "Existing working tree:"
git status --short

foreach ($RelativePath in $Files) {
    $Source = Join-Path $SourceRoot $RelativePath
    $Target = Join-Path $RepoRoot $RelativePath
    $TargetDirectory = Split-Path -Parent $Target

    New-Item -ItemType Directory -Path $TargetDirectory -Force | Out-Null
    Copy-Item -LiteralPath $Source -Destination $Target -Force

    $SourceHash = (Get-FileHash -LiteralPath $Source -Algorithm SHA256).Hash
    $TargetHash = (Get-FileHash -LiteralPath $Target -Algorithm SHA256).Hash

    if ($SourceHash -ne $TargetHash) {
        throw "SHA-256 mismatch: $RelativePath"
    }

    Write-Host "MATCH $RelativePath $SourceHash"
}

powershell -NoProfile -ExecutionPolicy Bypass `
    -File (Join-Path $RepoRoot "scripts\verify.ps1") `
    -Mode Audit `
    -ControlPlaneInstall

$Upstream = Get-GitText @("rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}") -AllowFailure
if ([string]::IsNullOrWhiteSpace($Upstream)) {
    git branch --set-upstream-to=$ExpectedUpstream $ExpectedBranch
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to configure upstream '$ExpectedUpstream'."
    }
}
elseif ($Upstream -ne $ExpectedUpstream) {
    throw "Wrong upstream '$Upstream'. Expected '$ExpectedUpstream'."
}

git add -- @Files
if ($LASTEXITCODE -ne 0) {
    throw "Failed to stage control-plane files."
}

$Staged = Get-GitText @("diff", "--cached", "--name-only")
$StagedList = @($Staged -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
$Unexpected = @($StagedList | Where-Object { $_ -notin $Files })
if ($Unexpected.Count -gt 0) {
    throw "Unexpected staged files:`n$($Unexpected -join "`n")"
}

git diff --cached --check
if ($LASTEXITCODE -ne 0) {
    throw "Staged control-plane diff is invalid."
}

$HasStagedChanges = -not [string]::IsNullOrWhiteSpace((Get-GitText @("diff", "--cached", "--name-only")))
if ($HasStagedChanges) {
    git commit -m "chore: install final A14 autonomous control plane"
    if ($LASTEXITCODE -ne 0) {
        throw "Control-plane commit failed."
    }

    git push origin $ExpectedBranch
    if ($LASTEXITCODE -ne 0) {
        throw "Push to authorized branch failed."
    }
}
else {
    Write-Host "Control-plane files already match; no commit required."
}

powershell -NoProfile -ExecutionPolicy Bypass `
    -File (Join-Path $RepoRoot "scripts\verify.ps1") `
    -Mode Audit

$Head = Get-GitText @("rev-parse", "HEAD")
Write-Host ""
Write-Host "A14 CONTROL PLANE INSTALLED"
Write-Host "Repository: $RepoRoot"
Write-Host "Branch:     $ExpectedBranch"
Write-Host "HEAD:       $Head"
Write-Host ""
Write-Host "Next action:"
Write-Host "Read DEVIN_START_PROMPT.md in full and immediately execute P0.1."
Write-Host "Do not wait for routine user confirmation."
