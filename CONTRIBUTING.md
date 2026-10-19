# Contributing to Axioma

Start with the [documentation index](docs/README.md), [setup](docs/development/setup.md)
and [source map](docs/development/source-map.md). Check
[known issues](docs/user/known-issues.md) before describing a capability as implemented.

## Engineering rules

- Keep project-owned code, comments, documentation, UI and diagnostics in English.
- Implement evaluation, exact arithmetic and CAS transformations in own C++.
  Rendering dependencies typeset notation; they must not decide its meaning.
- Preserve domains, signs, precedence, resource bounds and exact source. Do not
  reconstruct fractions or radicals from approximate decimal matches.
- Keep exact results, approximations and diagnostics distinct through JNI.
- Reproduce a defect and derive independent expectations before changing it.
  Never change an expectation merely to make a test pass.
- Verify affected UI/JNI/rendering paths: a helper test does not prove screen behavior.
- Record commands actually run and their results. Finite numerical agreement
  does not establish symbolic proof, complete coverage or universal accuracy.

## Verification and documentation

Run `python scripts/check-documentation.py` for documentation and distribution
boundaries. For runtime changes, follow [testing](docs/development/testing.md).
Pure prose changes do not require an APK reinstall.

Update user-visible behavior in the user guide, contracts/algorithms in technical
references, and defects in known issues. Keep raw measurements and personal
workspace notes separate. See [documentation maintenance](docs/development/documentation.md).

## Files and licensing

Retain source, tests, fixtures, build scripts and upstream notices. Exclude local
configuration, credentials, caches, installable outputs and disposable plans.
See [repository policy](docs/development/repository-policy.md). Ignore rules do
not delete files or untrack existing index entries; the checker inspects content too.

New Axioma-authored work uses `GPL-3.0-only`; contributors retain their copyrights.
Do not relicense upstream files or remove their notices. See
[licensing](docs/development/licensing.md). This guide does not authorize
publication or release distribution; those are separate maintainer decisions.
