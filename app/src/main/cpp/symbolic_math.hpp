#pragma once
#include "expression.hpp"
#include "rational.hpp"

namespace axioma {
struct ExactExpression {
    std::string text, latex;
    std::optional<Rational> rational;
    bool algebraic = false;
};
// A bounded real scalar simplifier. Unsupported symbolic operations retain their
// expression; only proven identities change their mathematical representation.
ExactExpression simplify_exact(const Node& root, bool degrees);
} // namespace axioma
