#include "exact_math.hpp"
#include "constants.hpp"
#include "evaluation_error.hpp"

namespace axioma {
namespace {
// A proven affine angle a*pi+b. Keeping both terms exact prevents pi+epsilon
// from being mistaken for pi after floating-point rounding.
struct Angle { Rational pi, scalar; };
std::optional<Angle> affine(const Node& node) {
    if (node.kind == Kind::number) return Angle{{}, Rational::decimal(node.text)};
    if (node.kind == Kind::constant) {
        if (node.text == "pi") return Angle{Rational(1), {}};
        return std::nullopt;
    }
    if (node.kind == Kind::function) return std::nullopt;
    auto left = affine(*node.left);
    if (!left) return std::nullopt;
    if (node.kind == Kind::group) return left;
    if (node.kind == Kind::negate) return Angle{-left->pi, -left->scalar};
    if (node.kind == Kind::percent) return Angle{left->pi / Rational(100), left->scalar / Rational(100)};
    const auto right = affine(*node.right);
    if (!right) return std::nullopt;
    switch (node.kind) {
        case Kind::add: return Angle{left->pi + right->pi, left->scalar + right->scalar};
        case Kind::subtract: return Angle{left->pi - right->pi, left->scalar - right->scalar};
        case Kind::multiply:
            if (!left->pi.zero() && !right->pi.zero()) return std::nullopt;
            return Angle{left->pi * right->scalar + right->pi * left->scalar, left->scalar * right->scalar};
        case Kind::divide:
            if (!right->pi.zero()) return std::nullopt;
            if (right->scalar.zero())
                throw EvaluationError(left->pi.zero() && left->scalar.zero()
                    ? EvaluationStatus::indeterminate : EvaluationStatus::undefined,
                    "Cannot divide by zero at position " + std::to_string(node.position + 1) + ".");
            return Angle{left->pi / right->scalar, left->scalar / right->scalar};
        case Kind::power: {
            if (!right->pi.zero() || !right->scalar.integer()) return std::nullopt;
            const auto& numerator = right->scalar.numerator();
            const unsigned magnitude = numerator.absolute().small(8192);
            const int exponent = numerator.negative() ? -static_cast<int>(magnitude) : static_cast<int>(magnitude);
            if (!left->pi.zero()) {
                if (exponent == 1) return left;
                if (exponent == 0) return Angle{{}, Rational(1)};
                return std::nullopt;
            }
            if (left->scalar.zero() && exponent <= 0)
                throw EvaluationError(exponent == 0 ? EvaluationStatus::indeterminate : EvaluationStatus::undefined,
                    "Zero cannot be raised to a nonpositive exponent at position "
                    + std::to_string(node.position + 1) + ".");
            return Angle{{}, left->scalar.power(exponent)};
        }
        default: return std::nullopt;
    }
}
} // namespace

std::optional<Rational> evaluate_exact(const Node& root) {
    const auto result = affine(root);
    if (!result || !result->pi.zero()) return std::nullopt;
    return result->scalar;
}

std::optional<Rational> exact_pi_multiple(const Node& argument, bool degrees) {
    const auto result = affine(argument);
    if (!result) return std::nullopt;
    if (degrees) return result->pi.zero() ? std::optional<Rational>(result->scalar / Rational(180)) : std::nullopt;
    return result->scalar.zero() ? std::optional<Rational>(result->pi) : std::nullopt;
}

std::optional<TrigonometricValue> exact_trigonometric(const Node& argument,
                                                   std::string_view function, bool degrees) {
    ExactBudget budget;
    try {
        auto angle = affine(argument);
        if (!angle) return std::nullopt;
        BigInteger turns;
        Rational residual = angle->scalar;
        if (degrees) {
            if (!angle->pi.zero()) return std::nullopt;
            const auto quarters = angle->scalar / Rational(90);
            auto [nearest, remainder] = quarters.numerator().divide(quarters.denominator());
            if ((remainder.absolute() * BigInteger(2)).compare(quarters.denominator()) > 0) {
                const BigInteger step(quarters.numerator().negative() ? -1 : 1);
                nearest = nearest + step; remainder = remainder - step * quarters.denominator();
            }
            turns = nearest;
            residual = Rational(remainder, quarters.denominator()) * Rational(90);
        } else {
            const auto half_turns = angle->pi * Rational(2);
            if (!half_turns.integer()) return std::nullopt;
            turns = half_turns.numerator();
        }
        auto phase = turns % BigInteger(4);
        if (phase.negative()) phase = phase + BigInteger(4);
        const unsigned quadrant = phase.small(3);
        const auto offset = residual.approximate();
        if (!offset) return std::nullopt;
        // Exact quadrants rotate the small scalar offset without first adding it
        // to an approximate pi. No epsilon test or blanket zero clamping is used.
        const Real radians = degrees ? *offset * (constants::pi / 180) : *offset;
        if (radians == 0 && !residual.zero()) return std::nullopt;
        const auto [sine, cosine] = numeric::sincos(radians);
        if (!sine.finite() || !cosine.finite()) return std::nullopt;
        Real sin_value = 0, cos_value = 0;
        switch (quadrant) {
            case 0: sin_value = sine; cos_value = cosine; break;
            case 1: sin_value = cosine; cos_value = -sine; break;
            case 2: sin_value = -sine; cos_value = -cosine; break;
            case 3: sin_value = -cosine; cos_value = sine; break;
        }
        if (function == "sin") return TrigonometricValue{sin_value};
        if (function == "cos") return TrigonometricValue{cos_value};
        if (cos_value == 0) return TrigonometricValue{0, true};
        const Real value = sin_value / cos_value;
        if (!numeric::isfinite(value)) return std::nullopt;
        return TrigonometricValue{value};
    } catch (const ExactLimit&) {
        // A failed proof must fall back to numeric evaluation, never assert an identity.
        return std::nullopt;
    } catch (const std::invalid_argument&) {
        // Let the numeric evaluator report the domain error with its source position.
        return std::nullopt;
    }
}
} // namespace axioma
