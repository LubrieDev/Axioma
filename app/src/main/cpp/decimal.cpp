#include "decimal.hpp"
#include "rational.hpp"
#include "constants.hpp"
#include <algorithm>

namespace axioma {
Real Real::overflow() { Real result; result.finite_ = false; return result; }
Real Real::scaled(BigInteger coefficient, int exponent) {
    Real result; result.coefficient_ = std::move(coefficient); result.exponent_ = exponent;
    result.normalize(); return result;
}
void Real::normalize() {
    if (coefficient_.zero()) { exponent_ = 0; digits_ = 1; return; }
    auto digits = coefficient_.absolute().text();
    if (digits.size() > working_digits) {
        const auto dropped = digits.size() - working_digits;
        BigInteger whole(digits.substr(0, working_digits));
        const char first_dropped = digits[working_digits];
        const bool remainder = digits.find_first_not_of('0', working_digits + 1) != std::string::npos;
        const bool odd = (digits[working_digits - 1] - '0') % 2 != 0;
        if (first_dropped > '5' || (first_dropped == '5' && (remainder || odd))) whole = whole + BigInteger(1);
        coefficient_ = coefficient_.negative() ? -whole : whole;
        exponent_ += static_cast<int>(dropped);
        digits = coefficient_.absolute().text();
    }
    const auto last = digits.find_last_not_of('0');
    const auto trailing = digits.size() - last - 1;
    if (trailing) {
        digits.resize(last + 1);
        coefficient_ = BigInteger((coefficient_.negative() ? "-" : "") + digits);
        exponent_ += static_cast<int>(trailing);
    }
    const int magnitude = exponent_ + static_cast<int>(digits.size()) - 1;
    digits_ = static_cast<int>(digits.size());
    if (magnitude > maximum_order) finite_ = false;
    if (magnitude < minimum_order) { coefficient_ = 0; exponent_ = 0; digits_ = 1; }
}
int Real::order() const { return zero() ? 0 : exponent_ + digits_ - 1; }
Real Real::decimal(std::string_view source) {
    const auto marker = source.find_first_of("eE");
    const auto mantissa = source.substr(0, marker);
    int exponent = 0;
    if (marker != std::string_view::npos) {
        auto tail = source.substr(marker + 1);
        const bool negative = !tail.empty() && tail.front() == '-';
        if (!tail.empty() && (tail.front() == '+' || tail.front() == '-')) tail.remove_prefix(1);
        if (tail.empty()) throw std::invalid_argument("Missing exponent digits.");
        for (char c : tail) {
            if (c < '0' || c > '9') throw std::invalid_argument("Invalid exponent digit.");
            exponent = std::min(100000, exponent * 10 + c - '0');
        }
        if (negative) exponent = -exponent;
    }
    std::string digits;
    bool point = false, found = false;
    for (char c : mantissa) {
        if (c == '.' || c == ',') {
            if (point) throw std::invalid_argument("Invalid decimal point.");
            point = true;
        } else if (c >= '0' && c <= '9') { digits += c; found = true; if (point) --exponent; }
        else if (digits.empty() && (c == '+' || c == '-')) digits += c;
        else throw std::invalid_argument("Invalid decimal digit.");
    }
    if (!found) throw std::invalid_argument("Expected decimal digits.");
    return scaled(BigInteger(digits), exponent);
}
Real Real::operator-() const { Real result = *this; result.coefficient_ = -result.coefficient_; return result; }
Real Real::operator+(const Real& other) const {
    if (!finite_ || !other.finite_) return overflow();
    if (zero()) return other;
    if (other.zero()) return *this;
    if (order() - other.order() > working_digits + 2) return *this;
    if (other.order() - order() > working_digits + 2) return other;
    const int exponent = std::min(exponent_, other.exponent_);
    return scaled(coefficient_ * BigInteger::power_of_ten(exponent_ - exponent)
        + other.coefficient_ * BigInteger::power_of_ten(other.exponent_ - exponent), exponent);
}
Real Real::operator*(const Real& other) const {
    if (!finite_ || !other.finite_) return overflow();
    return scaled(coefficient_ * other.coefficient_, exponent_ + other.exponent_);
}
Real Real::ratio(const BigInteger& numerator, const BigInteger& denominator) {
    if (denominator.zero()) return overflow();
    if (numerator.zero()) return {};
    const int shift = working_digits + 5 + static_cast<int>(denominator.absolute().text().size())
        - static_cast<int>(numerator.absolute().text().size());
    const auto whole = shift >= 0 ? (numerator * BigInteger::power_of_ten(shift)) / denominator
        : numerator / (denominator * BigInteger::power_of_ten(-shift));
    return scaled(whole, -shift);
}
Real Real::operator/(const Real& other) const {
    if (!finite_ || !other.finite_ || other.zero()) return overflow();
    const int shift = working_digits + 5 + other.digits_ - digits_;
    const auto whole = shift >= 0 ? (coefficient_ * BigInteger::power_of_ten(shift)) / other.coefficient_
        : coefficient_ / (other.coefficient_ * BigInteger::power_of_ten(-shift));
    return scaled(whole, exponent_ - other.exponent_ - shift);
}
int Real::compare(const Real& other) const {
    if (!finite_ || !other.finite_) throw std::invalid_argument("Cannot compare an out-of-range value.");
    if (zero() && other.zero()) return 0;
    if (negative() != other.negative()) return negative() ? -1 : 1;
    if (zero()) return -1;
    if (other.zero()) return 1;
    if (order() != other.order()) return (order() < other.order() ? -1 : 1) * (negative() ? -1 : 1);
    const int exponent = std::min(exponent_, other.exponent_);
    return (coefficient_ * BigInteger::power_of_ten(exponent_ - exponent)).compare(
        other.coefficient_ * BigInteger::power_of_ten(other.exponent_ - exponent));
}
Real Real::truncated() const {
    if (!finite_ || zero() || exponent_ >= 0) return *this;
    if (-exponent_ >= digits_) return {};
    return scaled(coefficient_ / BigInteger::power_of_ten(-exponent_), 0);
}
bool Real::odd() const { return integer() && exponent_ == 0 && !(coefficient_ % BigInteger(2)).zero(); }
int Real::small_integer(int maximum) const {
    if (!integer() || order() > 8) throw std::invalid_argument("Integer exceeds the numerical iteration limit.");
    const auto value = coefficient_.absolute() * BigInteger::power_of_ten(exponent_);
    const auto magnitude = value.small(maximum);
    return negative() ? -static_cast<int>(magnitude) : static_cast<int>(magnitude);
}
Rational Real::rational() const {
    if (!finite_) throw std::invalid_argument("The result must be finite.");
    return exponent_ >= 0 ? Rational(coefficient_ * BigInteger::power_of_ten(exponent_))
        : Rational(coefficient_, BigInteger::power_of_ten(-exponent_));
}

namespace numeric {
namespace {
const Real ln_two = Real::decimal("0.69314718055994530941723212145817656807550013436025525412068000949339362196969471560586");
const Real ln_ten = Real::decimal("2.30258509299404568401799145468436420760110148862877297603332790096757260967735248023599");
Real integer_power(Real factor, int exponent) {
    if (exponent < 0) factor = Real(1) / factor;
    unsigned count = exponent < 0 ? static_cast<unsigned>(-exponent) : static_cast<unsigned>(exponent);
    Real result(1);
    while (count) {
        if (count & 1U) { result = result * factor; if (!result.finite() || result.zero()) return result; }
        count >>= 1U;
        if (count) { factor = factor * factor; if (!factor.finite()) return Real::overflow(); }
    }
    return result;
}
bool converged(const Real& before, const Real& after) {
    return before == after || (!after.zero() && abs(after - before).order() < after.order() - 74);
}
} // namespace
Real sqrt(const Real& value) {
    if (!value.finite() || value < 0) return Real::overflow();
    if (value.zero()) return {};
    int order = value.order();
    if (order < 0 && order % 2) --order;
    Real guess = Real::scaled(1, order / 2);
    for (int i = 0; i < 300; ++i) {
        const auto next = (guess + value / guess) / 2;
        if (converged(guess, next)) return next;
        guess = next;
    }
    throw std::invalid_argument("Square root iteration limit exceeded.");
}
Real log(const Real& value) {
    if (!value.finite() || value <= 0) return Real::overflow();
    if (value == 1) return {};
    Real reduced = value;
    int order = 0, roots = 0;
    if (value < Real::decimal("0.5") || value > 2) {
        order = value.order(); reduced = value / Real::scaled(1, order);
        for (; roots < 8; ++roots) reduced = sqrt(reduced);
    }
    const Real z = (reduced - 1) / (reduced + 1), square = z * z;
    Real term = z, sum = z;
    for (int k = 1; k < 300; ++k) {
        term = term * square;
        const auto next = sum + term / (2 * k + 1);
        if (converged(sum, next)) return next * (2 << roots) + Real(order) * ln_ten;
        sum = next;
    }
    throw std::invalid_argument("Logarithm iteration limit exceeded.");
}
Real log10(const Real& value) { return log(value) / ln_ten; }
Real exp(const Real& value) {
    if (!value.finite() || value > 12000) return Real::overflow();
    if (value < -12000) return {};
    const int exponent = (value / ln_two).truncated().small_integer(20000);
    const Real residual = value - Real(exponent) * ln_two;
    Real term(1), sum(1);
    for (int k = 1; k < 300; ++k) {
        term = term * residual / k;
        const auto next = sum + term;
        if (converged(sum, next)) return next * integer_power(2, exponent);
        sum = next;
    }
    throw std::invalid_argument("Exponential iteration limit exceeded.");
}
Real pow(const Real& base, const Real& exponent) {
    if (!base.finite() || !exponent.finite()) return Real::overflow();
    if (exponent.zero()) return 1;
    if (base.zero()) return exponent > 0 ? Real(0) : Real::overflow();
    if (exponent.integer() && abs(exponent) <= 100000) return integer_power(base, exponent.small_integer(100000));
    if (base < 0 && !exponent.integer()) return Real::overflow();
    const auto result = exp(exponent * log(abs(base)));
    return base < 0 && exponent.odd() ? -result : result;
}
std::pair<Real, Real> sincos(const Real& value) {
    // Numerical period reduction retains at least 40 useful working digits.
    // Symbolic quadrants are reduced separately before reaching this function.
    if (!value.finite() || (!value.zero() && value.order() > 30)) return {Real::overflow(), Real::overflow()};
    const Real period = constants::pi * 2;
    Real angle = value - (value / period).truncated() * period;
    if (angle > constants::pi) angle = angle - period;
    if (angle < -constants::pi) angle = angle + period;
    Real cosine_sign(1);
    if (angle > constants::pi / 2) { angle = constants::pi - angle; cosine_sign = -1; }
    if (angle < -constants::pi / 2) { angle = -constants::pi - angle; cosine_sign = -1; }
    const Real negative_square = -(angle * angle);
    Real sine = angle, sine_term = angle, cosine(1), cosine_term(1);
    for (int k = 1; k < 200; ++k) {
        sine_term = sine_term * negative_square / ((2 * k) * (2 * k + 1));
        cosine_term = cosine_term * negative_square / ((2 * k - 1) * (2 * k));
        const Real next_sine = sine + sine_term, next_cosine = cosine + cosine_term;
        if (converged(sine, next_sine) && converged(cosine, next_cosine))
            return {next_sine, next_cosine * cosine_sign};
        sine = next_sine; cosine = next_cosine;
    }
    throw std::invalid_argument("Trigonometric iteration limit exceeded.");
}
} // namespace numeric
} // namespace axioma
