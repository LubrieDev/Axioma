# Decimal precision

Current behavior has confirmed exceptions: see [known issues](../user/known-issues.md)
and [dated validation](../development/validation.md). These contracts do not claim
that the open defects are fixed.

Axioma retains up to **40 significant decimal digits** and displays up to **30**.
The integer part counts, leading zeros do not, and scientific exponents do not.
For example, the displayed approximation of e has 29 decimal places:

```text
2.71828182845904523536028747135
```

The retained approximation is:

```text
2.718281828459045235360287471352662497757
```

Trailing zeros are omitted. These are digit budgets, not a requirement to append
zeros to every result. Exact integers, fractions, and supported radicals remain separate from decimal
approximations; no fraction is guessed from rounded digits.
Settings selects Numerical or Exact presentation. Both retain exact expressions
for continuation; see [result formats](result-formats.md).

## Own working arithmetic

The old IEEE binary128 backend had about 33 significant decimal digits. Merely
printing 40 would expose rounding noise. The current `Real` class instead stores
an own `BigInteger` coefficient and a decimal exponent with **80 working digits**,
including guards. Parsing and constants use decimal text, with no conversion to
`float`, `double`, or `long double`. Floating-point construction is forbidden by
the C++ type interface. Java/JNI still transfer strings only.

Addition aligns decimal exponents, multiplication combines coefficients, and
division obtains additional quotient digits before normalization. Normalization
rounds to nearest, ties to even. The 40- and 30-digit strings are each rounded
directly from the guarded value with own integer quotient/remainder, avoiding
rounding the retained string a second time. Proven rationals use their original
exact numerator and denominator instead of a finite working approximation.

Results below 1e-6 or at least 1e9 use scientific notation. Significant-digit
budgets apply to the mantissa. A carry through 10 adjusts the exponent.

## Elementary functions

All production algorithms are own C++ code over decimal arithmetic:

- Square root: Newton iteration, with an initial estimate based on decimal order.
- Natural logarithm: decimal scaling, repeated square roots where needed, and
  the odd series for `2*atanh((x-1)/(x+1))`. Arguments near one are handled directly
  to preserve small differences. Common/base-specific logarithms use log ratios.
- Exponential: reduction by an integer multiple of ln(2), a power series on the
  residual, and integer exponentiation by squaring.
- General positive-base powers: integer exponentiation where possible, otherwise
  `exp(exponent*ln(base))`. Existing negative-base domain restrictions remain.
- Sine and cosine: decimal period reduction, quadrant folding, and their series.
  Tangent divides the resulting sine and cosine.

The mathematical series are documented in the primary NIST DLMF references for
[logarithms and exponentials](https://dlmf.nist.gov/4.6) and
[trigonometric functions](https://dlmf.nist.gov/4.19). These references supply
mathematical identities, not implementation code or dependencies.

Existing rational/symbolic pi quadrant recognition happens before numerical
period reduction. `sin(pi)` remains exactly zero and exact tangent poles remain
Undefined. Small finite offsets are preserved without tolerance-based clamping.

## Range and accuracy boundaries

- Numerical decimal orders range from -4965 to 4932. Range failures remain
  distinct from Undefined and from parser/resource errors. Computed exact
  rationals can exist beyond the numerical function range.
- Numerical trig period reduction accepts decimal orders at most 30, retaining
  guard space after subtraction of whole periods. Larger non-symbolic arguments
  report Out of range instead of displaying unreliable digits. Exact recognized
  quadrants, such as `sin(1e4000*pi)`, bypass that numerical reduction.
- Series have bounded iteration counts; square root and log/exp allow 300
  iterations and sine/cosine allow 200. Convergence uses the guarded working
  values, not the rounded 40- or 30-digit text.
- Own integer arithmetic retains its resource budgets. Limits never imply that
  an expression is mathematically undefined.
- Forty digits is the retained precision target, not a universal proof that every
  arbitrary expression has forty correct digits. Severe cancellation,
  conditioning, and propagation of approximate inputs can reduce accuracy.
  Axioma does not claim interval-certified transcendental results.

## Verification

`scripts/generate-precision-fixtures.py` generates 106 test-only references using
Python's independent Decimal implementation at 160 working digits. It derives pi
with Machin's identity and uses Decimal's exp, ln, powers, and square root plus
independent series references for trigonometry. The script uses only the Python
standard library and is not part of the Android runtime.

On-device tests compare both native APIs against those strings. Cases cover
constants, extreme scales, cancellation, nearby inputs, negative arguments,
function composition, and positive/negative halfway and carry cases at both digit
boundaries. Existing rational tests compare against Java BigInteger/BigDecimal
solely in the test APK. UI tests verify the 30-digit e result in the actual offline
MathJax display, its bounds, and continuation using retained digits.
