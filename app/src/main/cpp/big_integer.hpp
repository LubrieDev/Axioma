#pragma once
#include <cstdint>
#include <string>
#include <string_view>
#include <utility>
#include <vector>
#include <stdexcept>

namespace axioma {
class ExactLimit : public std::runtime_error {
public:
    ExactLimit() : std::runtime_error("The exact arithmetic resource limit was exceeded.") {}
};

// One budget per exact operation, shared across its nested arithmetic calls.
class ExactBudget {
public:
    ExactBudget();
    ~ExactBudget();
    ExactBudget(const ExactBudget&) = delete;
    ExactBudget& operator=(const ExactBudget&) = delete;
    static void spend(std::size_t work);
private:
    std::size_t previous_;
};

/** Signed base-10^9 integers. Zero has no limbs and is never negative. */
class BigInteger {
public:
    BigInteger(std::int64_t value = 0);
    explicit BigInteger(std::string_view text);
    static constexpr std::size_t max_limbs = 1024;
    static BigInteger power_of_ten(unsigned exponent);
    bool zero() const { return limbs_.empty(); }
    bool negative() const { return negative_; }
    BigInteger absolute() const;
    BigInteger operator-() const;
    std::string text() const;
    int compare(const BigInteger& other) const;
    BigInteger operator+(const BigInteger& other) const;
    BigInteger operator-(const BigInteger& other) const { return *this + -other; }
    BigInteger operator*(const BigInteger& other) const;
    BigInteger operator/(const BigInteger& other) const;
    BigInteger operator%(const BigInteger& other) const;
    std::pair<BigInteger, BigInteger> divide(const BigInteger& other) const;
    unsigned small(unsigned maximum) const;
private:
    static constexpr std::uint64_t base = 1000000000;
    std::vector<std::uint32_t> limbs_;
    bool negative_ = false;
    void normalize();
    int compare_magnitude(const BigInteger& other) const;
    BigInteger multiply_small(std::uint32_t value) const;
};
BigInteger gcd(BigInteger left, BigInteger right);
} // namespace axioma
