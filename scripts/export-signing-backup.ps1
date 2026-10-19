[CmdletBinding()]
param()
# SPDX-License-Identifier: GPL-3.0-only
# Run interactively in your own terminal. Passwords are never printed or logged.
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$signing = Join-Path $root '.dev\signing'
$record = Get-Content (Join-Path $signing 'credentials.dpapi.json') -Raw | ConvertFrom-Json
$backup = Join-Path $signing ('backups\Axioma-release-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '.p12')
if (Test-Path -LiteralPath $backup) { throw 'The backup destination exists; it was preserved.' }
$password = Read-Host 'Choose a password for the portable signing-key backup' -AsSecureString
$confirmation = Read-Host 'Confirm the portable backup password' -AsSecureString
$backupPassword = [Net.NetworkCredential]::new('', $password).Password
if ($backupPassword.Length -lt 16 -or $backupPassword -cne [Net.NetworkCredential]::new('', $confirmation).Password) {
    throw 'Use matching passwords of at least 16 characters.'
}
$sourcePrevious = $env:AXIOMA_BACKUP_SOURCE_PASSWORD
$destinationPrevious = $env:AXIOMA_BACKUP_PASSWORD
try {
    $env:AXIOMA_BACKUP_SOURCE_PASSWORD = [Net.NetworkCredential]::new('', (ConvertTo-SecureString $record.password_dpapi)).Password
    $env:AXIOMA_BACKUP_PASSWORD = $backupPassword
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $backup) | Out-Null
    $keytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
    & $keytool -importkeystore -srckeystore (Join-Path $signing $record.store) -srcstoretype PKCS12 `
        '-srcstorepass:env' AXIOMA_BACKUP_SOURCE_PASSWORD -srcalias $record.alias `
        -destkeystore $backup -deststoretype PKCS12 '-deststorepass:env' AXIOMA_BACKUP_PASSWORD `
        '-destkeypass:env' AXIOMA_BACKUP_PASSWORD -noprompt
    if ($LASTEXITCODE -ne 0) { throw 'Portable backup export failed.' }
    Write-Host 'Portable backup created under .dev/signing/backups. Store it off this computer and keep its password in your password manager.'
} finally {
    $env:AXIOMA_BACKUP_SOURCE_PASSWORD = $sourcePrevious
    $env:AXIOMA_BACKUP_PASSWORD = $destinationPrevious
    $backupPassword = $null
}
