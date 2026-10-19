# Results, settings and history

Open overflow → **Settings** → **Result format** and select **Numerical** or
**Exact**. Exact is the default. The choice persists across recreation and fresh
launches; changing it updates the current result without adding history entries.

## Result formats

| Input | Exact | Numerical |
| --- | --- | --- |
| `2+1/2` | `5/2` | `2.5` |
| `sqrt(81)` | `9` | `9` |
| `sqrt(8)` | `2*sqrt(2)` | `2.82842712474619009760337744842` |
| `1/sqrt(2)` | `sqrt(2)/2` | Decimal approximation |
| `e` | `e` | `2.71828182845904523536028747135` |

The screen typesets fractions and radicals. Exact uses proven reductions and may
retain a domain-valid expression such as `sin(1)` when no supported rewrite exists.
This is not a proof of a simpler closed form. Large exact values may use a marked
approximation for readable display, retaining exact source separately when available.

Both modes preserve exact continuation instead of reconstructing rounded digits.
An oversized result can retain its original expression or report an input limit.
A digit after equals starts a new calculation.

## Precision and sizing

Approximate results retain up to 40 and display up to 30 **significant digits**.
The integer part counts: displayed e has 29 decimal places. Trailing zeros are
removed. Original magnitudes below 1e-6 or at least 1e9 use scientific notation.
Output rounds independently to nearest, ties to even, at each digit budget.

Eighty working digits do not provide universal accuracy. Conditioning,
cancellation and rounded domain decisions have [known limits/defects](known-issues.md).
A long decimal is not evidence of correctness.

While editing, the result is half the input font size at reduced opacity. Equals
halves the input and doubles the result's target size with full opacity. Editing
restores the hierarchy. Long results fit further within the panel, independently
of the input's horizontal scrolling.

## Diagnostics

| Label | Intended meaning |
| --- | --- |
| Undefined | No defined finite value at the argument, such as `1/0` |
| Indeterminate | A form not assigned a value by policy: `0/0`, `0^0` |
| Not real | Outside the real domain, such as `sqrt(-1)` |
| Out of range | A numerical range limitation, not a mathematical identity |
| Not supported | An operation unavailable to the evaluator |

Syntax/resource errors are separate. Some boundary classifications currently
fail; read known issues. Diagnostic labels are never numerical continuation values.

## History and copying

The latest 50 completed calculations retain input, angle mode and result data.
Saved rows keep their recorded presentation; opening a row reevaluates using the
current result format and its saved angle. History has its own clear action.

Overflow actions copy input, original-expression LaTeX, retained decimal digits
or exact source when available. **Copy LaTeX has an open defect for coefficient
times e followed by addition/subtraction.** Prefer original source with explicit
operators in those cases. See [privacy](privacy.md),
[result formats](../reference/result-formats.md) and
[decimal precision](../reference/decimal-precision.md).
