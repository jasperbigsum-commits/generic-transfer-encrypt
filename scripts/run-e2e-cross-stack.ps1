Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$springPluginDir = Join-Path $repoRoot 'spring2-plugin'
$demoServerDir = Join-Path $repoRoot 'e2e-demo\server'
$probeScript = Join-Path $repoRoot 'e2e-demo\tests\client-cross-stack-probe.mjs'
$mavenRepo = Join-Path $springPluginDir '.m2repo'
$baseUrl = 'http://localhost:8081'
$demoJar = Join-Path $demoServerDir 'target\generic-transfer-encrypt-e2e-demo-1.0.0.jar'
$stdoutLog = Join-Path $demoServerDir 'target\e2e-cross-stack.out.log'
$stderrLog = Join-Path $demoServerDir 'target\e2e-cross-stack.err.log'

function Require-Command {
    param([string]$Name)
    $command = Get-Command -Name $Name -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        throw "Missing command: $Name"
    }
    return $command.Source
}

function Assert-PortAvailable {
    param([int]$Port)

    $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -ne $listener) {
        throw "Port $Port is already in use by PID $($listener.OwningProcess). Stop the existing demo server before running the cross-stack probe."
    }
}

$mvn = Require-Command -Name 'mvn'
$node = Require-Command -Name 'node'
$java = Require-Command -Name 'java'

Assert-PortAvailable -Port 8081

Write-Host "Repo Root: $repoRoot" -ForegroundColor Cyan
Write-Host "Base URL:  $baseUrl" -ForegroundColor Cyan

Push-Location $springPluginDir
try {
    Write-Host ''
    Write-Host '==> Install spring2-plugin into local Maven repo' -ForegroundColor Cyan
    & $mvn "-Dmaven.repo.local=$mavenRepo" install
    if ($LASTEXITCODE -ne 0) {
        throw "spring2-plugin install failed, exit=$LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

Push-Location $demoServerDir
try {
    Write-Host ''
    Write-Host '==> Build e2e-demo/server' -ForegroundColor Cyan
    & $mvn "-Dmaven.repo.local=$mavenRepo" -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        throw "e2e-demo package failed, exit=$LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

if (Test-Path -LiteralPath $stdoutLog) {
    Remove-Item -LiteralPath $stdoutLog -Force
}
if (Test-Path -LiteralPath $stderrLog) {
    Remove-Item -LiteralPath $stderrLog -Force
}

Write-Host ''
Write-Host '==> Start e2e-demo/server' -ForegroundColor Cyan
$proc = Start-Process -FilePath $java `
    -ArgumentList @('-jar', $demoJar) `
    -WorkingDirectory $demoServerDir `
    -RedirectStandardOutput $stdoutLog `
    -RedirectStandardError $stderrLog `
    -PassThru

try {
    $ready = $false
    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep -Seconds 1
        try {
            $resp = Invoke-WebRequest -UseBasicParsing "$baseUrl/demo/public-key"
            if ($resp.StatusCode -eq 200) {
                $ready = $true
                break
            }
        }
        catch {
        }
    }

    if (-not $ready) {
        Write-Host '--- E2E STDOUT ---' -ForegroundColor Yellow
        if (Test-Path -LiteralPath $stdoutLog) {
            Get-Content -Raw $stdoutLog | Write-Host
        }
        Write-Host '--- E2E STDERR ---' -ForegroundColor Yellow
        if (Test-Path -LiteralPath $stderrLog) {
            Get-Content -Raw $stderrLog | Write-Host
        }
        throw 'e2e-demo/server did not become ready in time'
    }

    Write-Host ''
    Write-Host '==> Run cross-stack client probe' -ForegroundColor Cyan
    & $node $probeScript $baseUrl
    if ($LASTEXITCODE -ne 0) {
        throw "client cross-stack probe failed, exit=$LASTEXITCODE"
    }
}
finally {
    if ($proc -and -not $proc.HasExited) {
        Stop-Process -Id $proc.Id -Force
    }
}

Write-Host ''
Write-Host 'Cross-stack E2E probe passed.' -ForegroundColor Green
