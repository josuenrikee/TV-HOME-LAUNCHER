# Funciones compartidas. Se carga con: . "$PSScriptRoot\_common.ps1"
$ErrorActionPreference = 'Stop'
$script:Root = Split-Path -Parent $PSScriptRoot
$OutputEncoding = New-Object System.Text.UTF8Encoding $false

function Get-VenvPython {
    $p = Join-Path $script:Root 'tools\venv\Scripts\python.exe'
    if (Test-Path $p) { return $p }
    return $null
}

# Busca un Python 3.9+ ya instalado. Devuelve @{ Exe = ...; Args = ... } o $null.
function Find-Python {
    $candidates = @(
        @{ Exe = 'py'; Args = @('-3') },
        @{ Exe = 'python'; Args = @() },
        @{ Exe = 'python3'; Args = @() }
    )
    foreach ($c in $candidates) {
        $cmd = Get-Command $c.Exe -ErrorAction SilentlyContinue
        if (-not $cmd) { continue }
        try {
            $out = (& $c.Exe @($c.Args) --version 2>&1 | Out-String).Trim()
        } catch { continue }
        if ($out -match 'Python 3\.(\d+)' -and [int]$Matches[1] -ge 9) { return $c }
    }
    return $null
}

# Ejecuta un comando de shell en el TV (TV_HOST ya definido).
function Invoke-Tv([string]$Command) {
    $py = Get-VenvPython
    if (-not $py) { throw 'Falta el entorno de Python. Ejecuta scripts\setup.ps1 primero.' }
    $out = & $py -I (Join-Path $script:Root 'tools\tv.py') $Command 2>&1
    return ($out | Out-String)
}

# Ejecuta varios comandos (uno por línea) en una sola conexión.
function Invoke-TvBatch([string[]]$Commands) {
    $py = Get-VenvPython
    if (-not $py) { throw 'Falta el entorno de Python. Ejecuta scripts\setup.ps1 primero.' }
    $out = $Commands | & $py -I (Join-Path $script:Root 'tools\tv.py') --batch 2>&1
    return ($out | Out-String)
}

function Test-TcpPort([string]$HostName, [int]$Port, [int]$TimeoutMs = 3000) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $iar = $client.BeginConnect($HostName, $Port, $null, $null)
        if (-not $iar.AsyncWaitHandle.WaitOne($TimeoutMs, $false)) { return $false }
        $client.EndConnect($iar)
        return $client.Connected
    } catch { return $false } finally { $client.Close() }
}
