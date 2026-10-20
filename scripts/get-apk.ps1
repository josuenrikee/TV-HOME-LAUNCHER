# Descarga el APK compilado por GitHub Actions a dist\tvhome.apk.
# Uso: scripts\get-apk.ps1 [-Url <enlace>]
param([string]$Url = 'https://github.com/josuenrikee/TV-HOME-LAUNCHER/releases/latest/download/tvhome.apk')
. "$PSScriptRoot\_common.ps1"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$dist = Join-Path $script:Root 'dist'
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$dest = Join-Path $dist 'tvhome.apk'
Invoke-WebRequest -Uri $Url -OutFile $dest -UseBasicParsing
$len = (Get-Item $dest).Length
if ($len -lt 20000) { throw "El archivo descargado es muy pequeño ($len bytes); revisa que el repositorio sea público y que exista un release." }
Write-Host "APK listo: $dest ($([math]::Round($len / 1024)) KB)"
