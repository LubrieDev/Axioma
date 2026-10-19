#include "calculator.hpp"

#include <stdexcept>

namespace axioma {

Real calculate(Real left, Real right, Operation operation) {
    if (!numeric::isfinite(left) || !numeric::isfinite(right)) {
        throw std::invalid_argument("Enter two finite numbers.");
    }

    Real result;
    switch (operation) {
        case Operation::add:
            result = left + right;
            break;
        case Operation::subtract:
            result = left - right;
            break;
        case Operation::multiply:
            result = left * right;
            break;
        case Operation::divide:
            if (right == 0) {
                throw std::invalid_argument("Cannot divide by zero.");
            }
            result = left / right;
            break;
        default:
            throw std::invalid_argument("Unknown operation.");
    }

    if (!numeric::isfinite(result)) {
        throw std::overflow_error("The result exceeds the supported range.");
    }
    return result;
}

}  // namespace axioma
