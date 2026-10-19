# Axioma visual design

Axioma combines a familiar calculator key order with a distinct mathematical
workspace. The interface is implemented with the Android SDK, own vector assets,
and system typefaces for the interface. Formulas use MathJax's bundled mathematical
glyph data, separately from the CAS.

## Palette

| Role | Dark scheme |
| --- | --- |
| App foundation | `#1C1C1F` |
| Primary action | `#72645F` |
| Calculation surface | `#222225` |
| Number keys | `#28282D` |
| Resting border | `#3C3B40` |
| Main text | `#F1ECE7` |
| Secondary text | `#B8ADA7` |
| Operator surface | `#38322F` |

The exact accent also colors native controls. Primary action text is white;
secondary labels use a lighter warm neutral rather than low-contrast accent text
on the charcoal background. The light scheme retains the same primary action.

## Composition

- A serif Axioma wordmark anchors the header, with history and options beside it.
- A bordered, softly rounded calculation panel groups input, mathematical notation,
  and the result. Wide formulas scroll horizontally; tall content scrolls vertically.
  Number entry has no duplicate result preview. Long expressions shrink to a
  readable minimum before overflowing, and each edit keeps the trailing digit visible.
- Functions expand from an `f(x)` control beside the always available angle mode.
  The label stays the same when expanded, without plus or minus signs.
- Four columns of softly rectangular keys retain familiar number positions.
  Operators have warm neutral surfaces; equals carries the primary accent.
- Spacing and borders distinguish controls without oversized circles or bright fills.
- The keypad fills its width and adapts its height to reserve calculation space.
  Landscape places the calculation panel alongside the keypad.

Backspace, clear all, and angle units use own SVG artwork with native Android
vector counterparts. The family shares rounded 1.65-unit strokes on a 28 x 24
canvas: a softly pointed backspace outline, geometric AC lettering, and theta
with a small D, G, or R. Editable artwork is kept in
[artwork/icons](../../artwork/icons/README.md). Degrees and radians use these icons
in the mode toggle; the gradians icon is reserved for future engine support.
Mathematical expressions and the remaining mathematical labels use MathJax.

## Launcher icon

The launcher uses the supplied therefore symbol: three warm dots on charcoal.
Adaptive vector layers preserve the design across launcher masks, and a
monochrome layer supports themed icons. Original artwork and integration notes
are kept in [artwork/icon](../../artwork/icon/README.md).

## Startup branding

The startup logo places the exact Greek wordmark `Αξίωμα` below the therefore
symbol. Its own vector lettering uses rounded strokes and the warm light ink on
charcoal. The launch presentation is held for a 500 ms minimum from activity
creation, without delaying restored activity state. Editable SVGs and platform
integration notes are in [artwork/branding](../../artwork/branding/README.md).

## Local references

Ordo informed the use of subdued layers, modest corner radii, restrained borders,
and a shared palette. LMath informed the prominence and readability of notation.
These are design principles rather than copied screens. No CSS, assets, or font
files from either reference are included in Axioma. MathJax is obtained independently
for formula presentation; see [mathematical rendering](../reference/math-rendering.md).
