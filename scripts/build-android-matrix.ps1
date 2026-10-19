[CmdletBinding()]
param(
    [ValidateSet('arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86')]
    [string[]]$Abis = @('arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86'),
    [ValidateSet('Debug', 'Release')]
    [string]$Variant = 'Debug',
    [string]$ExpectedSignerSha256,
    [switch]$IncludeUniversal,
    [switch]$TestConnectedDevices,
    [switch]$RequireCompleteMatrix,
    [ValidateSet(33, 34, 35, 36)]
    [int[]]$EmulatorApis = @(),
    [string[]]$Serial
)

# SPDX-License-Identifier: GPL-3.0-only
# Installs only when explicitly requested; never stages, commits or publishes.
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$artifactRoot = Join-Path $projectRoot 'artifacts\android'
$reports = Join-Path $artifactRoot 'reports'
$manifestPath = Join-Path $artifactRoot 'manifests\build-manifest.json'
$managedPaths = [Collections.Generic.HashSet[string]]::new()
$connections = @{}
$criticalFailures = 0
$lock = $null
$canWriteOutputs = $false
$previous = $null
$variantLower = $Variant.ToLowerInvariant()
$isRelease = $Variant -eq 'Release'
$applicationId = 'org.axioma.calculator' + $(if ($isRelease) { '' } else { '.debug' })
$versionName = if ($isRelease) { '0.1.0' } else { '0.1.0-dev' }
$manifest = [ordered]@{
    schema_version = 1; generator = 'axioma-android-matrix'; started_utc = [DateTime]::UtcNow.ToString('o')
    status = 'running'; configuration = [ordered]@{
        variant = $variantLower; signing = $(if ($isRelease) { 'permanent private release key' } else { 'local debug key; not a signed production release' })
        application_id = $applicationId; expected_signer_sha256 = $ExpectedSignerSha256
        min_sdk = 26; compile_sdk = 36; target_sdk = 36; version_name = $versionName; version_code = 4
        requested_abis = @($Abis); universal_requested = [bool]$IncludeUniversal
        requested_emulator_apis = @($EmulatorApis)
        runtime_requested = [bool]$TestConnectedDevices; minimum_instrumentation_checks = 1300
        native_staging = '.build/<buildId>/native'; gradle_project_cache = '.build/<buildId>/project-cache'
    }
    tools = [ordered]@{}; builds = @(); artifacts = @(); devices = @(); runtime_attempts = @(); matrix = @(); emulator_environments = @()
    preexisting_defects = 'Five open mathematical defect groups; see docs/user/known-issues.md. No engine fixes in this task.'
    limitations = @('A successful build/static inspection is not runtime compatibility.',
        'No runtime result is inferred for an untested API/ABI or page size.',
        'No AAB or remote publication is performed by this local APK workflow.')
    generated_files = @(); managed_files = @(); preserved_releases = @()
}
function Get-AppRelativePath([string]$Id) {
    if ($isRelease) { return "release/0.1.0/Axioma-0.1.0-$Id.apk" }
    return "$Id/Axioma-$Id.apk"
}
function Get-BuildId([string]$Id) {
    if ($isRelease) { return "release-$Id" }
    return $Id
}

