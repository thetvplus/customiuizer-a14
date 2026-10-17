#!/usr/bin/env powershell
<#
.SYNOPSIS
    Check A14 signing configuration without printing secrets.
.DESCRIPTION
    Mirrors Gradle's app-project-relative paths. No configuration is allowed for
    ordinary development; use -RequireSigning for a formal release preflight.
    This checks fields and file presence, not the passwords or certificate.
#>
[CmdletBinding()]
param(
    [string]$KeystoreProperties,
    [switch]$RequireSigning
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$AppRoot = Join-Path $RepoRoot "app"

function ConvertFrom-PropertyEscape([string]$Value) {
    # java.util.Properties.load(InputStream): Unicode and standard escapes;
    # an unknown escape discards the backslash, including in Windows paths.
    return [regex]::Replace($Value, '\\(u.{0,4}|.)', {
        param($Match)
        $token = $Match.Groups[1].Value
        if ($token.StartsWith('u')) {
            if ($token -notmatch '^u[0-9a-fA-F]{4}$') {
                throw "Invalid Unicode escape in properties file"
            }
            return [string][char][Convert]::ToInt32($token.Substring(1), 16)
        }
        switch ($token) {
            't' { return "`t" }
            'n' { return "`n" }
            'r' { return "`r" }
            'f' { return "`f" }
            default { return $token }
        }
    })
}

function Read-JavaProperties([string]$Path) {
    $properties = [System.Collections.Generic.Dictionary[string, string]]::new([StringComparer]::Ordinal)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $properties
    }
    $pending = ""
    # The final empty line flushes a continuation at EOF, as Properties.load does.
    foreach ($physical in ([IO.File]::ReadAllLines($Path, [Text.Encoding]::GetEncoding(28591)) + @(""))) {
        $line = $pending + $physical.TrimStart([char[]]" `t`f")
        $pending = ""
        if ($line.Length -eq 0 -or $line[0] -in '#', '!') {
            continue
        }
        $trailingSlashes = [regex]::Match($line, '\\+$').Length
        if ($trailingSlashes % 2 -eq 1) {
            $pending = $line.Substring(0, $line.Length - 1)
            continue
        }
        if ($line -match '^(?<key>(?:\\.|[^:= \t\f])*)(?:[ \t\f]*[:=][ \t\f]*|[ \t\f]+)?(?<value>.*)$') {
            $key = ConvertFrom-PropertyEscape $matches['key']
            $value = ConvertFrom-PropertyEscape $matches['value']
            $properties[$key] = $value
        }
    }
    return $properties
}

function Resolve-AppPath([string]$Path) {
    if ([IO.Path]::IsPathRooted($Path)) {
        return [IO.Path]::GetFullPath($Path)
    }
    return [IO.Path]::GetFullPath((Join-Path $AppRoot $Path))
}

try {
    $propertiesSource = "explicit -KeystoreProperties"
    $propertiesPath = $KeystoreProperties
    if (-not $PSBoundParameters.ContainsKey('KeystoreProperties')) {
        $gradleUserHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE ".gradle" }
        $userProperties = Read-JavaProperties (Join-Path $gradleUserHome "gradle.properties")
        $projectProperties = Read-JavaProperties (Join-Path $RepoRoot "gradle.properties")
        $key = "customiuizerA14KeystoreProperties"
        if ($userProperties.ContainsKey($key)) {
            $propertiesPath = $userProperties[$key]
            $propertiesSource = "Gradle user property"
        } elseif ($projectProperties.ContainsKey($key)) {
            $propertiesPath = $projectProperties[$key]
            $propertiesSource = "Gradle project property"
        } elseif ($null -ne $env:ORG_GRADLE_PROJECT_customiuizerA14KeystoreProperties) {
            $propertiesPath = $env:ORG_GRADLE_PROJECT_customiuizerA14KeystoreProperties
            $propertiesSource = "Gradle project environment property"
        } else {
            $propertiesPath = $env:CUSTOMIUIZER_A14_KEYSTORE_PROPERTIES
            $propertiesSource = "CUSTOMIUIZER_A14_KEYSTORE_PROPERTIES"
        }
    }

    if ($null -eq $propertiesPath -and -not $PSBoundParameters.ContainsKey('KeystoreProperties')) {
        Write-Output "Signing: disabled (no properties source)"
        if ($RequireSigning) { exit 1 }
        exit 0
    }
    if ([string]::IsNullOrEmpty($propertiesPath)) {
        Write-Output "Signing: invalid (empty properties path)"
        exit 1
    }

    Write-Output "Properties source: $propertiesSource"
    $propertiesFile = Resolve-AppPath $propertiesPath
    if (-not (Test-Path -LiteralPath $propertiesFile -PathType Leaf)) {
        Write-Output "Signing: invalid (properties file not found)"
        exit 1
    }

    $properties = Read-JavaProperties $propertiesFile
    $required = @("storeFile", "storePassword", "keyAlias", "keyPassword")
    $missing = @($required | Where-Object { -not $properties.ContainsKey($_) -or [string]::IsNullOrEmpty($properties[$_]) })
    if ($missing.Count -ne 0) {
        Write-Output "Signing: invalid (missing fields: $($missing -join ', '))"
        exit 1
    }
    if (-not (Test-Path -LiteralPath (Resolve-AppPath $properties['storeFile']) -PathType Leaf)) {
        Write-Output "Signing: invalid (keystore file not found)"
        exit 1
    }

    Write-Output "Signing: configured (fields and files present)"
    exit 0
} catch {
    # A parser exception can contain the input line. Never echo secret values.
    Write-Output "Signing: invalid (configuration could not be read)"
    exit 1
}
