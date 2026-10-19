#include "rational.hpp"

namespace axioma {
Rational::Rational(BigInteger numerator, BigInteger denominator) {
    if (denominator.zero()) throw std::invalid_argument("Cannot divide by zero.");
    if (denominator.negative()) { numerator = -numerator; denominator = -denominator; }
    if (numerator.zero()) { numerator_ = 0; denominator_ = 1; return; }
    const auto divisor = gcd(numerator, denominator);
    numerator_ = numerator / divisor; denominator_ = denominator / divisor;
    // Leave scratch space for normalization and decimal conversion.
    if (numerator_.absolute().text().size() > 8192 || denominator_.text().size() > 8192) throw ExactLimit();
}
Rational Rational::decimal(std::string_view text) {
    const auto marker = text.find_first_of("eE");
    const auto mantissa = text.substr(0, marker);
    int exponent = 0;
    if (marker != std::string_view::npos) {
        auto power = text.substr(marker + 1);
        const bool negative = !power.empty() && power.front() == '-';
        if (!power.empty() && (power.front() == '+' || power.front() == '-')) power.remove_prefix(1);
        if (power.empty()) throw std::invalid_argument("Missing exponent digits.");
        for (char c : power) {
            if (c < '0' || c > '9') throw std::invalid_argument("Invalid exponent digit.");
            if (exponent > 8192 / 10) throw ExactLimit();
            exponent = exponent * 10 + (c - '0');
            if (exponent > 8192) throw ExactLimit();
        }
        if (negative) exponent = -exponent;
    }
    std::string digits;
    bool point = false, found_digit = false;
    for (char c : mantissa) {
        if (c == '.' || c == ',') {
            if (point) throw std::invalid_argument("Invalid decimal point.");
            point = true;
        } else if (c >= '0' && c <= '9') {
            digits += c; found_digit = true; if (point) --exponent;
        } else if (digits.empty() && (c == '-' || c == '+')) digits += c;
        else throw std::invalid_argument("Invalid decimal digit.");
    }
    if (!found_digit) throw std::invalid_argument("Expected decimal digits.");
    BigInteger numerator(digits);
    if (numerator.zero()) return {};
    if (exponent >= 0) return Rational(numerator * BigInteger::power_of_ten(exponent));
    return Rational(numerator, BigInteger::power_of_ten(-exponent));
}
Rational Rational::operator+(const Rational& other) const {
    const auto common = gcd(denominator_, other.denominator_);
    const auto left = denominator_ / common, right = other.denominator_ / common;
    return Rational(numerator_ * right + other.numerator_ * left, left * other.denominator_);
}
Rational Rational::operator*(const Rational& other) const {
    const auto left = gcd(numerator_, other.denominator_);
    const auto right = gcd(other.numerator_, denominator_);
    return Rational((numerator_ / left) * (other.numerator_ / right),
                    (denominator_ / right) * (other.denominator_ / left));
}
Rational Rational::operator/(const Rational& other) const {
    if (other.zero()) throw std::invalid_argument("Cannot divide by zero.");
    return *this * Rational(other.denominator_, other.numerator_);
}
Rational Rational::power(int exponent) const {
    if (exponent < -8192 || exponent > 8192) throw ExactLimit();
    if (zero() && exponent <= 0) throw std::invalid_argument("Zero cannot be raised to a nonpositive exponent.");
    Rational factor = exponent < 0 ? Rational(denominator_, numerator_) : *this;
    unsigned count = exponent < 0 ? unsigned(-exponent) : unsigned(exponent);
    Rational result(1);
    while (count) {
        if (count & 1U) result = result * factor;
        count >>= 1U; if (count) factor = factor * factor;
    }
    return result;
}
std::string Rational::text() const {
    return numerator_.text() + (integer() ? "" : "/" + denominator_.text());
}
std::optional<Real> Rational::approximate() const {
    if (zero()) return Real(0);
    const Real result = Real::ratio(numerator_, denominator_);
    if (!numeric::isfinite(result) || result == 0) return std::nullopt;
    return result;
}
} // namespace axioma
