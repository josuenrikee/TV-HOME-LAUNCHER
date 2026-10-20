# Devuelve el launcher original del fabricante y quita el nuevo como inicio.
# Uso: scripts\undo-launcher.ps1 -TvHost 192.168.1.50 -StockLauncher com.google.android.tvlauncher [-Uninstall]
param(
    [Parameter(Mandatory = $true)][string]$TvHost,
    [Parameter(Mandatory = $true)][string]$StockLauncher,
    [switch]$Uninstall
)
. "$PSScriptRoot\_common.ps1"
$env:TV_HOST = $TvHost
Write-Host (Invoke-Tv "pm enable --user 0 $StockLauncher")
Write-Host (Invoke-Tv "cmd package set-home-activity --user 0 `$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME $StockLauncher | tail -n 1)")
Write-Host (Invoke-Tv 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME')
if ($Uninstall) { Write-Host (Invoke-Tv 'pm uninstall ec.josue.tvhome') }
