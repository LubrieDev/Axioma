[CmdletBinding()]
param()
# SPDX-License-Identifier: GPL-3.0-only
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$privateRoot = Join-Path $projectRoot '.dev'
$jdk = $env:JAVA_HOME
if (-not $jdk) { $jdk = Split-Path -Parent (Split-Path -Parent (Get-Command java).Source) }
$jdk = [IO.Path]::GetFullPath($jdk).TrimEnd('\', '/')
if (-not (Test-Path (Join-Path $jdk 'release')) -or
    [IO.File]::ReadAllText((Join-Path $jdk 'release')) -notmatch 'JAVA_VERSION="17\.') {
    throw 'Select the installed JDK 17 with JAVA_HOME first. The Java language server may keep its newer embedded JRE.'
}
$database = Join-Path $projectRoot 'artifacts\android\.build\arm64-v8a\native\tools\debug\arm64-v8a\compile_commands.json'
if (-not (Test-Path -LiteralPath $database)) { throw 'Build the ARM64 debug variant once to obtain the actual NDK compilation database.' }
$entries = @(Get-Content -LiteralPath $database -Raw | ConvertFrom-Json)
if ($entries.Count -ne 10) { throw 'Unexpected native compilation database; review it before configuring IntelliSense.' }
$editorRoot = Join-Path $privateRoot 'vscode'
New-Item -ItemType Directory -Force -Path $editorRoot | Out-Null
$compiler = Join-Path $privateRoot 'tools\android-sdk\ndk\28.2.13676358\toolchains\llvm\prebuilt\windows-x86_64\bin\clang++.exe'
$sysroot = Join-Path (Split-Path -Parent (Split-Path -Parent $compiler)) 'sysroot'
if (-not (Test-Path -LiteralPath $compiler)) { throw 'The pinned Android NDK compiler is missing.' }
$workspacePath = Join-Path $privateRoot 'Axioma.code-workspace'
$settings = [ordered]@{}
if (Test-Path -LiteralPath $workspacePath) {
    $workspace = Get-Content -LiteralPath $workspacePath -Raw | ConvertFrom-Json
    foreach ($property in $workspace.settings.PSObject.Properties) { $settings[$property.Name] = $property.Value }
} elseif (Test-Path (Join-Path $editorRoot 'settings.json')) {
    $previous = Get-Content (Join-Path $editorRoot 'settings.json') -Raw | ConvertFrom-Json
    foreach ($property in $previous.PSObject.Properties) { $settings[$property.Name] = $property.Value }
}
$settings['java.import.gradle.java.home'] = $jdk
$settings['java.import.gradle.user.home'] = Join-Path $privateRoot 'tools\gradle'
$settings['java.configuration.runtimes'] = @(@{name='JavaSE-17';path=$jdk;default=$true})
$settings['C_Cpp.default.compilerPath'] = $compiler
$settings['C_Cpp.default.compilerArgs'] = @('--target=aarch64-none-linux-android26', "--sysroot=$sysroot")
$settings['C_Cpp.default.intelliSenseMode'] = 'linux-clang-arm64'
$settings['C_Cpp.default.cppStandard'] = 'c++20'
$settings['C_Cpp.default.compileCommands'] = '${workspaceFolder}/.dev/vscode/compile_commands.json'
$settings['C_Cpp.default.includePath'] = @('${workspaceFolder}/app/src/main/cpp')
$settings['terminal.integrated.env.windows'] = @{JAVA_HOME=$jdk;GRADLE_USER_HOME=(Join-Path $privateRoot 'tools\gradle')}
$content = [ordered]@{folders=@(@{path='..'});settings=$settings} | ConvertTo-Json -Depth 12
if (Test-Path -LiteralPath $workspacePath) {
    $backup = Join-Path $privateRoot ('backups\vscode-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-ffff'))
    New-Item -ItemType Directory -Force -Path $backup | Out-Null
    Copy-Item -LiteralPath $workspacePath -Destination $backup
}
Copy-Item -LiteralPath $database -Destination (Join-Path $editorRoot 'compile_commands.json') -Force
[IO.File]::WriteAllText($workspacePath, $content, [Text.UTF8Encoding]::new($false))
$properties = Join-Path $privateRoot 'tools\gradle\gradle.properties'
$existing = if (Test-Path -LiteralPath $properties) { [IO.File]::ReadAllText($properties) } else { '' }
$pointer = 'org.gradle.java.home=' + $jdk.Replace('\', '/')
if ($existing -match '(?m)^\s*org\.gradle\.java\.home\s*=') {
    if ($existing -notmatch [regex]::Escape($pointer)) { throw 'A different local Gradle JDK is already configured; it was preserved.' }
} else {
    [IO.File]::WriteAllText($properties, ($existing.TrimEnd() + "`n" + $pointer + "`n"), [Text.UTF8Encoding]::new($false))
}
Write-Host 'Open .dev/Axioma.code-workspace in VS Code. Gradle uses JDK 17; the Java language server keeps its own runtime.'
Write-Host 'IntelliSense uses the actual ARM64 NDK compiler and ten native translation units. No global editor settings were changed.'
