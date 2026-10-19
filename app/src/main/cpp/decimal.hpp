#pragma once
#include "big_integer.hpp"
#include <string>
#include <string_view>
#include <utility>
#include <type_traits>

namespace axioma {
class Rational;
// Own decimal arithmetic: 80 significant working digits, including guards.
// There is intentionally no conversion to a platform floating-point type.
class Real {
public:
    static constexpr int working_digits = 80, maximum_order = 4932, minimum_order = -4965;
    Real(std::int64_t value = 0) : coefficient_(value) { normalize(); }
    template<class T> requires std::is_floating_point_v<T> Real(T) = delete;
    static Real decimal(std::string_view source);
    static Real ratio(const BigInteger& numerator, const BigInteger& denominator);
    static Real scaled(BigInteger coefficient, int exponent);
    static Real overflow();
    bool finite() const { return finite_; }
    bool zero() const { return finite_ && coefficient_.zero(); }
    bool negative() const { return coefficient_.negative(); }
    int order() const;
    Real truncated() const;
    bool integer() const { return finite_ && (zero() || exponent_ >= 0); }
    bool odd() const;
    int small_integer(int maximum) const;
    Rational rational() const;
    Real operator-() const;
    Real operator+(const Real& other) const;
    Real operator-(const Real& other) const { return *this + -other; }
    Real operator*(const Real& other) const;
    Real operator/(const Real& other) const;
    int compare(const Real& other) const;
    bool operator==(const Real& other) const { return compare(other) == 0; }
    bool operator!=(const Real& other) const { return !(*this == other); }
    bool operator<(const Real& other) const { return compare(other) < 0; }
    bool operator<=(const Real& other) const { return compare(other) <= 0; }
    bool operator>(const Real& other) const { return compare(other) > 0; }
    bool operator>=(const Real& other) const { return compare(other) >= 0; }
private:
    BigInteger coefficient_;
    int exponent_ = 0;
    int digits_ = 1;
    bool finite_ = true;
    void normalize();
};
namespace numeric {
inline bool isfinite(const Real& value) { return value.finite(); }
inline Real abs(const Real& value) { return value.negative() ? -value : value; }
inline Real trunc(const Real& value) { return value.truncated(); }
Real sqrt(const Real& value);
Real log(const Real& value);
Real log10(const Real& value);
Real exp(const Real& value);
Real pow(const Real& base, const Real& exponent);
std::pair<Real, Real> sincos(const Real& value);
inline Real sin(const Real& value) { return sincos(value).first; }
inline Real cos(const Real& value) { return sincos(value).second; }
inline Real tan(const Real& value) { const auto [sine, cosine] = sincos(value); return sine / cosine; }
} // namespace numeric
} // namespace axioma
