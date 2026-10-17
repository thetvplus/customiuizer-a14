<#
.SYNOPSIS
    Run isolated signing-preflight regression checks, without real keys.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$engine = (Get-Process -Id $PID).Path
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ("a14-signing-test-" + [Guid]::NewGuid().ToString("N"))
$scriptPath = Join-Path $fixtureRoot "scripts/check-signing-config.ps1"
$environmentKeys = @("GRADLE_USER_HOME", "CUSTOMIUIZER_A14_KEYSTORE_PROPERTIES", "ORG_GRADLE_PROJECT_customiuizerA14KeystoreProperties")
$savedEnvironment = @{}
foreach ($key in $environmentKeys) {
    $savedEnvironment[$key] = [Environment]::GetEnvironmentVariable($key)
}
$checks = 0

function Assert-Preflight([int]$ExpectedExit, [string]$ExpectedText, [string[]]$Arguments = @()) {
    $output = & $engine -NoProfile -File $scriptPath @Arguments 2>&1 | Out-String
    if ($LASTEXITCODE -ne $ExpectedExit -or -not $output.Contains($ExpectedText)) {
        throw "Preflight case failed: expected exit $ExpectedExit and '$ExpectedText'; got exit $LASTEXITCODE"
    }
    if ($output.Contains("fixture-secret") -or $output.Contains("fixture-alias")) {
        throw "Signing preflight exposed a field value"
    }
    $script:checks++
}

try {
    New-Item -ItemType Directory -Path (Join-Path $fixtureRoot "scripts"), (Join-Path $fixtureRoot "app"), (Join-Path $fixtureRoot "gradle-home") | Out-Null
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot "check-signing-config.ps1") -Destination $scriptPath
    foreach ($key in $environmentKeys) { Remove-Item -LiteralPath ("Env:" + $key) -ErrorAction SilentlyContinue }
    $env:GRADLE_USER_HOME = Join-Path $fixtureRoot "gradle-home"
    Assert-Preflight 0 "disabled"
    Assert-Preflight 1 "disabled" @("-RequireSigning")
    Assert-Preflight 1 "properties file not found" @("-KeystoreProperties", "missing.properties")

    $properties = Join-Path $fixtureRoot "app/signing.properties"
    $store = Join-Path $fixtureRoot "app/fixture key.store"
    [IO.File]::WriteAllText($store, "test fixture only")
    $valid = "storeFile=fixture key.store`nstorePassword=fixture-secret`nkeyAlias=fixture-alias`nkeyPassword=fixture-secret`n"
    [IO.File]::WriteAllText($properties, $valid)
    Assert-Preflight 0 "configured" @("-RequireSigning", "-KeystoreProperties", "signing.properties")
    # Both relative paths follow app/, even when properties are elsewhere.
    $externalProperties = Join-Path $fixtureRoot "external.properties"
    [IO.File]::WriteAllText($externalProperties, $valid)
    Assert-Preflight 0 "configured" @("-KeystoreProperties", $externalProperties)

    [IO.File]::WriteAllText($properties, "storeFile=missing.store`nstorePassword=fixture-secret`nkeyAlias=fixture-alias`nkeyPassword=fixture-secret`n")
    Assert-Preflight 1 "keystore file not found" @("-KeystoreProperties", $properties)
    [IO.File]::WriteAllText($properties, "storeFile=fixture key.store`nstorePassword=fixture-secret`nkeyAlias=fixture-alias`n")
    Assert-Preflight 1 "missing fields: keyPassword" @("-KeystoreProperties", $properties)
    # Java properties support ':' separators, Unicode escapes and continuations.
    [IO.File]::WriteAllText($properties, "storeFile:fixture\u0020key\`n  .store`nstorePassword fixture-secret`nkeyAlias=fixture-alias`nkeyPassword=fixture-secret`n")
    Assert-Preflight 0 "configured" @("-KeystoreProperties", $properties)
    [IO.File]::WriteAllText($properties, "storeFile=\uZZZZ`nstorePassword=fixture-secret`nkeyAlias=fixture-alias`nkeyPassword=fixture-secret`n")
    Assert-Preflight 1 "could not be read" @("-KeystoreProperties", $properties)

    [IO.File]::WriteAllText($properties, $valid)
    $env:CUSTOMIUIZER_A14_KEYSTORE_PROPERTIES = $properties
    Assert-Preflight 0 "CUSTOMIUIZER_A14_KEYSTORE_PROPERTIES"
    $env:ORG_GRADLE_PROJECT_customiuizerA14KeystoreProperties = $properties
    $env:CUSTOMIUIZER_A14_KEYSTORE_PROPERTIES = "missing.properties"
    Assert-Preflight 0 "Gradle project environment property"
    [IO.File]::WriteAllText((Join-Path $fixtureRoot "gradle-home/gradle.properties"), "customiuizerA14KeystoreProperties=signing.properties`n")
    $env:ORG_GRADLE_PROJECT_customiuizerA14KeystoreProperties = "missing.properties"
    Assert-Preflight 0 "Gradle user property"
    Assert-Preflight 1 "properties file not found" @("-KeystoreProperties", "missing.properties")
    Write-Output "Signing preflight: $checks isolated checks passed."
} finally {
    foreach ($key in $environmentKeys) {
        if ($null -eq $savedEnvironment[$key]) {
            Remove-Item -LiteralPath ("Env:" + $key) -ErrorAction SilentlyContinue
        } else {
            Set-Item -LiteralPath ("Env:" + $key) -Value $savedEnvironment[$key]
        }
    }
    $resolvedFixture = [IO.Path]::GetFullPath($fixtureRoot)
    $resolvedTemp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    if (-not $resolvedFixture.StartsWith($resolvedTemp, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Fixture cleanup escaped the temporary directory"
    }
    Remove-Item -LiteralPath $resolvedFixture -Recurse -Force
}
