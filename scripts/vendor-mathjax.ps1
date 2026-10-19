[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$vendorRoot = Join-Path $projectRoot 'app\src\main\assets\renderer\vendor'
$manifestPath = Join-Path $vendorRoot 'manifest.json'
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$downloadRoot = Join-Path $projectRoot '.dev\tools\mathjax-vendor'
New-Item -ItemType Directory -Path $downloadRoot -Force | Out-Null

foreach ($package in $manifest.packages) {
    $shortName = ($package.name -split '/')[-1]
    $archive = Join-Path $downloadRoot ($shortName + '-' + $package.version + '.tgz')
    Invoke-WebRequest -Uri $package.tarball -OutFile $archive
    $expected = [Convert]::ToHexString([Convert]::FromBase64String(($package.integrity -split '-', 2)[1]))
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA512).Hash -ne $expected) {
        throw "Package integrity verification failed: $($package.name)"
    }
    $destination = Join-Path $downloadRoot $shortName
    New-Item -ItemType Directory -Path $destination -Force | Out-Null
    $members = & tar -tf $archive
    if ($LASTEXITCODE -ne 0) { throw 'Could not inspect the package archive.' }
    foreach ($member in $members) {
        if (-not $member.StartsWith('package/') -or $member -match '(^|/)\.\.(/|$)' -or $member.Contains('\')) {
            throw "Unexpected archive path: $member"
        }
    }
    & tar -xf $archive -C $destination
    if ($LASTEXITCODE -ne 0) { throw 'Could not extract the package archive.' }
}

$mathjax = Join-Path $downloadRoot 'mathjax\package'
$font = Join-Path $downloadRoot 'mathjax-newcm-font\package'
$mathjaxTarget = Join-Path $vendorRoot 'mathjax'
$fontTarget = Join-Path $vendorRoot 'fonts\mathjax-newcm-font'
New-Item -ItemType Directory -Path $mathjaxTarget, $fontTarget -Force | Out-Null
foreach ($name in @('tex-svg.js', 'package.json', 'LICENSE')) {
    Copy-Item -LiteralPath (Join-Path $mathjax $name) -Destination (Join-Path $mathjaxTarget $name) -Force
}
New-Item -ItemType Directory -Path (Join-Path $mathjaxTarget 'a11y') -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $mathjax 'a11y\assistive-mml.js') -Destination (Join-Path $mathjaxTarget 'a11y\assistive-mml.js') -Force
foreach ($name in @('svg.js', 'package.json')) {
    Copy-Item -LiteralPath (Join-Path $font $name) -Destination (Join-Path $fontTarget $name) -Force
}
# Both pinned packages declare Apache-2.0. The font archive omits its license text;
# include the unmodified Apache text shipped by the matching MathJax distribution.
Copy-Item -LiteralPath (Join-Path $mathjax 'LICENSE') -Destination (Join-Path $fontTarget 'LICENSE') -Force
foreach ($file in Get-ChildItem -LiteralPath (Join-Path $font 'svg') -Recurse -File) {
    $relative = [IO.Path]::GetRelativePath($font, $file.FullName)
    $destination = Join-Path $fontTarget $relative
    New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
    Copy-Item -LiteralPath $file.FullName -Destination $destination -Force
}

# Verify the recorded file hashes rather than silently updating the lock manifest.
foreach ($entry in $manifest.files) {
    $asset = Join-Path $vendorRoot $entry.path
    if ((Get-FileHash -LiteralPath $asset -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.sha256) {
        throw "Vendored asset does not match the manifest: $($entry.path)"
    }
}
Write-Output 'Pinned offline MathJax assets restored and verified.'
