#include "expression.hpp"
#include "approximation.hpp"
#include "exact_math.hpp"
#include "evaluation_error.hpp"
#include "calculation_result.hpp"
#include <jni.h>
#include <exception>
#include <stdexcept>

namespace {
class Input {
public:
    Input(JNIEnv* env, jstring source) : env_(env), source_(source) {
        if (!source) throw std::invalid_argument("Enter an expression.");
        if (env->GetStringLength(source) > static_cast<jsize>(axioma::max_characters))
            throw std::invalid_argument("The expression exceeds 2048 characters.");
        chars_ = env->GetStringUTFChars(source, nullptr);
        if (!chars_) throw std::runtime_error("Could not read the expression.");
    }
    ~Input() { env_->ReleaseStringUTFChars(source_, chars_); }
    const char* text() const { return chars_; }
private:
    axioma::ExactBudget budget_;
    JNIEnv* env_;
    jstring source_;
    const char* chars_ = nullptr;
};
void report(JNIEnv* env, const char* message) {
    if (env->ExceptionCheck()) return;
    if (jclass type = env->FindClass("java/lang/IllegalArgumentException")) env->ThrowNew(type, message);
}
void report_evaluation(JNIEnv* env, const axioma::EvaluationError& error) {
    if (env->ExceptionCheck()) return;
    const char* code = nullptr;
    switch (error.status()) {
        case axioma::EvaluationStatus::undefined: code = "UNDEFINED"; break;
        case axioma::EvaluationStatus::indeterminate: code = "INDETERMINATE"; break;
        case axioma::EvaluationStatus::not_real: code = "NOT_REAL"; break;
        case axioma::EvaluationStatus::out_of_range: code = "OUT_OF_RANGE"; break;
        case axioma::EvaluationStatus::unsupported: code = "UNSUPPORTED"; break;
    }
    jclass type = env->FindClass("org/axioma/calculator/EvaluationException");
    if (!type) return;
    jmethodID constructor = env->GetMethodID(type, "<init>", "(Ljava/lang/String;Ljava/lang/String;)V");
    if (!constructor) return;
    jstring status = env->NewStringUTF(code);
    if (!status) return;
    jstring detail = env->NewStringUTF(error.what());
    if (!detail) return;
    auto exception = static_cast<jthrowable>(env->NewObject(type, constructor, status, detail));
    if (exception && !env->ExceptionCheck()) env->Throw(exception);
}
jobject make_node(JNIEnv* env, jclass type, jmethodID constructor, const axioma::Node& node) {
    jobject left = node.left ? make_node(env, type, constructor, *node.left) : nullptr;
    if (env->ExceptionCheck()) return nullptr;
    jobject right = node.right ? make_node(env, type, constructor, *node.right) : nullptr;
    if (env->ExceptionCheck()) return nullptr;
    jstring text = env->NewStringUTF(node.text.c_str());
    if (!text) return nullptr;
    jobject result = env->NewObject(type, constructor, static_cast<jint>(node.kind), text, left, right);
    env->DeleteLocalRef(text);
    if (left) env->DeleteLocalRef(left);
    if (right) env->DeleteLocalRef(right);
    return result;
}
}  // namespace

extern "C" JNIEXPORT jobject JNICALL
Java_org_axioma_calculator_NativeCalculator_calculateMode(JNIEnv* env, jclass, jstring source, jboolean degrees, jboolean exact) {
    try {
        const Input input(env, source);
        const auto result = axioma::calculate_result(*axioma::parse(input.text()), input.text(), degrees == JNI_TRUE, exact == JNI_TRUE);
        jclass type = env->FindClass("org/axioma/calculator/CalculationResult");
        if (!type) return nullptr;
        jmethodID constructor = env->GetMethodID(type, "<init>",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)V");
        if (!constructor) return nullptr;
        jstring exact = env->NewStringUTF(result.exact.c_str());
        jstring precise = env->NewStringUTF(result.precise.c_str());
        jstring display = env->NewStringUTF(result.display.c_str());
        jstring continuation = env->NewStringUTF(result.continuation.c_str());
        jstring text = env->NewStringUTF(result.text.c_str());
        jstring latex = env->NewStringUTF(result.latex.c_str());
        if (env->ExceptionCheck()) return nullptr;
        return env->NewObject(type, constructor, exact, precise, display, continuation, text, latex,
                              static_cast<jboolean>(result.exact_display));
    } catch (const axioma::EvaluationError& error) {
        report_evaluation(env, error); return nullptr;
    } catch (const std::exception& error) {
        report(env, error.what()); return nullptr;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_axioma_calculator_NativeCalculator_evaluateExact(JNIEnv* env, jclass, jstring source) {
    try {
        const Input input(env, source);
        axioma::ExactBudget budget;
        const auto result = axioma::evaluate_exact(*axioma::parse(input.text()));
        return result ? env->NewStringUTF(result->text().c_str()) : nullptr;
    } catch (const std::exception& error) {
        report(env, error.what()); return nullptr;
    }
}

extern "C" JNIEXPORT jobject JNICALL
Java_org_axioma_calculator_NativeCalculator_parse(JNIEnv* env, jclass, jstring source) {
    try {
        const Input input(env, source);
        const auto root = axioma::parse(input.text());
        jclass node_class = env->FindClass("org/axioma/calculator/MathNode");
        if (!node_class) return nullptr;
        jmethodID node_constructor = env->GetMethodID(node_class, "<init>",
            "(ILjava/lang/String;Lorg/axioma/calculator/MathNode;Lorg/axioma/calculator/MathNode;)V");
        if (!node_constructor) return nullptr;
        jobject node = make_node(env, node_class, node_constructor, *root);
        if (env->ExceptionCheck() || !node) return nullptr;
        jclass formula_class = env->FindClass("org/axioma/calculator/Formula");
        if (!formula_class) return nullptr;
        jmethodID constructor = env->GetMethodID(formula_class, "<init>",
            "(Lorg/axioma/calculator/MathNode;Ljava/lang/String;)V");
        if (!constructor) return nullptr;
        jstring latex = env->NewStringUTF(axioma::to_latex(*root).c_str());
        if (!latex) return nullptr;
        return env->NewObject(formula_class, constructor, node, latex);
    } catch (const std::exception& error) {
        report(env, error.what()); return nullptr;
    }
}
extern "C" JNIEXPORT jobject JNICALL
Java_org_axioma_calculator_NativeCalculator_evaluate(JNIEnv* env, jclass, jstring source, jboolean degrees) {
    try {
        const Input input(env, source);
        const auto result = axioma::approximate(axioma::evaluate(*axioma::parse(input.text()), degrees == JNI_TRUE));
        jclass type = env->FindClass("org/axioma/calculator/Approximation");
        if (!type) return nullptr;
        jmethodID constructor = env->GetMethodID(type, "<init>", "(Ljava/lang/String;Ljava/lang/String;)V");
        if (!constructor) return nullptr;
        jstring precise = env->NewStringUTF(result.precise.c_str());
        if (!precise) return nullptr;
        jstring display = env->NewStringUTF(result.display.c_str());
        if (!display) return nullptr;
        return env->NewObject(type, constructor, precise, display);
    } catch (const axioma::EvaluationError& error) {
        report_evaluation(env, error); return nullptr;
    } catch (const std::exception& error) {
        report(env, error.what()); return nullptr;
    }
}
