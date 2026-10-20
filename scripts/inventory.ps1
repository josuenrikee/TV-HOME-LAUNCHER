# Inventario de SOLO LECTURA del TV. No cambia nada. Guarda el resultado en inventory\<ip>\inventory.txt
# Uso: scripts\inventory.ps1 -TvHost 192.168.1.50
param([Parameter(Mandatory = $true)][string]$TvHost)
. "$PSScriptRoot\_common.ps1"
$env:TV_HOST = $TvHost
$cmds = @(
    'echo ===modelo===',
    'getprop ro.product.manufacturer', 'getprop ro.product.brand', 'getprop ro.product.model', 'getprop ro.product.device',
    'getprop ro.build.version.release', 'getprop ro.build.version.sdk', 'getprop ro.build.display.id',
    'getprop ro.build.version.security_patch', 'getprop ro.board.platform', 'getprop ro.config.low_ram',
    'getprop ro.adb.secure',
    'echo ===espacio===', 'df /data',
    'echo ===memoria===', 'cat /proc/meminfo | head -3',
    'echo ===pantalla===', 'wm size', 'wm density',
    'echo ===inicio-actual===',
    'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME',
    'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME',
    'echo ===duenos-wifi===', 'dumpsys wifi | grep -o ''cname=[^ ]*''',
    'echo ===apps-instaladas-por-el-usuario===', 'pm list packages -3',
    'echo ===apps-desactivadas===', 'pm list packages -d',
    'echo ===todos-los-paquetes===', 'pm list packages',
    'echo ===apps-con-icono-de-tv===', 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER',
    'echo ===apps-con-icono-normal===', 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER',
    'echo ===entradas-de-tv===', 'dumpsys tv_input | head -80',
    'echo ===instalacion-permitida===', 'settings get global package_verifier_enable', 'settings get global verifier_verify_adb_installs'
)
$out = Invoke-TvBatch $cmds
$dir = Join-Path $script:Root ("inventory\" + ($TvHost -replace '[^A-Za-z0-9._-]', '_'))
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$file = Join-Path $dir 'inventory.txt'
[IO.File]::WriteAllText($file, $out, (New-Object System.Text.UTF8Encoding $false))
Write-Host "Inventario guardado en $file"
