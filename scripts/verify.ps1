[CmdletBinding()]
param(
    [ValidateSet("Audit", "Fast", "Full", "Final")]
    [string]$Mode = "Audit",

    [switch]$ControlPlaneInstall
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ExpectedRepository = "github.com/tomthenpc/customiuizer-a14"
$ExpectedBranch = "devin/a14-rom-intelligence-audit"
$ExpectedUpstream = "origin/$ExpectedBranch"

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $RepoRoot

function Write-Section {
    param([string]$Text)
    Write-Host ""
    Write-Host "==> $Text"
}

function Invoke-Native {
    param(
        [string]$Label,
        [string]$FilePath,
        [string[]]$Arguments = @()
    )

    Write-Section $Label
    & $FilePath @Arguments
    $ExitCode = $LASTEXITCODE

    if ($ExitCode -ne 0) {
        throw "$Label failed with exit code $ExitCode"
    }
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

function Resolve-Python {
    $Python = Get-Command python -ErrorAction SilentlyContinue
    if ($Python) {
        return @{ FilePath = $Python.Source; Prefix = @() }
    }

    $Py = Get-Command py -ErrorAction SilentlyContinue
    if ($Py) {
        return @{ FilePath = $Py.Source; Prefix = @("-3") }
    }

    throw "Python 3 was not found."
}

function Test-TrackedAtHead {
    param([string]$RelativePath)

    & git cat-file -e "HEAD:$RelativePath" 2>$null
    return ($LASTEXITCODE -eq 0)
}

Write-Section "Repository and branch lock"

$GitRoot = Get-GitText @("rev-parse", "--show-toplevel")
$ResolvedGitRoot = (Resolve-Path $GitRoot).Path
if ($ResolvedGitRoot -ne $RepoRoot) {
    throw "Repository root mismatch. Script root: '$RepoRoot'; Git root: '$ResolvedGitRoot'."
}

$Origin = Get-GitText @("remote", "get-url", "origin")
$NormalizedOrigin = Normalize-GitHubRemote $Origin
if ($NormalizedOrigin -ne $ExpectedRepository) {
    throw "Wrong origin '$Origin' (normalized '$NormalizedOrigin'). Expected '$ExpectedRepository'."
}

$Branch = Get-GitText @("symbolic-ref", "--quiet", "--short", "HEAD") -AllowFailure
if ([string]::IsNullOrWhiteSpace($Branch)) {
    throw "Detached HEAD is forbidden."
}
if ($Branch -ne $ExpectedBranch) {
    throw "Wrong branch '$Branch'. Expected exact branch '$ExpectedBranch'."
}

$Head = Get-GitText @("rev-parse", "HEAD")
$Upstream = Get-GitText @("rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}") -AllowFailure

if ([string]::IsNullOrWhiteSpace($Upstream)) {
    if ($Mode -eq "Audit" -or $ControlPlaneInstall) {
        Write-Warning "No upstream configured. Configure '$ExpectedUpstream' before Fast/Full/Final."
    }
    else {
        throw "Missing upstream. Expected '$ExpectedUpstream'."
    }
}
elseif ($Upstream -ne $ExpectedUpstream) {
    throw "Wrong upstream '$Upstream'. Expected '$ExpectedUpstream'."
}

Write-Host "Repository: $NormalizedOrigin"
Write-Host "Branch:     $Branch"
Write-Host "Upstream:   $Upstream"
Write-Host "HEAD:       $Head"
Write-Host "Mode:       $Mode"

Write-Section "Unfinished Git operations"

$GitDirText = Get-GitText @("rev-parse", "--git-dir")
$GitDir = if ([System.IO.Path]::IsPathRooted($GitDirText)) {
    $GitDirText
}
else {
    Join-Path $RepoRoot $GitDirText
}

$OperationMarkers = @(
    (Join-Path $GitDir "MERGE_HEAD"),
    (Join-Path $GitDir "CHERRY_PICK_HEAD"),
    (Join-Path $GitDir "REVERT_HEAD"),
    (Join-Path $GitDir "rebase-apply"),
    (Join-Path $GitDir "rebase-merge")
)

$ActiveMarkers = @($OperationMarkers | Where-Object { Test-Path $_ })
if ($ActiveMarkers.Count -gt 0) {
    throw "Unfinished Git operation: $($ActiveMarkers -join ', ')"
}

Write-Section "Control-plane files"

$ProtectedFiles = @(
    "GOAL.md",
    "AGENTS.md",
    "DEVIN_START_PROMPT.md",
    "INSTALL_A14_CONTROL_PLANE.md",
    "scripts/verify.ps1",
    "scripts/bootstrap-and-start.ps1"
)

foreach ($RelativePath in $ProtectedFiles) {
    $AbsolutePath = Join-Path $RepoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $AbsolutePath -PathType Leaf)) {
        throw "Missing control-plane file: $RelativePath"
    }

    if ((-not $ControlPlaneInstall) -and (Test-TrackedAtHead $RelativePath)) {
        & git diff --quiet HEAD -- $RelativePath
        if ($LASTEXITCODE -ne 0) {
            throw "Protected control-plane file has uncommitted changes: $RelativePath"
        }
    }
}

