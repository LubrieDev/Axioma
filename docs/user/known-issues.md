# Known issues and mathematical limitations

Current development version: 0.1.0. These defects were reproduced in the ARM64
APK's native library on 2026-10-18 and remain **open**. Licensing/documentation
work does not fix them. [Validation](../development/validation.md) separates
existing tests, independent comparisons and UI verification.

## K1. Indexed roots are parsed as a different expression

`\sqrt[3]{27}` returns `27*sqrt(3)`, approximately 46.7653718043596869252410512207,
rather than rejecting unimplemented indexed-root syntax. Its mathematical value
would be 3. `\sqrt[2]{4}` returns `4*sqrt(2)` and `\sqrt[4]{16}` returns 32.

Use `sqrt(4)` for square roots. Avoid indexed-root TeX, including an explicit index
2. Positive-base powers such as `27^(1/3)` have numerical support, but general
exact indexed-root simplification is not implemented.

## K2. Tangent range failure can return a false finite number

In RAD, `tan(pi/2+1e-4950)` produces a positive value near 2.3071756483e79 instead
of Out of range. The true value is negative with magnitude about 1e4950. Related
negative offsets and DEG `tan(90+1e-4950)` fail too. Ordinary proven poles such
as `tan(pi/2)` retain their intended Undefined classification.

Do not rely on these extreme-offset results. Range failure and a true pole are
different conditions; the defect loses an offset during fallback.

## K3. Rounding can change exponent domain and parity

`(-1)^(1+1e-100)` and its minus-offset counterpart are accepted with numerical
result -1, although their exact exponents are non-integers. The current
negative-base fractional-power policy should report Not supported.

`(-1)^(10^80+1)` numerically returns 1 instead of -1 because its odd exponent
loses the last digit. Exact may retain the original power while storing a wrong
decimal approximation. A retained expression does not validate its approximation.

## K4. Valid logarithm bases near one are labeled Undefined

`log(2;1+1e-100)` and `log(2;1-1e-100)` report Undefined. Both bases are positive
and differ from one; real results are finite with magnitudes about 6.9314718056e99
and opposite signs. Rounding the base before domain validation causes the defect.

## K5. Original-expression LaTeX can change meaning

`2*e+3` evaluates correctly, but its copied LaTeX `2e+3` is interpreted on reuse
as scientific 2000. `2*e-3` becomes 0.002. Generated LaTeX for `2*e+pi` and
`2*e-1/2` can instead produce a missing-exponent-digit input error.

The original typeset TeX can look correct. Prefer copying original source with
explicit multiplication instead of generated LaTeX until the boundary is fixed.

## Documented precision and scope limits

- Forty retained and thirty displayed digits are budgets. Severe conditioning
  or cancellation can leave fewer meaningful digits.
- `ln(1+1e-100)` numerically produces 0 instead of about 1e-100 when the
  displacement disappears in an 80-digit operand. This previously documented
  limitation is separate from the five defect groups above.
- Numerical orders normally range from -4965 to 4932. Computed exact rationals
  can exceed the numerical function range. Numerical trig reduction normally
  accepts orders at most 30; recognized exact angles can bypass that limit.
- Input bounds: 2048 characters, 256 nodes, 48 levels. Exact arithmetic, radical
  expansion and typesetting have separate budgets. Full factorization is not promised.
- Variables, assignments, general polynomial expansion/factorization, complex
  arithmetic, vectors/matrices, absolute value, factorial, general indexed roots,
  calculus and step-by-step derivations are not implemented.
- `log10(...)` is not an accepted alias: use `log(...)`. `abs(...)` and
  `cbrt(...)` have no hidden implementations.
- Evaluating `(40+4)^2` as 1936 is not a symbolic binomial expansion or a derivation.

See [expressions](expressions.md), [results](results-and-settings.md),
[exact arithmetic](../reference/exact-arithmetic.md) and
[decimal precision](../reference/decimal-precision.md).
