#pragma once
#include "big_integer.hpp"
#include "real.hpp"
#include <optional>

namespace axioma {
/** Reduced exact fractions with positive denominator, including canonical 0/1. */
class Rational {
public:
    Rational(BigInteger numerator = 0, BigInteger denominator = 1);
    static Rational decimal(std::string_view text);
    const BigInteger& numerator() const { return numerator_; }
    const BigInteger& denominator() const { return denominator_; }
    bool zero() const { return numerator_.zero(); }
    bool integer() const { return denominator_.compare(BigInteger(1)) == 0; }
    Rational operator-() const { return Rational(-numerator_, denominator_); }
    Rational operator+(const Rational& other) const;
    Rational operator-(const Rational& other) const { return *this + -other; }
    Rational operator*(const Rational& other) const;
    Rational operator/(const Rational& other) const;
    Rational power(int exponent) const;
    std::string text() const;
    std::optional<Real> approximate() const;
private:
    BigInteger numerator_, denominator_;
};
} // namespace axioma
