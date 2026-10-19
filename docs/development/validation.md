# Android compatibility and mathematical validation

## Production signing and editor configuration: 2026-10-18

Production builds now use version 0.1.0, the base application ID and a permanent
private signing identity. Five non-debuggable application APKs were compiled and
checked for the expected public certificate, ABI, native libraries and renderer.
The latest production execution evidence is generated separately in
`artifacts/android/manifests/build-manifest.json`, `reports/validation-report.md`
and the release folder's `COMPATIBILITY.md`. Debug observations below are not
attributed to production APKs. See [production releases](releasing.md).

The signed production artifacts completed ten full 1300-check instrumentation
executions: 13000 passing observations, zero failures. The x86_64 and universal
APKs passed on official Android 13–16 x86_64 emulators; the ARM64 and universal
APKs passed on the physical Android 16 phone. The phone connected after the
initial eight emulator runs, so the two phone executions reused the actual
maintained matrix functions against the unchanged signed APKs. Installed app/test
hashes were checked again. ARMv7/x86 and ARM64 Android 13–15 remain unexecuted;
16 KB runtime remains unverified. All actual environments used 4 KB pages.

The supplied VS Code log used a Java 25 Gradle daemon with Gradle 8.13, causing
repeated unsupported-class-version failures. A private workspace now selects
JDK 17 for Gradle while retaining the newer Java language-server runtime. With
the actual Java 25 launcher and the private JDK 17 daemon pointer, Gradle version
inspection and full task discovery succeeded. External Unsafe/native-access
warnings are not native source compilation errors.

All ten native translation units and all thirteen headers were independently
checked with the actual ARM64 NDK compiler in C++20 syntax-only mode: 23 passed,
zero failed. IntelliSense receives that compiler, Android target/sysroot and the
real compilation database. The editor's Problems panel itself was not inspected
after reloading; open `.dev/Axioma.code-workspace` to apply the local settings.
No diagnostic suppression or mathematical engine changes were introduced.

## Android matrix campaign: 2026-10-18

These are local debug builds of the 0.1.0 development tree, not a release
or tagged commit. The final matrix uses centralized Gradle and native staging.
Manifest status: `partially_verified`; critical failures: 0.
Final run UTC: `2026-10-19T03:20:26.6369396Z` to `2026-10-19T03:34:19.1658394Z`.

The SDK values were inspected and retained: minSdk 26, compileSdk 36 and
targetSdk 36, with versionName `0.1.0-dev` and versionCode 4. minSdk is an
installation floor; compile/target SDK values are not runtime evidence.
The same APKs were reused across Android versions; no targetSdk-only variants
were created. All 24 native source/build files match their pre-task hashes.

### Actual tools

| Tool | Observed version |
| --- | --- |
| jdk | openjdk version "17.0.20.1" 2026-08-18 |
| python | Python 3.14.8 |
| powershell | 7.6.7 |
| gradle | 8.13 |
| agp | 8.13.2 |
| ndk | 28.2.13676358 |
| cmake | 3.22.1 |
| build_tools | 35.0.0 |
| platform_tools | 37.0.1 |
| emulator | 37.2.12 |

### Builds and native inspection

Four independent application APKs were built for ARM64, ARMv7, x86_64 and
x86. A separate universal build contains all four native libraries; a sixth
APK contains the instrumentation runner. Per-ABI APKs are the default for
attributable native validation; the universal is an optional sideload build.
No AAB or production-signed release was produced.

Every APK passed package/SDK/signature/ZIP inspection. Every application
passed exact ABI/native-library checks and all 47 pinned offline renderer
hashes. All four real libaxioma.so variants expose parse, evaluate,
evaluateExact and calculateMode. Their only dynamic dependencies are the
Android system libraries libc, libm and libdl; the C++ runtime is static.
64-bit ELF PT_LOAD alignment is 16384 bytes and 32-bit alignment is 4096.
ZIP alignment was checked for 16 KB. This is static evidence; every runtime
in this campaign used 4096-byte pages. No 16 KB runtime is verified.

### Actual runtime matrix

