[CmdletBinding()]
param(
    [switch]$InitializeSigningKey,
    [switch]$TestConnectedDevices,
    [ValidateSet(33,34,35,36)][int[]]$EmulatorApis = @(),
    [string[]]$Serial
)
# SPDX-License-Identifier: GPL-3.0-only
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$signingRoot = Join-Path $projectRoot '.dev\signing'
$credentialsPath = Join-Path $signingRoot 'credentials.dpapi.json'
$storePath = Join-Path $signingRoot 'axioma-release.p12'
$certificatePath = Join-Path $signingRoot 'certificate.der'
$jdk = $env:JAVA_HOME
if (-not $jdk) { $jdk = Split-Path -Parent (Split-Path -Parent (Get-Command java).Source) }
$keytool = Join-Path $jdk 'bin\keytool.exe'
if (-not (Test-Path -LiteralPath $keytool)) { throw 'JDK keytool is required.' }
$environmentNames = @('AXIOMA_KEYSTORE_PATH','AXIOMA_KEYSTORE_PASSWORD','AXIOMA_KEY_ALIAS','AXIOMA_KEY_PASSWORD')
$previousEnvironment = @{}
foreach ($name in $environmentNames) { $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
Push-Location $projectRoot
try {
    if (Test-Path -LiteralPath (Join-Path $projectRoot 'artifacts\android\release\0.1.0\SHA256SUMS.txt')) {
        throw 'The completed 0.1.0 release folder is preserved. Do not overwrite an existing release; archive it explicitly or prepare a new version.'
    }
    if ($InitializeSigningKey) {
        if ((Test-Path -LiteralPath $storePath) -or (Test-Path -LiteralPath $credentialsPath)) {
            throw 'Signing material already exists. It was preserved; omit InitializeSigningKey to reuse it.'
        }
        New-Item -ItemType Directory -Force -Path $signingRoot | Out-Null
        $identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
        $acl = Get-Acl -LiteralPath $signingRoot
        $acl.SetAccessRuleProtection($true,$false)
        foreach ($sid in @($identity,[Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
            $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid,'FullControl','ContainerInherit,ObjectInherit','None','Allow'))
        }
        Set-Acl -LiteralPath $signingRoot -AclObject $acl
        $random = New-Object byte[] 48
        $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $generator.GetBytes($random) } finally { $generator.Dispose() }
        $password = [Convert]::ToBase64String($random)
        $env:AXIOMA_KEYSTORE_PASSWORD = $password
        $protected = ConvertTo-SecureString $password -AsPlainText -Force | ConvertFrom-SecureString
        # Save recovery material before key generation; partial state is never overwritten.
        [ordered]@{alias='axioma-release';store='axioma-release.p12';password_dpapi=$protected;protection='Windows current-user DPAPI';created_utc=[DateTime]::UtcNow.ToString('o')} |
            ConvertTo-Json | Set-Content -LiteralPath $credentialsPath -Encoding utf8
        & $keytool '-J-Duser.language=en' '-J-Duser.country=US' -genkeypair -storetype PKCS12 -keystore $storePath `
            -alias axioma-release -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -validity 10000 `
            -dname 'CN=Axioma' '-storepass:env' AXIOMA_KEYSTORE_PASSWORD '-keypass:env' AXIOMA_KEYSTORE_PASSWORD | Out-Host
        if ($LASTEXITCODE -ne 0) { throw 'Permanent signing-key generation failed; partial files were preserved.' }
        $password = $null
    }
    if (-not (Test-Path -LiteralPath $storePath) -or -not (Test-Path -LiteralPath $credentialsPath)) {
        throw 'No complete private signing identity exists. Use InitializeSigningKey once, or restore the existing identity.'
    }
    $credentials = Get-Content -LiteralPath $credentialsPath -Raw | ConvertFrom-Json
    $secure = ConvertTo-SecureString $credentials.password_dpapi
    $env:AXIOMA_KEYSTORE_PASSWORD = [Net.NetworkCredential]::new('', $secure).Password
    $env:AXIOMA_KEY_PASSWORD = $env:AXIOMA_KEYSTORE_PASSWORD
    $env:AXIOMA_KEYSTORE_PATH = $storePath
    $env:AXIOMA_KEY_ALIAS = $credentials.alias
    & $keytool '-J-Duser.language=en' '-J-Duser.country=US' -exportcert -keystore $storePath -alias $credentials.alias `
        '-storepass:env' AXIOMA_KEYSTORE_PASSWORD -file $certificatePath | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'Could not verify/export the permanent public certificate.' }
    $fingerprint = (Get-FileHash -LiteralPath $certificatePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $projectRoot '.dev\tools\gradle' }
    $arguments = @{Variant='Release';ExpectedSignerSha256=$fingerprint;IncludeUniversal=$true;TestConnectedDevices=[bool]$TestConnectedDevices;EmulatorApis=$EmulatorApis}
    if ($Serial) { $arguments.Serial = $Serial }
    & (Join-Path $PSScriptRoot 'build-android-matrix.ps1') @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Production build/validation failed. Do not publish the partial outputs.' }
    & py -3 (Join-Path $PSScriptRoot 'prepare-release.py')
    if ($LASTEXITCODE -ne 0) { throw 'Release-folder verification failed.' }
    Write-Host 'Production assets: artifacts/android/release/0.1.0. No commit or upload was performed.'
} finally {
    foreach ($name in $environmentNames) { [Environment]::SetEnvironmentVariable($name,$previousEnvironment[$name],'Process') }
    Pop-Location
}
