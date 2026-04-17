param(
    [switch]$SkipSpring,
    [switch]$SkipVanillaJs,
    [switch]$SkipVue3,
    [switch]$SkipE2EDemo,
    [switch]$SkipFlutter,
    [switch]$IncludeFlutterAnalyze,
    [switch]$AllowMissingTools,
    [switch]$ContinueOnError
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$results = New-Object System.Collections.Generic.List[object]

function Write-Section {
    param([string]$Message)
    Write-Host ''
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Add-Result {
    param(
        [string]$Module,
        [string]$Status,
        [string]$Details
    )

    $results.Add([pscustomobject]@{
            Module  = $Module
            Status  = $Status
            Details = $Details
        })
}

function Get-CommandPathOrNull {
    param([string]$Name)

    $command = Get-Command -Name $Name -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        return $null
    }
    return $command.Source
}

function Invoke-NativeStep {
    param(
        [string]$Module,
        [string]$Executable,
        [string[]]$Arguments,
        [string]$WorkingDirectory,
        [string]$DisplayName
    )

    Write-Section $DisplayName
    Push-Location $WorkingDirectory
    try {
        & $Executable @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "$Module failed with exit code $LASTEXITCODE"
        }
        Add-Result -Module $Module -Status 'PASS' -Details $DisplayName
    } catch {
        Add-Result -Module $Module -Status 'FAIL' -Details $_.Exception.Message
        if (-not $ContinueOnError) {
            throw
        }
    } finally {
        Pop-Location
    }
}

function Skip-OrFail-MissingTool {
    param(
        [string]$Module,
        [string]$ToolName,
        [string]$Reason
    )

    if ($AllowMissingTools) {
        Add-Result -Module $Module -Status 'SKIP' -Details "$ToolName missing: $Reason"
        return $true
    }

    throw "$Module requires '$ToolName'. $Reason"
}

Write-Section 'Repository Preflight'
Write-Host "Repo Root: $repoRoot"

$nodePath = Get-CommandPathOrNull -Name 'node'
$mavenPath = Get-CommandPathOrNull -Name 'mvn'
$flutterPath = Get-CommandPathOrNull -Name 'flutter'

Write-Host "node:    $(if ($nodePath) { $nodePath } else { 'missing' })"
Write-Host "mvn:     $(if ($mavenPath) { $mavenPath } else { 'missing' })"
Write-Host "flutter: $(if ($flutterPath) { $flutterPath } else { 'missing' })"

if (-not $SkipSpring) {
    if ($null -eq $mavenPath) {
        if (-not (Skip-OrFail-MissingTool -Module 'spring2-plugin' -ToolName 'mvn' -Reason 'Install Maven and ensure mvn is in PATH.')) {
            # no-op
        }
    } else {
        Invoke-NativeStep `
            -Module 'spring2-plugin' `
            -Executable $mavenPath `
            -Arguments @('-Dmaven.repo.local=.m2repo', 'test') `
            -WorkingDirectory (Join-Path $repoRoot 'spring2-plugin') `
            -DisplayName 'spring2-plugin: mvn -Dmaven.repo.local=.m2repo test'
    }
} else {
    Add-Result -Module 'spring2-plugin' -Status 'SKIP' -Details 'Skipped by parameter'
}

if (-not $SkipVanillaJs) {
    if ($null -eq $nodePath) {
        if (-not (Skip-OrFail-MissingTool -Module 'vanilla-js-plugin' -ToolName 'node' -Reason 'Install Node.js and ensure node is in PATH.')) {
            # no-op
        }
    } else {
        Invoke-NativeStep `
            -Module 'vanilla-js-plugin' `
            -Executable $nodePath `
            -Arguments @('tests/transfer-encrypt.native.test.js') `
            -WorkingDirectory (Join-Path $repoRoot 'vanilla-js-plugin') `
            -DisplayName 'vanilla-js-plugin: node tests/transfer-encrypt.native.test.js'
    }
} else {
    Add-Result -Module 'vanilla-js-plugin' -Status 'SKIP' -Details 'Skipped by parameter'
}

