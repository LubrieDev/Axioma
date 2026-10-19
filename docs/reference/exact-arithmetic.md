# Exact arithmetic and trigonometric angles

Current behavior has confirmed exceptions: see [known issues](../user/known-issues.md)
and [dated validation](../development/validation.md). These contracts do not claim
that the open defects are fixed.

## Motivation

Before this change, `sin(pi)` in RAD mode displayed
`8.671810130123781e-35`. The binary128 approximation of pi was passed directly to
the numerical sine function. A result that is mathematically zero became a visible
floating-point residual. Increasing display precision cannot establish the identity.

## Own integer and rational foundation

`BigInteger` stores a sign and little-endian base-10^9 limbs. It implements signed
addition, subtraction, multiplication, quotient/remainder, and Euclidean GCD.
Division truncates toward zero; the remainder has the dividend's sign.

`Rational` stores a reduced numerator and a positive denominator. Zero is always
`0/1`. Decimals and scientific literals are read from their original token text,
without converting through floating point. Multiplication cross-cancels factors
before constructing products; addition shares denominator factors. Integer powers
support negative exponents, with explicit division-by-zero and `0^0` rejection.

`NativeCalculator.evaluateExact(source)` returns a canonical integer/fraction string,
or null when the initial exact subsystem does not support the expression. Examples:

| Expression | Exact API result |
| --- | --- |
| `1/3+1/6` | `1/2` |
| `0.1+0.2` | `3/10` |
| `2^-3` | `1/8` |
| `(1e100+1)-1e100` | `1` |
| `sqrt(2)` | null |

The exact API supports rational arithmetic, percent, grouped expressions, and
integer powers. It can also cancel the pi terms supported by the affine analysis
below. It does not yet return general symbolic expressions, radicals, or function
values. Syntax and domain errors and exceeded resource limits throw English errors.

## Main calculation pipeline

`NativeCalculator.calculateMode(source, degrees, exactMode)` is the screen's entry point. It
returns a `CalculationResult` with distinct exact and decimal strings; empty
strings mean unavailable, never zero. The older `evaluate` and `evaluateExact`
APIs remain available for numerical comparisons and isolated exact tests. The
two-argument `calculate` wrapper defaults to Exact presentation. The exact string
can now contain a simplified radical or a retained symbolic expression, not only
a rational. See [result formats](result-formats.md).

The main pipeline first proves a rational value from original literal text and
supported affine pi expressions. Exact arithmetic happens before approximation:
`(1e100+1)-1e100` is 1, and `0.1+0.2` is 3/10. No fraction is inferred from a
rounded decimal. Unsupported expressions use numerical evaluation; supported
rational operands are reduced before conversion to numerical function arguments.
For example, `sqrt(1e100+1-1e100)` simplifies to exactly 1 rather than losing the
unit during addition. The scalar simplifier also handles supported square-root
expressions without converting their coefficients to decimals.

The exact path reports typed domain failures before rounding can hide them:
`1/(0.1+0.2-0.3)` is Undefined and division of that exact zero by itself is
Indeterminate. If an exact proof exhausts its arithmetic budget, the numerical
fallback may still produce a result. Exact mode retains the original expression
if simplification is unavailable; that does not certify it as a rational normal form.

### Decimal approximations

Proven rationals are rounded directly with own BigInteger quotient/remainder.
The 40-significant-digit retained and 30-significant-digit displayed strings are rounded independently
from the exact numerator and denominator, to nearest with ties to even. Trailing
zeros are removed. Magnitudes below 1e-6 or at least 1e9 use scientific notation,
with significant-digit limits applied to the mantissa. Rounding a mantissa through 10
adjusts its exponent. Negative zero is normalized to zero.

This avoids both double rounding and binary rounding at a decimal midpoint.
Midpoint cases are evaluated directly from the exact numerator and denominator,
including when the first digit beyond a midpoint occurs far beyond the displayed
digit budget. The exact value remains the original rational.
Computed exact integers such as `10^6000` can retain and format their decimal
approximation even beyond the numerical function range. Input literals remain
constrained by the parser's finite decimal range; enter such values as powers.

Function evaluation uses own 80-digit working decimal arithmetic and a 40/30-digit
formatter. See [decimal precision](decimal-precision.md).
Those results do not carry a general exactness or accuracy guarantee.

### Screen, history, and continuation

