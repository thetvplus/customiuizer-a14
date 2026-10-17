#!/usr/bin/env powershell
<#
.SYNOPSIS
    Check A14 signing configuration without printing secrets.
.DESCRIPTION
    Verifies that the Gradle property customiuizerA14KeystoreProperties or the
    environment variable CUSTOMIUIZER_A14_KEYSTORE_PROPERTIES points to a valid
    keystore.properties file and that the referenced storeFile exists.
#>
[CmdletBinding()]
param()

try {
    $ErrorActionPreference = "Stop"

    $RepoRoot = (git rev-parse --show-toplevel) | Out-String | ForEach-Object { $_.Trim() }

    $GradlePropertiesPath = [System.IO.Path]::Combine($env:USERPROFILE, ".gradle", "gradle.properties")

    $propertiesSource = $null
    $propertiesPath = $null

    function Get-GradleProperty($path, $name) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            return $null
        }
        foreach ($line in (Get-Content -LiteralPath $path -Encoding UTF8)) {
            if ($line -match "^\s*$([regex]::Escape($name))\s*=\s*(.+)\s*$") {
                return $matches[1].Trim()
            }
        }
        return $null
    }

    # 1. Gradle property in ~/.gradle/gradle.properties
    if (Test-Path -LiteralPath $GradlePropertiesPath -PathType Leaf) {
        $propertiesPath = Get-GradleProperty $GradlePropertiesPath "customiuizerA14KeystoreProperties"
        if ($propertiesPath) {
            $propertiesSource = "Gradle property customiuizerA14KeystoreProperties"
        }
    }

    # 2. Environment variable
    if (-not $propertiesPath) {
        $propertiesPath = $env:CUSTOMIUIZER_A14_KEYSTORE_PROPERTIES
        if ($propertiesPath) {
            $propertiesSource = "Environment variable CUSTOMIUIZER_A14_KEYSTORE_PROPERTIES"
        }
    }

    Write-Output "Properties source: $propertiesSource"
    Write-Output "Properties path: $propertiesPath"

    if (-not $propertiesPath) {
        Write-Output "Signing: disabled (no properties source)"
        exit 0
    }

    $propertiesFile = $propertiesPath | Resolve-Path -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Path
    if (-not $propertiesFile) {
        $propertiesFile = Join-Path $RepoRoot $propertiesPath
    }

    Write-Output "Properties file exists: $(Test-Path -LiteralPath $propertiesFile -PathType Leaf)"

    if (-not (Test-Path -LiteralPath $propertiesFile -PathType Leaf)) {
        Write-Output "Signing: disabled (properties file not found)"
        exit 0
    }

    $properties = @{
    }
    foreach ($line in (Get-Content -LiteralPath $propertiesFile -Encoding UTF8)) {
        if ($line -match "^\s*([^#\s=][^=]*)\s*=\s*(.+)\s*$") {
            $properties[$matches[1].Trim()] = $matches[2].Trim()
        }
    }

    $required = @("storeFile", "storePassword", "keyAlias", "keyPassword")
    $present = @()
    $missing = @()
    foreach ($key in $required) {
        if ($properties.ContainsKey($key) -and -not [string]::IsNullOrWhiteSpace($properties[$key])) {
            $present += $key
        } else {
            $missing += $key
        }
    }

    Write-Output "Required fields present: $present"
    Write-Output "Required fields missing: $missing"

    $storeFile = $properties["storeFile"]
    $storeFilePath = $null
    $storeFileExists = $false
    if ($storeFile) {
        if ([System.IO.Path]::IsPathRooted($storeFile)) {
            $storeFilePath = $storeFile
        } else {
            $storeFilePath = Join-Path (Split-Path -Parent $propertiesFile) $storeFile
        }
        $resolved = $storeFilePath | Resolve-Path -ErrorAction SilentlyContinue
        if ($resolved) {
            $storeFilePath = $resolved.Path
        }
        $storeFileExists = Test-Path -LiteralPath $storeFilePath -PathType Leaf
    }

    Write-Output "storeFile path: $storeFilePath"
    Write-Output "storeFile exists: $storeFileExists"

    if ($present.Count -eq $required.Count -and $storeFileExists) {
        Write-Output "Signing: enabled"
        exit 0
    }

    Write-Output "Signing: disabled (incomplete configuration)"
    exit 0
} catch {
    Write-Output "ERROR: $_"
    exit 1
}
