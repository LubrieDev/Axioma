#pragma once
#include "expression.hpp"
#include "rational.hpp"

namespace axioma {
// Unsupported expressions remain unevaluated by this initial exact subsystem.
std::optional<Rational> evaluate_exact(const Node& root);
// A proven angle as a rational multiple of pi; decimal pi is never symbolic.
std::optional<Rational> exact_pi_multiple(const Node& argument, bool degrees);
struct TrigonometricValue { Real value; bool pole = false; };
std::optional<TrigonometricValue> exact_trigonometric(const Node& argument,
                                                   std::string_view function, bool degrees);
} // namespace axioma
