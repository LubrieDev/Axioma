[CmdletBinding()]
param([string]$Serial)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $env:GRADLE_USER_HOME) {
    $env:GRADLE_USER_HOME = Join-Path $projectRoot '.dev\tools\gradle'
}
$localAdb = Join-Path $projectRoot '.dev\tools\android-sdk\platform-tools\adb.exe'
$adb = if (Test-Path -LiteralPath $localAdb) { $localAdb } else { 'adb' }

if (-not (Get-Command $adb -ErrorAction SilentlyContinue)) {
    throw 'ADB was not found. Run .\scripts\setup-android.ps1 first.'
}

if (-not $Serial) {
    $lines = & $adb devices
    if ($LASTEXITCODE -ne 0) { throw 'Could not query ADB.' }
    $connected = @($lines | Where-Object { $_ -match '^\S+\s+device$' } |
        ForEach-Object { ($_ -split '\s+')[0] })
    if ($connected.Count -eq 0) {
        throw 'Connect your phone, enable USB debugging, and authorize this computer.'
    }
    if ($connected.Count -gt 1) {
        throw 'Multiple devices are connected. Use -Serial YOUR_SERIAL to select one.'
    }
    $Serial = $connected[0]
}

Push-Location $projectRoot
try {
    $abi = ((& $adb -s $Serial shell getprop ro.product.cpu.abi) -join '').Trim()
    if ($LASTEXITCODE -ne 0 -or $abi -notin @('arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86')) {
        throw 'Could not determine a supported device ABI.'
    }
    & .\scripts\build-android-matrix.ps1 -Abis @($abi) -TestConnectedDevices -Serial @($Serial)
    if ($LASTEXITCODE -ne 0) { throw 'The verified device build or instrumentation failed.' }

    $launchOutput = & $adb -s $Serial shell am start -W -n `
        'org.axioma.calculator.debug/org.axioma.calculator.MainActivity' 2>&1
    $launchExit = $LASTEXITCODE
    $launchOutput | Write-Output
    if ($launchExit -ne 0 -or ($launchOutput -join "`n") -match '(?m)^Error') {
        throw 'Could not launch Axioma.'
    }
} finally {
    Pop-Location
}
