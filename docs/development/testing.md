# Testing and interpreting evidence

## Documentation and distribution boundary

```powershell
python scripts/check-documentation.py
```

The standard-library-only checker reads Git candidates without staging them. It
checks local Markdown targets/headings, licensing metadata, private boundaries,
pinned renderer hashes and clear temporary/private declarations in owned text.
It does not evaluate mathematics or prove legal compliance.

## Existing Android suite

```powershell
.\scripts\test-android.ps1
.\scripts\test-android.ps1 -Serial YOUR_SERIAL
```

The script builds app/test APKs, runs lint and verifyOwnRuntime, verifies pinned
assets, installs through ADB, runs platform instrumentation and opens Axioma. It
writes ignored logs, APKs and a central manifest under `artifacts/android/`.
This is not a read-only command. The custom runner
uses Instrumentation without JUnit/AndroidX.

It checks C++/JNI, parsing, modes, diagnostics, precision, continuation, keyboard,
persistence, history and lifecycle; actual offline MathJax SVG, controls, stale
requests, invalid TeX recovery, injection handling, sizing, equals and scrolling.

Some ordinary smoke tests use double references with tolerance. Precision checks
compare decimal strings directly. BigInteger/BigDecimal oracles are test-only.
Expected mathematics must be derived independently, not from another path that
shares the implementation's bug.

## Reproducible API/ABI validation

```powershell
.\scripts\build-android-matrix.ps1 -IncludeUniversal -TestConnectedDevices -EmulatorApis @(33,34,35,36)
```

The existing 1280-check suite was run before extending it. The current suite adds
20 checks for explicit matrix examples, all four JNI entry points across 128
repetitions, malformed input, typed domain errors, absence of Internet permission,
and actual in-app SVG presentation of `(40+4)^2`, `3*sqrt(6)*sqrt(42)` and `2^10`.
These expected results are 1936, `18*sqrt(7)` and 1024. An independent ordinary
double oracle checks the numerical radical; precision tests retain string oracles.
The engine and its exactness criteria are unchanged.

Each applicable application APK is installed with an explicit ABI, its selected
Android package ABI and on-device application/test APK SHA-256 are checked, its activity is launched, and instrumentation
records API, process architecture/bitness and page size. Each run must complete
with at least 1300 passing checks and zero failures. The universal APK is tested
separately where a matching runtime exists. Settings/history/keyboard/lifecycle
and packaged MathJax checks are retained, including security and invalid-TeX cases.

One initial API 33 universal run recorded 1299 passes and one unreadable-renderer
failure immediately after activity recreation. The inspection helper previously
referenced `MathJax.version` before the recreated document necessarily defined it.
It now waits up to 15 seconds for that document/global before inspecting it;
all original value, diagnostic, layout and SVG assertions remain unchanged.
The failed observation is retained separately in generated reports, not erased
or recounted as a pass. Emulator log redaction also uses separate readable raw
intermediates rather than overwriting redirected output files while handles close.

The manifest distinguishes static verification, installation, launch and functional
results. Missing devices/images are `blocked` or `not_executed`, never passed.
Critical build, signature, ELF/JNI/content or functional failures return nonzero;
`-RequireCompleteMatrix` also treats any unverified requested cell as failure.
Build-only mode intentionally needs no device. See [matrix details](android-matrix.md).

## Regeneration and focused commands

Production APKs use the same suite with Release instrumentation and their own
application ID. Use `scripts/build-release.ps1` as described in
[production releases](releasing.md); a passing debug run is not attributed to a
different production APK or signing identity.

```powershell
python scripts/generate-precision-fixtures.py
.\gradlew.bat :app:verifyOwnRuntime --console=plain
.\gradlew.bat assembleDebug lintDebug --console=plain
```

Regeneration rewrites `app/src/androidTest/assets/precision-cases.json`. Python
Decimal at 160 digits uses Machin pi and independent elementary/trig references;
Python never enters the APK. An expectation change needs mathematical justification.

To audit an already installed test APK without rebuilding project files:

```powershell
.\.dev\tools\android-sdk\platform-tools\adb.exe -s YOUR_SERIAL shell am instrument -w -r org.axioma.calculator.debug.test/org.axioma.calculator.SmokeInstrumentation
```

Tests still exercise on-device state. Verify installed APK identity before
attributing results to current source.

## Regression expectations

Reproduce [known defects](../user/known-issues.md) in both result formats and
relevant angle modes. Check values, statuses, exact source, decimal strings,
LaTeX and its semantic reuse. Check real rendering/restoration for UI changes.

The historical 1280-passing suite did not catch the additional audit defects.
[Validation](validation.md) records this gap. Temporary audit probes/raw reports
are not maintained test tools; these published commands do not claim to reproduce
all independent audit cases. No complete line/branch coverage or universal
accuracy proof has been established.