if (-not $SkipVue3) {
    if ($null -eq $nodePath) {
        if (-not (Skip-OrFail-MissingTool -Module 'vue3-plugin' -ToolName 'node' -Reason 'Install Node.js and ensure node is in PATH.')) {
            # no-op
        }
    } else {
        Invoke-NativeStep `
            -Module 'vue3-plugin' `
            -Executable $nodePath `
            -Arguments @('tests/transfer-encrypt-vue3.native.test.mjs') `
            -WorkingDirectory (Join-Path $repoRoot 'vue3-plugin') `
            -DisplayName 'vue3-plugin: node tests/transfer-encrypt-vue3.native.test.mjs'
    }
} else {
    Add-Result -Module 'vue3-plugin' -Status 'SKIP' -Details 'Skipped by parameter'
}

if (-not $SkipE2EDemo) {
    if ($null -eq $mavenPath) {
        if (-not (Skip-OrFail-MissingTool -Module 'e2e-demo' -ToolName 'mvn' -Reason 'Install Maven and ensure mvn is in PATH.')) {
            # no-op
        }
    } else {
        Invoke-NativeStep `
            -Module 'e2e-demo' `
            -Executable $mavenPath `
            -Arguments @('-Dmaven.repo.local=..\..\spring2-plugin\.m2repo', 'test') `
            -WorkingDirectory (Join-Path $repoRoot 'e2e-demo\server') `
            -DisplayName 'e2e-demo/server: mvn -Dmaven.repo.local=..\..\spring2-plugin\.m2repo test'
    }
} else {
    Add-Result -Module 'e2e-demo' -Status 'SKIP' -Details 'Skipped by parameter'
}

if (-not $SkipFlutter) {
    if ($null -eq $flutterPath) {
        if (-not (Skip-OrFail-MissingTool -Module 'flutter-plugin' -ToolName 'flutter' -Reason 'Install Flutter SDK and ensure flutter is in PATH.')) {
            # no-op
        }
    } else {
        $flutterPluginDir = Join-Path $repoRoot 'flutter-plugin'
        $flutterExampleDir = Join-Path $flutterPluginDir 'example'

        Invoke-NativeStep `
            -Module 'flutter-plugin' `
            -Executable $flutterPath `
            -Arguments @('pub', 'get') `
            -WorkingDirectory $flutterPluginDir `
            -DisplayName 'flutter-plugin: flutter pub get'

        Invoke-NativeStep `
            -Module 'flutter-plugin' `
            -Executable $flutterPath `
            -Arguments @('test') `
            -WorkingDirectory $flutterPluginDir `
            -DisplayName 'flutter-plugin: flutter test'

        if ($IncludeFlutterAnalyze) {
            Invoke-NativeStep `
                -Module 'flutter-plugin' `
                -Executable $flutterPath `
                -Arguments @('analyze') `
                -WorkingDirectory $flutterPluginDir `
                -DisplayName 'flutter-plugin: flutter analyze'
        }

        Invoke-NativeStep `
            -Module 'flutter-plugin-example' `
            -Executable $flutterPath `
            -Arguments @('pub', 'get') `
            -WorkingDirectory $flutterExampleDir `
            -DisplayName 'flutter-plugin/example: flutter pub get'
    }
} else {
    Add-Result -Module 'flutter-plugin' -Status 'SKIP' -Details 'Skipped by parameter'
}

Write-Section 'Integration Summary'
$results | Format-Table -AutoSize | Out-String | Write-Host

$failed = @($results | Where-Object { $_.Status -eq 'FAIL' })
if ($failed.Count -gt 0) {
    throw "Integration script completed with $($failed.Count) failed module(s)."
}

$skipped = @($results | Where-Object { $_.Status -eq 'SKIP' })
if ($skipped.Count -gt 0) {
    Write-Host "Completed with $($skipped.Count) skipped module(s)." -ForegroundColor Yellow
} else {
    Write-Host 'All integration steps passed.' -ForegroundColor Green
}
