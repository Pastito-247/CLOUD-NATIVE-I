# ============================================================
# Despliega RabbitMQ (1 nodo, Docker) en el EC2 del backend y,
# opcionalmente, apunta los 6 servicios systemd a 127.0.0.1.
#
# Uso:
#   .\scripts\deploy-rabbitmq-ec2.ps1 -Ec2Host 184.192.252.196
#   .\scripts\deploy-rabbitmq-ec2.ps1 -Ec2Host 184.192.252.196 -ApplyBackendConfig
#
# Requisitos:
#   - Docker + docker compose instalados en el EC2.
#   - Key pair tuki-key.pem en la raiz del repo (o -KeyPath).
# ============================================================
param(
    [Parameter(Mandatory = $true)]
    [string]$Ec2Host,

    [string]$User = "ubuntu",
    [string]$KeyPath = "",
    [string]$ComposeFile = "",
    [string]$RabbitUser = "tuki",
    [string]$RabbitPass = "tuki",
    [switch]$ApplyBackendConfig
)

$ErrorActionPreference = "Stop"

if (-not $PSScriptRoot) {
    $PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
}
$root = Split-Path -Parent $PSScriptRoot
if (-not $KeyPath) { $KeyPath = Join-Path $root "tuki-key.pem" }
if (-not $ComposeFile) { $ComposeFile = Join-Path $root "docker-compose.ec2.yml" }

if (-not (Test-Path -LiteralPath $KeyPath)) {
    Write-Error "No se encontro la key: $KeyPath"
    exit 1
}
if (-not (Test-Path -LiteralPath $ComposeFile)) {
    Write-Error "No se encontro el compose: $ComposeFile"
    exit 1
}

$remote = "$User@$Ec2Host"
$sshBase = @("-i", $KeyPath, "-o", "ConnectTimeout=15", "-o", "StrictHostKeyChecking=accept-new")

function Invoke-Remote {
    param([string]$Command)
    Write-Host ">> ssh $remote `"$Command`"" -ForegroundColor DarkGray
    & ssh @sshBase $remote $Command
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Fallo el comando remoto (exit $LASTEXITCODE)."
        exit $LASTEXITCODE
    }
}

Write-Host "1/4 Verificando Docker en el EC2..." -ForegroundColor Cyan
Invoke-Remote "command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1 || { echo 'ERROR: falta Docker o el plugin compose'; exit 1; }"

Write-Host "2/4 Copiando docker-compose.yml..." -ForegroundColor Cyan
Invoke-Remote "mkdir -p ~/rabbitmq"
& scp @sshBase $ComposeFile "${remote}:rabbitmq/docker-compose.yml"
if ($LASTEXITCODE -ne 0) {
    Write-Error "Fallo scp del compose (exit $LASTEXITCODE)."
    exit $LASTEXITCODE
}

Write-Host "3/4 Levantando RabbitMQ..." -ForegroundColor Cyan
Invoke-Remote "cd ~/rabbitmq && RABBITMQ_USERNAME='$RabbitUser' RABBITMQ_PASSWORD='$RabbitPass' docker compose up -d"
Invoke-Remote "cd ~/rabbitmq && docker compose ps"

Write-Host "4/4 Verificando Management API..." -ForegroundColor Cyan
Invoke-Remote "for i in 1 2 3 4 5 6 7 8 9 10; do curl -sf -u '${RabbitUser}:${RabbitPass}' http://127.0.0.1:15672/api/overview >/dev/null && { echo 'OK: RabbitMQ responde en 127.0.0.1:5672 / :15672'; exit 0; }; sleep 3; done; echo 'ERROR: RabbitMQ no respondio a tiempo'; exit 1"

if ($ApplyBackendConfig) {
    Write-Host "Apuntando los 6 servicios a 127.0.0.1:5672..." -ForegroundColor Cyan
    $services = @("products", "categories", "users", "orders", "rabbit-admin", "payments")
    $lines = @(
        "[Service]",
        "Environment=RABBITMQ_HOST=127.0.0.1",
        "Environment=RABBITMQ_PORT=5672",
        "Environment=RABBITMQ_SSL=false",
        "Environment=RABBITMQ_USERNAME=$RabbitUser",
        "Environment=RABBITMQ_PASSWORD=$RabbitPass",
        "Environment=RABBITMQ_VHOST=/"
    )
    $printfArgs = ($lines | ForEach-Object { "'$_'" }) -join " "

    foreach ($svc in $services) {
        $dir = "/etc/systemd/system/tuki-$svc.service.d"
        $cmd = "sudo mkdir -p $dir && printf '%s\n' $printfArgs | sudo tee $dir/override-rabbitmq.conf >/dev/null"
        Invoke-Remote $cmd
    }

    $mgmtLines = @(
        "[Service]",
        "Environment=RABBITMQ_MANAGEMENT_ENABLED=true",
        "Environment=RABBITMQ_MANAGEMENT_URL=http://127.0.0.1:15672"
    )
    $mgmtArgs = ($mgmtLines | ForEach-Object { "'$_'" }) -join " "
    Invoke-Remote "sudo mkdir -p /etc/systemd/system/tuki-rabbit-admin.service.d && printf '%s\n' $mgmtArgs | sudo tee /etc/systemd/system/tuki-rabbit-admin.service.d/override-management.conf >/dev/null"

    Invoke-Remote "sudo systemctl daemon-reload && sudo systemctl restart tuki-products tuki-categories tuki-users tuki-orders tuki-rabbit-admin tuki-payments && systemctl is-active tuki-products tuki-categories tuki-users tuki-orders tuki-rabbit-admin tuki-payments"
}

Write-Host "Listo. Management UI:" -ForegroundColor Green
Write-Host "  Directo:  http://${Ec2Host}:15672  ($RabbitUser / $RabbitPass)  [abrir 15672 en el Security Group solo para tu IP]" -ForegroundColor Green
Write-Host "  Sin abrir puertos (tunel SSH):" -ForegroundColor Green
Write-Host "    ssh -i $KeyPath -L 15672:127.0.0.1:15672 $remote" -ForegroundColor Green
Write-Host "    -> http://localhost:15672  ($RabbitUser / $RabbitPass)" -ForegroundColor Green
