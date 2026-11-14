# Dify 启动脚本 - 重启电脑让 WSL2 生效后运行此脚本
$env:PATH += ";D:\Docker\resources\bin"

Write-Host "=== Starting Docker Desktop ===" -ForegroundColor Cyan
Start-Process "D:\Docker\Docker Desktop.exe"

Write-Host "=== Waiting for Docker engine (max 300s) ===" -ForegroundColor Cyan
$ready = $false
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep 5
    $info = docker info 2>&1 | Out-String
    if ($info -match "Server Version") {
        $ready = $true
        Write-Host "Docker engine ready!" -ForegroundColor Green
        break
    }
    Write-Host "  waiting... ($($i * 5)s)"
}
if (-not $ready) {
    Write-Host "Docker engine not ready. Open Docker Desktop UI and check WSL2." -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host "=== Starting Dify (docker compose up -d) ===" -ForegroundColor Cyan
Set-Location "D:\dify\docker"
docker compose up -d 2>&1 | Select-Object -Last 30

Write-Host "=== Waiting 40s for containers to initialize ===" -ForegroundColor Cyan
Start-Sleep 40
Write-Host "=== Container status ===" -ForegroundColor Cyan
docker compose ps 2>&1

Write-Host ""
Write-Host "=== Dify ready ===" -ForegroundColor Green
Write-Host "Open http://localhost in your browser to access Dify"
Write-Host "First visit: set admin account & password"
Read-Host "Press Enter to exit"
