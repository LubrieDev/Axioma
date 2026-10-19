# Logarithms with arbitrary bases

Current behavior has confirmed exceptions: see [known issues](../user/known-issues.md)
and [dated validation](../development/validation.md). These contracts do not claim
that the open defects are fixed.

The own C++ parser accepts a base as a second subtree of a `log` function. Its
left child is the argument; its optional right child is the base. A missing base
retains the existing base-10 behavior. `ln` remains the natural logarithm.

| Input | Meaning | Approximate result |
| --- | --- | --- |
| `log(100)` | Common logarithm | `2` |
| `ln(e)` | Natural logarithm | `1` |
| `log(8;2)` | Logarithm of 8 to base 2 | `3` |
| `\log_{2}(8)` | The same, written in LaTeX | `3` |
| `\log_2 8` | Single ungrouped argument | `3` |
| `\log_{2+1}\left(81\right)` | An expression as the base | `4` |
| `log(8;0.5)` | A base between zero and one | `-3` |

Use braces around compound bases and parentheses or braces around compound
arguments. A bare argument after a subscript consumes one unary/power expression;
for example `\log_2 8+1` means `log(8;2)+1`.

Plain two-argument input uses a **semicolon**, in argument-then-base order.
Commas retain their original decimal meaning: `log(8,2)` means the common
logarithm of `8.2`, not the logarithm of 8 to base 2. `log(8;0,5)` is valid.
Writing both a subscript and a second base, extra arguments, or an empty base
is rejected with a positioned input error.

## Evaluation and presentation

For real values, the base must be positive and different from one, and the
argument must be positive. Axioma computes `ln(argument)/ln(base)` using own
80-digit decimal working arithmetic and applies the same 40-significant-digit
retention and 30-significant-digit
display formatting as other results. See the
[NIST logarithm definitions](https://dlmf.nist.gov/4.2).

The intended policy gives invalid bases `Undefined`; near-one valid bases have
the open rounding defect recorded in known issues. A zero argument is `Undefined`, and a negative
argument is `Not real`. Child diagnostics propagate before outer evaluation.
Numerical range failures remain `Out of range`. A valid nonintegral result is
still approximate, not a symbolic identity or universal precision guarantee.

Generated LaTeX writes an explicit base as `\log_{base}\left(argument\right)`.
The notation round-trips through the own parser and is rendered by MathJax.
Node count, depth, and input limits cover both the argument and the base.
LMath's subscripted logarithm notation informed the input design; no LMath
modules or mathematical dependencies are imported.
