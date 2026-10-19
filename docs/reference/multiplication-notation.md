# Contextual multiplication notation

Current behavior has confirmed exceptions: see [known issues](../user/known-issues.md)
and [dated validation](../development/validation.md). These contracts do not claim
that the open defects are fixed.

## Audit and scope

The native parser builds scalar `Node` trees. `expression.cpp::to_latex` formats
the original expression used by the input display and history. The exact-result
pipeline in `calculation_result.cpp` uses rational formatting, then the bounded
`symbolic_math.cpp` simplifier, with the original AST formatter as a fallback.
Both result modes share the original-expression display. Numerical result
formatting is independent of symbolic product formatting.

Previously both the AST formatter and the generic symbolic emitter inserted
`\cdot` unconditionally. The separate surd emitter already used conventional
coefficient/root juxtaposition. The problem was presentation, not normalization.
Square-factor extraction, radical combination, shared denominators, conjugate
rationalization, exact function rules, and factor ordering remain unchanged.

The implementation consults LMath's `src/CAS/presentacion/latex.ts` separator
policy and its AST-based presentation in `src/algebra/latex/nodoATex.ts` as
references. Axioma implements its own small C++ presentation helper and imports
no LMath code or dependencies.

## Rules

`latex_product.hpp` is shared by both native emitters. It uses the shape of each
rendered operand and the lexical boundary between their generated fragments:

- Juxtapose coefficients with constants, roots, and named functions, including
  inside fractions and exponents: `3\sqrt{6}`, `e\pi`, `2\sin(1)`.
- Preserve factor order. This formatter neither combines powers nor rearranges
  factors. Existing algebraic simplification may still do so.
- Keep a dot before an ungrouped factor beginning with a number, including
  numeric powers: `2\cdot3`, `e\cdot3`, `2e\cdot3`. The last example must never
  become the scientific literal `2e3`.
- Keep a dot before an ungrouped fraction to avoid mixed-number notation:
  `2\cdot\frac{1}{3}`. A fraction followed by a root or constant can juxtapose.
- Keep a dot between adjacent alphabetic names: `e\cdot e`. The current
  simplifier does not combine these constants into a power, and `ee` is not an
  admitted name. Pi's existing trailing space terminates its TeX command, so
  `\pi e` does not become the unsupported command `\pie`.
- Group sums used as factors and negative right operands. Preserve explicit
  AST groups. Exact operands carry their rendered shape because simplification
  can turn a function into a sum, fraction, or signed coefficient.
- Preserve power scope, including a scientific literal used as a base:
  `1e2^2` displays as `(1\cdot10^2)^2`.

The reference input `\frac{1+3\sqrt{6}}{\sqrt{42}}` retains precisely that LaTeX
in the original-expression display. Its exact result may be rationalized by the
existing simplifier to `\frac{18\sqrt{7}+\sqrt{42}}{42}`; presentation does not
introduce or disable that transformation.

Other explicit multiplication emitters are intentional and unchanged:
scientific AST literals use `\cdot10^n`, numerical results use `\times10^n`,
the multiplication key uses `\times`, and unfinished source uses escaped
explicit operators. The renderer typesets supplied LaTeX without rewriting it.

Variables, cube roots, vectors, matrices, composition, and user-defined operators
currently have no typed native AST support. Unknown names are rejected, but
indexed-root TeX currently has the parsing exception described in known issues.
Although `\times` is accepted as scalar multiplication by the existing parser,
it is not a vector-product operation. MathJax's presentation-only vector cross
product remains `\times`; this change does not reinterpret it.

## Verification

The platform instrumentation suite asserts exact AST and exact-result LaTeX,
re-evaluates emitted products in RAD and DEG, compares retained/display digits,
checks Numerical/Exact value and continuation invariants, and renders both
paths through the packaged MathJax instance. SVG assertions check omitted and
retained dot glyphs and a preserved presentation-only vector cross glyph.
Existing radical, rationalization, domain, independent numerical precision,
Settings persistence, history, lifecycle, and renderer safety checks also run.
