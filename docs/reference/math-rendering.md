# Offline mathematical rendering

Current behavior has confirmed exceptions: see [known issues](../user/known-issues.md)
and [dated validation](../development/validation.md). These contracts do not claim
that the open defects are fixed.

The pipeline is: own C++ syntax tree and LaTeX generation → platform WebView →
MathJax 4.1.3 SVG output. This resembles LMath's separation between generated
LaTeX and a host renderer, without importing Obsidian's MarkdownRenderer API.
The CAS, numerical evaluation, exact arithmetic, and angle recognition stay in C++.

Both native expression and exact-result emitters use contextual scalar product
notation, including implicit coefficient/root and constant products. See
[multiplication notation](multiplication-notation.md) for the audit, grouping
rules, explicit-product exceptions, and regression coverage.

## Assets and operation

`renderer/index.html` loads own integration code and a pinned MathJax bundle.
SVG glyph data, including dynamically requested ranges, are bundled in the APK.
All URLs resolve to an intercepted `https://axioma.invalid/renderer/` origin.
The app has no Internet permission. Requests outside that asset origin are
rejected, and file/content access and page navigation are disabled.

MathJax receives LaTeX as a JSON-encoded string, never interpolated HTML or code.
The bundled base, AMS, and newcommand TeX features have input and macro limits;
automatic extension loading, require, configmacros, and noundefined are disabled.
Raw TeX rendering is limited to 16384 characters. MathJax does not receive JNI
access or an evaluation API. The presentation bridge reports readiness, rendered
height, generation, rendering status, control-label images, and requests to open
the source editor.

The renderer is asynchronous. Generation checks discard stale results, and a
serialized queue handles dynamic font loading. The view adapts its height to
the output, scrolls wide expressions horizontally, and preserves an accessible
expression description. Invalid typesetting falls back to showing its LaTeX
as plain text and can recover on the next valid expression.

The main display contains a single typeset expression. A number literal, including
a leading sign or grouping parentheses, has no duplicate numerical preview.
Complete operations and functions show a typeset exact or approximate result; pressing
equals explicitly evaluates even a number literal. The source editor is opened
by tapping the expression, the keyboard button, or using
the overflow menu. Unfinished input is escaped and typeset without evaluating it;
very long malformed previews are abbreviated within the renderer's input budget.
The explicit editor uses Axioma's own QWERTY and symbol keyboard, with literal
source labels and no system IME. See [expression keyboard](../user/expression-keyboard.md).
Typed native mathematical diagnostics use text labels in the same typeset result
area, without being converted into numerical strings. See
[mathematical status](mathematical-status.md).

Settings selects exact or numerical presentation without changing mathematical
evaluation or continuation. Supported exact results use own fraction/radical LaTeX.
Perfect square roots render their simplified integers. Exact and approximate results
have distinct accessibility labels. Exact values too large for readable typesetting
use a bounded decimal approximation with an approximation sign; their full value
is retained for copying, history, and continuation. See
[exact arithmetic](exact-arithmetic.md).

## Expression sizing and scrolling

Expressions start at the normal formula size and shrink continuously as they
grow past the available width. Shrinking stops at two-thirds of the normal size
(20 relative to the default 30), preserving the user's system font scaling.
Longer expressions overflow within a dedicated horizontal viewport. Each new
render reveals the final mathematical token, so the last digit stays visible,
including a denominator in a fraction. Users can pan
back through an expression between edits. Width changes refit the expression;
shorter input restores the normal size and clears obsolete horizontal overflow.

While editing, the result uses half the expression's current font size at reduced
opacity. Pressing equals halves the expression size and doubles the result's
target size, with full result opacity. Editing restores the preview hierarchy;
repeated equals does not compound either scale. Both formats follow the same
rules, responsive input fitting, and system font scaling. Results that still
exceed the available width shrink further to fit. The result stays outside that viewport and remains
right-aligned under the expression, including when the expression is panned.
Sizing and scrolling affect presentation only, preserving the original source,
native evaluation, and retained result precision.

Mathematical keypad labels, scientific controls, and history rows are typeset by
the same MathJax instance. It exports self-contained SVG labels to transparent PNGs
at device resolution, which are cached and tinted inside native Android touch
targets. There is one shared WebView, rather than one per key. Labels use display
math so multi-atom labels remain a single complete SVG instead of being truncated
to the first inline fragment. English interface messages and action icons
remain ordinary Android UI. Backspace, clear all, and angle units use own SVG
artwork converted to Android VectorDrawables. The function toggle keeps a
typeset `f(x)` label.

The renderer can display integrals, derivatives, sums, and matrices. Their display
does not imply that the CAS can evaluate them yet. The current calculator input
still accepts only expressions supported by the native parser.

## Reproducible dependency handling

Versions, archive integrity strings, and individual asset hashes are recorded in
`renderer/vendor/manifest.json`. Every build runs `verifyRendererAssets`; the
Java/NDK classpath audit remains separate from the allowed presentation assets.
Use `scripts/vendor-mathjax.ps1` to restore the exact pinned assets from official
npm archives. Ordinary builds use the checked-in assets and require no npm install.

See [third-party notices](../../THIRD_PARTY_NOTICES.md) for license information.

## Verification

The platform instrumentation runner loads the real bundled renderer in a visible
WebView and checks MathJax's version, SVG glyph output, generated native LaTeX,
integrals, derivative notation, sums, matrices, dynamic font ranges, invalid input,
recovery, rapid updates, clearing, and treating TeX as data. Native mathematical
tests run independently. ADB screenshots verify appearance on the phone.
