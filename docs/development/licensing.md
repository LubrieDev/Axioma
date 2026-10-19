# Licensing and notices

## Project-owned work

[LICENSE](../../LICENSE) is the complete unmodified GNU GPL version 3 text from
the [Free Software Foundation](https://www.gnu.org/licenses/gpl-3.0.txt).
[COPYRIGHT](../../COPYRIGHT) applies it to Axioma-authored source, documentation
and artwork unless an individual file explicitly states another license.

The selected SPDX identifier is **GPL-3.0-only**, not an automatic grant of later
versions. The standard license text contains examples of applying licenses; the
project's actual version grant is in COPYRIGHT. Contributors retain copyrights;
no personal legal identity or copyright assignment is invented here.

New owned files may include `SPDX-License-Identifier: GPL-3.0-only` in an
appropriate comment. Do not replace third-party headers with an Axioma header.

## Upstream components

MathJax and its packaged SVG glyph data retain Apache-2.0 metadata/licenses and
pinned hashes. The Wrapper retains Apache-2.0. Native binaries use NDK C++ and
compiler runtime code too: preserve applicable notices and inspect linked
components before distributing an APK. The retained r28c notice includes tools
beyond the runtime; it does not claim every listed component enters Axioma.

See [third-party notices](../../THIRD_PARTY_NOTICES.md) and
[toolchain notices](../../licenses/README.md). LMath and Ordo were behavior/design
references, not imported source or silently relicensed dependencies.

## Before binary distribution

A license addition is not a release. Provide applicable license/notices and the
corresponding modifiable source/build instructions required by the chosen
distribution method. Review in-app notice access and source/binary correspondence.
This work does not implement an About/licenses screen, signed release pipeline
or source-offer service. Source obligations do not justify publishing private
keys, personal configuration or unrelated workspace notes.