function Get-ArtifactPath([string]$Relative) {
    if ([IO.Path]::IsPathRooted($Relative)) { throw 'Artifact paths must be relative.' }
    $resolved = [IO.Path]::GetFullPath((Join-Path $artifactRoot $Relative))
    if (-not $resolved.StartsWith($artifactRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Artifact path would leave artifacts/android.'
    }
    $cursor = $resolved
    while ($cursor.StartsWith($projectRoot, [StringComparison]::OrdinalIgnoreCase)) {
        $entry = Get-Item -LiteralPath $cursor -Force -ErrorAction SilentlyContinue
        if ($entry -and ($entry.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw 'Reparse-point artifact paths are not allowed for generated output cleanup.'
        }
        if ($cursor -eq $projectRoot) { break }
        $cursor = Split-Path -Parent $cursor
    }
    return $resolved
}
function Protect-Text([string]$Text) {
    $result = $Text.Replace($projectRoot, '<PROJECT>').Replace($projectRoot.Replace('\', '/'), '<PROJECT>')
    if ($env:USERPROFILE) { $result = $result.Replace($env:USERPROFILE, '<USER_HOME>') }
    foreach ($alias in $connections.Keys) { $result = $result.Replace($connections[$alias], $alias) }
    return $result
}
function Write-ReportText([string]$Relative, [string]$Text) {
    $path = Get-ArtifactPath $Relative
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $path) | Out-Null
    [IO.File]::WriteAllText($path, (Protect-Text $Text), [Text.UTF8Encoding]::new($false))
    [void]$managedPaths.Add($Relative.Replace('\', '/'))
}
function Invoke-Recorded([string]$Program, [string[]]$Arguments, [string]$Log) {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $lines = @(& $Program @Arguments 2>&1 | ForEach-Object { "$_" })
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    $text = $lines -join "`n"
    Write-ReportText $Log $text
    return [pscustomobject]@{ code = $code; text = $text }
}
function Save-ManifestCheckpoint {
    $manifest.generated_files = @($managedPaths | Sort-Object) + @('manifests/build-manifest.json')
    $manifest.managed_files = @()
    foreach ($relative in $managedPaths) {
        $path = Get-ArtifactPath $relative
        if (Test-Path -LiteralPath $path) {
            $manifest.managed_files += [pscustomobject]@{ path = $relative; sha256 = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() }
        }
    }
    $temporary = Get-ArtifactPath '.build/manifest-checkpoint.tmp'
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $temporary) | Out-Null
    [IO.File]::WriteAllText($temporary, ($manifest | ConvertTo-Json -Depth 20), [Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $temporary -Destination $manifestPath -Force
}
function Read-Adb([string]$Device, [string[]]$Arguments) {
    $lines = @(& $adb -s $Device @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) { throw 'Could not query the connected Android environment.' }
    return ($lines -join "`n").Trim()
}
function Verify-Apk([string]$Path, [string[]]$ExpectedAbis, [bool]$IsTest, [string]$Label) {
    $temporary = Get-ArtifactPath '.build/apk-inspection.json'
    $arguments = @($inspector, '--apk', $Path, '--sdk', $sdk, '--ndk', $ndk, '--output', $temporary)
    $arguments += @('--variant', $variantLower)
    if ($ExpectedSignerSha256) { $arguments += @('--expected-cert-sha256', $ExpectedSignerSha256) }
    if ($IsTest) { $arguments += '--test-apk' } else { $arguments += @('--abis', ($ExpectedAbis -join ',')) }
    $label = $Label
    $result = Invoke-Recorded $python ($pythonPrefix + $arguments) "reports/verify-$label.log"
    if ($result.code -ne 0) { throw "APK inspection failed; see reports/verify-$label.log." }
    $record = Get-Content -LiteralPath $temporary -Raw | ConvertFrom-Json
    Remove-Item -LiteralPath $temporary
    $record | Add-Member -NotePropertyName verified_runtime_cells -NotePropertyValue @()
    return $record
}
function Build-App([string]$Id, [string[]]$BuildAbis) {
    Write-Host "Building $Id ($($BuildAbis -join ', '))..."
    $build = [ordered]@{ id = $Id; abis = @($BuildAbis); status = 'failed'; log = "reports/build-$Id.log" }
    try {
        $buildId = Get-BuildId $Id
        $arguments = @("assemble$Variant", "lint$Variant", ':app:verifyOwnRuntime',
            "-PaxiomaAbis=$($BuildAbis -join ',')", "-PaxiomaBuildId=$buildId", "-PaxiomaTestBuildType=$variantLower", '--project-cache-dir',
            (Get-ArtifactPath ".build/$buildId/project-cache"), '--console=plain', '--stacktrace')
        $result = Invoke-Recorded $gradle $arguments $build.log
        $build.attempts = @([pscustomobject]@{ number = 1; exit_code = $result.code; log = $build.log })
        # Windows output-cleanup locks can be transient. Retry the full checks
        # once, preserve the failed attempt, and never retry functional failures.
        if ($result.code -ne 0 -and $result.text -match 'Failed to clean up output files|Unable to delete directory') {
            $firstLog = "reports/build-$Id-attempt-1.log"
            Write-ReportText $firstLog $result.text
            $build.attempts[0].log = $firstLog
            Write-Warning "$Id output cleanup failed; the failed log is retained, retrying all Gradle checks once."
            Save-ManifestCheckpoint
            Start-Sleep -Seconds 2
            $result = Invoke-Recorded $gradle $arguments $build.log
            $build.attempts += [pscustomobject]@{ number = 2; exit_code = $result.code; log = $build.log }
        }
        $build.exit_code = $result.code
        if ($result.code -ne 0) { throw 'Gradle compilation, lint or dependency verification failed.' }
        $source = Get-ArtifactPath ".build/$buildId/app/outputs/apk/$variantLower/app-$variantLower.apk"
        if (-not (Test-Path -LiteralPath $source)) { throw 'Gradle did not produce the expected APK.' }
        $relative = Get-AppRelativePath $Id
        $destination = Get-ArtifactPath $relative
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
        if (Test-Path -LiteralPath $destination) { throw 'Unmanaged APK already exists at the destination.' }
        Move-Item -LiteralPath $source -Destination $destination
        [void]$managedPaths.Add($relative)
        Save-ManifestCheckpoint
        $record = Verify-Apk $destination $BuildAbis $false $Id
        $record | Add-Member -NotePropertyName build_id -NotePropertyValue $Id
        $manifest.artifacts += $record
        $build.status = 'passed'; $build.artifact = $record.path
    } catch {
        $script:criticalFailures++
        $build.error = Protect-Text $_.Exception.Message
        Write-Warning "$Id failed: $($build.error)"
    }
    $manifest.builds += [pscustomobject]$build
    Save-ManifestCheckpoint
}
function Test-Artifact($Artifact, $Device, [string]$Abi, [string]$TestApk) {
    $alias = $Device.id; $deviceSerial = $connections[$alias]
    $label = "$alias-$Abi-$($Artifact.build_id)"
    $attempt = [ordered]@{ device = $alias; api = $Device.api; abi = $Abi; artifact = $Artifact.path
        installation = 'not_executed'; launch = 'not_executed'; tests = 'not_executed'; status = 'failed'
        passed = 0; failed = 0; log = "reports/runtime-$label.log" }
    Write-Host "Testing $($Artifact.path) on $alias, API $($Device.api), ABI $Abi..."
    try {
        foreach ($step in @(
            @{ name = 'install'; args = @('-s', $deviceSerial, 'install', '-r', '--abi', $Abi, (Get-ArtifactPath $Artifact.path)) },
            @{ name = 'install-tests'; args = @('-s', $deviceSerial, 'install', '-r', '-t', $TestApk) }
        )) {
            $result = Invoke-Recorded $adb $step.args "reports/$($step.name)-$label.log"
            if ($result.code -ne 0 -or $result.text -notmatch '(?m)^Success\s*$') { throw "$($step.name) failed." }
        }
        $attempt.installation = 'passed'
        foreach ($identity in @(
            @{ package = $applicationId; sha256 = $Artifact.sha256; field = 'installed_apk_sha256' },
            @{ package = "$applicationId.test"; sha256 = (Get-FileHash -LiteralPath $TestApk -Algorithm SHA256).Hash.ToLowerInvariant(); field = 'installed_test_apk_sha256' }
        )) {
            $installed = Read-Adb $deviceSerial @('shell', 'pm', 'path', $identity.package)
            if ($installed -notmatch '^package:(/data/app/[A-Za-z0-9_./~+=-]+\.apk)$') { throw 'Unexpected installed package/split layout.' }
            $installedPath = $Matches[1]
            $digest = Read-Adb $deviceSerial @('shell', 'sha256sum', $installedPath)
            $match = [regex]::Match($digest, '^([a-fA-F0-9]{64})\s')
            if (-not $match.Success -or $match.Groups[1].Value.ToLowerInvariant() -ne $identity.sha256) {
                throw 'Installed APK bytes do not match the verified local artifact.'
            }
            $attempt[$identity.field] = $match.Groups[1].Value.ToLowerInvariant()
        }
        $package = Read-Adb $deviceSerial @('shell', 'dumpsys', 'package', $applicationId)
        if ($package -notmatch ('primaryCpuAbi=' + [regex]::Escape($Abi) + '(\s|$)')) {
            throw 'Android did not select the requested native ABI.'
        }
        $null = Read-Adb $deviceSerial @('shell', 'am', 'force-stop', $applicationId)
        $result = Invoke-Recorded $adb @('-s', $deviceSerial, 'shell', 'am', 'start', '-W', '-n',
            "$applicationId/org.axioma.calculator.MainActivity") "reports/launch-$label.log"
        if ($result.code -ne 0 -or $result.text -notmatch '(?m)^Status: ok\s*$') { throw 'Activity launch failed.' }
        $attempt.launch = 'passed'
        $result = Invoke-Recorded $adb @('-s', $deviceSerial, 'shell', 'am', 'instrument', '--abi', $Abi, '-w', '-r',
            "$applicationId.test/org.axioma.calculator.SmokeInstrumentation") $attempt.log
        $passedMatch = [regex]::Match($result.text, 'INSTRUMENTATION_RESULT: passed=(\d+)')
        $failedMatch = [regex]::Match($result.text, 'INSTRUMENTATION_RESULT: failed=(\d+)')
        if ($passedMatch.Success) { $attempt.passed = [int]$passedMatch.Groups[1].Value }
        if ($failedMatch.Success) { $attempt.failed = [int]$failedMatch.Groups[1].Value }
        foreach ($field in @('api', 'androidRelease', 'processArch', 'process64Bit', 'supportedAbis', 'pageSize')) {
            $match = [regex]::Match($result.text, "INSTRUMENTATION_RESULT: $field=([^\r\n]+)")
            if ($match.Success) { $attempt[$field] = $match.Groups[1].Value.Trim() }
        }
        if ($result.code -ne 0 -or -not $passedMatch.Success -or -not $failedMatch.Success -or
            $attempt.passed -lt 1300 -or $attempt.failed -ne 0 -or
            $result.text -notmatch 'INSTRUMENTATION_CODE: -1' -or
            $result.text -match 'INSTRUMENTATION_FAILED|Fatal signal|JNI DETECTED ERROR|UnsatisfiedLinkError' -or
            $attempt.api -ne $Device.api) { throw 'Functional/JNI instrumentation failed or did not complete.' }
        $expected64 = $Abi -in @('arm64-v8a', 'x86_64')
        if ($attempt.process64Bit -ne $expected64.ToString().ToLowerInvariant()) { throw 'Wrong instrumentation process bitness.' }
        $attempt.tests = 'passed'; $attempt.status = 'passed'
        $Artifact.runtime_verified = $true
        $Artifact.verified_runtime_cells += [pscustomobject]@{ api = $Device.api; abi = $Abi; device = $alias; page_size = $attempt.pageSize }
        foreach ($runner in @($manifest.artifacts | Where-Object {
            $_.kind -eq 'instrumentation' -and $_.sha256 -eq $attempt.installed_test_apk_sha256
        })) {
            $runner.runtime_verified = $true
            $runner.verified_runtime_cells += [pscustomobject]@{ api = $Device.api; abi = $Abi; device = $alias; page_size = $attempt.pageSize }
        }
    } catch {
        $script:criticalFailures++
        $attempt.error = Protect-Text $_.Exception.Message
        if ($attempt.installation -eq 'not_executed') { $attempt.installation = 'failed' }
        elseif ($attempt.launch -eq 'not_executed') { $attempt.launch = 'failed' }
        else { $attempt.tests = 'failed' }
        Write-Warning "$label failed: $($attempt.error)"
    }
    $manifest.runtime_attempts += [pscustomobject]$attempt
    Save-ManifestCheckpoint
}
function Register-AndroidDevice([string]$DeviceSerial, [string]$Alias) {
    $connections[$Alias] = $DeviceSerial
    $device = [pscustomobject]@{ id = $Alias; api = [int](Read-Adb $DeviceSerial @('shell', 'getprop', 'ro.build.version.sdk'))
        android = (Read-Adb $DeviceSerial @('shell', 'getprop', 'ro.build.version.release'))
        abis = @((Read-Adb $DeviceSerial @('shell', 'getprop', 'ro.product.cpu.abilist')) -split ',')
        emulator = $DeviceSerial.StartsWith('emulator-'); page_size = (Read-Adb $DeviceSerial @('shell', 'getconf', 'PAGE_SIZE'))
        native_bridge = (Read-Adb $DeviceSerial @('shell', 'getprop', 'ro.dalvik.vm.native.bridge')) }
    $manifest.devices += $device
    return $device
}
function Test-AndroidDevice($Device, [string]$TestApk) {
    if ($Device.api -notin 33..36) { return }
    foreach ($artifact in @($manifest.artifacts | Where-Object kind -eq 'application')) {
        foreach ($abi in @($artifact.abis | Where-Object { $_ -in $Device.abis })) {
            Test-Artifact $artifact $Device $abi $TestApk
        }
    }
}

Push-Location $projectRoot
try {
    New-Item -ItemType Directory -Force -Path $reports, (Split-Path -Parent $manifestPath) | Out-Null
    $lock = [IO.File]::Open((Join-Path $artifactRoot '.matrix.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    if (Test-Path -LiteralPath $manifestPath) {
        $previous = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
        if ($previous.generator -ne 'axioma-android-matrix') { throw 'Unknown previous manifest; no cleanup performed.' }
        if ($previous.preserved_releases) { $manifest.preserved_releases = @($previous.preserved_releases) }
        if ($previous.release_bundle) { $manifest.preserved_releases += $previous.release_bundle }
        foreach ($releaseRecord in $manifest.preserved_releases) {
            foreach ($fingerprint in $releaseRecord.file_hashes.PSObject.Properties) {
                $frozenPath = Get-ArtifactPath "$($releaseRecord.path)/$($fingerprint.Name)"
                if ((Test-Path -LiteralPath $frozenPath) -and
                    (Get-FileHash -LiteralPath $frozenPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $fingerprint.Value) {
                    throw 'A preserved release file changed. It was not removed; review the immutable release before continuing.'
                }
            }
        }
        # Validate every prior output before deleting any. Never recursively delete.
        foreach ($entry in $previous.managed_files) {
            $path = Get-ArtifactPath $entry.path
            if ((Test-Path -LiteralPath $path) -and (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.sha256) {
                throw "A previous generated file was modified: $($entry.path). Preserve it before rerunning."
            }
        }
    }
    if ($Abis.Count -eq 0 -or @($Abis | Select-Object -Unique).Count -ne $Abis.Count) { throw 'Select distinct ABI names.' }
    if ($RequireCompleteMatrix -and -not $TestConnectedDevices) { throw 'RequireCompleteMatrix needs TestConnectedDevices.' }
    if ($EmulatorApis.Count -and -not $TestConnectedDevices) { throw 'EmulatorApis needs TestConnectedDevices.' }
    if ($isRelease) {
        if ($ExpectedSignerSha256 -notmatch '^[a-fA-F0-9]{64}$') { throw 'Release builds require the permanent signing certificate fingerprint.' }
        foreach ($name in @('AXIOMA_KEYSTORE_PATH', 'AXIOMA_KEYSTORE_PASSWORD', 'AXIOMA_KEY_ALIAS', 'AXIOMA_KEY_PASSWORD')) {
            if (-not [Environment]::GetEnvironmentVariable($name, 'Process')) { throw 'Use build-release.ps1 to load private release signing credentials.' }
        }
    }
    $java = Get-Command java -ErrorAction Stop
    $javaVersion = @(& $java.Source -version 2>&1 | ForEach-Object { "$_" }) -join "`n"
    if ($javaVersion -notmatch 'version "17[.\"]') { throw 'The pinned workflow requires JDK 17.' }
    $manifest.tools.jdk = ($javaVersion -split "`n")[0]
    $pythonCommand = Get-Command py -ErrorAction SilentlyContinue
    $pythonPrefix = @()
    if ($pythonCommand) { $python = $pythonCommand.Source; $pythonPrefix = @('-3') }
    else { $python = (Get-Command python -ErrorAction Stop).Source }
    & $python @pythonPrefix -c 'import sys; assert sys.version_info >= (3,10)'
    if ($LASTEXITCODE -ne 0) { throw 'Python 3.10 or newer is required for ZIP/ELF verification.' }
    $manifest.tools.python = (@(& $python @pythonPrefix --version) -join '').Trim()
    $manifest.tools.powershell = $PSVersionTable.PSVersion.ToString()
    $sdk = Join-Path $projectRoot '.dev\tools\android-sdk'
    if ($env:ANDROID_HOME) { $sdk = [IO.Path]::GetFullPath($env:ANDROID_HOME) }
    elseif ($env:ANDROID_SDK_ROOT) { $sdk = [IO.Path]::GetFullPath($env:ANDROID_SDK_ROOT) }
    # Match Gradle's existing local SDK pointer instead of silently using another SDK.
    $localProperties = Join-Path $projectRoot 'local.properties'
    if (Test-Path -LiteralPath $localProperties) {
        $sdkEntries = [regex]::Matches([IO.File]::ReadAllText($localProperties), '(?m)^\s*sdk\.dir\s*=(.+)$')
        if ($sdkEntries.Count -gt 1) { throw 'Ambiguous sdk.dir entries; review local.properties without overwriting it.' }
        if ($sdkEntries.Count -eq 1) {
            $sdkPointer = $sdkEntries[0].Groups[1].Value.Trim().Replace('\:', ':').Replace('\\', '\').Replace('\ ', ' ')
            if (-not [IO.Path]::IsPathRooted($sdkPointer)) { $sdkPointer = Join-Path $projectRoot $sdkPointer }
            $sdk = [IO.Path]::GetFullPath($sdkPointer)
        }
    }
    $ndk = Join-Path $sdk 'ndk\28.2.13676358'
    $adb = Join-Path $sdk 'platform-tools\adb.exe'
    $gradle = Join-Path $projectRoot 'gradlew.bat'
    $inspector = Join-Path $PSScriptRoot 'android-artifacts.py'
    foreach ($required in @($adb, $gradle, $inspector, (Join-Path $sdk 'platforms\android-36\android.jar'),
        (Join-Path $sdk 'build-tools\35.0.0\aapt.exe'), (Join-Path $sdk 'build-tools\35.0.0\zipalign.exe'),
        (Join-Path $sdk 'build-tools\35.0.0\apksigner.bat'), (Join-Path $sdk 'cmake\3.22.1\bin\cmake.exe'),
        (Join-Path $ndk 'meta\abis.json'))) {
        if (-not (Test-Path -LiteralPath $required)) { throw 'Missing pinned SDK/NDK/CMake tools. Run setup-android.ps1.' }
    }
    if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $projectRoot '.dev\tools\gradle' }
    $manifest.tools.gradle = '8.13'; $manifest.tools.agp = '8.13.2'; $manifest.tools.ndk = '28.2.13676358'
    $manifest.tools.cmake = '3.22.1'; $manifest.tools.build_tools = '35.0.0'
    $platformProperties = Join-Path $sdk 'platform-tools\source.properties'
    if (Test-Path -LiteralPath $platformProperties) {
        $revision = [regex]::Match([IO.File]::ReadAllText($platformProperties), '(?m)^Pkg.Revision=(.+)$')
        if ($revision.Success) { $manifest.tools.platform_tools = $revision.Groups[1].Value.Trim() }
    }
    $emulatorProperties = Join-Path $sdk 'emulator\source.properties'
    if (Test-Path -LiteralPath $emulatorProperties) {
        $revision = [regex]::Match([IO.File]::ReadAllText($emulatorProperties), '(?m)^Pkg.Revision=(.+)$')
        if ($revision.Success) { $manifest.tools.emulator = $revision.Groups[1].Value.Trim() }
    }
    $ndkAbis = Get-Content -LiteralPath (Join-Path $ndk 'meta\abis.json') -Raw | ConvertFrom-Json
    foreach ($abi in $Abis) {
        if (-not $ndkAbis.PSObject.Properties[$abi]) { throw "Installed NDK does not declare $abi." }
    }
    # Preflight is complete. Only now replace hash-identified previous outputs.
    if ($previous) {
        foreach ($entry in $previous.managed_files) {
            $path = Get-ArtifactPath $entry.path
            if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path }
        }
    }
    $canWriteOutputs = $true
    Save-ManifestCheckpoint
    foreach ($abi in $Abis) {
        Build-App $abi @($abi)
    }
    $successfulAbis = @($manifest.builds | Where-Object status -eq 'passed' | ForEach-Object { $_.id })
    if ($IncludeUniversal -and $successfulAbis.Count -gt 1) { Build-App 'universal' $successfulAbis }
    elseif ($IncludeUniversal) { $manifest.limitations += 'Universal skipped: fewer than two ABI builds passed.' }

    if ($TestConnectedDevices) {
        $testBuildId = Get-BuildId 'tests'
        $result = Invoke-Recorded $gradle @("assemble$($Variant)AndroidTest", "-PaxiomaBuildId=$testBuildId", "-PaxiomaTestBuildType=$variantLower", '--project-cache-dir',
            (Get-ArtifactPath ".build/$testBuildId/project-cache"), '--console=plain', '--stacktrace') 'reports/build-instrumentation.log'
        if ($result.code -ne 0) { throw 'Instrumentation APK build failed.' }
        $testRelative = if ($isRelease) { 'tests/Axioma-instrumentation-release.apk' } else { 'tests/Axioma-instrumentation.apk' }
        $testApk = Get-ArtifactPath $testRelative
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $testApk) | Out-Null
        if (Test-Path -LiteralPath $testApk) { throw 'Unmanaged instrumentation APK already exists.' }
        Move-Item -LiteralPath (Get-ArtifactPath ".build/$testBuildId/app/outputs/apk/androidTest/$variantLower/app-$variantLower-androidTest.apk") -Destination $testApk
        [void]$managedPaths.Add($testRelative)
        Save-ManifestCheckpoint
        $manifest.artifacts += Verify-Apk $testApk @() $true 'instrumentation'
        Save-ManifestCheckpoint
        $ready = @(& $adb devices | Where-Object { $_ -match '^\S+\s+device\s*$' } | ForEach-Object { ($_ -split '\s+')[0] })
        if ($LASTEXITCODE -ne 0) { throw 'ADB enumeration failed.' }
        if ($Serial) {
            foreach ($item in $Serial) { if ($item -notin $ready) { throw 'A selected device is not connected and authorized.' } }
            $ready = @($Serial)
        }
        $index = 0
        foreach ($item in $ready) {
            $index++
            $device = Register-AndroidDevice $item "device-$index"
            Test-AndroidDevice $device $testApk
        }
        . (Join-Path $PSScriptRoot 'android-emulator.ps1')
        foreach ($emulatorApi in $EmulatorApis) {
            $instance = $null
            try {
                Write-Host "Starting dedicated Android API $emulatorApi emulator..."
                $instance = Start-AxiomaEmulator $emulatorApi
                $device = Register-AndroidDevice $instance.serial "emulator-api-$emulatorApi"
                $imageProperties = Join-Path $sdk "system-images\android-$emulatorApi\default\x86_64\package.xml"
                $imageXml = [xml][IO.File]::ReadAllText($imageProperties)
                $imageRevision = @($imageXml.SelectNodes("//*[local-name()='localPackage']/*[local-name()='revision']/*") | ForEach-Object { $_.InnerText }) -join '.'
                $manifest.emulator_environments += [pscustomobject]@{ api = $emulatorApi; status = 'booted'; page_size = $device.page_size; abis = $device.abis
                    image_package = "system-images;android-$emulatorApi;default;x86_64"; image_revision = $imageRevision }
                Test-AndroidDevice $device $testApk
            } catch {
                $reason = Protect-Text $_.Exception.Message
                $manifest.emulator_environments += [pscustomobject]@{ api = $emulatorApi; status = 'blocked'; reason = $reason }
                $manifest.limitations += "API $emulatorApi emulator blocked: $reason"
                Write-Warning "API $emulatorApi emulator blocked: $reason"
            } finally {
                if ($instance) {
                    try { Stop-AxiomaEmulator $instance }
                    catch {
                        $criticalFailures++
                        $reason = Protect-Text $_.Exception.Message
                        $manifest.limitations += "API $emulatorApi emulator cleanup failed: $reason"
                        Write-Warning "API $emulatorApi emulator cleanup failed: $reason"
                    }
                }
                Save-ManifestCheckpoint
            }
        }
    }
} catch {
    $criticalFailures++
    $manifest.fatal_error = Protect-Text $_.Exception.Message
    Write-Warning $manifest.fatal_error
} finally {
    foreach ($api in 33..36) {
        foreach ($abi in $Abis) {
            $build = @($manifest.builds | Where-Object { $_.id -eq $abi -and $_.status -eq 'passed' })
            $expectedArtifact = Get-AppRelativePath $abi
            $runs = @($manifest.runtime_attempts | Where-Object { $_.api -eq $api -and $_.abi -eq $abi -and $_.artifact -eq $expectedArtifact })
            $passedRuns = @($runs | Where-Object status -eq 'passed')
            $status = if ($build.Count -eq 0 -or @($runs | Where-Object status -eq 'failed').Count) { 'failed' }
                elseif ($passedRuns.Count) { 'passed' } elseif ($TestConnectedDevices) { 'blocked' } else { 'not_executed' }
            $manifest.matrix += [pscustomobject]@{ android = $api - 20; api = $api; abi = $abi
                compilation = $(if ($build.Count) { 'passed' } else { 'failed' })
                installation = $(if ($runs.Count) { ($runs.installation | Select-Object -Unique) -join ',' } else { 'not_executed' })
                functional_tests = $(if ($runs.Count) { ($runs.tests | Select-Object -Unique) -join ',' } else { 'not_executed' })
                status = $status; evidence = @($runs.log)
                limitation = $(if (-not $runs.Count) { 'No matching tested Android API/ABI environment in this run.' } else { 'Only the recorded devices and page sizes were tested.' }) }
        }
    }
    $incomplete = @($manifest.matrix | Where-Object status -ne 'passed').Count
    if ($RequireCompleteMatrix -and $incomplete) { $criticalFailures++ }
    $manifest.critical_failures = $criticalFailures
    $manifest.status = if ($criticalFailures) { 'failed' } elseif ($incomplete) { 'partially_verified' } else { 'passed' }
    $manifest.finished_utc = [DateTime]::UtcNow.ToString('o')
    $report = @('# Android build and validation report', '', "Status: $($manifest.status)", '',
        "Configuration: minSdk 26, compileSdk 36, targetSdk 36; $variantLower $versionName.",
        'Static native/ZIP/signature inspection does not prove runtime compatibility.', '',
        '## Artifacts', '', '| Path | ABI | Bytes | SHA-256 |', '| --- | --- | ---: | --- |')
    foreach ($artifact in $manifest.artifacts) { $report += "| $($artifact.path) | $($artifact.abis -join ', ') | $($artifact.size_bytes) | $($artifact.sha256) |" }
    $report += @('', '## Android/ABI matrix', '', '| Android | API | ABI | Build | Install | Functional tests | Status |', '| --- | --- | --- | --- | --- | --- | --- |')
    foreach ($cell in $manifest.matrix) { $report += "| $($cell.android) | $($cell.api) | $($cell.abi) | $($cell.compilation) | $($cell.installation) | $($cell.functional_tests) | $($cell.status) |" }
    $report += @('', '## Runtime evidence', '')
    foreach ($attempt in $manifest.runtime_attempts) {
        $report += "- $($attempt.device), API $($attempt.api), $($attempt.abi), $($attempt.artifact): $($attempt.status); $($attempt.passed) passed, $($attempt.failed) failed; [$($attempt.log)]($(Split-Path -Leaf $attempt.log))."
    }
    $report += @('', '## Limitations', '') + @($manifest.limitations | ForEach-Object { '- ' + $_ })
    $report += @('- Five preexisting mathematical defect groups remain open; this is not a stable mathematics certification.',
        '- Historical audit evidence is in docs/development/validation.md and is not counted as a new matrix run.',
        '- No commits, tags, uploads or publication are performed by this script.')
    if ($manifest.Contains('fatal_error')) { $report += '- Critical failure: ' + $manifest.fatal_error }
    if ($lock -and $canWriteOutputs) {
        Write-ReportText 'reports/validation-report.md' ($report -join "`n")
        Save-ManifestCheckpoint
    }
    if ($lock) { $lock.Dispose() }
    Pop-Location
}
Write-Host "Matrix status: $($manifest.status); critical failures: $criticalFailures."
if ($criticalFailures) { exit 1 }
exit 0
