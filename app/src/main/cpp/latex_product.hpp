#pragma once
#include "expression.hpp"

namespace axioma {
inline std::string latex_group(const std::string& text) { return "\\left(" + text + "\\right)"; }

// Shape describes the rendered value, not necessarily its original AST node:
// simplification can turn a function into a sum, fraction, or signed coefficient.
inline std::string latex_product(const std::string& left, Kind left_shape,
                                 const std::string& right, Kind right_shape) {
    const auto sum = [](Kind shape) { return shape == Kind::add || shape == Kind::subtract; };
    const auto a = sum(left_shape) ? latex_group(left) : left;
    const auto b = sum(right_shape) || right_shape == Kind::negate ? latex_group(right) : right;
    const auto letter = [](char c) { return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'); };
    // Never concatenate numeric literals, print a misleading mixed number, or
    // merge names (e*e -> ee). Pi already carries its TeX command delimiter.
    const bool explicit_product = !b.empty() &&
        ((b.front() >= '0' && b.front() <= '9') || b.front() == '.' ||
         right_shape == Kind::divide || (!a.empty() && letter(a.back()) && letter(b.front())));
    return a + (explicit_product ? "\\cdot " : "") + b;
}

inline std::string latex_power(const std::string& base, Kind shape, const std::string& exponent) {
    const bool atomic = shape == Kind::constant || shape == Kind::group || shape == Kind::divide ||
        shape == Kind::function || (shape == Kind::number && base.find("\\cdot") == std::string::npos);
    return (atomic ? base : latex_group(base)) + "^{" + exponent + "}";
}
} // namespace axioma
