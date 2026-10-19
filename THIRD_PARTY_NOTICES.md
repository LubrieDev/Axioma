# Third-party notices

## Axioma-authored work

The project grant is GNU GPL version 3 only (`GPL-3.0-only`), as stated in
[COPYRIGHT](COPYRIGHT) and [LICENSE](LICENSE). It does not replace the licenses
or copyright notices of the upstream components below.

## Offline mathematical presentation

All CAS operations and numerical evaluation are implemented by Axioma in C++.
The following dependencies are used exclusively for mathematical presentation.

| Package | Version | Declared license | Purpose |
| --- | --- | --- | --- |
| `mathjax` | 4.1.3 | Apache-2.0 | LaTeX to SVG rendering |
| `@mathjax/mathjax-newcm-font` | 4.1.3 | Apache-2.0 | SVG mathematical glyph data |

MathJax is maintained by the MathJax Consortium and contributors.

- Project and license: <https://github.com/mathjax/MathJax>
- Rendering documentation: <https://docs.mathjax.org/en/latest/>
- Original pinned package metadata and full Apache license texts are preserved
  beside the assets in `app/src/main/assets/renderer/vendor/`.
- `manifest.json` records official npm package URLs, SHA-512 archive integrity,
  and SHA-256 hashes of each included file. No upstream JavaScript is modified.
- The font package declares Apache-2.0 in `package.json` but does not include a
  standalone license file. The matching MathJax Apache license text is included.

Only the SVG renderer and SVG font data are packaged. The app does not include
Obsidian, mathjs, KaTeX, or third-party CAS code. No network connection is used
for rendering, fonts, or mathematical evaluation.

## Build tooling and native runtime

The Gradle Wrapper retains its Apache-2.0 text in `gradle/wrapper/LICENSE`.
Android SDK, NDK, Gradle, AGP and CMake are build tools with their own terms.
They are not mathematical libraries and are not relicensed by the Axioma grant.

The NDK C++ and compiler runtimes may contribute code to native application
binaries even though the project declares no external Java/CAS dependency.
The unchanged NDK r28c `NOTICE.toolchain` is retained as
`licenses/android-ndk-r28c-NOTICE.txt`, covering LLVM/runtime licenses and other
toolchain notices. That broader collection does not mean every listed component
is shipped in Axioma. See [notice scope](licenses/README.md) and
[licensing/distribution guidance](docs/development/licensing.md).
