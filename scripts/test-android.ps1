[CmdletBinding()]
param([string]$Serial)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $projectRoot '.dev\tools\android-sdk\platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adb)) { throw 'Run setup-android.ps1 first.' }
if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $projectRoot '.dev\tools\gradle' }
if (-not $Serial) {
    $lines = & $adb devices
    if ($LASTEXITCODE -ne 0) { throw 'Could not query ADB.' }
    $devices = @($lines | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
    if ($devices.Count -ne 1) { throw 'Connect one authorized phone or specify -Serial YOUR_SERIAL.' }
    $Serial = $devices[0]
}
Push-Location $projectRoot
try {
    $abi = ((& $adb -s $Serial shell getprop ro.product.cpu.abi) -join '').Trim()
    if ($LASTEXITCODE -ne 0 -or $abi -notin @('arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86')) {
        throw 'Could not determine a supported device ABI.'
    }
    & .\scripts\build-android-matrix.ps1 -Abis @($abi) -TestConnectedDevices -Serial @($Serial)
    if ($LASTEXITCODE -ne 0) { throw 'The matrix build, APK verification or existing instrumentation failed.' }
    & $adb -s $Serial shell am start -W -n `
        'org.axioma.calculator.debug/org.axioma.calculator.MainActivity'
    if ($LASTEXITCODE -ne 0) { throw 'Could not launch Axioma.' }
} finally { Pop-Location }
