# Axioma

An Android calculator with an own C++20 mathematical engine, exact fractions and
square roots, and offline LaTeX rendering. Java uses the Android SDK; MathJax 4.1.3
typesets notation without evaluating mathematics.

**Development preview, version 0.1.0.** Confirmed correctness defects remain open.
Read [known issues and limitations](docs/user/known-issues.md) before relying on
results. A passing suite does not establish general mathematical correctness.

## Calculator capabilities

- Arithmetic, powers, percent, grouping and implicit multiplication.
- Constants `pi`, `π`, `e`; `sqrt`, `sin`, `cos`, `tan`, `ln`, `log`, `exp`.
- Explicit RAD/DEG and arbitrary logarithm bases: `log(8;2)` or `\log_{2}(8)`.
- Numerical and Exact formats, reduced fractions, bounded square-root
  simplification and selected exact function values.
- Up to 40 retained and 30 displayed significant digits, with 80 working digits.
  These are precision budgets, not a universal accuracy guarantee.
- Typeset input/results, an in-app source keyboard, persistent result-format
  settings and the latest 50 calculations in local history.
- Offline operation with no Internet permission. Installation floor: Android
  8.0/API 26. The APK build matrix includes ARM64, ARMv7, x86_64 and x86;
  see [recorded API/ABI validation](docs/development/validation.md) for actual tests.

Variables, assignments, general polynomial algebra, absolute value, factorial,
indexed roots, calculus and step-by-step derivations are not implemented. A
keyboard character or renderable notation does not implement a CAS operation.
Unsupported indexed-root TeX currently has a known parsing defect.

| Input | Exact result | Numerical result |
| --- | --- | --- |
| `1/2+1/3` | `5/6` | Decimal approximation |
| `2(3+4)` | `14` | `14` |
| `-2^2` | `-4` | `-4` |
| `sqrt(8)` | `2*sqrt(2)` | `2.82842712474619009760337744842` |
| `sqrt(8)+sqrt(18)` | `5*sqrt(2)` | Decimal approximation |
| `sin(pi)` in RAD | `0` | `0` |

Table entries are parseable source; the screen renders fractions and radicals.

## Documentation

- [Calculator guide](docs/user/README.md): controls, expressions, results, history,
  precision, privacy and known issues.
- [Documentation index](docs/README.md): user, technical and developer guides.
- [Build and test](docs/development/setup.md): terminal tools and USB testing;
  Android Studio is optional.
- [Android APK matrix](docs/development/android-matrix.md): reproducible ABI
  artifacts and separate Android 13–16 runtime evidence.
- [Production releases](docs/development/releasing.md): permanent private signing,
  portable key backup and the versioned public APK/source folder.
- [Contributing](CONTRIBUTING.md): engineering and documentation expectations.

There is no published download location documented for this preview. Follow the
source-build instructions rather than assuming a stable APK release exists.

## Source and dependencies

All evaluation and CAS transformations are own C++ code. The source repository
retains tests, fixtures, build scripts, editable vectors, the Gradle Wrapper and
pinned offline renderer assets needed to modify and reproduce Axioma.
[Architecture](docs/development/architecture.md) explains the layers;
[source map](docs/development/source-map.md) locates their implementations.

## License

Axioma-authored code, documentation and artwork use the GNU General Public License
**version 3 only**, SPDX `GPL-3.0-only`. See [LICENSE](LICENSE) and
[COPYRIGHT](COPYRIGHT). Third-party components retain their original licenses and
notices; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
