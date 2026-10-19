# Development setup

The supported terminal workflow uses Windows PowerShell and JDK 17. Android
Studio is optional. First setup needs network access and several GB of disk space.
All paths below are relative to the checkout.

Python 3.10 or newer is used for documentation, APK/ELF inspection and test-reference
generation, not application runtime. On Windows the `py -3` launcher can replace
`python` in the documented commands when needed.

## Install tools

Install/configure JDK 17 and make `java` available. From the repository root:

```powershell
.\scripts\setup-android.ps1
```

The script downloads checksum-verified official Command-line Tools, installs the
pinned SDK/NDK/CMake into `.dev/tools/android-sdk`, sets process-local Android
variables, and writes ignored `local.properties`. It does not change global PATH.
An existing differing `local.properties` is preserved and requires manual review.
Accept licenses interactively. Use `-AcceptLicenses` only after reading and
accepting the relevant terms. First builds download Gradle and the Android plugin.

| Component | Version |
| --- | --- |
| JDK | 17 |
| Gradle | 8.13 |
| Android Gradle Plugin | 8.13.2 |
| Compile / target SDK | 36 |
| Build Tools | 35.0.0 |
| NDK | 28.2.13676358 (r28c) |
| CMake | 3.22.1 |
| Minimum Android | API 26 / Android 8.0 |
| Default Gradle ABIs | arm64-v8a, x86_64 |
| Matrix ABI choices | arm64-v8a, armeabi-v7a, x86_64, x86 |

Build without a phone:

```powershell
.\scripts\build-android-matrix.ps1
.\scripts\build-android-matrix.ps1 -Abis @('arm64-v8a','x86_64') -IncludeUniversal
```

Outputs are centralized in `artifacts/android/`: `<abi>/Axioma-<abi>.apk`, an
optional `universal/Axioma-universal.apk`, `manifests/build-manifest.json` and
`reports/validation-report.md`. This entire generated tree is ignored by Git.
The matrix moves APKs into their canonical paths; it does not duplicate one APK
under several ABI names. Gradle intermediates stay in `.build/` under this tree.
Direct Gradle builds use `.build/default/app/` there, with CMake/Ninja staging in
`.build/default/native/`. Each matrix variant has equivalent isolated directories.
The development application
ID is `org.axioma.calculator.debug`; base ID: `org.axioma.calculator`. Debug builds
use local debug signing. Keep keys outside source. A signed release/distribution
pipeline is documented in [production releases](releasing.md).

`minSdk 26` controls the installation floor. `compileSdk 36` selects build-time
APIs; `targetSdk 36` selects the declared Android behavior contract. None of these
is evidence that an API/ABI was executed. All Android 13–16 checks use the same
APK configuration; no targetSdk-only variants are generated.

## USB installation

Enable USB debugging and authorize the computer. Scripts prefer the local SDK ADB.
If it is not on PATH:

```powershell
.\.dev\tools\android-sdk\platform-tools\adb.exe devices
.\scripts\run-android.ps1
.\scripts\run-android.ps1 -Serial YOUR_SERIAL
```

The script builds, installs with replacement and starts MainActivity. Select a
serial with multiple devices; no personal serial is hardcoded.
Both run/test wrappers now use the matrix for the selected device's primary ABI
and execute its complete instrumentation before opening the app.

## Android 13–16 emulators

Optional official AOSP x86_64 images can be installed without Android Studio:

```powershell
.\scripts\setup-android.ps1 -EmulatorApis @(33,34,35,36)
.\scripts\build-android-matrix.ps1 -IncludeUniversal -TestConnectedDevices -EmulatorApis @(33,34,35,36)
```

SDK license acceptance remains interactive unless explicitly accepted with the
existing switch. Images require several GB of disk. Windows must already provide
working emulator acceleration; the scripts do not enable Windows features, change
BIOS settings or reboot the host. Check it with
`.\.dev\tools\android-sdk\emulator\emulator.exe -accel-check`.

Dedicated Axioma AVDs/user settings live under `.dev/emulator/`. They boot headless
one at a time, use bounded boot waits and are stopped after testing. Existing user
AVDs are not overwritten or wiped. Only ABIs advertised by the booted image are
eligible for installation; missing 32-bit environments remain unverified.
See [the matrix contract](android-matrix.md) and [actual validation](validation.md).

## Tests and assets

Follow [testing](testing.md). Offline renderer assets are pinned in source;
ordinary builds need no npm installation. `scripts/vendor-mathjax.ps1` restores
verified upstream assets when deliberately maintaining them. It needs network
access and rewrites vendor files/manifest; it is not a prose-editing command.

The setup scripts target Windows. Other hosts can use compatible official tools,
but no equivalent bootstrap has been validated here. See
[repository policy](repository-policy.md) for generated/local exclusions.

## VS Code and Android IntelliSense

After an ARM64 debug build, configure the private workspace:

```powershell
.\scripts\configure-vscode.ps1
code .dev/Axioma.code-workspace
```

Open the workspace file rather than only the project folder. Its settings and a
copy of the actual compilation database remain under ignored `.dev/`. Gradle uses
JDK 17 and the private Gradle user cache; the Java language server retains its
own newer embedded runtime. Do not force that language server onto JDK 17.
The private Gradle user properties also pin its daemon to JDK 17.

IntelliSense uses the NDK clang++, ARM64 Android API 26 target, sysroot, C++20
and the ten actual compilation commands. The fallback supports headers too.
No diagnostics are disabled and no global editor preferences are changed.
Reopen the workspace, or use Developer: Reload Window after configuration.

`Unsupported class file major version 69` in the supplied Gradle log came from
running Gradle 8.13 with Java 25. Repeated configure failures shared that cause.
Unsafe/native-access warnings in the extension server originate in external
Java libraries and are distinct from application compile errors. Header
`C/C++(1696)` diagnostics indicate an unconfigured editor include environment;
verify actual compiler diagnostics separately. See [IntelliSense configuration](https://code.visualstudio.com/docs/cpp/configure-intellisense)
and [Gradle JVM settings](https://github.com/microsoft/vscode-gradle#compatibility-with-the-java-language-support-extension).
