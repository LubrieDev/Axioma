#pragma once
#include "real.hpp"
#include <string>

namespace axioma {

struct Approximation {
    std::string precise;
    std::string display;
};

Approximation approximate(Real value);
class Rational;
// Round a proven rational directly using integer quotient and remainder.
Approximation approximate(const Rational& value);

}  // namespace axioma
