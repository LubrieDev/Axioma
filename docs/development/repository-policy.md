# Repository and distribution policy

A source checkout, an installable APK and a developer workspace have different
contents. Files useful only on one maintainer's machine are private workspace
material; build/test instructions useful to other contributors are public engineering.

## Retained source

Keep application sources/resources, editable artwork, tests/fixtures, build
configuration, setup/run/test scripts, Gradle Wrapper, pinned offline renderer,
licenses and maintained guides. Do not exclude tests or developer documentation
merely because they do not enter the APK.

Upstream MathJax/font JavaScript and the Wrapper JAR are intentional inputs with
notices/integrity records, not personal build output. Preserve required vendor
files. Android vector XML is editable source; maintained design SVGs remain too.

## Excluded workspace material

Ignore private notes, editor/agent settings, temporary plans/probes, raw audit
output, original reference archives, SDKs/caches, local paths, signing credentials,
logs and installable/build output. Preserve needed personal material in the
ignored maintainer workspace; exclusion does not delete it or make it a build dependency.

The canonical private workspace is `.dev/`: agent instructions, editor settings,
reference notes, raw audits, backups and downloaded tools belong there. Its entire
tree is ignored and excluded from source exports. Android's `local.properties`
and generated Gradle/CMake/build directories retain their conventional locations
and remain ignored. The `.git` directory stores repository metadata and stays in
place. Older private locations remain ignored defensively.

Review **content**, not only names/extensions. A normal Markdown file can declare
itself a disposable working plan. Ignore rules cannot recognize such declarations;
the documentation checker adds content review for high-confidence local/temporary
markers. Arithmetic scratch-space comments are not file-distribution declarations.
Never remove upstream legal wording merely to silence a check.

## Read-only candidate review

```powershell
python scripts/check-documentation.py
git status --short --untracked-files=all
git ls-files --cached --others --exclude-standard
git check-ignore --verbose .dev/AGENTS.md .dev/tools/example.log local.properties
```

The checker reads candidate content and index entries but does not stage anything.
Ignore rules do not untrack files already indexed. Review deliberate overrides
and the final inventory. No documentation/setup/check command commits or publishes.

## APK and future distribution

Android packages declared source sets/resources, not the repository as a whole.
Tests, developer guides and editable reference artwork do not automatically enter
the APK. Signing credentials remain outside distributable source. No stable
release/signing pipeline is currently implemented.

| Material | Source repository | Application APK |
| --- | --- | --- |
| Application C++/Java, resources and manifest | Retain editable source | Compiled/packaged by Android |
| Pinned renderer and font assets | Retain with licenses/hashes | Packaged offline |
| Instrumentation and deterministic fixtures | Retain for verification | Separate test APK |
| Build scripts, Gradle files and Wrapper | Retain for reproducibility | Not application assets |
| User/reference/developer guides and contribution policy | Retain maintained documentation | Not automatically packaged |
| Editable design SVGs and artwork guides | Retain maintained source | Native resource counterparts are used |
| Project and upstream license notices | Retain original texts | Review explicit notice packaging/access before release |
| Personal import archives, editor/agent settings and local audits | Exclude from candidates; preserve locally | Not required build inputs |
| SDKs, caches, credentials, logs and generated APKs | Exclude from source candidates | Development outputs are separate artifacts |
| `artifacts/android/` matrix APKs, manifest, logs and build caches | Ignore and exclude from source exports | Only selected application/test APKs are installed; reports and caches stay local |

Before distribution, revalidate mathematics, preserve notices, provide applicable
license/source information and inspect exact artifacts. Publication is a separate
maintainer decision. A clean inventory or a license file does not fix release
blockers. See [licensing](licensing.md), [roadmap](roadmap.md) and
[known issues](../user/known-issues.md).
