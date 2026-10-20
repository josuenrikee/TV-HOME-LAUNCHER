# Cierra la depuración por red del TV y comprueba que el puerto 5555 quedó cerrado.
# Uso: scripts\finish.ps1 -TvHost 192.168.1.50
param([Parameter(Mandatory = $true)][string]$TvHost)
. "$PSScriptRoot\_common.ps1"
$env:TV_HOST = $TvHost
try { Invoke-TvBatch @('settings put global adb_wifi_enabled 0', 'settings put global adb_enabled 0') | Out-Null } catch { }
Start-Sleep -Seconds 4
$py = Get-VenvPython
& $py -I (Join-Path $script:Root 'tools\adb-closed.py') $TvHost
if ($LASTEXITCODE -eq 0) { Write-Host 'Puerto 5555 cerrado.' }
else { Write-Host 'El puerto sigue abierto: en el TV, Ajustes > Opciones de desarrollador, apaga Depuración USB y Depuración por red, y reinicia.' }
exit $LASTEXITCODE
