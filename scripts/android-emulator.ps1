# SPDX-License-Identifier: GPL-3.0-only
# Dot-sourced by build-android-matrix.ps1; creates only dedicated Axioma AVDs.
function Start-AxiomaEmulator([int]$Api) {
    $emulator = Join-Path $sdk 'emulator\emulator.exe'
    $avdManager = Join-Path $sdk 'cmdline-tools\latest\bin\avdmanager.bat'
    $image = Join-Path $sdk "system-images\android-$Api\default\x86_64\package.xml"
    if (-not (Test-Path -LiteralPath $emulator) -or -not (Test-Path -LiteralPath $image)) {
        throw "Official emulator/default x86_64 API $Api image is not installed. See setup.md."
    }
    $acceleration = Invoke-Recorded $emulator @('-accel-check') "reports/emulator-api-$Api-acceleration.log"
    if ($acceleration.code -ne 0) { throw 'Android Emulator hardware acceleration is unavailable.' }
    $avdHome = Join-Path $projectRoot '.dev\emulator\avd'
    $userHome = Join-Path $projectRoot '.dev\emulator\user'
    New-Item -ItemType Directory -Force -Path $avdHome, $userHome | Out-Null
    $name = "Axioma_Matrix_API$Api"
    $path = Join-Path $avdHome "$name.avd"
    $previousAvd = $env:ANDROID_AVD_HOME; $previousUser = $env:ANDROID_USER_HOME
    $previousSdk = $env:ANDROID_HOME
    $process = $null
    try {
        $env:ANDROID_AVD_HOME = $avdHome; $env:ANDROID_USER_HOME = $userHome; $env:ANDROID_HOME = $sdk
        if (-not (Test-Path -LiteralPath (Join-Path $path 'config.ini'))) {
            $result = Invoke-Recorded $avdManager @('create', 'avd', '-n', $name, '-p', $path,
                '-k', "system-images;android-$Api;default;x86_64", '-d', 'pixel_2') "reports/emulator-api-$Api-create.log"
            if ($result.code -ne 0) { throw 'Could not create the dedicated Axioma AVD.' }
        }
        $port = 5560
        while ($port -lt 5680) {
            $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $port)
            try { $listener.Start(); break } catch { $port += 2 } finally { $listener.Stop() }
        }
        if ($port -ge 5680) { throw 'No unused emulator console port is available.' }
        $serial = "emulator-$port"
        # Redirect handles may remain open briefly after process exit. Read these
        # intermediates into separate sanitized reports; never rewrite a live log.
        $stdout = Get-ArtifactPath ".build/emulator-api-$Api-stdout.raw"
        $stderr = Get-ArtifactPath ".build/emulator-api-$Api-stderr.raw"
        $process = Start-Process -FilePath $emulator -ArgumentList @('-avd', $name, '-port', $port,
            '-no-window', '-no-audio', '-no-snapshot', '-no-boot-anim', '-gpu', 'swiftshader_indirect',
            '-memory', '2048', '-cores', '2', '-camera-back', 'none', '-camera-front', 'none') `
            -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
        $clock = [Diagnostics.Stopwatch]::StartNew()
        while ($clock.Elapsed.TotalSeconds -lt 240) {
            if ($process.HasExited) { throw "Emulator exited with code $($process.ExitCode); inspect its logs." }
            $savedPreference = $ErrorActionPreference
            try {
                $ErrorActionPreference = 'Continue'
                $state = @(& $adb -s $serial shell getprop sys.boot_completed 2>$null) -join ''
                $queryExit = $LASTEXITCODE
            } finally { $ErrorActionPreference = $savedPreference }
            if ($queryExit -eq 0 -and $state.Trim() -eq '1') {
                $bootedApi = Read-Adb $serial @('shell', 'getprop', 'ro.build.version.sdk')
                if ([int]$bootedApi -ne $Api) { throw 'Booted emulator reports an unexpected API.' }
                $null = Read-Adb $serial @('shell', 'input', 'keyevent', '82')
                return [pscustomobject]@{ process = $process; serial = $serial; api = $Api }
            }
            Start-Sleep -Seconds 2
        }
        throw 'Emulator did not boot within 240 seconds.'
    } catch {
        if ($process -and -not $process.HasExited) { Stop-Process -Id $process.Id -ErrorAction SilentlyContinue }
        Save-AxiomaEmulatorLogs $Api
        throw
    } finally {
        $env:ANDROID_AVD_HOME = $previousAvd; $env:ANDROID_USER_HOME = $previousUser; $env:ANDROID_HOME = $previousSdk
    }
}

function Stop-AxiomaEmulator($Instance) {
    $savedPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $null = & $adb -s $Instance.serial emu kill 2>&1
    } finally { $ErrorActionPreference = $savedPreference }
    if (-not $Instance.process.WaitForExit(20000)) { Stop-Process -Id $Instance.process.Id -ErrorAction SilentlyContinue }
    Save-AxiomaEmulatorLogs $Instance.api
    $Instance.process.Dispose()
}

function Save-AxiomaEmulatorLogs([int]$Api) {
    foreach ($stream in @('stdout', 'stderr')) {
        $path = Get-ArtifactPath ".build/emulator-api-$Api-$stream.raw"
        if (Test-Path -LiteralPath $path) {
            $handle = [IO.File]::Open($path, 'Open', 'Read', [IO.FileShare]::ReadWrite)
            $reader = [IO.StreamReader]::new($handle)
            try { $text = $reader.ReadToEnd() } finally { $reader.Dispose() }
            Write-ReportText "reports/emulator-api-$Api-$stream.log" $text
        }
    }
}
