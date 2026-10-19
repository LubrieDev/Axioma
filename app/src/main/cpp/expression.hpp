#pragma once
#include "real.hpp"
#include <memory>
#include <string>
#include <string_view>

namespace axioma {
enum class Kind { number, constant, add, subtract, multiply, divide, power, negate, function, group, percent };
struct Node {
    Kind kind;
    std::string text;
    Real value = 0;
    std::unique_ptr<Node> left, right;
    std::size_t position = 0;
    unsigned depth = 1;
};
inline constexpr unsigned max_depth = 48, max_nodes = 256, max_characters = 2048;
std::unique_ptr<Node> parse(std::string_view source);
Real evaluate(const Node& root, bool degrees = false, bool exact_operands = false);
std::string to_latex(const Node& root);
}  // namespace axioma
