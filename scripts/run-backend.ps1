# ============================================================
# Arranca un microservicio cargando las variables del .env raiz
# (incluidas las credenciales de RabbitMQ/CloudAMQP), sin exponerlas en git.
#
# Uso:
#   .\scripts\run-backend.ps1 orders-service
#   .\scripts\run-backend.ps1 users-service
#   .\scripts\run-backend.ps1 rabbitmq-admin-service
#   .\scripts\run-backend.ps1 products-service
#   .\scripts\run-backend.ps1 categories-service
#   .\scripts\run-backend.ps1 payments-service
# ============================================================
param(
    [Parameter(Mandatory = $true)]
    [string]$Service
)

$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root ".env"

if (Test-Path -LiteralPath $envFile) {
    Get-Content -LiteralPath $envFile | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith('#')) { return }
        $eq = $line.IndexOf('=')
        if ($eq -lt 1) { return }
        $key = $line.Substring(0, $eq).Trim()
        $value = $line.Substring($eq + 1).Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($key, $value, 'Process')
    }
    Write-Host "OK: variables cargadas desde $envFile" -ForegroundColor Green
} else {
    Write-Warning "No se encontro $envFile. Se usaran los valores por defecto (localhost)."
}

$pom = Join-Path $root "backend\$Service\pom.xml"
if (-not (Test-Path -LiteralPath $pom)) {
    Write-Error "No existe backend\$Service. Servicios validos: products-service, categories-service, users-service, orders-service, payments-service, rabbitmq-admin-service"
    exit 1
}

Write-Host "Arrancando $Service ..." -ForegroundColor Cyan
mvn -f $pom spring-boot:run