| Android | API | ABI | Build | Installation | Functional | Final status |
| --- | --- | --- | --- | --- | --- | --- |
| 13 | 33 | arm64-v8a | passed | not_executed | not_executed | blocked |
| 13 | 33 | armeabi-v7a | passed | not_executed | not_executed | blocked |
| 13 | 33 | x86_64 | passed | passed | passed | passed |
| 13 | 33 | x86 | passed | not_executed | not_executed | blocked |
| 14 | 34 | arm64-v8a | passed | not_executed | not_executed | blocked |
| 14 | 34 | armeabi-v7a | passed | not_executed | not_executed | blocked |
| 14 | 34 | x86_64 | passed | passed | passed | passed |
| 14 | 34 | x86 | passed | not_executed | not_executed | blocked |
| 15 | 35 | arm64-v8a | passed | not_executed | not_executed | blocked |
| 15 | 35 | armeabi-v7a | passed | not_executed | not_executed | blocked |
| 15 | 35 | x86_64 | passed | passed | passed | passed |
| 15 | 35 | x86 | passed | not_executed | not_executed | blocked |
| 16 | 36 | arm64-v8a | passed | passed | passed | passed |
| 16 | 36 | armeabi-v7a | passed | not_executed | not_executed | blocked |
| 16 | 36 | x86_64 | passed | passed | passed | passed |
| 16 | 36 | x86 | passed | not_executed | not_executed | blocked |

The final run completed 10 instrumentation executions: 13000 passing
check observations and 0 failed. Each completed suite has 1300 checks;
repeated observations are not independent mathematical test cases or a
coverage percentage. Both the per-ABI and universal APK were tested in each
eligible environment. Application and test APK SHA-256 were checked on the
device against their verified local files before instrumentation.

| Environment | Android/API | Advertised ABI | Page size |
| --- | --- | --- | --- |
| device-1 | 16/36 | arm64-v8a | 4096 |
| emulator-api-33 | 13/33 | x86_64 | 4096 |
| emulator-api-34 | 14/34 | x86_64 | 4096 |
| emulator-api-35 | 15/35 | x86_64 | 4096 |
| emulator-api-36 | 16/36 | x86_64 | 4096 |

Official AOSP image packages used the following installed revisions:

| API | Package | Revision |
| --- | --- | --- |
| 33 | `system-images;android-33;default;x86_64` | 2 |
| 34 | `system-images;android-34;default;x86_64` | 4 |
| 35 | `system-images;android-35;default;x86_64` | 2 |
| 36 | `system-images;android-36;default;x86_64` | 2 |

The dedicated headless emulators used working WHPX acceleration and ran
sequentially. The connected physical phone was Android 16/ARM64. These OS
images and the phone advertise only 64-bit ABIs. ARMv7/x86 compilation is
verified, but their JNI loading, installation and functional suites were not
executed. ARM64 on Android 13–15 was not executed either. No native result
is inferred from another ABI, an x86_64 emulator or a different page size.

### Functional scope and preserved failures

The unchanged existing suite first passed 1280 checks on the physical phone.
Twenty additional checks retain that suite and add explicit matrix examples,
128 repetitions of each of the four JNI entry points, malformed inputs, typed
domain errors and actual MainActivity/MathJax SVG verification. Required values
1936 for `(40+4)^2`, `18*sqrt(7)` for `3*sqrt(6)*sqrt(42)` and 1024 for `2^10`
were checked as results and in the app. Existing numerical precision, Exact/
Numerical settings, history, editing/deletion, keyboard, startup/lifecycle and
renderer security/recovery checks remain active. Offline rendering is enforced
by packaged assets and absence of the app's Internet permission.

Earlier failures remain evidence, separate from the final totals:

- `reports/prior-run-01/`: 5199 passes and one API 33 universal renderer
  inspection failure after activity recreation. The helper accessed the new
  document before MathJax existed; it now waits at most 15 seconds for that
  document/global. Original mathematical, layout and SVG assertions remain.
- `reports/prior-run-02/`: 5200 functional passes, zero failures, but emulator
  log cleanup failed because redirected files were still open. Separate raw
  streams and a shared-read log reader resolved the automation failure.
- `reports/prior-run-03/`: 13000 functional passes, zero failures, but ARMv7
  Gradle packaging failed while cleaning task outputs. Per-build project
  caches isolate cleanup histories. A recognized cleanup failure alone may
  retry the entire build/lint/dependency checks once, preserving both attempts.
- `reports/prior-run-04/`: the completed repetition before relocating CMake's
  staging directory; its build/runtime observations are retained separately.
- The final repetition uses `.build/<buildId>/native` and its final APK hashes.
  Earlier runtime totals are not added to the final-run totals above.

Five preexisting [mathematical defect groups](../user/known-issues.md) remain
open. This compatibility campaign does not certify a stable mathematical
engine, vendor-wide Android behavior, complete source coverage or 16 KB runtime
support. General symbolic expansion and step-by-step explanations remain
outside the implemented calculator scope.

