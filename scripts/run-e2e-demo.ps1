Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$springPluginDir = Join-Path $repoRoot 'spring2-plugin'
$demoServerDir = Join-Path $repoRoot 'e2e-demo\server'
$mavenRepo = Join-Path $springPluginDir '.m2repo'

function Require-Command {
    param([string]$Name)
    $command = Get-Command -Name $Name -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        throw "缺少命令: $Name"
    }
    return $command.Source
}

function Assert-PortAvailable {
    param([int]$Port)

    $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -ne $listener) {
        throw "Port $Port is already in use by PID $($listener.OwningProcess). Stop the existing demo server before starting a new one."
    }
}

$mvn = Require-Command -Name 'mvn'

Assert-PortAvailable -Port 8081

Write-Host "Repo Root: $repoRoot" -ForegroundColor Cyan
Write-Host "Maven Repo: $mavenRepo" -ForegroundColor Cyan

Write-Host ''
Write-Host '==> 安装 spring2-plugin 到本地 Maven 仓库' -ForegroundColor Cyan
Push-Location $springPluginDir
try {
    & $mvn "-Dmaven.repo.local=$mavenRepo" install
    if ($LASTEXITCODE -ne 0) {
        throw "spring2-plugin install 失败，exit=$LASTEXITCODE"
    }
} finally {
    Pop-Location
}

Write-Host ''
Write-Host '==> 启动 E2E Demo Server' -ForegroundColor Cyan
Write-Host '打开浏览器访问: http://localhost:8081/' -ForegroundColor Green

Push-Location $demoServerDir
try {
    & $mvn "-Dmaven.repo.local=$mavenRepo" spring-boot:run
    if ($LASTEXITCODE -ne 0) {
        throw "e2e-demo server 启动失败，exit=$LASTEXITCODE"
    }
} finally {
    Pop-Location
}
