# Architecture and runtime flows

The Android SDK supplies native UI. Java coordinates input, preferences, activity
state and JNI. Own C++20 code evaluates mathematics. MathJax 4.1.3 in an offline
WebView typesets supplied LaTeX and exports control glyphs; it has no CAS bridge.

## Evaluation flow

1. CalculatorInput edits source/selection; ExpressionEditor supplies the app-owned
   keyboard. MainActivity schedules preview evaluation.
2. NativeCalculator.parse creates a typed scalar AST and original-expression
   LaTeX. Alphabetic keys do not add variable nodes.
3. `calculateMode(source, degrees, exact)` enters JNI with input and work budgets.
   `calculate` is its Exact-mode wrapper.
4. calculate_result attempts rational/affine-pi proof from original token text,
   followed by bounded sparse square-root simplification.
5. Own decimal evaluation supplies optional 40/30-digit strings. Domain, range,
   syntax and resource errors remain distinct where implemented correctly.
6. The format selects exact or decimal presentation independently of continuation.
7. Java forwards LaTeX to FormulaView. Generations, serialized rendering and stale
   result checks coordinate asynchronous WebView updates.

CalculationResult contains `exact`, `precise`, `display`, `continuation`, `text`,
`latex`, `exactDisplay`. Empty means unavailable, never zero. Legacy `evaluate`
is an approximate comparison API; `evaluateExact` returns an initial rational or
null. Neither replaces the main mode-aware pipeline.

## Mathematical representation

Node::Kind covers number, constant, add, subtract, multiply, divide, power,
negate, function, group and percent. No symbol, assignment, matrix or calculus
node exists. BigInteger uses signed base-10^9 limbs; Rational keeps reduced
fractions with positive denominators. Real uses integer coefficient/decimal
exponent with 80 significant working digits, without production binary floating
point. Surd stores bounded sparse sums of rational coefficients times square roots.

EvaluationError crosses JNI as typed EvaluationException. Known issues identify
places where approximation/fallback violates intended mathematical contracts.

## State and rendering

SharedPreferences stores the format and up to 50 history records. Restoring a
record/completed state reevaluates source instead of treating saved decimals as
exact. History restores the entry's angle mode and current global result format.

One FormulaView serves expressions, results and glyph generation. MathGlyphs caches
bitmaps for MathButton labels. Action icons use native vectors. Input shrinks then
pans to its last token; results fit separately. Equals reverses the size hierarchy.
LaunchScreen holds fresh startup branding for 500 ms without blocking the main thread.

Use [source map](source-map.md), [exact arithmetic](../reference/exact-arithmetic.md),
[surds/results](../reference/result-formats.md), [decimals](../reference/decimal-precision.md),
[diagnostics](../reference/mathematical-status.md), [products](../reference/multiplication-notation.md)
and [rendering](../reference/math-rendering.md). Read
[known issues](../user/known-issues.md) before treating contracts as universally met.
