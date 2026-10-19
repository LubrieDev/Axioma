#include "approximation.hpp"
#include "rational.hpp"
#include <stdexcept>

namespace axioma {
namespace {

std::string format_rational(const Rational& value, int places, int exponent, bool scientific) {
    auto numerator = value.numerator().absolute(), denominator = value.denominator();
    const int scale = places - (scientific ? exponent : 0);
    if (scale >= 0) numerator = numerator * BigInteger::power_of_ten(scale);
    else denominator = denominator * BigInteger::power_of_ten(-scale);
    auto [whole, remainder] = numerator.divide(denominator);
    const int halfway = (remainder * BigInteger(2)).compare(denominator);
    // Round to nearest, ties to even. No floating-point conversion and no
    // second rounding of the retained result for the shorter display.
    if (halfway > 0 || (halfway == 0 && !(whole % BigInteger(2)).zero())) whole = whole + BigInteger(1);
    if (whole.zero()) return "0";
    std::string digits = whole.text();
    if (scientific && digits.size() > static_cast<std::size_t>(places + 1)) {
        ++exponent; digits.pop_back();
    }
    if (digits.size() <= static_cast<std::size_t>(places))
        digits.insert(0, places + 1 - digits.size(), '0');
    digits.insert(digits.size() - places, ".");
    while (digits.back() == '0') digits.pop_back();
    if (digits.back() == '.') digits.pop_back();
    if (value.numerator().negative()) digits.insert(0, "-");
    if (scientific && exponent != 0) digits += "e" + std::to_string(exponent);
    return digits;
}

}  // namespace

Approximation approximate(Real value) {
    if (!value.finite()) throw std::invalid_argument("The result must be finite.");
    return approximate(value.rational());
}

Approximation approximate(const Rational& value) {
    if (value.zero()) return {"0", "0"};
    const auto numerator = value.numerator().absolute();
    const auto& denominator = value.denominator();
    int exponent = static_cast<int>(numerator.text().size()) - static_cast<int>(denominator.text().size());
    const int order = exponent >= 0
        ? numerator.compare(denominator * BigInteger::power_of_ten(exponent))
        : (numerator * BigInteger::power_of_ten(-exponent)).compare(denominator);
    if (order < 0) --exponent;
    const bool scientific = exponent < -6 || exponent >= 9;
    const int offset = scientific ? 1 : exponent + 1;
    return {format_rational(value, result_significant_digits - offset, exponent, scientific),
            format_rational(value, display_significant_digits - offset, exponent, scientific)};
}

}  // namespace axioma
