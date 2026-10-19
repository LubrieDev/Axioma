# Calculator guide

Axioma targets Android 8.0/API 26 or newer. It is a development preview with
[known mathematical defects](known-issues.md), not a stable release. To build and
install it, use the separate [developer setup guide](../development/setup.md).

## First calculation

1. Enter an expression, such as `2×4`.
2. An operation shows a smaller, dimmed result below the expression. A number
   alone does not duplicate itself as a preview.
3. Press equals. The expression halves its editing size; the result doubles its
   target preview size and uses full opacity.
4. A digit starts a new calculation. An operator continues the previous exact
   result when available. Backspace can edit the original expression.
5. Clear all clears the input, not the history.

Long expressions shrink to a readable floor before scrolling. Edits reveal their
final mathematical token. Results fit separately and can shrink further; nominal
size changes do not guarantee that every long result doubles its fitted width.
Tall display content can scroll vertically.

## Controls

- `f(x)` expands scientific functions; compact layouts can show a function menu.
- The theta/unit control switches RAD/DEG. Gradians are not active.
- The keyboard button or a tap on the expression opens the in-app source editor.
- History opens the latest 50 calculations. Selecting an entry restores its angle
  mode and reevaluates using the current result format.
- The overflow menu contains editing, copying and Settings. Settings selects
  Numerical or Exact. Variable/calculus panels are not implemented.

The interface follows system light/dark themes and font scaling. UI labels are
English; the Greek startup wordmark `Αξίωμα` is intentional artwork.

Continue with [expressions](expressions.md), [results](results-and-settings.md),
[the source keyboard](expression-keyboard.md) and [privacy](privacy.md).
