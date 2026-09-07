# ============================================================
# Lanza users-service cargando las variables del .env de la raiz.
#
# Uso (desde backend/users-service):
#   powershell -ExecutionPolicy Bypass -File run.ps1
# ============================================================

$ErrorActionPreference = 'Stop'

# Ruta al .env (raiz del repo)
$envFile = Join-Path (Resolve-Path "../../") ".env"

if (-not (Test-Path $envFile)) {
    Write-Error "No se encontro el archivo .env en la raiz del proyecto."
    exit 1
}

# Cargar el .env y exportar las variables de entorno del proceso
foreach ($line in Get-Content $envFile) {
    $trimmed = $line.Trim()
    if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith("#")) { continue }
    if ($trimmed -match "^(?<key>[^=]+)=(?<value>.*)$") {
        $key = $matches['key'].Trim()
        $value = $matches['value'].Trim()
        # Quitar comillas
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($key, $value, 'Process')
    }
}

Write-Host "Variables de .env cargadas. Lanzando users-service..." -ForegroundColor Green
mvn spring-boot:run
