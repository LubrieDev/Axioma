#pragma once
#include "real.hpp"

namespace axioma::constants {

// Read constants directly as decimal text, preserving all working digits.
inline const Real pi = Real::decimal("3.14159265358979323846264338327950288419716939937510582097494459230781640628620899862803");
inline const Real e = Real::decimal("2.71828182845904523536028747135266249775724709369995957496696762772407663035354759457138");

}  // namespace axioma::constants
