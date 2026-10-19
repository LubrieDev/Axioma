# Result formats and exact square roots

Current behavior has confirmed exceptions: see [known issues](../user/known-issues.md)
and [dated validation](../development/validation.md). These contracts do not claim
that the open defects are fixed.

Open the overflow menu in the upper-right corner, choose **Settings**, and select
**Numerical** or **Exact** under **Result format**. The preference is stored locally
and survives activity recreation and fresh launches. Exact is the default.

| Expression | Numerical | Exact |
| --- | --- | --- |
| `2+1/2` | `2.5` | `5/2` |
| `6/8` | `0.75` | `3/4` |
| `sqrt(81)` | `9` | `9` |
| `sqrt(8)` | `2.82842712474619009760337744842` | `2*sqrt(2)` |
| `sqrt(8)+sqrt(18)` | Decimal approximation | `5*sqrt(2)` |
| `1/sqrt(2)` | Decimal approximation | `sqrt(2)/2` |
| `1/(1+sqrt(2))` | Decimal approximation | `-1+sqrt(2)` |
| `sqrt(3+2*sqrt(2))` | Decimal approximation | `1+sqrt(2)` |
| `cos(pi/4)` in RAD | Decimal approximation | `sqrt(2)/2` |
| `e` | `2.71828182845904523536028747135` | `e` |

These are source strings for documentation and copying. The screen typesets the
result with MathJax: stacked fractions and mathematical square-root notation,
without exposing `*sqrt(...)` as raw output.

Numerical mode retains 40 significant decimal digits and displays up to 30.
Integers stay integers, without appended decimal zeros. Exact mode preserves
fractions, supported simplified radicals, and symbolic constants. If a rewrite is
not supported, a domain-valid exact expression such as `sin(1)` is retained.
Displaying that expression does not claim a rational or closed algebraic result.

Changing the preference recalculates the current preview or completed result
without adding history entries. Entering a number alone still has no provisional
duplicate result. History rows retain the representation originally recorded;
opening a row reevaluates its expression using the current preference. The
setting does not change the saved angle mode.

## Native mathematical model

`NativeCalculator.calculateMode(source, degrees, exact)` calls the own C++ result
pipeline. `calculate(source, degrees)` is an Exact-mode convenience wrapper.
Android stores the preference and displays the native strings; it does not
evaluate or simplify expressions. MathJax also performs presentation only.

The scalar simplifier represents a bounded sparse sum of rational coefficients
times square roots of positive integers. A radicand of one is the rational part.
Only equal integer radicands are merged. No floating-point proximity test is used
to establish equality, cancel terms, or reconstruct an irrational constant.

Square roots of rational values are simplified with own integer arithmetic:

1. Remove decimal square factors, then use integer Newton iteration to prove
   perfect squares, including integers too large for native floating-point roots.
2. Extract square factors with bounded trial division. Any unresolved cofactor
   stays under its root rather than being falsely certified square-free.
3. Normalize numerator and denominator roots, rationalizing rational radicands.
4. Combine like terms and implement sums, products, integer powers, and supported
   half-integer powers using rational coefficients.
5. Divide by a radical monomial or a binomial using exact products/conjugates.
   A general multi-term denominator may retain its symbolic expression.

Selected nested roots use an exact rational-square discriminant identity. The
principal nonnegative branch is preserved; for example,
`sqrt((sqrt(2)-2)^2)` becomes `2-sqrt(2)`. Unsupported nested roots remain nested.
This is not a general radical-denesting algorithm.

Proven multiples of pi/12 in RAD, or 15 degrees in DEG, use exact rational and
square-root trigonometric values. Decimal approximations of pi do not acquire
those identities. Supported exact elementary cases include `ln(e)`, `ln(1)`,
`exp(0)`, and bounded rational log-power relations. Other functions retain their
symbolic form after numerical domain validation.

## Continuation and diagnostics

Result format controls presentation. Both formats retain the exact expression for
continued operations whenever available. Consequently, a decimal preview of
`sqrt(8)` multiplied by `sqrt(2)` gives exactly 4. Complete sums/products and
negative operands are grouped before insertion, preserving power precedence.
If an expanded result exceeds the input/depth limits, continuation uses the
original expression or reports the existing limit message, without substituting
rounded digits for an exact value.

Copy exact result copies the parseable exact source; Copy full precision result
copies its optional 40-digit decimal approximation. Settings never converts a
diagnostic into a number. Both modes preserve Undefined, Indeterminate, Not real,
Out of range, and Not supported. In particular,
`1/(sqrt(8)-2*sqrt(2))` is Undefined and `0*sqrt(-1)` remains Not real.

## Explicit bounds

- Existing 2048-character input, 256-node, 48-level, own integer/rational, and
  charged arithmetic budgets apply.
- Radical trial divisors are bounded at 10000. Large unresolved factors remain
  exact under their roots; complete factorization is not promised.
- Expansion stores at most 32 radical terms. Symbolic source/LaTeX construction
  is bounded at 16384 characters. Budget exhaustion preserves an unexpanded
  expression when possible, rather than claiming an unproved normal form.
- Full result typesetting uses at most 30 digits per rational component, or at
  most 256 source characters and 2048 LaTeX characters for nonrational results.
  Larger values use a marked decimal approximation while keeping the complete
  exact source for copying and continuation. A proven exact value without an
  available decimal approximation can use a bounded Exact result message.
- Numerical mode reports Out of range if an irrational result cannot be
  approximated within the supported numerical range. It does not silently
  replace a requested decimal with a symbolic expression. Proven rational values
  still support integer-formatted decimals beyond that function range.
- Variables, general symbolic algebra, cube roots, complex arithmetic,
  differentiation, and integration are outside this change.

## Reference and verification

LMath's `src/math/simbolico/valorExacto.ts` informed square-factor extraction,
integer-root proofs, conjugate division, and shared denominator presentation.
`src/algebra/expr/radicales.ts` informed domain-preserving radical rewrites. All
production implementation is own C++; no TypeScript, mathjs, Obsidian, or external
mathematical library is imported.

Phone tests check both formats, exact normal forms and parse/LaTeX reuse, large
perfect-square neighbors, randomized square/conjugate identities, exact
cancellation and diagnostics, actual rendered fractions/roots, persistent radio
selection, recreation with Settings open, fresh launches, and history restoration.
Existing independent 160-digit numerical references remain in the suite.
