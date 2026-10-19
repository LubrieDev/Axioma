#include "symbolic_math.hpp"
#include "latex_product.hpp"
#include "exact_math.hpp"
#include "evaluation_error.hpp"
#include <map>
#include <vector>

namespace axioma {
namespace {
[[noreturn]] void issue(EvaluationStatus status, const char* message, const Node& node) {
    throw EvaluationError(status, std::string(message) + " at position " + std::to_string(node.position + 1) + ".");
}
BigInteger integer_sqrt(const BigInteger& value) {
    if (value.compare(BigInteger(2)) < 0) return value;
    BigInteger guess = BigInteger::power_of_ten((value.text().size() + 1) / 2);
    for (;;) {
        const auto next = (guess + value / guess) / BigInteger(2);
        if (next.compare(guess) >= 0) return guess;
        guess = next;
    }
}
struct SquareParts { BigInteger outside, inside; };
SquareParts square_parts(BigInteger value) {
    if (value.zero()) return {0, 1};
    BigInteger outside(1), inside(1);
    // Extract decimal square factors before Newton or trial division. This also
    // handles powers with thousands of digits without a floating-point root.
    auto digits = value.text();
    const auto trailing = digits.size() - digits.find_last_not_of('0') - 1;
    if (trailing >= 2) {
        const auto removed = trailing / 2 * 2;
        digits.resize(digits.size() - removed);
        outside = BigInteger::power_of_ten(removed / 2); value = BigInteger(digits);
    }
    auto root = integer_sqrt(value);
    if ((root * root).compare(value) == 0) return {outside * root, 1};
    // Bounded factor extraction: an unproved remaining factor stays under the
    // root, preserving equality rather than guessing square-free structure.
    for (unsigned factor = 2; factor <= 10000; factor = factor == 2 ? 3 : factor + 2) {
        const BigInteger prime(factor);
        if ((prime * prime).compare(value) > 0) break;
        unsigned count = 0;
        while ((value % prime).zero()) { value = value / prime; ++count; }
        for (unsigned i = 0; i < count / 2; ++i) outside = outside * prime;
        if (count % 2) inside = inside * prime;
        if (count) {
            root = integer_sqrt(value);
            if ((root * root).compare(value) == 0) return {outside * root, inside};
        }
    }
    return {outside, inside * value};
}
struct Less { bool operator()(const BigInteger& a, const BigInteger& b) const { return a.compare(b) < 0; } };

// A sparse sum of rational coefficients times positive integer square roots.
// A radicand of one is the rational part. No approximate equality is used.
class Surd {
public:
    std::map<BigInteger, Rational, Less> terms;
    explicit Surd(Rational value = {}) { add(1, value); }
    void add(const BigInteger& radicand, const Rational& coefficient) {
        if (coefficient.zero()) return;
        auto found = terms.find(radicand);
        if (found == terms.end()) {
            if (terms.size() >= 32) throw ExactLimit();
            terms.emplace(radicand, coefficient);
        } else {
            const auto sum = found->second + coefficient;
            if (sum.zero()) terms.erase(found); else found->second = sum;
        }
    }
    bool zero() const { return terms.empty(); }
    std::optional<Rational> rational() const {
        if (zero()) return Rational();
        if (terms.size() == 1 && terms.begin()->first.compare(BigInteger(1)) == 0) return terms.begin()->second;
        return std::nullopt;
    }
    Surd scaled(const Rational& value) const {
        Surd result;
        for (const auto& [radicand, coefficient] : terms) result.add(radicand, coefficient * value);
        return result;
    }
    Surd plus(const Surd& other) const {
        Surd result = *this;
        for (const auto& [radicand, coefficient] : other.terms) result.add(radicand, coefficient);
        return result;
    }
    Surd times(const Surd& other) const {
        Surd result;
        for (const auto& [left, a] : terms) for (const auto& [right, b] : other.terms) {
            const auto common = gcd(left, right);
            result.add((left / common) * (right / common), a * b * Rational(common));
        }
        return result;
    }
    std::optional<Surd> divided(const Surd& other, const Node& node) const {
        if (other.zero()) issue(zero() ? EvaluationStatus::indeterminate : EvaluationStatus::undefined,
                               "Cannot divide by zero", node);
        if (const auto denominator = other.rational()) return scaled(Rational(1) / *denominator);
        if (other.terms.size() == 1) {
            const auto& [radicand, coefficient] = *other.terms.begin();
            Surd inverse; inverse.add(radicand, Rational(1) / (coefficient * Rational(radicand)));
            return times(inverse);
        }
        if (other.terms.size() == 2) {
            Surd conjugate = other;
            auto last = std::prev(conjugate.terms.end()); last->second = -last->second;
            const auto norm = other.times(conjugate).rational();
            if (norm && !norm->zero()) return times(conjugate).scaled(Rational(1) / *norm);
        }
        return std::nullopt;
    }
    std::optional<Surd> power(int exponent, const Node& node) const {
        if (exponent < -8192 || exponent > 8192) throw ExactLimit();
        if (zero() && exponent <= 0) issue(exponent == 0 ? EvaluationStatus::indeterminate : EvaluationStatus::undefined,
                                          "Zero cannot be raised to a nonpositive exponent", node);
        if (exponent < 0) {
            const auto inverse = Surd(Rational(1)).divided(*this, node);
            return inverse ? inverse->power(-exponent, node) : std::nullopt;
        }
        Surd result(Rational(1)), factor = *this;
        unsigned count = static_cast<unsigned>(exponent);
        while (count) {
            if (count & 1U) result = result.times(factor);
            count >>= 1U; if (count) factor = factor.times(factor);
        }
        return result;
    }
    static Surd square_root(const Rational& value, const Node& node) {
        if (value.numerator().negative()) issue(EvaluationStatus::not_real,
                                               "The square root requires a nonnegative number", node);
        if (value.zero()) return Surd();
        const auto numerator = square_parts(value.numerator()), denominator = square_parts(value.denominator());
        Surd result;
        result.add(numerator.inside * denominator.inside,
            Rational(numerator.outside, denominator.outside * denominator.inside));
        return result;
    }
    std::optional<Surd> square_root(const Node& node) const {
        if (const auto argument = rational()) return square_root(*argument, node);
        if (terms.size() != 2 || terms.begin()->first.compare(BigInteger(1)) != 0) return std::nullopt;
        const auto a = terms.begin()->second;
        const auto& [d, b] = *std::next(terms.begin());
        const auto norm = a*a - b*b*Rational(d);
        if (a.numerator().negative() || norm.numerator().negative()) return std::nullopt;
        const auto norm_root = square_root(norm, node).rational();
        if (!norm_root) return std::nullopt;
        const auto upper = (a + *norm_root) / Rational(2), lower = (a - *norm_root) / Rational(2);
        if (lower.numerator().negative()) return std::nullopt;
        return square_root(upper, node).plus(square_root(lower, node).scaled(Rational(b.numerator().negative() ? -1 : 1)));
    }
    ExactExpression expression() const {
        if (zero()) return {"0", "0", Rational(), true};
        // Share one denominator across a sum, matching handwritten fractions.
        BigInteger denominator(1);
        for (const auto& [radicand, coefficient] : terms) {
            (void)radicand;
            denominator = (denominator / gcd(denominator, coefficient.denominator())) * coefficient.denominator();
        }
        std::string text, latex;
        for (const auto& [radicand, coefficient] : terms) {
            const auto numerator = coefficient.numerator() * (denominator / coefficient.denominator());
            const bool negative = numerator.negative();
            const auto magnitude = numerator.absolute();
            const bool scalar = radicand.compare(BigInteger(1)) == 0;
            const std::string sign = negative ? "-" : text.empty() ? "" : "+";
            text += sign; latex += sign;
            if (scalar || magnitude.compare(BigInteger(1)) != 0) { text += magnitude.text(); latex += magnitude.text(); }
            if (!scalar) {
                if (magnitude.compare(BigInteger(1)) != 0) text += "*";
                text += "sqrt(" + radicand.text() + ")"; latex += "\\sqrt{" + radicand.text() + "}";
            }
        }
        if (denominator.compare(BigInteger(1)) != 0) {
            const bool negative = text.front() == '-' && terms.size() == 1;
            if (negative) { text.erase(0, 1); latex.erase(0, 1); }
            text = (negative ? "-" : "") + std::string(terms.size() > 1 ? "(" : "") + text
                + (terms.size() > 1 ? ")" : "") + "/" + denominator.text();
            latex = (negative ? "-" : "") + std::string("\\frac{") + latex + "}{" + denominator.text() + "}";
        }
        if (text.size() > 16384 || latex.size() > 16384) throw ExactLimit();
        return {text, latex, rational(), true};
    }
};
struct Value {
    ExactExpression expression;
    std::optional<Surd> surd;
    Kind shape = Kind::number;
};
Value value(Surd result) {
    auto expression = result.expression();
    bool fraction = false;
    for (const auto& [radicand, coefficient] : result.terms) {
        (void)radicand;
        if (coefficient.denominator().compare(BigInteger(1)) != 0) fraction = true;
    }
    Kind shape = fraction ? Kind::divide : result.terms.size() > 1 ? Kind::add
        : expression.rational ? Kind::number : Kind::multiply;
    if (shape != Kind::add && expression.latex.front() == '-') shape = Kind::negate;
    return {std::move(expression), std::move(result), shape};
}
Value scalar(const Rational& number) { return value(Surd(number)); }

std::optional<Surd> exact_trig(const Node& node, bool degrees) {
    const auto multiple = exact_pi_multiple(*node.left, degrees);
    if (!multiple) return std::nullopt;
    const auto twelfths = *multiple * Rational(12);
    if (!twelfths.integer()) return std::nullopt;
    auto phase = twelfths.numerator() % BigInteger(24);
    if (phase.negative()) phase = phase + BigInteger(24);
    const unsigned index = phase.small(23), quadrant = index / 6, offset = index % 6;
    const Surd root2 = Surd::square_root(Rational(2), node), root3 = Surd::square_root(Rational(3), node),
               root6 = Surd::square_root(Rational(6), node);
    const std::vector<Surd> sine = {Surd(), root6.plus(root2.scaled(Rational(-1))).scaled(Rational(1,4)),
        Surd(Rational(1,2)), root2.scaled(Rational(1,2)), root3.scaled(Rational(1,2)),
        root6.plus(root2).scaled(Rational(1,4)), Surd(Rational(1))};
    Surd s = sine[offset], c = sine[6-offset];
    if (quadrant == 1) { s = sine[6-offset]; c = sine[offset].scaled(Rational(-1)); }
    if (quadrant == 2) { s = s.scaled(Rational(-1)); c = c.scaled(Rational(-1)); }
    if (quadrant == 3) { s = sine[6-offset].scaled(Rational(-1)); c = sine[offset]; }
    if (node.text == "sin") return s;
    if (node.text == "cos") return c;
    return s.divided(c, node);
}
std::optional<Rational> exact_log(const Rational& argument, const Rational& base) {
    if (argument.numerator().negative() || argument.zero() || base.numerator().negative()
            || base.zero() || (base - Rational(1)).zero()) return std::nullopt;
    if ((argument - Rational(1)).zero()) return Rational();
    Rational positive(1), negative(1), argument_power(1);
    for (int n = 1; n <= 64; ++n) {
        positive = positive * base; negative = negative / base;
        if ((positive - argument).zero()) return Rational(n);
        if ((negative - argument).zero()) return Rational(-n);
        argument_power = argument_power * argument;
        if ((argument_power - base).zero()) return Rational(1, n);
        if ((argument_power * base - Rational(1)).zero()) return Rational(-1, n);
    }
    return std::nullopt;
}
const Node& ungroup(const Node& node) { return node.kind == Kind::group ? ungroup(*node.left) : node; }
Value simplify(const Node& node, bool degrees) {
    if (node.kind == Kind::number) return scalar(Rational::decimal(node.text));
    if (node.kind == Kind::constant) return {{node.text, node.text == "pi" ? "\\pi " : "e", {}, false}, {}, Kind::constant};
    auto left = simplify(*node.left, degrees);
    if (node.kind == Kind::group) return left;
    std::optional<Value> right;
    if (node.right) right = simplify(*node.right, degrees);
    if (left.surd) {
        if (node.kind == Kind::negate) return value(left.surd->scaled(Rational(-1)));
        if (node.kind == Kind::percent) return value(left.surd->scaled(Rational(1,100)));
        if (right && right->surd) {
            switch (node.kind) {
                case Kind::add: return value(left.surd->plus(*right->surd));
                case Kind::subtract: return value(left.surd->plus(right->surd->scaled(Rational(-1))));
                case Kind::multiply: return value(left.surd->times(*right->surd));
                case Kind::divide: {
                    const auto result = left.surd->divided(*right->surd, node);
                    if (result) return value(*result);
                    break;
                }
                case Kind::power: {
                    const auto exponent = right->surd->rational();
                    if (exponent && exponent->integer()) {
                        const unsigned magnitude = exponent->numerator().absolute().small(8192);
                        const auto result = left.surd->power(exponent->numerator().negative()
                            ? -static_cast<int>(magnitude) : static_cast<int>(magnitude), node);
                        if (result) return value(*result);
                    } else if (exponent && exponent->denominator().compare(BigInteger(2)) == 0) {
                        const auto base = left.surd->rational();
                        if (base && !base->numerator().negative()) {
                            const unsigned magnitude = exponent->numerator().absolute().small(8192);
                            const auto result = Surd::square_root(*base, node).power(exponent->numerator().negative()
                                ? -static_cast<int>(magnitude) : static_cast<int>(magnitude), node);
                            if (result) return value(*result);
                        }
                    }
                    break;
                }
                default: break;
            }
        }
        if (node.kind == Kind::function && node.text == "sqrt") {
            if (const auto result = left.surd->square_root(node)) return value(*result);
        }
    }
    if (node.kind == Kind::function) {
        if (node.text == "sin" || node.text == "cos" || node.text == "tan") {
            if (const auto result = exact_trig(node, degrees)) return value(*result);
        }
        if (node.text == "exp" && left.expression.rational && left.expression.rational->zero()) return scalar(Rational(1));
        if (node.text == "ln" && ungroup(*node.left).kind == Kind::constant && ungroup(*node.left).text == "e")
            return scalar(Rational(1));
        if (node.text == "ln" && left.expression.rational && (*left.expression.rational - Rational(1)).zero()) return scalar(Rational());
        if (node.text == "log" && left.expression.rational) {
            const auto base = right ? right->expression.rational : std::optional<Rational>(Rational(10));
            if (base) if (const auto result = exact_log(*left.expression.rational, *base)) return scalar(*result);
        }
    }
    const auto& a = left.expression;
    const std::string b = right ? right->expression.text : "", tex = right ? right->expression.latex : "";
    const auto grouped = [](const std::string& text) { return "\\left(" + text + "\\right)"; };
    ExactExpression result;
    switch (node.kind) {
        case Kind::negate: result.text = "-("+a.text+")"; result.latex = "-"+grouped(a.latex); break;
        case Kind::percent: result.text = "("+a.text+")%"; result.latex = grouped(a.latex)+"\\%"; break;
        case Kind::function:
            result.text = node.text+"("+a.text+(right ? ";"+b : "")+")";
            result.latex = node.text == "sqrt" ? "\\sqrt{"+a.latex+"}"
                : "\\"+node.text+(right ? "_{"+tex+"}" : "")+grouped(a.latex);
            break;
        default: {
            const std::string op = node.kind == Kind::add ? "+" : node.kind == Kind::subtract ? "-"
                : node.kind == Kind::multiply ? "*" : node.kind == Kind::divide ? "/" : "^";
            result.text = "("+a.text+")"+op+"("+b+")";
            result.latex = node.kind == Kind::divide ? "\\frac{"+a.latex+"}{"+tex+"}"
                : node.kind == Kind::power ? latex_power(a.latex, left.shape, tex)
                : node.kind == Kind::multiply ? latex_product(a.latex, left.shape, tex, right->shape)
                : grouped(a.latex)+op+grouped(tex);
        }
    }
    if (result.text.size() > 16384 || result.latex.size() > 16384) throw ExactLimit();
    return {result, {}, node.kind};
}
} // namespace

ExactExpression simplify_exact(const Node& root, bool degrees) { return simplify(root, degrees).expression; }
} // namespace axioma
