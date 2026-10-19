#include "expression.hpp"
#include "latex_product.hpp"
#include "calculator.hpp"
#include "constants.hpp"
#include "exact_math.hpp"
#include "evaluation_error.hpp"
#include <algorithm>
#include <stdexcept>
#include <tuple>
#include <utility>
#include <vector>

namespace axioma {
namespace {
enum class TokenKind { number, name, plus, minus, times, divide, power, percent, open, close, subscript, separator, end };
struct Token { TokenKind kind; std::string text; std::size_t position; };
bool digit(char c) { return c >= '0' && c <= '9'; }
bool letter(char c) { return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'); }
[[noreturn]] void fail(const std::string& message, std::size_t position) {
    throw std::invalid_argument(message + " (position " + std::to_string(position + 1) + ").");
}
[[noreturn]] void diagnose(EvaluationStatus status, const std::string& message, const Node& node) {
    throw EvaluationError(status, message + " (position " + std::to_string(node.position + 1) + ").");
}
std::size_t column(std::string_view source, std::size_t end) {
    std::size_t count = 0;
    for (std::size_t i = 0; i < end; ++i)
        if ((static_cast<unsigned char>(source[i]) & 0xc0U) != 0x80U) ++count;
    return count;
}

std::vector<Token> tokenize(std::string_view source) {
    if (source.size() > max_characters * 4 || column(source, source.size()) > max_characters)
        fail("The expression exceeds 2048 characters", 0);
    std::vector<Token> tokens;
    std::size_t i = 0;
    auto emit = [&](TokenKind kind, std::string text, std::size_t start) {
        if (tokens.size() >= max_nodes * 3) fail("The expression has too many elements", column(source, start));
        tokens.push_back({kind, std::move(text), column(source, start)});
    };
    while (i < source.size()) {
        const char c = source[i];
        const auto start = i;
        if (c == ' ' || c == '\t' || c == '\n' || c == '\r') { ++i; continue; }
        if (digit(c) || c == '.' || c == ',') {
            bool has_digit = false;
            while (i < source.size() && digit(source[i])) { ++i; has_digit = true; }
            if (i < source.size() && (source[i] == '.' || source[i] == ',')) {
                ++i;
                while (i < source.size() && digit(source[i])) { ++i; has_digit = true; }
            }
            if (!has_digit) fail("Expected a number", column(source, start));
            if (i < source.size() && (source[i] == '.' || source[i] == ','))
                fail("Malformed number", column(source, i));
            if (i < source.size() && (source[i] == 'e' || source[i] == 'E')) {
                auto next = i + 1;
                bool signed_exponent = false;
                if (next < source.size() && (source[next] == '+' || source[next] == '-')) {
                    signed_exponent = true; ++next;
                }
                if (next < source.size() && digit(source[next])) {
                    i = next + 1;
                    while (i < source.size() && digit(source[i])) ++i;
                } else if (signed_exponent) fail("Missing digits in the exponent", column(source, next));
            }
            auto text = std::string(source.substr(start, i - start));
            std::replace(text.begin(), text.end(), ',', '.');
            emit(TokenKind::number, std::move(text), start);
            continue;
        }
        if (source.substr(i).starts_with("\\%")) {
            i += 2; emit(TokenKind::percent, "%", start); continue;
        }
        if (letter(c) || c == '\\') {
            const bool command = c == '\\';
            if (command) ++i;
            const auto name_start = i;
            while (i < source.size() && letter(source[i])) ++i;
            if (i == name_start) fail("Unsupported command", column(source, start));
            auto name = std::string(source.substr(name_start, i - name_start));
            if (command && (name == "left" || name == "right")) continue;
            if (command && (name == "cdot" || name == "times")) emit(TokenKind::times, "*", start);
            else if (command && name == "div") emit(TokenKind::divide, "/", start);
            else {
                if (command && (name == "dfrac" || name == "tfrac")) name = "frac";
                emit(TokenKind::name, std::move(name), start);
            }
            continue;
        }
        bool unicode = false;
        for (const auto& [symbol, kind, text] : {
            std::tuple<std::string_view, TokenKind, const char*>{"π", TokenKind::name, "pi"},
            {"√", TokenKind::name, "sqrt"}, {"×", TokenKind::times, "*"},
            {"·", TokenKind::times, "*"}, {"÷", TokenKind::divide, "/"}, {"−", TokenKind::minus, "-"}}) {
            if (source.substr(i).starts_with(symbol)) {
                i += symbol.size(); emit(kind, text, start); unicode = true; break;
            }
        }
        if (unicode) continue;
        ++i;
        switch (c) {
            case '+': emit(TokenKind::plus, "+", start); break;
            case '-': emit(TokenKind::minus, "-", start); break;
            case '*': emit(TokenKind::times, "*", start); break;
            case '/': emit(TokenKind::divide, "/", start); break;
            case '^': emit(TokenKind::power, "^", start); break;
            case '%': emit(TokenKind::percent, "%", start); break;
            case '_': emit(TokenKind::subscript, "_", start); break;
            case ';': emit(TokenKind::separator, ";", start); break;
            case '(': case '[': case '{': emit(TokenKind::open, std::string(1, c), start); break;
            case ')': case ']': case '}': emit(TokenKind::close, std::string(1, c), start); break;
            default: fail("Unsupported character", column(source, start));
        }
    }
    tokens.push_back({TokenKind::end, "", column(source, source.size())});
    return tokens;
}

class Parser {
public:
    explicit Parser(std::vector<Token> tokens) : tokens_(std::move(tokens)) {}
    std::unique_ptr<Node> read() {
        if (current().kind == TokenKind::end) fail("Enter an expression", current().position);
        auto root = sum();
        if (current().kind != TokenKind::end) fail("Unexpected element: " + current().text, current().position);
        return root;
    }
private:
    std::vector<Token> tokens_;
    std::size_t cursor_ = 0;
    unsigned nodes_ = 0, nesting_ = 0;
    const Token& current() const { return tokens_[cursor_]; }
    Token take() { return tokens_[cursor_++]; }
    std::unique_ptr<Node> make(Kind kind, Token token, std::unique_ptr<Node> left = {},
                              std::unique_ptr<Node> right = {}) {
        if (++nodes_ > max_nodes) fail("The expression exceeds 256 nodes", token.position);
        const unsigned depth = 1 + std::max(left ? left->depth : 0, right ? right->depth : 0);
        if (depth > max_depth) fail("The expression exceeds 48 levels", token.position);
        return std::make_unique<Node>(Node{kind, std::move(token.text), 0, std::move(left),
                                          std::move(right), token.position, depth});
    }
    std::unique_ptr<Node> sum() {
        auto left = product();
        while (current().kind == TokenKind::plus || current().kind == TokenKind::minus) {
            auto token = take();
            const auto kind = token.kind == TokenKind::plus ? Kind::add : Kind::subtract;
            auto right = product();
            left = make(kind, std::move(token), std::move(left), std::move(right));
        }
        return left;
    }
    std::unique_ptr<Node> product() {
        auto left = unary();
        for (;;) {
            const auto kind = current().kind;
            if (kind == TokenKind::times || kind == TokenKind::divide) {
                auto token = take();
                auto right = unary();
                left = make(kind == TokenKind::times ? Kind::multiply : Kind::divide,
                            std::move(token), std::move(left), std::move(right));
            } else if (kind == TokenKind::open || kind == TokenKind::name || kind == TokenKind::number) {
                if (kind == TokenKind::number && tokens_[cursor_ - 1].kind == TokenKind::number)
                    fail("Missing operator between numbers", current().position);
                Token token{TokenKind::times, "*", current().position};
                auto right = unary();
                left = make(Kind::multiply, std::move(token), std::move(left), std::move(right));
            } else return left;
        }
    }
    // -2^2 = -4; 2^-3 = 1/8; 2^3^2 = 512. Multiplication/division are left-associative.
    std::unique_ptr<Node> unary() {
        if (++nesting_ > max_depth) fail("The expression exceeds 48 levels", current().position);
        struct Exit { unsigned& depth; ~Exit() { --depth; } } exit{nesting_};
        if (current().kind == TokenKind::plus) { take(); return unary(); }
        if (current().kind == TokenKind::minus) {
            auto token = take();
            auto child = unary();
            return make(Kind::negate, std::move(token), std::move(child));
        }
        auto left = primary();
        while (current().kind == TokenKind::percent) {
            auto token = take();
            left = make(Kind::percent, std::move(token), std::move(left));
        }
        if (current().kind == TokenKind::power) {
            auto token = take();
            auto right = current().kind == TokenKind::open && current().text == "{"
                ? delimited(false) : unary();
            return make(Kind::power, std::move(token), std::move(left), std::move(right));
        }
        return left;
    }
    std::unique_ptr<Node> delimited(bool show_parentheses) {
        const auto opening = take();
        const char closing = opening.text == "(" ? ')' : opening.text == "[" ? ']' : '}';
        auto child = sum();
        if (current().kind != TokenKind::close || current().text[0] != closing)
            fail(std::string("Expected '") + closing + "'", current().position);
        take();
        return show_parentheses ? make(Kind::group, opening, std::move(child)) : std::move(child);
    }
    std::unique_ptr<Node> logarithm(Token token) {
        std::unique_ptr<Node> base;
        if (current().kind == TokenKind::subscript) {
            take();
            base = current().kind == TokenKind::open && current().text == "{"
                ? delimited(false) : unary();
        }
        if (current().kind != TokenKind::open) {
            if (!base) fail("The logarithm requires an argument in parentheses", current().position);
            auto argument = unary();
            return make(Kind::function, std::move(token), std::move(argument), std::move(base));
        }
        const auto opening = take();
        const char closing = opening.text == "(" ? ')' : opening.text == "[" ? ']' : '}';
        auto argument = sum();
        if (current().kind == TokenKind::separator) {
            if (base) fail("Specify the logarithm base only once", current().position);
            take(); base = sum();
        }
        if (current().kind != TokenKind::close || current().text[0] != closing)
            fail(std::string("Expected '") + closing + "' after the logarithm arguments", current().position);
        take();
        return make(Kind::function, std::move(token), std::move(argument), std::move(base));
    }
    std::unique_ptr<Node> primary() {
        if (current().kind == TokenKind::number) {
            auto token = take();
            const Real value = Real::decimal(token.text);
            if (!numeric::isfinite(value)) fail("Number out of range", token.position);
            const auto mantissa = token.text.substr(0, token.text.find_first_of("eE"));
            if (value == 0 && mantissa.find_first_of("123456789") != std::string::npos)
                fail("Number is too small", token.position);
            auto node = make(Kind::number, std::move(token));
            node->value = value;
            return node;
        }
        if (current().kind == TokenKind::open) return delimited(true);
        if (current().kind == TokenKind::name) {
            auto token = take();
            if (token.text == "pi" || token.text == "e") return make(Kind::constant, std::move(token));
            if (token.text == "frac") {
                if (current().kind != TokenKind::open || current().text != "{")
                    fail("Use \\frac{numerator}{denominator}", current().position);
                auto numerator = delimited(false);
                if (current().kind != TokenKind::open || current().text != "{")
                    fail("Missing denominator in braces", current().position);
                auto denominator = delimited(false);
                return make(Kind::divide, std::move(token), std::move(numerator), std::move(denominator));
            }
            if (token.text != "sqrt" && token.text != "sin" && token.text != "cos" && token.text != "tan" &&
                token.text != "ln" && token.text != "log" && token.text != "exp")
                fail("Unsupported name: " + token.text, token.position);
            if (token.text == "log") return logarithm(std::move(token));
            if (current().kind != TokenKind::open)
                fail("The function requires an argument in parentheses", current().position);
            auto argument = delimited(false);
            return make(Kind::function, std::move(token), std::move(argument));
        }
        fail("Expected a number, a function, or an opening parenthesis", current().position);
    }
};
Real checked(Real value, const Node& node) {
    if (!numeric::isfinite(value)) diagnose(EvaluationStatus::out_of_range, "The result exceeds the supported range", node);
    return value;
}
}  // namespace

std::unique_ptr<Node> parse(std::string_view source) { return Parser(tokenize(source)).read(); }
Real evaluate(const Node& root, bool degrees, bool exact_operands) {
    if (exact_operands) {
        try {
            if (const auto exact = evaluate_exact(root)) {
                if (const auto value = exact->approximate()) return *value;
                diagnose(EvaluationStatus::out_of_range,
                         "The exact operand is outside the numerical function range", root);
            }
        } catch (const ExactLimit&) {
            // The caller shares a bounded proof budget across the evaluation.
            exact_operands = false;
        }
    }
    auto child = [&](const std::unique_ptr<Node>& node) { return evaluate(*node, degrees, exact_operands); };
    switch (root.kind) {
        case Kind::number: return root.value;
        case Kind::constant: return root.text == "pi" ? constants::pi : constants::e;
        case Kind::negate: return -child(root.left);
        case Kind::group: return child(root.left);
        case Kind::percent: {
            const Real argument = child(root.left), value = argument / 100;
            if (value == 0 && argument != 0) diagnose(EvaluationStatus::out_of_range, "The result is too small", root);
            return value;
        }
        case Kind::add: case Kind::subtract: case Kind::multiply: case Kind::divide: {
            const Real left = child(root.left), right = child(root.right);
            Operation operation = Operation::add;
            if (root.kind == Kind::subtract) operation = Operation::subtract;
            if (root.kind == Kind::multiply) operation = Operation::multiply;
            if (root.kind == Kind::divide) operation = Operation::divide;
            if (operation == Operation::divide && right == 0)
                diagnose(left == 0 ? EvaluationStatus::indeterminate : EvaluationStatus::undefined,
                         left == 0 ? "Zero divided by zero has no determined value" : "Cannot divide by zero", root);
            try {
                const Real value = calculate(left, right, operation);
                if (value == 0 && left != 0 && right != 0 &&
                    (operation == Operation::multiply || operation == Operation::divide))
                    diagnose(EvaluationStatus::out_of_range, "The result is too small", root);
                return value;
            } catch (const EvaluationError&) {
                throw;
            } catch (const std::overflow_error&) {
                diagnose(EvaluationStatus::out_of_range, "The result exceeds the supported range", root);
            } catch (const std::invalid_argument& error) {
                if (std::string_view(error.what()).find("position") != std::string_view::npos) throw;
                fail(error.what(), root.position);
            }
        }
        case Kind::power: {
            const Real base = child(root.left), exponent = child(root.right);
            if (base == 0 && exponent <= 0)
                diagnose(exponent == 0 ? EvaluationStatus::indeterminate : EvaluationStatus::undefined,
                         "Zero cannot be raised to a nonpositive exponent", root);
            if (base < 0 && numeric::trunc(exponent) != exponent)
                diagnose(EvaluationStatus::unsupported,
                         "Non-integer powers of negative numbers are not supported yet", root);
            const Real value = checked(numeric::pow(base, exponent), root);
            if (value == 0 && base != 0) diagnose(EvaluationStatus::out_of_range, "The result is too small", root);
            return value;
        }
        case Kind::function: {
            if (root.text == "log" && root.right) {
                const Real argument = child(root.left), base = child(root.right);
                if (base <= 0 || base == 1)
                    diagnose(EvaluationStatus::undefined,
                             "The logarithm base must be positive and different from one", root);
                if (argument <= 0)
                    diagnose(argument == 0 ? EvaluationStatus::undefined : EvaluationStatus::not_real,
                             "The logarithm requires a positive number", root);
                const Real value = checked(numeric::log(argument) / numeric::log(base), root);
                if (value == 0 && argument != 1)
                    diagnose(EvaluationStatus::out_of_range, "The result is too small", root);
                return value;
            }
            if (root.text == "sin" || root.text == "cos" || root.text == "tan") {
                const auto exact = exact_trigonometric(*root.left, root.text, degrees);
                if (exact) {
                    if (exact->pole) diagnose(EvaluationStatus::undefined, "The tangent is undefined for this angle", root);
                    return checked(exact->value, root);
                }
            }
            const Real argument = child(root.left);
            const Real angle = degrees ? argument * (constants::pi / 180) : argument;
            Real value = 0;
            if (root.text == "sqrt") {
                if (argument < 0) diagnose(EvaluationStatus::not_real, "The square root requires a nonnegative number", root);
                value = numeric::sqrt(argument);
            } else if (root.text == "ln" || root.text == "log") {
                if (argument <= 0)
                    diagnose(argument == 0 ? EvaluationStatus::undefined : EvaluationStatus::not_real,
                             "The logarithm requires a positive number", root);
                value = root.text == "ln" ? numeric::log(argument) : numeric::log10(argument);
            } else if (root.text == "sin") value = numeric::sin(angle);
            else if (root.text == "cos") value = numeric::cos(angle);
            else if (root.text == "tan") {
                const auto [sine, cosine] = numeric::sincos(angle);
                if (!cosine.finite()) diagnose(EvaluationStatus::out_of_range,
                        "The angle exceeds the supported numerical reduction range", root);
                if (cosine == 0) diagnose(EvaluationStatus::undefined, "The tangent is undefined for this angle", root);
                value = sine / cosine;
            } else {
                value = checked(numeric::exp(argument), root);
                if (value == 0) diagnose(EvaluationStatus::out_of_range, "The result is too small", root);
            }
            return checked(value, root);
        }
    }
    throw std::logic_error("Unsupported node");
}
std::string to_latex(const Node& root) {
    auto child = [&](const std::unique_ptr<Node>& node) { return to_latex(*node); };
    switch (root.kind) {
        case Kind::number: {
            const auto exponent = root.text.find_first_of("eE");
            if (exponent == std::string::npos) return root.text;
            return root.text.substr(0, exponent) + "\\cdot 10^{" + root.text.substr(exponent + 1) + "}";
        }
        case Kind::constant: return root.text == "pi" ? "\\pi " : "e";
        case Kind::add: return child(root.left) + "+" + child(root.right);
        case Kind::subtract: return child(root.left) + "-" + child(root.right);
        case Kind::multiply: return latex_product(child(root.left), root.left->kind, child(root.right), root.right->kind);
        case Kind::divide: return "\\frac{" + child(root.left) + "}{" + child(root.right) + "}";
        case Kind::power: return latex_power(child(root.left), root.left->kind, child(root.right));
        case Kind::negate: return "-" + child(root.left);
        case Kind::group: return "\\left(" + child(root.left) + "\\right)";
        case Kind::percent: return "{" + child(root.left) + "}\\%";
        case Kind::function:
            if (root.text == "sqrt") return "\\sqrt{" + child(root.left) + "}";
            if (root.text == "log" && root.right)
                return "\\log_{" + child(root.right) + "}\\left(" + child(root.left) + "\\right)";
            return "\\" + root.text + "\\left(" + child(root.left) + "\\right)";
    }
    throw std::logic_error("Unsupported node");
}
}  // namespace axioma
