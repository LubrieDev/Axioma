#pragma once
#include <stdexcept>
#include <string>

namespace axioma {
enum class EvaluationStatus { undefined, indeterminate, not_real, out_of_range, unsupported };

/** A mathematical diagnostic, distinct from malformed input or a resource limit. */
class EvaluationError final : public std::invalid_argument {
public:
    EvaluationError(EvaluationStatus status, const std::string& message)
        : std::invalid_argument(message), status_(status) {}
    EvaluationStatus status() const { return status_; }
private:
    EvaluationStatus status_;
};
} // namespace axioma
