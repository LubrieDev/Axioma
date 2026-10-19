[CmdletBinding()]
param(
    [switch]$AcceptLicenses,
    [ValidateSet(33, 34, 35, 36)]
    [int[]]$EmulatorApis = @()
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolsRoot = Join-Path $projectRoot '.dev\tools'
$sdkRoot = Join-Path $toolsRoot 'android-sdk'
$downloads = Join-Path $toolsRoot 'downloads'
$sdkManager = Join-Path $sdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw 'Install JDK 17 and configure JAVA_HOME before continuing.'
}

New-Item -ItemType Directory -Force -Path $downloads,$sdkRoot | Out-Null

if (-not (Test-Path -LiteralPath $sdkManager)) {
    # Pinned tools compatible with JDK 17. Checksum published by Google in:
    # https://dl.google.com/android/repository/repository2-1.xml
    $archiveName = 'commandlinetools-win-13114758_latest.zip'
    $archive = Join-Path $downloads $archiveName
    $expectedSha1 = '54a582f3bf73e04253602f2d1c80bd5868aac115'
    $validArchive = (Test-Path -LiteralPath $archive) -and (
        (Get-FileHash -Algorithm SHA1 -LiteralPath $archive).Hash -eq $expectedSha1
    )
    if (-not $validArchive) {
        Write-Host 'Downloading Android Command-line Tools from Google...'
        $uri = 'https://dl.google.com/android/repository/' + $archiveName
        if (Get-Command curl.exe -ErrorAction SilentlyContinue) {
            & curl.exe --fail --location --retry 3 --silent --show-error --output $archive $uri
            if ($LASTEXITCODE -ne 0) { throw 'Could not download the Android tools.' }
        } else {
            Invoke-WebRequest -UseBasicParsing -Uri $uri -OutFile $archive
        }
    }
    if ((Get-FileHash -Algorithm SHA1 -LiteralPath $archive).Hash -ne $expectedSha1) {
        throw 'The Android Command-line Tools checksum does not match the value published by Google.'
    }

    $unpacked = Join-Path $downloads 'command-line-tools-19.0'
    Expand-Archive -LiteralPath $archive -DestinationPath $unpacked -Force
    $commandLineRoot = Join-Path $sdkRoot 'cmdline-tools'
    New-Item -ItemType Directory -Force -Path $commandLineRoot | Out-Null
    Copy-Item -LiteralPath (Join-Path $unpacked 'cmdline-tools') `
        -Destination (Join-Path $commandLineRoot 'latest') -Recurse -Force
}

$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
if (-not $env:GRADLE_USER_HOME) {
    $env:GRADLE_USER_HOME = Join-Path $toolsRoot 'gradle'
}

$packages = @(
    'platform-tools',
    'platforms;android-36',
    'build-tools;35.0.0',
    'ndk;28.2.13676358',
    'cmake;3.22.1'
)
if ($EmulatorApis.Count) {
    $packages += 'emulator'
    $packages += @($EmulatorApis | Select-Object -Unique | ForEach-Object { "system-images;android-$_;default;x86_64" })
}
Write-Host 'Installing the SDK, NDK, and CMake; accept the requested licenses to continue.'
if ($AcceptLicenses) {
    # Only use this switch if you have read and accepted the SDK licenses.
    1..20 | ForEach-Object { 'y' } | & $sdkManager "--sdk_root=$sdkRoot" @packages
} else {
    & $sdkManager "--sdk_root=$sdkRoot" @packages
}
if ($LASTEXITCODE -ne 0) { throw 'Could not install all required SDK components.' }

$expectedFiles = @(
    'platform-tools\adb.exe',
    'platforms\android-36\android.jar',
    'build-tools\35.0.0\aapt2.exe',
    'ndk\28.2.13676358\source.properties',
    'cmake\3.22.1\bin\cmake.exe'
)
foreach ($relativePath in $expectedFiles) {
    if (-not (Test-Path -LiteralPath (Join-Path $sdkRoot $relativePath))) {
        throw "Missing component: $relativePath. Review the licenses and run setup again."
    }
}

$sdkProperty = 'sdk.dir=' + $sdkRoot.Replace('\', '/').Replace(':', '\:') + "`n"
$localProperties = Join-Path $projectRoot 'local.properties'
if (Test-Path -LiteralPath $localProperties) {
    if ([IO.File]::ReadAllText($localProperties).Trim() -ne $sdkProperty.Trim()) {
        throw 'Existing local.properties differs from the local SDK pointer. Review it manually; it was not overwritten.'
    }
} else {
    [IO.File]::WriteAllText($localProperties, $sdkProperty)
}
Write-Host 'SDK and NDK ready. Build with .\scripts\build-android-matrix.ps1'
