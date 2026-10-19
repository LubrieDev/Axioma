#include "big_integer.hpp"
#include <algorithm>
#include <limits>

namespace axioma {
namespace { thread_local std::size_t remaining_work = 8000000; }
ExactBudget::ExactBudget() : previous_(remaining_work) { remaining_work = 8000000; }
ExactBudget::~ExactBudget() { remaining_work = previous_; }
void ExactBudget::spend(std::size_t work) {
    if (work > remaining_work) throw ExactLimit();
    remaining_work -= work;
}

BigInteger::BigInteger(std::int64_t value) {
    negative_ = value < 0;
    std::uint64_t magnitude = value < 0 ? std::uint64_t(-(value + 1)) + 1 : std::uint64_t(value);
    while (magnitude) { limbs_.push_back(magnitude % base); magnitude /= base; }
}
BigInteger::BigInteger(std::string_view text) {
    if (text.empty()) throw std::invalid_argument("Expected an integer.");
    negative_ = text.front() == '-';
    if (text.front() == '-' || text.front() == '+') text.remove_prefix(1);
    if (text.empty()) throw std::invalid_argument("Expected integer digits.");
    for (char c : text) if (c < '0' || c > '9') throw std::invalid_argument("Invalid integer digit.");
    const auto first = text.find_first_not_of('0');
    if (first == std::string_view::npos) { negative_ = false; return; }
    text.remove_prefix(first);
    if (text.size() > max_limbs * 9) throw ExactLimit();
    ExactBudget::spend(text.size());
    for (std::size_t end = text.size(); end > 0;) {
        const auto begin = end > 9 ? end - 9 : 0;
        std::uint32_t limb = 0;
        for (auto i = begin; i < end; ++i) limb = limb * 10 + (text[i] - '0');
        limbs_.push_back(limb); end = begin;
    }
}
void BigInteger::normalize() {
    while (!limbs_.empty() && limbs_.back() == 0) limbs_.pop_back();
    if (zero()) negative_ = false;
    if (limbs_.size() > max_limbs) throw ExactLimit();
}
BigInteger BigInteger::power_of_ten(unsigned exponent) {
    if (exponent >= max_limbs * 9) throw ExactLimit();
    ExactBudget::spend(exponent / 9 + 1);
    BigInteger result;
    result.limbs_.resize(exponent / 9 + 1);
    std::uint32_t top = 1;
    for (unsigned i = 0; i < exponent % 9; ++i) top *= 10;
    result.limbs_.back() = top; return result;
}
BigInteger BigInteger::absolute() const { auto result = *this; result.negative_ = false; return result; }
BigInteger BigInteger::operator-() const {
    auto result = *this; if (!zero()) result.negative_ = !negative_; return result;
}
int BigInteger::compare_magnitude(const BigInteger& other) const {
    ExactBudget::spend(1);
    if (limbs_.size() != other.limbs_.size()) return limbs_.size() < other.limbs_.size() ? -1 : 1;
    ExactBudget::spend(limbs_.size());
    for (auto i = limbs_.size(); i-- > 0;)
        if (limbs_[i] != other.limbs_[i]) return limbs_[i] < other.limbs_[i] ? -1 : 1;
    return 0;
}
int BigInteger::compare(const BigInteger& other) const {
    if (negative_ != other.negative_) return negative_ ? -1 : 1;
    return (negative_ ? -1 : 1) * compare_magnitude(other);
}
std::string BigInteger::text() const {
    if (zero()) return "0";
    std::string result = negative_ ? "-" : "";
    result += std::to_string(limbs_.back());
    for (auto i = limbs_.size() - 1; i-- > 0;) {
        auto part = std::to_string(limbs_[i]); result.append(9 - part.size(), '0'); result += part;
    }
    return result;
}
BigInteger BigInteger::operator+(const BigInteger& other) const {
    BigInteger result;
    const auto count = std::max(limbs_.size(), other.limbs_.size());
    ExactBudget::spend(count + 1);
    if (negative_ == other.negative_) {
        result.negative_ = negative_; std::uint64_t carry = 0;
        for (std::size_t i = 0; i < count || carry; ++i) {
            std::uint64_t value = carry;
            if (i < limbs_.size()) value += limbs_[i];
            if (i < other.limbs_.size()) value += other.limbs_[i];
            result.limbs_.push_back(value % base); carry = value / base;
        }
    } else {
        const bool larger = compare_magnitude(other) >= 0;
        const auto& a = larger ? *this : other;
        const auto& b = larger ? other : *this;
        result.negative_ = a.negative_; std::int64_t borrow = 0;
        for (std::size_t i = 0; i < a.limbs_.size(); ++i) {
            std::int64_t value = std::int64_t(a.limbs_[i]) - borrow - (i < b.limbs_.size() ? b.limbs_[i] : 0);
            borrow = value < 0; if (borrow) value += base;
            result.limbs_.push_back(static_cast<std::uint32_t>(value));
        }
    }
    result.normalize(); return result;
}
BigInteger BigInteger::multiply_small(std::uint32_t value) const {
    if (zero() || value == 0) return {};
    ExactBudget::spend(limbs_.size() + 1);
    BigInteger result; std::uint64_t carry = 0;
    for (auto limb : limbs_) {
        const std::uint64_t product = std::uint64_t(limb) * value + carry;
        result.limbs_.push_back(product % base); carry = product / base;
    }
    if (carry) result.limbs_.push_back(carry);
    // A private division candidate may need one extra scratch limb. Only a
    // candidate no larger than the bounded remainder can become a stored value.
    result.negative_ = negative_; return result;
}
BigInteger BigInteger::operator*(const BigInteger& other) const {
    if (zero() || other.zero()) return {};
    if (limbs_.size() + other.limbs_.size() > max_limbs + 1) throw ExactLimit();
    ExactBudget::spend(limbs_.size() * other.limbs_.size());
    BigInteger result; result.limbs_.resize(limbs_.size() + other.limbs_.size());
    for (std::size_t i = 0; i < limbs_.size(); ++i) {
        std::uint64_t carry = 0;
        for (std::size_t j = 0; j < other.limbs_.size() || carry; ++j) {
            const auto value = std::uint64_t(result.limbs_[i+j]) + carry
                    + (j < other.limbs_.size() ? std::uint64_t(limbs_[i]) * other.limbs_[j] : 0);
            result.limbs_[i+j] = value % base; carry = value / base;
        }
    }
    result.negative_ = negative_ != other.negative_; result.normalize(); return result;
}
std::pair<BigInteger, BigInteger> BigInteger::divide(const BigInteger& other) const {
    if (other.zero()) throw std::invalid_argument("Cannot divide by zero.");
    BigInteger quotient, remainder;
    const auto divisor = other.absolute();
    const auto order = compare_magnitude(divisor);
    if (order < 0) return {quotient, *this};
    if (order == 0) return {BigInteger(negative_ != other.negative_ ? -1 : 1), BigInteger(0)};
    if (divisor.limbs_.size() == 1) {
        ExactBudget::spend(limbs_.size());
        quotient.limbs_.resize(limbs_.size());
        std::uint64_t carry = 0;
        for (auto i = limbs_.size(); i-- > 0;) {
            const auto value = carry * base + limbs_[i];
            quotient.limbs_[i] = value / divisor.limbs_[0];
            carry = value % divisor.limbs_[0];
        }
        remainder = BigInteger(static_cast<std::int64_t>(carry));
        quotient.negative_ = negative_ != other.negative_; remainder.negative_ = negative_;
        quotient.normalize(); remainder.normalize(); return {quotient, remainder};
    }
    quotient.limbs_.resize(limbs_.size());
    // Long division, finding each base-10^9 quotient limb by bounded binary search.
    for (auto i = limbs_.size(); i-- > 0;) {
        ExactBudget::spend(remainder.limbs_.size() + 1);
        remainder.limbs_.insert(remainder.limbs_.begin(), limbs_[i]); remainder.normalize();
        if (remainder.compare_magnitude(divisor) < 0) continue;
        std::uint32_t low = 0, high = base - 1, selected = 0;
        while (low <= high) {
            const auto middle = low + (high - low) / 2;
            const auto candidate = divisor.multiply_small(middle);
            if (candidate.compare_magnitude(remainder) <= 0) { selected = middle; low = middle + 1; }
            else { if (middle == 0) break; high = middle - 1; }
        }
        quotient.limbs_[i] = selected;
        remainder = remainder - divisor.multiply_small(selected);
    }
    quotient.negative_ = negative_ != other.negative_; remainder.negative_ = negative_;
    quotient.normalize(); remainder.normalize(); return {quotient, remainder};
}
BigInteger BigInteger::operator/(const BigInteger& other) const { return divide(other).first; }
BigInteger BigInteger::operator%(const BigInteger& other) const { return divide(other).second; }
unsigned BigInteger::small(unsigned maximum) const {
    if (negative_ || limbs_.size() > 1 || (!zero() && limbs_[0] > maximum)) throw ExactLimit();
    return zero() ? 0 : limbs_[0];
}
BigInteger gcd(BigInteger left, BigInteger right) {
    left = left.absolute(); right = right.absolute();
    while (!right.zero()) { auto remainder = left % right; left = std::move(right); right = std::move(remainder); }
    return left;
}
} // namespace axioma
