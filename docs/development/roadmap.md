# Mathematical development roadmap

This is future work, not implemented calculator functionality.

## Correctness gate

Resolve the five [defect groups](../user/known-issues.md) and add permanent
independent regressions before claiming stable basics. Prioritize wrong operations,
signs, domains and range classification, then semantic LaTeX reuse. Preserve
exact continuation, units and independent 40/30-digit rounding. The historical
green suite does not close these defects.

## Elementary scope

Plan absolute value, factorial and typed indexed roots with real domains, budgets
and exact/numerical semantics. Clarify negative-base rational exponents and
near-one logs without deciding domains from rounded operands.

## Broader CAS

Variables/assignments need typed symbol nodes and a symbol context. Polynomial
normalization, expansion and factoring need domain-aware rules, bounds and
independent equivalence checks. Differentiation and definite integration need
their expression/domain infrastructure; symbolic integration has broader scope.
Rendering a symbol does not activate any of these operations.

Step-by-step work needs explicit intermediate expressions, rule names, preconditions
and traces. Computing 1936 from `(40+4)^2` is final evaluation, not an explained
binomial transformation.

## Presentation and distribution

Align controls with active capabilities. Gradians artwork stays inactive until
engine semantics exist. Review precision-loss reporting, notice access and
source/artifact correspondence before a future release. No publication or timeline
is implied by this roadmap.
