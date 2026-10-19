# Source map

Paths are relative to the root. This map describes active code, not proposed features.

| Location | Responsibility |
| --- | --- |
| `app/src/main/cpp/expression.hpp`, `expression.cpp` | Tokens, parser, AST, numerical evaluation and input LaTeX |
| `big_integer.hpp/.cpp`, `rational.hpp/.cpp` in the C++ directory | Own exact arithmetic and charged work |
| `decimal.hpp/.cpp`, `real.hpp`, `constants.hpp` | Working decimals, constants and elementary functions |
| `exact_math.hpp/.cpp` | Affine-pi proof and quadrant recognition |
| `symbolic_math.hpp/.cpp` | Surds, exact function cases and symbolic fallback |
| `approximation.hpp/.cpp` | Independent 40/30-digit ties-to-even output |
| `calculation_result.hpp/.cpp` | Main pipeline, bounded display and continuation |
| `latex_product.hpp` | Shared product/power presentation |
| `calculator.hpp/.cpp` | Finite numerical arithmetic primitive |
| `evaluation_error.hpp`, `native_bridge.cpp` | Statuses, input guard, JNI data |
| `app/src/main/java/org/axioma/calculator/MainActivity.java` | Screen, preview/equals, state, copying, Settings and history |
| `NativeCalculator`, `MathNode`, `Formula`, `CalculationResult`, `Approximation`, `EvaluationException` in that Java package | JNI interfaces, AST/result/status data |
| `CalculatorInput`, `ExpressionEditor` | Selection-aware keypad and source keyboard |
| `CalculatorSettings`, `SettingsDialog`, `CalculationHistory` | Persistent format and local history |
| `FormulaView`, `MathText`, `MathGlyphs`, `MathButton` | Offline presentation, unfinished input and cached labels |
| `CalculatorKeypad`, `CalculatorStyle`, `LaunchScreen` | Native controls, palette/layout and startup |
| `app/src/main/assets/renderer/` | Own HTML/JS and pinned MathJax/font assets |
| `app/src/main/res/`, `app/src/main/AndroidManifest.xml` | Strings, themes, vectors, permissions and launcher |
| `artwork/` | Editable SVGs and maintained design specifications |
| `app/src/androidTest/` | Platform instrumentation and 106 precision fixtures |
| `scripts/` | Documentation check, Windows setup/run/test/vendor tools, reference generator |
| `scripts/build-android-matrix.ps1` | Isolated ABI builds, native verification, device/API matrix and safe output ownership |
| `scripts/android-artifacts.py`, `scripts/android-emulator.ps1` | Standard-library APK/ELF inspection and dedicated sequential AVDs |
| `scripts/configure-vscode.ps1` | Private editor workspace, JDK 17 daemon and actual NDK compilation database |
| `scripts/build-release.ps1`, `scripts/export-signing-backup.ps1` | Permanent private signing and interactive portable key backup |
| `scripts/prepare-release.py` | Public production folder, corresponding source, checksums and compatibility |
| `artifacts/android/` (ignored) | Canonical APKs, one build manifest, runtime reports and Gradle intermediates |
| `gradle/`, root/app Gradle files | Build configuration, wrapper and dependency/asset audits |
| `docs/user/`, `docs/reference/`, `docs/development/` | Calculator guides, technical contracts and engineering |
| `LICENSE`, `COPYRIGHT`, `THIRD_PARTY_NOTICES.md`, `licenses/` | Project grant and upstream notices |

See [architecture](architecture.md), [testing](testing.md) and
[repository policy](repository-policy.md) before moving/excluding modules.