### Evidence, output boundaries and reproduction

All distributable builds and generated verification records are ignored under
`artifacts/android/`. The single `manifests/build-manifest.json` records APK
sizes/SHA-256/native metadata, actual installations, device aliases, process
bitness, page sizes, per-run logs and owned output hashes. The readable report
is `reports/validation-report.md`; `reports/generated-files.txt` is this campaign's
inventory snapshot. The manifest's `generated_files` lists each later invocation's
owned outputs. Historical logs and automation control checks remain under `reports/`.
Tool downloads, user SDK pointers and dedicated AVD state remain private local
development material. The original native cache was preserved, not deleted.

Negative/control checks cover malformed ELF, wrong machine/packaged ABI,
insufficient 64-bit alignment, overlapping runs, path traversal/junctions, live
emulator log reading, duplicate ABI input and modified-output protection.
The final source-boundary review includes documentation, syntax, unchanged
native hashes and candidate/APK private-content scans. No files were staged,
committed, tagged or uploaded; no license or upstream notices were changed.

From a terminal at the project root, with JDK 17 and Python available:

```powershell
.\scripts\setup-android.ps1 -EmulatorApis @(33,34,35,36)
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.dev\tools\gradle'
.\scripts\build-android-matrix.ps1 -IncludeUniversal -TestConnectedDevices -EmulatorApis @(33,34,35,36)
py -3 scripts/check-documentation.py
```

SDK license acceptance stays interactive. Connect/authorize a phone for ARM64
coverage. Add `-RequireCompleteMatrix` to require all requested API/ABI cells;
it returns nonzero with this environment's unexecuted combinations. Critical
build, APK, JNI or functional failures return nonzero without that switch too.
See [setup](setup.md), [testing](testing.md) and [the matrix contract](android-matrix.md)
for prerequisites, cleanup boundaries and focused commands.

## Historical mathematical audit: 2026-10-18

This record predates the Android matrix above. Its independent audit battery was
not rerun as part of this compatibility campaign; the earlier environment limits
below describe that historical audit.

Scope: the 0.1.0 development working tree and matching installed ARM64 APK, not a
published release or tagged commit. Runtime code was not changed by subsequent
licensing/documentation organization.

The version label was subsequently normalized to 0.1.0. This is a naming
change, not a new mathematical test run.

| Evidence | Observed result |
| --- | --- |
| Existing Android instrumentation | 1280 passed, 0 failed |
| Independent inputs, Exact and Numerical | 539 expressions / 1078 checks |
| Main independent comparisons | 1014 correct; 34 expected unsupported-feature rejections; 30 discrepancies |
| Exact-source/original-LaTeX/result-LaTeX reuse | 1272 checks; 4 original-AST-LaTeX failures |
| Independent stored precision cross-check | All 106 references matched |
| Read-only pinned renderer verification | All 47 assets matched |
| ARM64 APK versus Android-target x86_64 static build under WSL | All 1078 mode and 1272 reuse observations matched |

Of the 30 discrepancies, 28 concern confirmed defects and two demonstrate a
previously documented conditioning limit. The four reuse failures are separate
checks. These counts are observations, not mathematical/source coverage percentages.

Independent oracles used Fraction, 220-digit Decimal, Gauss-Legendre pi, independent
trig series/reduction and exact rational quadratic/conjugate calculations. Seed:
`0x4158494F4D41`. Additional clients called production functions from the unchanged
native library, not a replacement engine.

### Scope and limits

Ordinary arithmetic, precedence, fractions, supported radical simplifications,
conjugates and numeric identities passed. Values 1936, 1296 and 96 establish
evaluation, not symbolic expansion or step-by-step derivations.

All five [defect groups](../user/known-issues.md) reproduced in the installed
ARM64 library. A green historical suite did not cover them; permanent regressions
remain needed.

Instrumentation exercised JNI, Settings, history, lifecycle and offline rendering.
The additional battery called native functions directly, without displaying every
expression or performing every clipboard gesture. No full-device matrix, complete
line/branch coverage, certified intervals or exhaustive performance proof exists.

### Maintained records and disposable evidence

This summary and concrete counterexamples are maintained documentation. Raw logs,
personal paths/serials, temporary plans, executable probes and session reports
are excluded from source distribution. The audit battery is not claimed as a
maintained command. Add independent regressions before fixing defects and record
a new dated result; do not reinterpret this historical suite as proof they passed.
