# Instala el launcher en el TV y lo deja como pantalla de inicio. Si algo falla, devuelve el inicio original.
# Uso: scripts\install.ps1 -TvHost 192.168.1.50 [-Apk dist\tvhome.apk] [-StockLauncher <paquete>|none]
# El launcher del fabricante se detecta solo si es uno conocido; en otro caso hay que indicarlo.
param(
    [Parameter(Mandatory = $true)][string]$TvHost,
    [string]$Apk = '',
    [string]$StockLauncher = ''
)
. "$PSScriptRoot\_common.ps1"
$env:TV_HOST = $TvHost
$Pkg = 'ec.josue.tvhome'
$Component = "$Pkg/.MainActivity"
$py = Get-VenvPython
if (-not $py) { throw 'Falta el entorno de Python. Ejecuta scripts\setup.ps1 primero.' }
$tv = Join-Path $script:Root 'tools\tv.py'
$push = Join-Path $script:Root 'tools\b64push.py'

if (-not $Apk) {
    $Apk = Join-Path $script:Root 'dist\tvhome.apk'
    if (-not (Test-Path $Apk)) { & "$PSScriptRoot\get-apk.ps1" }
}
if (-not (Test-Path $Apk)) { throw "No existe el APK: $Apk" }

$homeQuery = 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
$knownStock = @('com.google.android.tvlauncher', 'com.google.android.apps.tv.launcherx', 'com.google.android.leanbacklauncher', 'com.mitv.tvhome')

function Get-HomePackage {
    $o = (& $py -I $tv $homeQuery 2>&1 | Out-String).Trim().Split("`n") | Select-Object -Last 1
    return ("$o").Trim().Split('/')[0]
}

if (-not $StockLauncher) {
    $homePkg = Get-HomePackage
    if (-not $homePkg) { throw 'No pude preguntarle al TV quién es su pantalla de inicio. Revisa la IP y el aviso de depuración en el TV.' }
    if ($knownStock -contains $homePkg) { $StockLauncher = $homePkg }
    elseif ($homePkg -eq $Pkg) { $StockLauncher = 'none'; Write-Host 'El inicio ya es este launcher; no se desactiva nada.' }
    elseif ($homePkg -eq 'android') {
        $claim = (& $py -I $tv 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME' 2>&1 | Out-String)
        $found = @($knownStock | Where-Object { $claim -match ([regex]::Escape($_) + '/') })
        if ($found.Count -eq 1) { $StockLauncher = $found[0] }
        else { throw "El inicio es el selector de Android y $($found.Count) launchers conocidos compiten. Indica -StockLauncher <paquete> o none." }
    }
    else { throw "El inicio actual es '$homePkg', que no es un launcher conocido. Indica -StockLauncher <paquete a desactivar> o none." }
}
Write-Host "Launcher original: $StockLauncher"

# 1. Subir e instalar (todavía no se toca el launcher original).
& $py -I $push $Apk /data/local/tmp/tvhome.apk
if ($LASTEXITCODE -ne 0) { throw 'No se pudo subir el APK al TV.' }
$out = (& $py -I $tv 'pm install -r /data/local/tmp/tvhome.apk; rm -f /data/local/tmp/tvhome.apk' 2>&1 | Out-String)
Write-Host $out
if ($out -notmatch 'Success') { throw 'La instalación falló; el launcher original quedó intacto.' }

# 2. Arrancarlo solo, antes de tocar nada más.
$start = (& $py -I $tv "am start -W -n $Component" 2>&1 | Out-String)
Write-Host $start
if ($start -notmatch 'Status: ok' -or $start -match '(?m)^Error') { throw "El launcher nuevo no arrancó; $StockLauncher quedó activo." }

# 3. Desactivar el original y fijar el nuevo como inicio.
$disable = if ($StockLauncher -eq 'none') { 'echo launcher original sin tocar' } else { "pm disable-user --user 0 $StockLauncher" }
$batch = @("cmd package compile -m speed -f $Pkg", $disable, "cmd package set-home-activity --user 0 $Component")
Write-Host (Invoke-TvBatch $batch)

# 4. Comprobar. Si el inicio no es el nuevo, se devuelve el original.
$now = (& $py -I $tv $homeQuery 2>&1 | Out-String).Trim().Split("`n") | Select-Object -Last 1
$now = ("$now").Trim()
if ($now -notlike "$Pkg/*") {
    if ($StockLauncher -eq 'none') {
        throw "El inicio sigue siendo '$now': ese launcher tiene prioridad mientras esté activo. Nada se desactivó. Para que el nuevo tome el control hay que desactivarlo (repite sin -StockLauncher none); si prefieres dejar ambos, el nuevo queda instalado y se abre desde Todas las apps."
    }
    if ($StockLauncher -ne 'none') {
        & $py -I $tv "pm enable --user 0 $StockLauncher" | Out-Null
        & $py -I $tv "cmd package set-home-activity --user 0 `$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME $StockLauncher | tail -n 1)" | Out-Null
    }
    throw "El inicio resuelve a '$now', no al launcher nuevo; se reactivó $StockLauncher. En Google TV suele faltar desactivar com.google.android.tungsten.setupwraith (mira docs\TROUBLESHOOTING.md)."
}
Write-Host "Listo: el inicio ahora es $now"
Write-Host "Para deshacer: scripts\undo-launcher.ps1 -TvHost $TvHost -StockLauncher $StockLauncher"
