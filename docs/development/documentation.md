# Maintaining documentation

Use [the index](../README.md). Calculator documentation explains decisions a user
can make; implementation details belong in references/development guides.

| Information | Canonical home |
| --- | --- |
| Overview, status and license | Root README, LICENSE, COPYRIGHT, third-party notices |
| Controls, syntax, result settings and privacy | docs/user/ |
| Confirmed defects and absent capabilities | docs/user/known-issues.md |
| Algorithms, native contracts, budgets and rendering | docs/reference/ |
| Setup, tests, source map, validation and distribution | docs/development/ |
| Editable vector specifications | artwork/ README files and SVG/XML |
| Personal notes, raw evidence and disposable plans | Ignored local workspace |

## Reviewing claims

Trace the active caller, not just a helper. Describe accepted input, result/status,
domains and budgets. Distinguish intended contracts, observed behavior, confirmed
defects, historical evidence and planned work.

Mathematical examples need independent expectations. Finite numerical agreement
does not prove an identity; retained source is not a simplification certificate.
A keyboard character, vector asset or renderable integral does not implement a CAS
operation. Do not describe proposed work as active code.

Link to contracts instead of duplicating them. Update incoming links when moving
a page. Keep public paths relative, prose English, examples reproducible, and
commands explicit about writes, regeneration and device requirements. Personal
paths and serials do not belong in public guides.

## Verification

Run `python scripts/check-documentation.py`. It checks local targets/headings,
navigation, licensing metadata, private boundaries and pinned assets without
following external URLs or executing setup/build/evaluation commands.

Review semantics separately: link checks cannot determine mathematical truth.
For prose-only changes, do not claim historical numerical tests were rerun.
Preserve upstream legal wording even when it differs from project prose style.
