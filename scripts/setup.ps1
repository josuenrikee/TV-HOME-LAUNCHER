# Prepara las herramientas: crea tools\venv e instala adb-shell. No necesita permisos de administrador.
. "$PSScriptRoot\_common.ps1"
$venvPy = Get-VenvPython
if ($venvPy) { Write-Host "Entorno listo: $venvPy"; exit 0 }
$py = Find-Python
if (-not $py) {
    Write-Host 'NO: no hay Python 3.9 o superior instalado.'
    Write-Host 'Instálalo solo para tu usuario (sin administrador) desde https://www.python.org/downloads/windows/'
    Write-Host 'y marca "Install for current user only". Luego vuelve a ejecutar este script.'
    exit 1
}
Write-Host "Usando $($py.Exe) $($py.Args -join ' ')"
& $py.Exe @($py.Args) -m venv (Join-Path $script:Root 'tools\venv')
if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear el entorno virtual.' }
$vp = Get-VenvPython
& $vp -m pip install --quiet --disable-pip-version-check -r (Join-Path $script:Root 'tools\requirements.txt')
if ($LASTEXITCODE -ne 0) { throw 'No se pudieron instalar los paquetes (¿proxy o firewall de la empresa?).' }
Write-Host 'Listo: herramientas instaladas.'