if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot "TASK_STATE.md") -PathType Leaf)) {
    throw "Missing TASK_STATE.md"
}

Write-Section "Git integrity"

Invoke-Native "git diff --check" "git" @("diff", "--check")

$Unmerged = Get-GitText @("diff", "--name-only", "--diff-filter=U")
if (-not [string]::IsNullOrWhiteSpace($Unmerged)) {
    throw "Unmerged files detected:`n$Unmerged"
}

Write-Section "Forbidden tracked files"

$Tracked = Get-GitText @("ls-files")
$ForbiddenPatterns = @(
    '(^|/)\.env($|\.)',
    '(^|/)keystore\.properties$',
    '(^|/)local\.properties$',
    '\.(jks|keystore|p12|pfx|pem|key)$',
    '\.(apk|aab)$'
)

$Forbidden = @()
foreach ($Line in ($Tracked -split "`r?`n")) {
    if ([string]::IsNullOrWhiteSpace($Line)) {
        continue
    }

    foreach ($Pattern in $ForbiddenPatterns) {
        if ($Line -match $Pattern) {
            $Forbidden += $Line
            break
        }
    }
}

if ($Forbidden.Count -gt 0) {
    throw "Forbidden tracked files:`n$($Forbidden -join "`n")"
}

Write-Section "Toolchain"

$Python = Resolve-Python
Invoke-Native "Python version" $Python.FilePath ($Python.Prefix + @("--version"))
Invoke-Native "Java version" "java" @("-version")
Invoke-Native "Git version" "git" @("--version")

Invoke-Native "Control-state invariants" $Python.FilePath ($Python.Prefix + @("tools/check_automation_state.py"))

if ($Mode -eq "Audit") {
    Write-Host ""
    Write-Host "A14 AUDIT PASSED"
    exit 0
}

$VerifyMode = if ($Mode -eq "Fast") { "fast" } else { "full" }
Invoke-Native "Repository verifier ($VerifyMode)" $Python.FilePath ($Python.Prefix + @("tools/verify.py", $VerifyMode))

if ($Mode -eq "Full" -or $Mode -eq "Final") {
    Invoke-Native "Compile Python tools" $Python.FilePath ($Python.Prefix + @("-m", "compileall", "-q", "tools"))
    Invoke-Native "Python tool tests" $Python.FilePath ($Python.Prefix + @("-m", "unittest", "discover", "-s", "tools/tests", "-p", "test_*.py"))

    $Gradlew = Join-Path $RepoRoot "gradlew.bat"
    if (-not (Test-Path -LiteralPath $Gradlew -PathType Leaf)) {
        throw "gradlew.bat was not found."
    }

    Invoke-Native "Assemble debug APK" $Gradlew @("--no-daemon", ":app:assembleDebug")
    Invoke-Native "Assemble unsigned develop APK with R8" $Gradlew @("--no-daemon", ":app:assembleDevelop")

    Invoke-Native "Brutal suite hermeticity" $Python.FilePath ($Python.Prefix + @("tools/brutal_test_runner.py", "--config", "tools/brutal_test_config.json", "hermeticity"))
    Invoke-Native "Brutal suite determinism" $Python.FilePath ($Python.Prefix + @("tools/brutal_test_runner.py", "--config", "tools/brutal_test_config.json", "determinism"))
    Invoke-Native "Brutal suite mutation tests" $Python.FilePath ($Python.Prefix + @("tools/brutal_test_runner.py", "--config", "tools/brutal_test_config.json", "mutate"))
}

Write-Section "Final state"

$Status = Get-GitText @("status", "--short")
if ([string]::IsNullOrWhiteSpace($Status)) {
    Write-Host "Working tree: clean"
}
else {
    Write-Host "Working tree changes:"
    Write-Host $Status
}

if ($Mode -eq "Final") {
    if (-not [string]::IsNullOrWhiteSpace($Status)) {
        throw "Final mode requires a clean working tree."
    }

    Invoke-Native "Fetch authorized upstream" "git" @("fetch", "origin", $ExpectedBranch)

    $LocalHead = Get-GitText @("rev-parse", "HEAD")
    $RemoteHead = Get-GitText @("rev-parse", "origin/$ExpectedBranch")

    if ($LocalHead -ne $RemoteHead) {
        throw "Local HEAD '$LocalHead' does not equal authorized remote HEAD '$RemoteHead'."
    }

    Write-Host "Authorized branch synchronized: $LocalHead"
}

Write-Host ""
Write-Host "A14 VERIFICATION PASSED"
Write-Host "Mode:   $Mode"
Write-Host "Branch: $Branch"
Write-Host "HEAD:   $Head"
exit 0
