param(
    [string]$Output = ""
)

$root = Split-Path -Parent $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($Output)) {
    $Output = Join-Path $root "TUKI-TECH-EFT.zip"
}
$Output = [System.IO.Path]::GetFullPath($Output)

$files = @(git -C $root ls-files --cached --others --exclude-standard)
if ($LASTEXITCODE -ne 0 -or $files.Count -eq 0) {
    Write-Error "No se pudo obtener la lista de archivos desde git."
    exit 1
}

$excluded = '(?i)((^|[\\/])\.env$|(^|[\\/])([^\\/]+\.(pem|key|ppk|p12|pfx))$|(^|[\\/])target[\\/]|(^|[\\/])dist[\\/]|(^|[\\/])node_modules[\\/]|(^|[\\/])\.angular[\\/])'
$files = @($files | Where-Object { $_ -notmatch $excluded })

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$stream = [System.IO.File]::Open($Output, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
$zip = [System.IO.Compression.ZipArchive]::new($stream, [System.IO.Compression.ZipArchiveMode]::Create)
$written = 0

try {
    foreach ($file in $files) {
        $source = Join-Path $root $file
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
            continue
        }

        $entry = $zip.CreateEntry(($file -replace '\\', '/'), [System.IO.Compression.CompressionLevel]::Optimal)
        $entryStream = $entry.Open()
        $input = [System.IO.File]::OpenRead($source)
        try {
            $input.CopyTo($entryStream)
            $written++
        }
        finally {
            $input.Dispose()
            $entryStream.Dispose()
        }
    }
}
finally {
    $zip.Dispose()
    $stream.Dispose()
}

Write-Host "OK: paquete generado en $Output" -ForegroundColor Green
Write-Host "Archivos incluidos: $written" -ForegroundColor Green
