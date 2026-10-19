# Mathematical result labels

Current behavior has confirmed exceptions: see [known issues](../user/known-issues.md)
and [dated validation](../development/validation.md). These contracts do not claim
that the open defects are fixed.

Axioma distinguishes a finite real result from a mathematical diagnostic, invalid
input, and an exhausted resource budget. The own C++ evaluator determines the
diagnostic at the failing operation. JNI preserves a typed status in
`EvaluationException`; Android does not infer mathematics by matching error text.

## Labels

| Label | Current examples | Meaning |
| --- | --- | --- |
| `Undefined` | `tan(pi/2)` in RAD, `tan(90)` in DEG, `1/0`, `ln(0)`, `0^-1` | The operation has no defined finite value at that argument. |
| `Indeterminate` | `0/0`, `0^0` | The written form is not assigned a value by Axioma's current policy. |
| `Not real` | `sqrt(-1)`, `ln(-1)` | The operation is outside the current real domain; complex evaluation is not implemented. |
| `Out of range` | `1e4900*1e4900`, `1e-4900*1e-4900`, `exp(-12000)` | Numerical overflow or underflow prevents a supported approximation; this does not prove the expression undefined. |
| `Not supported` | `(-8)^(1/3)` | The evaluator does not yet support the operation, even though a real answer may exist. |

The intended negative-base non-integer policy is `Not supported`; general real
rational-root branches are not implemented. Near-integer exponents can currently
be misclassified after decimal rounding, as documented in known issues.
The engine must not claim that a real cube root is
non-real merely because that operation is unavailable.

The main pipeline first attempts an exact rational proof. A computed integer such
as `10^6000` is a valid exact result and can also receive an integer-formatted
scientific decimal approximation. It is not labeled Out of range simply because
it exceeds the decimal numerical range. Numerical functions still require their operands and results
to fit the supported numerical range. Exact arithmetic and parser budgets remain
independent resource constraints. See [exact arithmetic](exact-arithmetic.md).

Invalid syntax, unknown names, out-of-range literals rejected by the parser, and
input/resource limits retain their explicit input errors. They do not become
`Undefined`. Diagnostic details retain the operation's source position in the
native exception. Operand failures propagate before any outer operation can
erase them: `0*(1/0)` stays undefined and `0*sqrt(-1)` stays non-real.

## Trigonometric behavior

In RAD mode, `tan(pi)` is exactly zero. Tangent is undefined at `pi/2 + k*pi`
for integer `k`, including positive and negative equivalents. These follow from
the [NIST trigonometric definitions and zeros](https://dlmf.nist.gov/4.14).
In DEG mode, the corresponding poles are `90 + 180*k` degrees.

The existing exact quadrant recognizer proves these cases using rational
coefficients of symbolic pi or rational degree angles. No tolerance window is
added around a pole. For example, `tan(pi/2+1e-40)` remains a finite approximation
near `-1e40`; it is not relabeled undefined. A decimal approximation of pi is not
treated as symbolic pi. See [exact arithmetic](exact-arithmetic.md).

## Presentation and editing

Complete expressions show a typeset English label in the result area during
preview and after equals. Unfinished expressions wait for valid input; syntax
errors are explained on equals. Diagnostic labels use MathJax text and the same
single result area as numbers, at a smaller consistent text size in preview and
after equals. Accessibility announces them as a result rather
than an approximate number.

Labels never enter the numeric continuation, precision-copy action, or numeric
history. After equals on a diagnostic, entering a number starts a new expression.
Backspace can still edit the expression that produced it. Switching angle modes
recomputes the diagnostic, and completed diagnostics are recomputed when saved
activity state is restored.

## LMath reference

LMath's `src/algebra/degeneradas.ts` and
`src/algebra/expr/diagnosticoConstante.ts` informed the separation of undefined,
indeterminate, and non-real expressions. `src/CAS/numeros/resultadoNumerico.ts`
also separates numerical failures from successful approximations. Axioma uses
own C++ diagnostics; it does not import LMath's TypeScript, mathjs evaluator,
graph sampling, or tolerance-based pole guards.
