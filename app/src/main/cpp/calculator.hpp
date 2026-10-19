#pragma once
#include "real.hpp"

namespace axioma {

enum class Operation { add, subtract, multiply, divide };

// Throws std::invalid_argument for invalid input or std::overflow_error
// when the result cannot be represented as a finite Real.
Real calculate(Real left, Real right, Operation operation);

}  // namespace axioma
