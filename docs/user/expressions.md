# Expressions and mathematical syntax

See [known issues](known-issues.md) for cases violating intended contracts.
The evaluator works over real numbers and a bounded scalar exact subset.

## Numbers and operators

Accept decimal points or commas, scientific literals (`1e-3`), constants `pi`, `π`,
`e`, and `+ - * / ^ %`. Unicode `× ÷ − · √` is accepted. Parentheses, brackets and
braces group ordinary expressions. Adjacent operands can multiply: `2(3+4)`,
`(2+3)(4-1)`, `2pi`.

| Expression | Meaning/result |
| --- | --- |
| `2+3*4` | `14` |
| `(2+3)*4` | `20` |
| `-2^2` | `-4` |
| `(-2)^2` | `4` |
| `2^3^2` | `512`: powers associate right |
| `6/2(1+2)` | `9`: multiplication/division share precedence and associate left |
| `200*10%` | `20`: percent divides by 100 |
| `200+10%` | `200.1`, not a contextual percentage increase |
| `200*(1+10%)` | `220` |

Percent binds before powers; `(2^3)%` is 0.08. Use explicit grouping where needed.
`2e` means twice Euler's constant, but `2e+3` is scientific 2000. Write `2*e+3`
for an arithmetic sum. Reusing its generated LaTeX has a separate
[known defect](known-issues.md#k5-original-expression-latex-can-change-meaning).

## Functions and angle units

Names: `sqrt`, `sin`, `cos`, `tan`, `ln`, `log`, `exp`. Functions normally need a
grouped argument. `ln` is natural log; plain `log` is base 10. Use a semicolon for
an arbitrary base: `log(argument;base)`. `log(8;2)=3`; `log(8,2)` instead means
base-10 log of decimal 8.2.

RAD means radians; DEG means degrees. Units are not inferred. In RAD,
`sin(pi)=0`, `tan(pi)=0`, and `tan(pi/2)` is Undefined. In DEG, `sin(90)=1` and
`tan(90)` is Undefined. Selected multiples of pi/12 in RAD or 15 degrees in DEG
have exact radical values.

## Supported LaTeX

Examples: `\frac{1}{2}`, `\sqrt{8}`, `2^{3}`, `\pi`,
`\sin\left(\pi/2\right)`, `\log_{2}(8)`. Compound exponents and bases can use
braces. `\cdot` and `\times` currently mean scalar multiplication. This is a
scalar subset, not a complete LaTeX parser.

Do not enter indexed roots such as `\sqrt[3]{27}`: they are misparsed. Positive
base powers such as `27^(1/3)` can be approximated but do not implement a general
exact indexed-root engine. Variables, factorial, absolute value, vectors, matrices
and calculus are not implemented mathematical features.

Inputs are bounded to 2048 characters, 256 nodes and 48 levels. Unknown names,
invalid arguments and exceeded bounds produce English diagnostics. The keyboard
can enter more characters than the mathematical parser accepts.