- Short exact results use own native LaTeX: `1/3` is a stacked fraction, including
  in live previews and history. A number literal still has no preview until equals.
  Accessibility distinguishes Exact result from Approximate result and diagnostics.
  Numerical mode instead uses the decimal display string, preserving the exact
  expression internally. Supported roots, constants, and function identities are
  handled by the own scalar simplifier; unsupported rewrites retain their syntax.
- Each exact numerator and denominator may have up to 30 digits for full
  typesetting. Larger values use a decimal approximation with an explicit
  approximation sign, retaining the complete canonical exact value in memory and
  history. If decimal formatting exhausts its separate budget, a bounded Exact
  result message is shown instead of creating an unbounded SVG.
- The overflow menu offers Copy exact result independently of Copy full precision
  result, which copies the optional decimal approximation.
- Operators after equals use the exact value, grouping fractions and negative
  operands. Thus `1/3`, equals, multiply, 3, equals produces exactly 1. New digits
  still start a new calculation; backspace still edits the source expression.
- Canonical continuation must pass the parser and fit a 1024-character allowance.
  Otherwise the original expression is preserved in parentheses, with room for an
  operator and operand. No rounded decimal replaces an oversized exact value.
  If neither representation fits the input/depth limits, continuation is rejected
  with an English message while the result remains available for copying.
- History saves exact, decimal, continuation, and presentation data with the angle
  mode. Legacy decimal records remain readable. Selecting a record reevaluates its
  original expression with the current engine, so an old `1/3` record now restores
  an exact fraction when Exact is selected. Saved rows retain their recorded
  presentation; opening a row uses the current preference. Activity recreation also reevaluates the original expression
  without adding duplicate history entries or converting through saved decimals.

The current keypad and mathematical keyboard are retained. Settings supplies the
result-format preference. Variables, general symbolic algebra, derivatives, and
integrals remain future work.

## Trigonometric recognition

Before numerical evaluation, the engine attempts to represent an angle as
`a*pi+b`, where both coefficients are exact rationals. The recognizer supports
affine sums, signs, rational scaling, and division by rational scalars. Unsupported
operations remain on the numerical path; an unproved identity is never assumed.

In RAD mode, when `a` is a multiple of `1/2`, the engine reduces its exact quadrant
modulo four and rotates `sin(b)` and `cos(b)`. Thus:

- `sin(pi)` and `tan(pi)` are exactly zero.
- `cos(pi/2)` is exactly zero, while `cos(pi)` is exactly minus one.
- `tan(pi/2)` and its periodic equivalents are rejected as undefined.
- Huge integer pi coefficients retain their exact parity.
- `sin(pi+1e-40)` remains approximately `-1e-40` rather than zero or a pi residual.

In DEG mode, rational angles are reduced to the nearest exact multiple of 90
degrees before conversion of their residual to radians. This also preserves tiny
offsets on either side of quadrants. Symbolic `pi` in DEG mode still means pi
degrees; it does not acquire the RAD identities.

Decimal approximations such as `3.141592653589793` are not symbolic pi. Small
results are not clamped to zero. The former numerical cosine threshold for tangent
poles has been replaced by exact recognition and a direct numerical zero check.

## Limits and verification

- Existing parser limits still apply: 2048 characters, 256 nodes, 48 levels, and
  the current finite decimal literal range.
- Stored reduced rational numerators and denominators have at most 8192 decimal
  digits each. Integer storage reserves up to 1024 limbs, including scratch space.
- Exact decimal exponents and integer power magnitudes are limited to 8192.
- Each exact evaluation or angle proof has a budget of 8,000,000 charged arithmetic
  work units. An exact API call reports limit exhaustion. The trig recognizer
  falls back to the existing numerical evaluator if proof exceeds its budget.
- Floating-point fallback and offset evaluation are still approximations, without
  a universal accuracy guarantee for every expression.

On-device tests cover exact zeros, signs, periodicity, RAD/DEG separation, tangent
poles, small nonzero offsets, domain preservation, rational normalization, integer
powers, and resource rejection. Deterministic randomized cases compare the C++
integer/rational results against Java's standard-library BigInteger solely in the
test APK. Main-pipeline rounding also uses independent BigDecimal division oracles
and explicit midpoint, carry, and scientific-boundary cases. UI tests verify real
MathJax fractions, exact continuation, old and new history, angle restoration,
and recreation of completed results and diagnostics. Neither the application nor
its tests include third-party mathematical libraries.
