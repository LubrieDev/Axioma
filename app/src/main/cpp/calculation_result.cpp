#include "calculation_result.hpp"
#include "approximation.hpp"
#include "exact_math.hpp"
#include "symbolic_math.hpp"
#include "evaluation_error.hpp"

namespace axioma {
namespace {
std::string number_latex(const std::string& text) {
    const auto exponent = text.find('e');
    return exponent == std::string::npos ? text
        : text.substr(0, exponent) + "\\times10^{" + text.substr(exponent + 1) + "}";
}
std::string rational_latex(const Rational& value) {
    if (value.integer()) return value.numerator().text();
    return std::string(value.numerator().negative() ? "-" : "") + "\\frac{"
        + value.numerator().absolute().text() + "}{" + value.denominator().text() + "}";
}
std::string operand(std::string text) {
    if (!text.empty() && (text.front() == '-' || text.find('/') != std::string::npos))
        return "(" + text + ")";
    return text;
}
std::string continuation(const std::string& canonical, std::string_view source) {
    // Canonical results can exceed either the input budget or the finite literal
    // range. Preserve the original exact expression instead of rounding it.
    auto candidate = operand(canonical);
    if (candidate.size() <= 1024) {
        try {
            const auto parsed = parse(candidate);
            if (parsed->kind == Kind::add || parsed->kind == Kind::subtract || parsed->kind == Kind::multiply
                    || parsed->kind == Kind::power || parsed->kind == Kind::percent)
                candidate = "(" + canonical + ")";
            parse(candidate + "+0"); return candidate;
        }
        catch (const std::invalid_argument&) { }
    }
    const auto original = "(" + std::string(source) + ")";
    if (original.size() + 2 <= max_characters) {
        try { parse(original + "+0"); return original; }
        catch (const std::invalid_argument&) { }
    }
    return {};
}
} // namespace

CalculationResult calculate_result(const Node& root, std::string_view source, bool degrees, bool exact_mode) {
    CalculationResult result;
    std::optional<Rational> exact;
    std::optional<ExactExpression> symbolic;
    bool proof_exhausted = false;
    {
        ExactBudget budget;
        try { exact = evaluate_exact(root); }
        catch (const ExactLimit&) {
            // An exhausted proof may use the numerical path, but must not claim
            // an exact result. Domain diagnostics are never swallowed here.
            proof_exhausted = true;
        }
    }
    if (!exact && !proof_exhausted) {
        ExactBudget budget;
        try {
            symbolic = simplify_exact(root, degrees);
            if (symbolic->rational) exact = symbolic->rational;
        } catch (const ExactLimit&) { proof_exhausted = true; }
    }
    if (exact) {
        result.exact = exact->text();
        try {
            ExactBudget budget;
            const auto decimal = approximate(*exact);
            result.precise = decimal.precise; result.display = decimal.display;
        } catch (const ExactLimit&) {
            // A presentation budget cannot invalidate an already proven value.
        }
        result.continuation = continuation(result.exact, source);
        // Bound SVG work independently of the larger exact arithmetic budget.
        // The complete value remains available for copying and continuation.
        if (exact_mode && exact->numerator().absolute().text().size() <= display_significant_digits
                && exact->denominator().text().size() <= display_significant_digits) {
            result.text = result.exact; result.latex = rational_latex(*exact);
            result.exact_display = true;
        } else if (!result.display.empty()) {
            result.text = result.display; result.latex = (exact_mode ? "\\approx " : "") + number_latex(result.display);
        } else {
            result.text = "Exact result"; result.latex = "\\text{Exact result}";
            result.exact_display = true;
        }
    } else {
        {
            ExactBudget budget;
            try {
                const auto decimal = approximate(evaluate(root, degrees, !proof_exhausted));
                result.precise = decimal.precise; result.display = decimal.display;
            } catch (const EvaluationError& error) {
                if (!exact_mode || !symbolic || !symbolic->algebraic || error.status() != EvaluationStatus::out_of_range) throw;
            }
        }
        // A retained symbolic expression is itself an exact representation, not
        // a rational guessed from numerical output. Numerical evaluation above
        // validates unsupported function domains before it can be displayed.
        result.exact = symbolic ? symbolic->text : std::string(source);
        const auto exact_latex = symbolic ? symbolic->latex : to_latex(root);
        result.continuation = continuation(result.exact, source);
        if (exact_mode && result.exact.size() <= 256 && exact_latex.size() <= 2048) {
            result.text = result.exact; result.latex = exact_latex; result.exact_display = true;
        } else if (!result.display.empty()) {
            result.text = result.display; result.latex = (exact_mode ? "\\approx " : "") + number_latex(result.display);
        } else {
            result.text = "Exact result"; result.latex = "\\text{Exact result}"; result.exact_display = true;
        }
    }
    return result;
}
} // namespace axioma
