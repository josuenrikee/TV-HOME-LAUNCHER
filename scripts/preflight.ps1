# Verifica en 1 minuto si este PC sirve para el proceso. No toca el TV ni instala nada permanente.
# Uso: powershell -ExecutionPolicy Bypass -File scripts\preflight.ps1 [-TvHost 192.168.1.50]
param([string]$TvHost = '')
. "$PSScriptRoot\_common.ps1"
$ErrorActionPreference = 'Continue'
$results = @()
function Add-Check([string]$name, [bool]$ok, [string]$detail) {
    $script:results += [pscustomobject]@{ Prueba = $name; Resultado = $(if ($ok) { 'SI' } else { 'NO' }); Detalle = $detail }
}

Add-Check 'PowerShell 5.1 o superior' ($PSVersionTable.PSVersion.Major -ge 5) ("versión $($PSVersionTable.PSVersion)")

$py = Find-Python
if ($py) {
    $ver = (& $py.Exe @($py.Args) --version 2>&1 | Out-String).Trim()
    Add-Check 'Python 3.9 o superior' $true $ver
} else {
    Add-Check 'Python 3.9 o superior' $false 'no encontrado; se puede instalar solo para tu usuario sin administrador'
}

foreach ($h in @('github.com', 'pypi.org', 'files.pythonhosted.org')) {
    $ok = Test-TcpPort $h 443 4000
    Add-Check "Internet hacia $h" $ok $(if ($ok) { 'responde' } else { 'bloqueado o sin red' })
}

# Probar que se puede crear un entorno virtual en una carpeta temporal del usuario.
if ($py) {
    $tmp = Join-Path $env:TEMP ('tvprobe_' + [guid]::NewGuid().ToString('N').Substring(0, 8))
    try {
        & $py.Exe @($py.Args) -m venv $tmp 2>&1 | Out-Null
        $vp = Join-Path $tmp 'Scripts\python.exe'
        $okVenv = Test-Path $vp
        Add-Check 'Crear entorno virtual de Python' $okVenv $(if ($okVenv) { 'funciona' } else { 'falló (política de la empresa)' })
        if ($okVenv) {
            $o = & $vp -m pip download --quiet --disable-pip-version-check --no-deps -d (Join-Path $tmp 'dl') adb-shell==0.4.4 2>&1 | Out-String
            $okPip = ($LASTEXITCODE -eq 0)
            Add-Check 'Descargar paquetes con pip' $okPip $(if ($okPip) { 'funciona' } else { ($o.Trim() -split "`n" | Select-Object -Last 1) })
        }
    } catch {
        Add-Check 'Crear entorno virtual de Python' $false $_.Exception.Message
    } finally {
        Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
    }
}

# VPN o adaptadores que suelen impedir ver al TV en la red local.
$vpn = Get-NetAdapter -ErrorAction SilentlyContinue | Where-Object { $_.Status -eq 'Up' -and ($_.InterfaceDescription -match 'VPN|TAP|WireGuard|Cisco|Fortinet|Palo Alto|Pulse|GlobalProtect|Zscaler') }
if ($vpn) { Add-Check 'Sin VPN activa' $false ("activo: " + (($vpn | ForEach-Object { $_.Name }) -join ', ') + '; desconéctala al llegar a casa') }
else { Add-Check 'Sin VPN activa' $true 'no se ve ninguna' }

if ($TvHost) {
    $ok = Test-TcpPort $TvHost 5555 4000
    Add-Check "TV $TvHost responde en el puerto 5555" $ok $(if ($ok) { 'depuración por red activa' } else { 'apagado, otra red, o la depuración por red no está activa' })
}

$results | Format-Table -AutoSize -Wrap | Out-String | Write-Host
$bad = @($results | Where-Object { $_.Resultado -eq 'NO' -and $_.Prueba -notlike 'Sin VPN*' -and $_.Prueba -notlike 'TV *' })
if ($bad.Count -eq 0) { Write-Host 'RESULTADO: SI, este PC sirve.'; exit 0 }
Write-Host 'RESULTADO: NO todavía. Falta:'
$bad | ForEach-Object { Write-Host (' - ' + $_.Prueba + ': ' + $_.Detalle) }
exit 1
