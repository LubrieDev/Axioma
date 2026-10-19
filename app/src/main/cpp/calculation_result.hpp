#pragma once
#include "expression.hpp"

namespace axioma {
// Empty exact/decimal fields mean unavailable, never a numeric zero.
struct CalculationResult {
    std::string exact, precise, display, continuation, text, latex;
    bool exact_display = false;
};
CalculationResult calculate_result(const Node& root, std::string_view source, bool degrees, bool exact_mode = true);
} // namespace axioma
