package org.axioma.calculator;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.List;
import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.util.function.Predicate;

/** Platform test runner: actual C++/JNI arithmetic and offline MathJax on the device. */
public final class SmokeInstrumentation extends Instrumentation {
    private int passed;
    private final List<String> failures = new ArrayList<>();
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    private void check(String name, Runnable test) {
        try { test.run(); ++passed; }
        catch (Throwable error) {
            String failure = name + ": " + error;
            failures.add(failure);
            Bundle status = new Bundle(); status.putString("stream", failure + "\n");
            sendStatus(-2, status);
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void equal(double expected, double actual) {
        require(Double.isFinite(actual) && Math.abs(expected - actual) <= 1e-12 * Math.max(1, Math.abs(expected)),
                "Expected " + expected + ", got " + actual);
    }
    // Existing numerical smoke checks use a double oracle. Precision checks below
    // compare decimal strings directly and never narrow their expected values.
    private static void equal(double expected, Approximation actual) {
        equal(expected, Double.parseDouble(actual.precise));
    }
    private static void decimal(String source, String precise, String display) {
        Approximation result = NativeCalculator.evaluate(source, false);
        require(precise.equals(result.precise), "Internal: expected " + precise + ", got " + result.precise);
        require(display.equals(result.display), "Display: expected " + display + ", got " + result.display);
    }
    private static void rejected(String source) {
        try { NativeCalculator.evaluate(source, false); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Accepted invalid input: " + source);
    }
    private static void diagnostic(String source, boolean degrees, String status, String label) {
        try { NativeCalculator.evaluate(source, degrees); }
        catch (EvaluationException issue) {
            require(issue.status.name().equals(status), "Wrong status for " + source + ": " + issue.status);
            require(issue.label().equals(label), "Wrong label for " + source + ": " + issue.label());
            require(issue.getMessage().contains("position"), "Diagnostic lost its source position");
            return;
        }
        throw new AssertionError("Expected " + status + " for " + source);
    }
    private static void exact(String source, String expected) {
        String actual = NativeCalculator.evaluateExact(source);
        require(expected.equals(actual), "Expected exact " + expected + ", got " + actual);
    }
    private static String fraction(BigInteger numerator, BigInteger denominator) {
        if (denominator.signum() < 0) { numerator = numerator.negate(); denominator = denominator.negate(); }
        BigInteger divisor = numerator.gcd(denominator);
        numerator = numerator.divide(divisor); denominator = denominator.divide(divisor);
        return numerator + (denominator.equals(BigInteger.ONE) ? "" : "/" + denominator);
    }
    @Override public void onStart() {
        CalculatorSettings preferences = new CalculatorSettings(getTargetContext());
        boolean previousResultFormat = preferences.exactResults();
        preferences.setExactResults(true);
        String[] expressions = {
                "1/2+1/3", "2+3*4", "(2+3)*4", "-2^2", "(-2)^2", "2^-3", "2^3^2",
                "8/4/2", "6/2(1+2)", "2(3+4)", "(2+3)(4-1)", "2pi", "2e", "1,5+2.25",
                ".5+1.", "1e-3+2E2", "sqrt(9)+sin(pi/2)", "cos(0)", "ln(e)", "log(100)",
                "exp(0)", "√(16)+π-π", "2×3−4÷2", "[1+2]*3", "2--3", "---2",
                "\\frac{1}{2}+\\sqrt{9}", "\\frac{1+2}{\\frac{3}{4}}", "\\sin\\left(0\\right)",
                "1e-300/1e-300", "sqrt(1/4)", "sin(pi/6)^2+cos(pi/6)^2",
                "{-2}^2", "{1+2}3", "2^{3^2}", "2^{-3}", "\\frac{1}{2}^2",
                "50%", "200*10%", "200+10%", "(2+3)%", "-50%", "50%%", "(2^3)%"
        };
        double[] expected = {
                5.0/6, 14, 20, -4, 4, .125, 512, 1, 9, 14, 15, 2*Math.PI, 2*Math.E, 3.75,
                1.5, 200.001, 4, 1, 1, 2, 1, 4, 4, 9, 5, -2, 3.5, 4, 0, 1, .5, 1,
                4, 9, 512, .125, .25, .5, 20, 200.1, .05, -.5, .005, .08
        };
        for (int i = 0; i < expressions.length; ++i) {
            final String source = expressions[i]; final double answer = expected[i];
            check("evaluate " + source, () -> equal(answer, NativeCalculator.evaluate(source, false)));
            check("LaTeX roundtrip " + source, () -> {
                Formula formula = NativeCalculator.parse(source);
                equal(answer, NativeCalculator.evaluate(formula.latex, false));
            });
        }
        for (String source : new String[]{"", "1+", "()", "(1+2]", "1 2", "1.2.3", "1,,2", "1e+",
                "1/0", "1/-0", "0^0", "0^-1", "sqrt(-1)", "ln(0)", "log(-2)", "(-2)^.5",
                "1e5000", "1e-5000", "1e4900*1e4900", "1e-4900*1e-4900", "exp(-12000)",
                "\\frac{1}", "\\sqrt{}", "\\unknown{2}", "2;3", "NaN", "Infinity", "sin(90°)", "x+1", "%", "2+%"}) {
            check("reject " + source, () -> rejected(source));
        }
        check("degrees mode", () -> equal(1, NativeCalculator.evaluate("sin(90)", true)));
        check("radians mode", () -> equal(Math.sin(90), NativeCalculator.evaluate("sin(90)", false)));
        for (String[] sample : new String[][]{
                {"log(8;2)", "3"}, {"log(81;3)", "4"}, {"log(2;4)", "0.5"},
                {"log(8;0.5)", "-3"}, {"log(0.125;0.5)", "3"}, {"log(1;2)", "0"},
                {"log(8;1+1)", "3"}, {"log(8;sqrt(4))", "3"},
                {"log(8;0,5)", "-3"}, {"log(e;e)", "1"}, {"log(1000)", "3"},
                {"log(log(256;2);2)", "3"}, {"log_2(8)", "3"},
                {"\\log_{2}(8)", "3"}, {"\\log_2 8", "3"},
                {"\\log_{2+1}\\left(81\\right)", "4"},
                {"\\log_{\\frac{1}{2}}{8}", "-3"},
                {"log(1.00000000000000000001;1.00000000000000000001)", "1"}}) {
            check("arbitrary logarithm base " + sample[0], () -> decimal(sample[0], sample[1], sample[1]));
            check("logarithm LaTeX round trip " + sample[0], () -> {
                String latex = NativeCalculator.parse(sample[0]).latex;
                decimal(latex, sample[1], sample[1]);
            });
        }
        check("decimal comma is not a logarithm separator", () ->
                equal(Math.log10(8.2), NativeCalculator.evaluate("log(8,2)", false)));
        check("logarithm base is preserved in AST", () -> {
            Formula formula = NativeCalculator.parse("log(8;2+1)");
            require(formula.root.kind == MathNode.FUNCTION && formula.root.right.kind == MathNode.ADD,
                    "Lost the logarithm base subtree");
            require(formula.latex.startsWith("\\log_{2+1}"), "Missing typeset logarithm subscript");
        });
        for (String source : new String[]{"log(8;1)", "log(8;0)", "log(8;-2)", "log(1;1)", "log(0;2)"}) {
            check("logarithm base domain " + source, () -> diagnostic(source, false, "UNDEFINED", "Undefined"));
        }
        check("negative logarithm argument", () -> diagnostic("log(-8;2)", false, "NOT_REAL", "Not real"));
        check("logarithm base failure propagates", () ->
                diagnostic("log(8;0/0)", false, "INDETERMINATE", "Indeterminate"));
        for (String source : new String[]{"log(8;)", "log(;2)", "log(8;2;3)", "log_{2}(8;3)",
                "log_{}(8)", "log_2", "log_{2}", "sin(8;2)", "_2", "log(8;2]"}) {
            check("reject ambiguous logarithm " + source, () -> rejected(source));
        }
        check("tangent pole", () -> {
            try { NativeCalculator.evaluate("tan(90)", true); }
            catch (IllegalArgumentException expectedError) { return; }
            throw new AssertionError("Accepted tan(90) DEG");
        });
        for (String[] sample : new String[][]{
                {"tan(pi/2)", "UNDEFINED", "Undefined"},
                {"tan(-pi/2)", "UNDEFINED", "Undefined"},
                {"tan(3*pi/2)", "UNDEFINED", "Undefined"},
                {"1/cos(pi/2)", "UNDEFINED", "Undefined"},
                {"1/0", "UNDEFINED", "Undefined"}, {"1/-0", "UNDEFINED", "Undefined"},
                {"0/0", "INDETERMINATE", "Indeterminate"},
                {"0^0", "INDETERMINATE", "Indeterminate"}, {"0^-1", "UNDEFINED", "Undefined"},
                {"sqrt(-1)", "NOT_REAL", "Not real"}, {"ln(-1)", "NOT_REAL", "Not real"},
                {"ln(0)", "UNDEFINED", "Undefined"}, {"log(0)", "UNDEFINED", "Undefined"},
                {"0*(1/0)", "UNDEFINED", "Undefined"}, {"1/(0^-1)", "UNDEFINED", "Undefined"},
                {"0*sqrt(-1)", "NOT_REAL", "Not real"},
                {"tan(pi/2+0/0)", "INDETERMINATE", "Indeterminate"},
                {"1e4900*1e4900", "OUT_OF_RANGE", "Out of range"},
                {"1e-4900*1e-4900", "OUT_OF_RANGE", "Out of range"},
                {"exp(-12000)", "OUT_OF_RANGE", "Out of range"},
                {"(-8)^(1/3)", "UNSUPPORTED", "Not supported"}}) {
            check("mathematical status " + sample[0], () -> diagnostic(sample[0], false, sample[1], sample[2]));
        }
        check("degree tangent diagnostic", () -> diagnostic("tan(90)", true, "UNDEFINED", "Undefined"));
        for (String source : new String[]{"1+", "x+1", "1e5000", repeat("(", 60) + "1" + repeat(")", 60)}) {
            check("input failure is not a mathematical label " + source, () -> {
                try { NativeCalculator.evaluate(source, false); }
                catch (EvaluationException issue) { throw new AssertionError("Mislabeled invalid input", issue); }
                catch (IllegalArgumentException expectedError) { return; }
                throw new AssertionError("Accepted invalid input");
            });
        }
        check("power AST association", () -> {
            MathNode root = NativeCalculator.parse("2^3^2").root;
            require(root.kind == MathNode.POWER && root.right.kind == MathNode.POWER, "Lost right association");
        });
        check("unary AST precedence", () -> {
            MathNode root = NativeCalculator.parse("-2^2").root;
            require(root.kind == MathNode.NEGATE && root.left.kind == MathNode.POWER, "Wrong unary precedence");
        });
        check("domain failure keeps drawable AST", () -> {
            require(NativeCalculator.parse("1/0").root.kind == MathNode.DIVIDE, "Domain error destroyed syntax");
            rejected("1/0"); equal(4, NativeCalculator.evaluate("2+2", false));
        });
        check("Unicode error position", () -> {
            try { NativeCalculator.parse("π + @"); }
            catch (IllegalArgumentException expectedError) {
                require(expectedError.getMessage().contains("position 5"), expectedError.getMessage()); return;
            }
            throw new AssertionError("Unknown character ignored");
        });
        check("character limit", () -> rejected(repeat("1", 2049)));
        check("parenthesis limit", () -> rejected(repeat("(", 60) + "1" + repeat(")", 60)));
        check("unary depth limit", () -> rejected(repeat("-", 60) + "1"));
        check("left-deep AST limit", () -> rejected(repeat("1+", 60) + "1"));
        check("node budget", () -> rejected(balanced(7)));
        check("null input", () -> rejected(null));
        check("scientific TeX is unambiguous", () ->
                require(NativeCalculator.parse("1e-3").latex.equals("1\\cdot 10^{-3}"), "Scientific notation changed meaning"));

        String[][] decimals = {
                {"1/3", "0.3333333333333333333333333333333333333333", "0.333333333333333333333333333333"},
                {"1/6", "0.1666666666666666666666666666666666666667", "0.166666666666666666666666666667"},
                {"2/3", "0.6666666666666666666666666666666666666667", "0.666666666666666666666666666667"},
                {"-1/6", "-0.1666666666666666666666666666666666666667", "-0.166666666666666666666666666667"},
                {"0.1+0.2", "0.3", "0.3"},
                {"1.00000000000000000001", "1.00000000000000000001", "1.00000000000000000001"},
                {"1.00000000000000000001+0.00000000000000000001", "1.00000000000000000002", "1.00000000000000000002"},
                {"1.23456789012345678901", "1.23456789012345678901", "1.23456789012345678901"},
                {"-1.23456789012345678901", "-1.23456789012345678901", "-1.23456789012345678901"},
                {"1.234567890123456499999", "1.234567890123456499999", "1.234567890123456499999"},
                {"-1.234567890123456499999", "-1.234567890123456499999", "-1.234567890123456499999"},
                {"999.9999999999999999", "999.9999999999999999", "999.9999999999999999"},
                {"-0", "0", "0"},
                {"sqrt(2)", "1.41421356237309504880168872420969807857", "1.41421356237309504880168872421"},
                {"2^0.5", "1.41421356237309504880168872420969807857", "1.41421356237309504880168872421"},
                {"pi", "3.141592653589793238462643383279502884197", "3.14159265358979323846264338328"},
                {"e", "2.718281828459045235360287471352662497757", "2.71828182845904523536028747135"},
                {"sin(1)", "0.8414709848078965066525023216302989996226", "0.84147098480789650665250232163"},
                {"cos(1)", "0.5403023058681397174009366074429766037323", "0.540302305868139717400936607443"},
                {"tan(1)", "1.557407724654902230506974807458360173087", "1.55740772465490223050697480746"},
                {"ln(2)", "0.6931471805599453094172321214581765680755", "0.693147180559945309417232121458"},
                {"log(2)", "0.3010299956639811952137388947244930267682", "0.301029995663981195213738894724"},
                {"exp(1)", "2.718281828459045235360287471352662497757", "2.71828182845904523536028747135"},
                {"(10000000000000000+1)-10000000000000000", "1", "1"},
                {"9007199254740993-9007199254740992", "1", "1"},
                {"1e-300", "1e-300", "1e-300"},
                {"1e300", "1e300", "1e300"},
                {"1e309", "1e309", "1e309"}
        };
        for (String[] sample : decimals) {
            check("decimal precision " + sample[0], () -> decimal(sample[0], sample[1], sample[2]));
        }
        check("independent 40/30-digit fixtures", this::checkHighPrecision);
        for (String source : new String[]{"sin(30)", "cos(60)"}) {
            check("degree precision " + source, () -> {
                Approximation result = NativeCalculator.evaluate(source, true);
                require("0.5".equals(result.precise) && "0.5".equals(result.display), "Lost degree precision");
            });
        }

        for (int turn = -16; turn <= 16; ++turn) {
            final int k = turn;
            final int phase = Math.floorMod(k, 4);
            final int sine = new int[]{0, 1, 0, -1}[phase];
            final int cosine = new int[]{1, 0, -1, 0}[phase];
            check("exact RAD sine quadrant " + k, () -> decimal("sin(" + k + "*pi/2)", "" + sine, "" + sine));
            check("exact RAD cosine quadrant " + k, () -> decimal("cos(" + k + "*pi/2)", "" + cosine, "" + cosine));
            check("exact DEG quadrant " + k, () -> {
                Approximation answer = NativeCalculator.evaluate("sin(" + k + "*90)", true);
                require(answer.precise.equals("" + sine) && answer.display.equals("" + sine), "Lost exact DEG quadrant");
            });
            if ((phase & 1) == 0) {
                check("exact tangent zero " + k, () -> decimal("tan(" + k + "*pi/2)", "0", "0"));
            } else {
                check("exact tangent pole " + k, () -> rejected("tan(" + k + "*pi/2)"));
            }
        }
        for (String source : new String[]{"sin(pi)", "sin(π)", "sin(-pi)", "sin(pi+pi)",
                "sin(0.5*pi+pi/2)", "sin((1/3+2/3)*pi)", "sin(1e2*pi)",
                "sin(1000000000000000000000000000000000000000000*pi)",
                "sin(\\frac{2\\pi}{2})", "tan(pi)", "cos(pi/2)"}) {
            check("symbolic trigonometric zero " + source, () -> decimal(source, "0", "0"));
        }
        check("large odd pi coefficient keeps parity", () -> decimal(
                "cos(1000000000000000000000000000000000000000001*pi)", "-1", "-1"));
        check("decimal pi is not symbolic pi", () -> {
            Approximation result = NativeCalculator.evaluate("sin(3.141592653589793)", false);
            require(!result.precise.equals("0"), "A decimal approximation was treated as exact pi");
        });
        for (String[] sample : new String[][]{
                {"sin(1e-40)", "1e-40"}, {"sin(pi+1e-20)", "-1e-20"},
                {"sin(pi-1e-20)", "1e-20"}, {"sin(pi+1e-40)", "-1e-40"},
                {"cos(pi/2+1e-40)", "-1e-40"}, {"cos(pi/2-1e-40)", "1e-40"},
                {"tan(pi/2+1e-40)", "-1e40"}, {"tan(pi/2-1e-40)", "1e40"}}) {
            check("preserve near-quadrant value " + sample[0], () -> decimal(sample[0], sample[1], sample[1]));
        }
        check("pi in DEG is not a half turn", () -> {
            Approximation result = NativeCalculator.evaluate("sin(pi)", true);
            equal(Math.sin(Math.PI * Math.PI / 180), result);
            require(!result.precise.equals("0"), "RAD identity leaked into DEG mode");
        });
        for (String[] sample : new String[][]{
                {"sin(180+1e-40)", "-1.7453292519943296e-42"},
                {"sin(180-1e-40)", "1.7453292519943296e-42"},
                {"tan(90+1e-40)", "-5.729577951308232e41"},
                {"tan(90-1e-40)", "5.729577951308232e41"}}) {
            check("preserve near-quadrant DEG value " + sample[0], () -> {
                double answer = Double.parseDouble(NativeCalculator.evaluate(sample[0], true).precise);
                double expectedValue = Double.parseDouble(sample[1]);
                require(Double.isFinite(answer) && Math.abs((answer - expectedValue) / expectedValue) < 1e-14,
                        "Lost the nonzero degree offset: " + answer);
            });
        }
        for (String source : new String[]{"sin(pi+1/0)", "cos(0*(1/0))", "tan(pi/2+0/0)", "sin(0^0)"}) {
            check("exact recognition preserves domain errors " + source, () -> rejected(source));
        }

        for (String[] sample : new String[][]{
                {"1/3+1/6", "1/2"}, {"0.1+0.2", "3/10"}, {"1/(-2)", "-1/2"},
                {"-0.000", "0"}, {"1.25e-3", "1/800"}, {"1,5+2.25", "15/4"},
                {"1./.5", "2"}, {"2^-3", "1/8"}, {"(-2)^3", "-8"},
                {"50%", "1/2"}, {"50%%", "1/200"}, {"(1e100+1)-1e100", "1"},
                {"(1e300/7)*(7/1e300)", "1"}, {"(pi-pi)+1/2", "1/2"},
                {"(-9223372036854775808)/(-1)", "9223372036854775808"}}) {
            check("exact rational " + sample[0], () -> exact(sample[0], sample[1]));
        }
        check("large integer power", () -> exact("2^1000", BigInteger.valueOf(2).pow(1000).toString()));
        for (String source : new String[]{"pi", "sqrt(2)", "sin(pi)", "2^.5", "pi*pi"}) {
            check("unsupported exact expression remains unevaluated " + source, () ->
                    require(NativeCalculator.evaluateExact(source) == null, "Invented an exact rational result"));
        }
        for (String source : new String[]{"1/0", "0^0", "2^8193", "(2^8192)^8192"}) {
            check("exact arithmetic rejects domain or resource excess " + source, () -> {
                try { NativeCalculator.evaluateExact(source); }
                catch (IllegalArgumentException expectedError) { return; }
                throw new AssertionError("Accepted invalid or excessive exact computation");
            });
        }
        // Independent standard-library oracle exists only in tests, never in the application.
        Random random = new Random(0x4158494F4D41L);
        for (int i = 0; i < 30; ++i) {
            BigInteger a = new BigInteger(20 + random.nextInt(1500), random);
            BigInteger b = new BigInteger(20 + random.nextInt(1500), random).add(BigInteger.ONE);
            if (random.nextBoolean()) a = a.negate();
            if (random.nextBoolean()) b = b.negate();
            final BigInteger left = a, right = b;
            String prefix = "(" + a + ")", suffix = "(" + b + ")";
            check("integer oracle addition " + i, () -> exact(prefix + "+" + suffix, left.add(right).toString()));
            check("integer oracle subtraction " + i, () -> exact(prefix + "-" + suffix, left.subtract(right).toString()));
            check("integer oracle multiplication " + i, () -> exact(prefix + "*" + suffix, left.multiply(right).toString()));
            check("rational oracle reduction " + i, () -> exact(prefix + "/" + suffix, fraction(left, right)));
            check("rational oracle cancellation " + i, () -> exact(
                    "(" + prefix + "/" + suffix + ")*(" + suffix + "/" + prefix + ")", "1"));
            check("main exact and decimal oracle " + i, () -> {
                CalculationResult result = NativeCalculator.calculate(prefix + "/" + suffix, false);
                require(result.exact.equals(fraction(left, right)), "Main pipeline changed the exact rational");
                require(result.precise.equals(rationalDecimalOracle(left, right, 40))
                        && result.display.equals(rationalDecimalOracle(left, right, 30)),
                        "Main decimal formatter disagrees with independent BigDecimal division");
            });
        }

        check("keypad clear", () -> edited("123", 3, 3, "AC", "", 0));
        check("keypad backspace", () -> edited("123", 2, 2, "backspace", "13", 1));
        check("keypad selection deletion", () -> edited("12345", 1, 4, "backspace", "15", 1));
        check("keypad selected replacement", () -> edited("12345", 1, 4, "9", "195", 2));
        check("keypad insert at cursor", () -> edited("12", 1, 1, "+", "1+2", 2));
        check("keypad Unicode deletion", () -> edited("1π", 2, 2, "backspace", "1", 1));
        check("keypad initial decimal", () -> edited("", 0, 0, ".", "0.", 2));
        check("keypad duplicate decimal", () -> edited("1.2", 3, 3, ".", "1.2", 3));
        check("keypad exponent decimal", () -> edited("1e-3", 4, 4, ".", "1e-3", 4));
        check("keypad replace operator", () -> edited("2+", 2, 2, "×", "2×", 2));
        check("keypad negative factor", () -> edited("2×", 2, 2, "−", "2×−", 3));
        check("keypad opening parenthesis", () -> edited("", 0, 0, "()", "(", 1));
        check("keypad closing parenthesis", () -> edited("(2+3", 4, 4, "()", "(2+3)", 5));
        check("keypad function cursor", () -> edited("", 0, 0, "sqrt()", "sqrt()", 5));
        check("keypad skip closing parenthesis", () -> edited("sqrt(9)", 6, 6, "()", "sqrt(9)", 7));
        check("keypad constant is not exponent", () -> edited("2", 1, 1, "e", "2×e", 3));
        check("keypad length limit", () -> edited(repeat("1", 2048), 2048, 2048, "1", repeat("1", 2048), 2048));
        check("keypad new number after equals", () -> {
            CalculatorInput.Edit edit = CalculatorInput.edit("1÷3", 3, 3, "7", true, "0.33333333333333333333");
            require(edit.text.equals("7") && edit.cursor == 1, "New entry retained the previous calculation");
        });
        check("keypad continue precise result", () -> {
            CalculatorInput.Edit edit = CalculatorInput.edit("1÷3", 3, 3, "+", true, "0.33333333333333333333");
            require(edit.text.equals("0.33333333333333333333+"), "Continuation used rounded display digits");
        });

        checkExactResults();
        checkSymbolicResults();
        checkMultiplicationNotation();
        check("rendered calculator controls", this::checkRenderedCalculator);
        check("exact calculator lifecycle and history", this::checkExactCalculator);
        check("result settings and symbolic UI", this::checkResultSettings);
        check("in-app literal keyboard", this::checkSourceKeyboard);
        check("offline MathJax integration", this::checkRenderer);
        checkAndroidMatrix();
        Bundle result = new Bundle();
        result.putInt("passed", passed); result.putInt("failed", failures.size());
        result.putInt("api", android.os.Build.VERSION.SDK_INT);
        result.putString("androidRelease", android.os.Build.VERSION.RELEASE);
        result.putString("processArch", System.getProperty("os.arch"));
        result.putBoolean("process64Bit", android.os.Process.is64Bit());
        result.putString("supportedAbis", String.join(",", android.os.Build.SUPPORTED_ABIS));
        result.putLong("pageSize", pageSize());
        result.putString("stream", "\nAxioma: " + passed + " passed, " + failures.size() + " failed.\n"
                + String.join("\n", failures));
        preferences.setExactResults(previousResultFormat);
        finish(failures.isEmpty() ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    private void checkAndroidMatrix() {
        String[][] cases = {
                {"2+3*4", "14"}, {"(2+3)*4", "20"}, {"(40+4)^2", "1936"},
                {"2^10", "1024"}, {"sqrt(81)", "9"}, {"1/2+1/3", "5/6"},
                {"0.1+0.2", "3/10"}, {"sin(pi)", "0"}, {"cos(0)", "1"},
                {"tan(pi/4)", "1"}, {"log(8;2)", "3"}, {"ln(1)", "0"}, {"exp(0)", "1"},
                {"3*sqrt(6)*sqrt(42)", "18*sqrt(7)"}
        };
        for (String[] sample : cases) check("matrix exact JNI " + sample[0], () -> {
            CalculationResult result = NativeCalculator.calculateMode(sample[0], false, true);
            require(sample[1].equals(result.text) && result.exactDisplay,
                    "Expected exact " + sample[1] + ", got " + result.text);
        });
        check("matrix numeric radical uses an independent ordinary-precision oracle", () ->
                equal(18 * Math.sqrt(7), NativeCalculator.evaluate("3*sqrt(6)*sqrt(42)", false)));
        check("matrix all JNI entry points remain usable across repeated calls", () -> {
            for (int i = 0; i < 128; ++i) {
                require(NativeCalculator.parse("2^10").root != null, "Missing native AST");
                require("1024".equals(NativeCalculator.evaluateExact("2^10")), "Exact JNI mismatch");
                require("1024".equals(NativeCalculator.evaluate("2^10", false).precise), "Numeric JNI mismatch");
                require("1024".equals(NativeCalculator.calculateMode("2^10", false, (i & 1) == 0).text),
                        "Result JNI mismatch");
            }
        });
        check("matrix invalid input returns a Java error instead of a native crash", () -> {
            for (String source : new String[]{"", " ", "2+*3", "(2+3", "@", null}) {
                for (boolean exact : new boolean[]{false, true}) {
                    boolean rejected = false;
                    try { NativeCalculator.calculateMode(source, false, exact); }
                    catch (IllegalArgumentException expected) { rejected = true; }
                    require(rejected, "Accepted malformed input");
                }
            }
        });
        check("matrix native domain diagnostics survive JNI in both formats", () -> {
            for (boolean exact : new boolean[]{false, true}) {
                for (String source : new String[]{"1/0", "sqrt(-1)"}) {
                    boolean rejected = false;
                    try { NativeCalculator.calculateMode(source, false, exact); }
                    catch (EvaluationException issue) {
                        rejected = issue.status.name().equals(source.equals("1/0") ? "UNDEFINED" : "NOT_REAL");
                    }
                    require(rejected, "Missing native diagnostic for " + source);
                }
            }
        });
        check("matrix offline renderer has no Internet permission", () -> {
            try {
                String[] requested = getTargetContext().getPackageManager().getPackageInfo(
                        getTargetContext().getPackageName(), android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions;
                if (requested != null) for (String permission : requested)
                    require(!"android.permission.INTERNET".equals(permission), "Unexpected Internet permission");
            } catch (android.content.pm.PackageManager.NameNotFoundException error) {
                throw new AssertionError(error);
            }
        });
        check("matrix required examples reach the actual calculator and SVG renderer", () -> {
            CalculatorSettings settings = new CalculatorSettings(getTargetContext());
            boolean previousMode = settings.exactResults();
            android.content.SharedPreferences history = getTargetContext().getSharedPreferences("calculation_history", 0);
            java.util.Map<String, ?> savedHistory = new java.util.HashMap<>(history.getAll());
            Activity activity = null;
            try {
                settings.setExactResults(true);
                activity = startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                Activity current = activity;
                FormulaView[] view = {null};
                onUi(() -> view[0] = current.findViewById(R.id.formula_display));
                for (String[] sample : new String[][]{{"(40+4)^2", "1936"},
                        {"3*sqrt(6)*sqrt(42)", "18*sqrt(7)"}, {"2^10", "1024"}}) {
                    setSource(current, sample[0]); press(current, "Calculate");
                    waitForState(view[0], sample[0], sample[1]);
                    require(inspect(view[0]).optInt("glyphs") > 0, "No visible mathematical SVG");
                    if (sample[1].contains("sqrt"))
                        require(inspect(view[0]).optInt("answerRoots") == 1, "Simplified radical is not rendered");
                }
            } finally {
                settings.setExactResults(previousMode);
                android.content.SharedPreferences.Editor restore = history.edit().clear();
                for (java.util.Map.Entry<String, ?> entry : savedHistory.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof String) restore.putString(entry.getKey(), (String) value);
                }
                restore.apply();
                if (activity != null) { Activity current = activity; onUi(current::finish); }
            }
        });
    }
    private static long pageSize() {
        return android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE);
    }
    private static String balanced(int depth) {
        if (depth == 0) return "1";
        String child = balanced(depth - 1); return "(" + child + "+" + child + ")";
    }
    private void checkHighPrecision() {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                getContext().getAssets().open("precision-cases.json"), java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) text.append(line);
            org.json.JSONArray cases = new org.json.JSONArray(text.toString());
            require(cases.length() >= 100, "Missing independent precision references");
            for (int i = 0; i < cases.length(); ++i) {
                JSONObject item = cases.getJSONObject(i);
                String source = item.getString("source"), precise = item.getString("precise"), display = item.getString("display");
                check("40-digit numerical reference " + source, () -> decimal(source, precise, display));
                check("40-digit main-pipeline reference " + source, () -> {
                    CalculationResult result = NativeCalculator.calculate(source, false);
                    require(result.precise.equals(precise) && result.display.equals(display),
                            "Expected " + precise + " / " + display + ", got " + result.precise + " / " + result.display);
                });
            }
        } catch (java.io.IOException | org.json.JSONException failure) { throw new AssertionError(failure); }
        for (String source : new String[]{"sin(1e31)", "cos(1e31)", "tan(1e31)", "exp(12000)"}) {
            check("bounded high-precision period reduction " + source, () ->
                    diagnostic(source, false, "OUT_OF_RANGE", "Out of range"));
        }
        check("exact huge pi quadrant bypasses numerical reduction limit", () -> decimal("sin(1e4000*pi)", "0", "0"));
        check("40 retained digits survive continuation", () -> {
            CalculationResult result = NativeCalculator.calculate("e", false);
            CalculatorInput.Edit edit = CalculatorInput.edit("e", 1, 1, "-", true, result.continuation);
            CalculationResult difference = NativeCalculator.calculate(edit.text + "2.71828182845904523536028747135", false);
            require(difference.precise.equals("2.662497757247093699959574966967627724077e-30"),
                    "Exact continuation lost the symbolic constant: " + difference.precise);
        });
    }
    private static String rationalDecimalOracle(BigInteger numerator, BigInteger denominator, int places) {
        if (numerator.signum() == 0) return "0";
        if (denominator.signum() < 0) { numerator = numerator.negate(); denominator = denominator.negate(); }
        java.math.BigDecimal n = new java.math.BigDecimal(numerator), d = new java.math.BigDecimal(denominator);
        int exponent = n.precision() - d.precision();
        if (n.abs().compareTo(d.scaleByPowerOfTen(exponent)) < 0) --exponent;
        boolean scientific = exponent < -6 || exponent >= 9;
        java.math.BigDecimal rounded = n.divide(d, new java.math.MathContext(places,
                java.math.RoundingMode.HALF_EVEN)).stripTrailingZeros();
        if (!scientific || rounded.signum() == 0) return rounded.toPlainString();
        int finalExponent = rounded.precision() - rounded.scale() - 1;
        return rounded.movePointLeft(finalExponent).toPlainString() + (finalExponent == 0 ? "" : "e" + finalExponent);
    }
    private void checkExactResults() {
        for (String[] sample : new String[][]{
                {"1/3", "1/3", "0.3333333333333333333333333333333333333333", "0.333333333333333333333333333333"},
                {"1/3+1/6", "1/2", "0.5", "0.5"}, {"0.1+0.2", "3/10", "0.3", "0.3"},
                {"2^-3", "1/8", "0.125", "0.125"}, {"-50%", "-1/2", "-0.5", "-0.5"},
                {"(1e100+1)-1e100", "1", "1", "1"},
                {"1/(1e100+1-1e100)", "1", "1", "1"},
                {"(pi-pi)+1/2", "1/2", "0.5", "0.5"},
                {"12345678901234567890+1", "12345678901234567891", "1.2345678901234567891e19", "1.2345678901234567891e19"}}) {
            check("main exact pipeline " + sample[0], () -> {
                CalculationResult result = NativeCalculator.calculate(sample[0], false);
                require(result.exact.equals(sample[1]) && result.text.equals(sample[1]) && result.exactDisplay,
                        "The main pipeline lost the exact value: " + result.exact);
                require(result.precise.equals(sample[2]) && result.display.equals(sample[3]),
                        "Wrong decimal approximation of the exact value: " + result.precise + " / " + result.display);
                exact(result.continuation, sample[1]);
                exact(result.latex, sample[1]);
            });
        }
        check("continued exact fraction cancels without rounded digits", () -> {
            CalculationResult result = NativeCalculator.calculate("1/3", false);
            CalculatorInput.Edit edit = CalculatorInput.edit("1/3", 3, 3, "×", true, result.continuation);
            CalculationResult next = NativeCalculator.calculate(edit.text + "3", false);
            require(next.exact.equals("1"), "A continued fraction used a decimal approximation");
        });
        check("negative result remains grouped when raised to a power", () -> {
            CalculationResult result = NativeCalculator.calculate("-2", false);
            CalculatorInput.Edit edit = CalculatorInput.edit("-2", 2, 2, "^", true, result.continuation);
            require(NativeCalculator.calculate(edit.text + "2", false).exact.equals("4"), "Lost negative result grouping");
        });
        check("numerical negative result preserves its exact grouped operand", () -> {
            CalculationResult result = NativeCalculator.calculateMode("-sqrt(2)", false, false);
            require(result.exact.equals("-sqrt(2)") && !result.exactDisplay && result.continuation.startsWith("(-"),
                    "A negative exact operand lost its sign grouping in numerical mode");
        });
        for (String[] sample : new String[][]{{"sqrt(2)", "sqrt(2)"}, {"sin(pi)", "0"},
                {"log(8;2)", "3"}, {"pi", "pi"}, {"2^.5", "sqrt(2)"}}) {
            String source = sample[0];
            check("main pipeline preserves proven exact structure " + source, () -> {
                CalculationResult result = NativeCalculator.calculate(source, false);
                require(result.exact.equals(sample[1]) && result.exactDisplay, "Wrong exact structure: " + result.exact);
                require(result.precise.equals(NativeCalculator.evaluate(source, false).precise), "Numerical fallback changed");
            });
        }
        for (String[] sample : new String[][]{
                {"1/0", "UNDEFINED"}, {"0/0", "INDETERMINATE"}, {"0^0", "INDETERMINATE"},
                {"0^-1", "UNDEFINED"}, {"sqrt(-1)", "NOT_REAL"}, {"tan(pi/2)", "UNDEFINED"},
                {"0*(1/0)", "UNDEFINED"}, {"1/(0.1+0.2-0.3)", "UNDEFINED"},
                {"(0.1+0.2-0.3)/(0.1+0.2-0.3)", "INDETERMINATE"},
                {"(0.1+0.2-0.3)^0", "INDETERMINATE"}, {"(2^8192)^8192", "OUT_OF_RANGE"}}) {
            check("main exact pipeline diagnostic " + sample[0], () -> {
                try { NativeCalculator.calculate(sample[0], false); }
                catch (EvaluationException expected) {
                    require(expected.status.name().equals(sample[1]) && expected.getMessage().contains("position"),
                            "Wrong or unpositioned diagnostic: " + expected);
                    return;
                }
                throw new AssertionError("Expected a typed diagnostic for " + sample[0]);
            });
        }
        check("large exact result uses bounded presentation and exact continuation", () -> {
            CalculationResult result = NativeCalculator.calculate("2^4000", false);
            require(result.exact.equals(BigInteger.valueOf(2).pow(4000).toString()), "Lost the large integer");
            require(result.latex.startsWith("\\approx ") && !result.exactDisplay, "Large decimal preview claims equality");
            require(result.continuation.equals("(2^4000)"), "A large result should preserve its short original expression");
            require(NativeCalculator.calculate(result.continuation + "/2^4000", false).exact.equals("1"),
                    "Large exact result cannot continue");
        });
        check("exact result can exist beyond the floating point range", () -> {
            CalculationResult result = NativeCalculator.calculate("10^6000", false);
            require(result.exact.equals("1" + repeat("0", 6000)) && result.precise.equals("1e6000") && result.display.equals("1e6000"),
                    "A valid exact integer was confused with numerical overflow");
            require(!result.exactDisplay && result.text.equals("1e6000") && result.latex.length() < 100,
                    "Unbounded result sent to the typesetter");
            require(NativeCalculator.calculate(result.continuation + "/10^6000", false).exact.equals("1"),
                    "Exact out-of-range result was rounded during continuation");
        });
        check("exact resource boundary preserves an unexpanded expression", () -> {
            CalculationResult result = NativeCalculator.calculate("1^8193", false);
            require(result.exact.equals("1^8193") && result.exactDisplay && result.precise.equals("1"),
                    "An exhausted simplification lost the original exact expression");
        });
        check("unwrappable maximum-depth result cannot be silently rounded", () -> {
            String source = repeat("(", 46) + "1/3" + repeat(")", 46);
            CalculationResult result = NativeCalculator.calculate(source, false);
            require(result.continuation.equals("(1/3)"), "A compact exact result should not depend on source nesting");
        });
        check("oversized exact result respects continuation nesting limits", () -> {
            String source = repeat("(", 45) + "2^4000" + repeat(")", 45);
            CalculationResult result = NativeCalculator.calculate(source, false);
            require(!result.exact.isEmpty() && !result.precise.isEmpty() && result.continuation.isEmpty(),
                    "An exact result was lost or replaced by an invalid continuation expression");
        });
        for (String[] sample : new String[][]{
                {"1.0000000000000005", "1.0000000000000005", "1.0000000000000005"},
                {"1.0000000000000015", "1.0000000000000015", "1.0000000000000015"},
                {"1.0000000000000005000000000000000000001", "1.0000000000000005000000000000000000001", "1.0000000000000005"},
                {"1.0000000000000004999999999999999999999", "1.0000000000000004999999999999999999999", "1.0000000000000005"},
                {"-1.0000000000000015", "-1.0000000000000015", "-1.0000000000000015"},
                {"1.000000000000000000005", "1.000000000000000000005", "1.000000000000000000005"},
                {"1.000000000000000000015", "1.000000000000000000015", "1.000000000000000000015"},
                {"1.0000000000000000000050000000000000001", "1.0000000000000000000050000000000000001", "1.000000000000000000005"},
                {"9.9999999999999995e100", "9.9999999999999995e100", "9.9999999999999995e100"},
                {"9.9999999999999995e-100", "9.9999999999999995e-100", "9.9999999999999995e-100"},
                {"1e-6", "0.000001", "0.000001"}, {"1e-7", "1e-7", "1e-7"},
                {"1e9", "1e9", "1e9"}}) {
            check("integer-based rational rounding " + sample[0], () -> {
                CalculationResult result = NativeCalculator.calculate(sample[0], false);
                require(result.precise.equals(sample[1]) && result.display.equals(sample[2]),
                        "Wrong rational rounding: " + result.precise + " / " + result.display);
            });
        }
        for (String[] sample : new String[][]{
                {"sqrt(1e100+1-1e100)", "1"}, {"sqrt(0.1+0.2-0.3)", "0"},
                {"sqrt((1/3+1/6)*2)", "1"}, {"(0.1+0.2-0.3)^.5", "0"}}) {
            check("exact operands feed numerical functions " + sample[0], () -> {
                CalculationResult result = NativeCalculator.calculate(sample[0], false);
                require(result.exact.equals(sample[1]) && result.precise.equals(sample[1]), "Rational subtree was rounded prematurely");
            });
        }
        check("numerical function preserves an exact operand domain failure", () -> {
            try { NativeCalculator.calculate("sqrt(1/(0.1+0.2-0.3))", false); }
            catch (EvaluationException issue) {
                require(issue.status == EvaluationException.Status.UNDEFINED, "Exact operand diagnostic changed"); return;
            }
            throw new AssertionError("An exact division by zero was hidden inside a function");
        });
    }
    private static String[][] multiplicationNotationCases() {
        return new String[][]{
                {"3*sqrt(6)", "3\\sqrt{6}"}, {"7*sqrt(3)", "7\\sqrt{3}"},
                {"-4*sqrt(2)", "-4\\sqrt{2}"}, {"2*pi", "2\\pi "}, {"4*e", "4e"},
                {"e*pi", "e\\pi "}, {"pi*e", "\\pi e"}, {"pi*pi", "\\pi \\pi "},
                {"e*e", "e\\cdot e"}, {"pi*sqrt(2)", "\\pi \\sqrt{2}"},
                {"e*sqrt(3)", "e\\sqrt{3}"}, {"2*pi*sqrt(5)", "2\\pi \\sqrt{5}"},
                {"2*3", "2\\cdot 3"}, {"12*5", "12\\cdot 5"}, {"0.5*8", "0.5\\cdot 8"},
                {"-3*4", "-3\\cdot 4"}, {"2.5*1.2", "2.5\\cdot 1.2"},
                {"e*3", "e\\cdot 3"}, {"2*e*3", "2e\\cdot 3"},
                {"2*3^2", "2\\cdot 3^{2}"}, {"2*pi^2", "2\\pi ^{2}"},
                {"e^2*pi", "e^{2}\\pi "}, {"e^pi*pi", "e^{\\pi }\\pi "},
                {"2*sin(1)", "2\\sin\\left(1\\right)"}, {"3*cos(1)", "3\\cos\\left(1\\right)"},
                {"sin(1)*cos(1)", "\\sin\\left(1\\right)\\cos\\left(1\\right)"},
                {"sin(2*pi)", "\\sin\\left(2\\pi \\right)"},
                {"sqrt(e)*ln(2)", "\\sqrt{e}\\ln\\left(2\\right)"},
                {"2*log(3;2)", "2\\log_{2}\\left(3\\right)"},
                {"2*exp(1)", "2\\exp\\left(1\\right)"},
                {"sqrt(2)*sqrt(3)", "\\sqrt{2}\\sqrt{3}"},
                {"sqrt(sqrt(2))*pi", "\\sqrt{\\sqrt{2}}\\pi "},
                {"(e+1)*(pi-1)", "\\left(e+1\\right)\\left(\\pi -1\\right)"},
                {"2*(e+1)", "2\\left(e+1\\right)"},
                {"(2*e)^3", "\\left(2e\\right)^{3}"},
                {"pi^2*(e+1)", "\\pi ^{2}\\left(e+1\\right)"},
                {"e^(-2)*pi", "e^{\\left(-2\\right)}\\pi "},
                {"e^(1/2)*pi", "e^{\\left(\\frac{1}{2}\\right)}\\pi "},
                {"2*-pi", "2\\left(-\\pi \\right)"},
                {"\\frac{1}{2}*pi", "\\frac{1}{2}\\pi "},
                {"2*\\frac{1}{3}", "2\\cdot \\frac{1}{3}"},
                {"\\frac{e*pi}{2}", "\\frac{e\\pi }{2}"},
                {"\\frac{e*pi}{pi*sqrt(2)}", "\\frac{e\\pi }{\\pi \\sqrt{2}}"},
                {"\\frac{1+3\\sqrt{6}}{\\sqrt{42}}", "\\frac{1+3\\sqrt{6}}{\\sqrt{42}}"},
                {"\\frac{\\frac{2*pi}{3}}{e*sqrt(2)}", "\\frac{\\frac{2\\pi }{3}}{e\\sqrt{2}}"},
                {"1e-3*8", "1\\cdot 10^{-3}\\cdot 8"},
                {"1e2^2*pi", "\\left(1\\cdot 10^{2}\\right)^{2}\\pi "}
        };
    }
    private void checkMultiplicationNotation() {
        for (String[] sample : multiplicationNotationCases()) {
            check("contextual product LaTeX " + sample[0], () -> {
                String latex = NativeCalculator.parse(sample[0]).latex;
                require(latex.equals(sample[1]), "Expected " + sample[1] + ", got " + latex);
                for (boolean degrees : new boolean[]{false, true}) {
                    CalculationResult exact = NativeCalculator.calculateMode(sample[0], degrees, true);
                    CalculationResult numeric = NativeCalculator.calculateMode(sample[0], degrees, false);
                    CalculationResult reparsed = NativeCalculator.calculateMode(latex, degrees, false);
                    require(reparsed.precise.equals(numeric.precise) && reparsed.display.equals(numeric.display),
                            "Formatting changed the numerical value: " + latex);
                    require(exact.precise.equals(numeric.precise) && exact.exact.equals(numeric.exact)
                            && exact.continuation.equals(numeric.continuation), "Result mode changed mathematics");
                    require(numeric.latex.equals(MathText.number(numeric.display)), "Numerical typography changed");
                }
            });
        }
        for (String[] sample : new String[][]{
                {"3*sqrt(6)", "3\\sqrt{6}"}, {"e*pi", "e\\pi "}, {"pi*e", "\\pi e"},
                {"e*e", "e\\cdot e"}, {"2*pi*sqrt(5)", "2\\pi \\sqrt{5}"},
                {"e^2*pi", "e^{2}\\pi "}, {"2*pi^2", "2\\pi ^{2}"},
                {"-4*e", "-4e"}, {"e*-2", "e\\left(-2\\right)"},
                {"e*sqrt(8)", "e\\cdot 2\\sqrt{2}"},
                {"sqrt(8)*e", "2\\sqrt{2}e"},
                {"e*(sqrt(2)+sqrt(3))", "e\\left(\\sqrt{2}+\\sqrt{3}\\right)"},
                {"(sqrt(2)+sqrt(3))*e", "\\left(\\sqrt{2}+\\sqrt{3}\\right)e"},
                {"2*sin(1)", "2\\sin\\left(1\\right)"},
                {"sin(1)*cos(1)", "\\sin\\left(1\\right)\\cos\\left(1\\right)"},
                {"2*ln(2)", "2\\ln\\left(2\\right)"},
                {"\\frac{2*pi}{3}", "\\frac{2\\pi }{3}"},
                {"e*\\frac{1}{2}", "e\\cdot \\frac{1}{2}"},
                {"sqrt(8)+sqrt(18)", "5\\sqrt{2}"},
                {"2*sqrt(3)+4*sqrt(3)", "6\\sqrt{3}"},
                {"sqrt(6)*sqrt(2)", "2\\sqrt{3}"}, {"sqrt(12)*sqrt(3)", "6"},
                {"sqrt(5)*sqrt(5)", "5"}, {"sqrt(2)*sqrt(2)*sqrt(3)", "2\\sqrt{3}"},
                {"-sqrt(1/2)*e", "-\\frac{\\sqrt{2}}{2}e"},
                {"\\frac{1+3\\sqrt{6}}{\\sqrt{42}}", "\\frac{18\\sqrt{7}+\\sqrt{42}}{42}"}
        }) {
            check("exact product LaTeX " + sample[0], () -> {
                CalculationResult result = NativeCalculator.calculateMode(sample[0], false, true);
                require(result.exactDisplay && result.latex.equals(sample[1]),
                        "Expected " + sample[1] + ", got " + result.latex);
                require(NativeCalculator.calculateMode(result.latex, false, false).precise.equals(result.precise),
                        "Exact presentation changed meaning: " + result.latex);
            });
        }
        // These operators/names have no typed AST support yet. Presentation must
        // not silently turn them into a scalar product or introduce new syntax.
        for (String source : new String[]{"x*y", "a*b*c", "cbrt(2)", "\\vec{a}\\times\\vec{b}",
                "\\begin{pmatrix}1&2\\end{pmatrix}", "e**pi", "ee", "\\pie"})
            check("product notation preserves unsupported syntax " + source, () -> rejected(source));
    }
    private void checkSymbolicResults() {
        for (String[] sample : new String[][]{
                {"2+1/2", "5/2"}, {"6/8", "3/4"}, {"sqrt(81)", "9"}, {"sqrt(0)", "0"},
                {"sqrt(8)", "2*sqrt(2)"}, {"sqrt(72)", "6*sqrt(2)"}, {"sqrt(300)", "10*sqrt(3)"},
                {"sqrt(4/9)", "2/3"}, {"sqrt(8/9)", "2*sqrt(2)/3"}, {"sqrt(1/2)", "sqrt(2)/2"},
                {"sqrt(2/3)", "sqrt(6)/3"}, {"sqrt(0.08)", "sqrt(2)/5"},
                {"sqrt(8)+sqrt(18)", "5*sqrt(2)"}, {"sqrt(50)-sqrt(18)", "2*sqrt(2)"},
                {"sqrt(8)-2*sqrt(2)", "0"}, {"sqrt(8)*sqrt(18)", "12"},
                {"sqrt(2)*sqrt(3)", "sqrt(6)"}, {"sqrt(6)*sqrt(10)", "2*sqrt(15)"},
                {"(sqrt(2)+sqrt(3))^2", "5+2*sqrt(6)"}, {"sqrt(2)+sqrt(3)", "sqrt(2)+sqrt(3)"},
                {"sqrt(8)/2", "sqrt(2)"}, {"1/sqrt(2)", "sqrt(2)/2"}, {"sqrt(8)/sqrt(2)", "2"},
                {"1/(1+sqrt(2))", "-1+sqrt(2)"}, {"1/(sqrt(3)-sqrt(2))", "sqrt(2)+sqrt(3)"},
                {"1/(sqrt(3)+sqrt(2))", "-sqrt(2)+sqrt(3)"},
                {"sqrt(2)^-2", "1/2"}, {"2^(1/2)", "sqrt(2)"}, {"8^(3/2)", "16*sqrt(2)"},
                {"sqrt(8)%", "sqrt(2)/50"}, {"sqrt(sqrt(81))", "3"},
                {"sqrt(3+2*sqrt(2))", "1+sqrt(2)"}, {"sqrt(3-2*sqrt(2))", "-1+sqrt(2)"},
                {"sqrt((sqrt(2)-2)^2)", "2-sqrt(2)"},
                {"sin(pi/6)", "1/2"}, {"cos(pi/4)", "sqrt(2)/2"}, {"tan(pi/3)", "sqrt(3)"},
                {"sin(pi/12)", "(-sqrt(2)+sqrt(6))/4"}, {"tan(pi/12)", "2-sqrt(3)"},
                {"sin(pi)", "0"}, {"ln(e)", "1"}, {"ln(1)", "0"}, {"exp(0)", "1"},
                {"log(81;3)", "4"}, {"log(2;4)", "1/2"}, {"log(8;0.5)", "-3"},
                {"pi", "pi"}, {"e", "e"}, {"sin(1)", "sin(1)"}}) {
            check("exact scalar simplification " + sample[0], () -> {
                CalculationResult result = NativeCalculator.calculateMode(sample[0], false, true);
                require(result.exact.equals(sample[1]) && result.text.equals(sample[1]) && result.exactDisplay,
                        "Expected " + sample[1] + ", got " + result.exact + " / " + result.text);
                CalculationResult roundtrip = NativeCalculator.calculateMode(result.exact, false, true);
                require(roundtrip.exact.equals(result.exact), "Exact normal form changed on reuse: " + roundtrip.exact);
            });
            check("numerical presentation retains exact scalar " + sample[0], () -> {
                CalculationResult exact = NativeCalculator.calculateMode(sample[0], false, true);
                CalculationResult number = NativeCalculator.calculateMode(sample[0], false, false);
                require(number.exact.equals(sample[1]) && !number.exactDisplay && number.text.equals(number.display)
                        && number.precise.equals(exact.precise) && number.continuation.equals(exact.continuation),
                        "Changing format altered value, precision, or continuation");
            });
        }
        for (String[] sample : new String[][]{{"2+1/2", "2.5"}, {"sqrt(81)", "9"},
                {"sqrt(8)", "2.82842712474619009760337744842"}}) {
            check("requested numerical result " + sample[0], () ->
                    require(NativeCalculator.calculateMode(sample[0], false, false).text.equals(sample[1]), "Wrong numerical result"));
        }
        check("negative radical fraction has a single outside sign", () -> {
            CalculationResult result = NativeCalculator.calculate("-sqrt(1/2)", false);
            require(result.exact.equals("-sqrt(2)/2") && result.latex.equals("-\\frac{\\sqrt{2}}{2}"), "Wrong radical fraction typography");
        });
        check("large perfect square uses integer root proof", () -> {
            String integer = "1234567890123456789";
            require(NativeCalculator.calculate("sqrt("+integer+"^2)", false).exact.equals(integer), "Large square rounded incorrectly");
            require(NativeCalculator.calculate("sqrt("+integer+"^2+1)", false).exact.contains("sqrt"), "Neighbor of a square became an integer");
        });
        check("decimal approximation is never guessed to be a radical", () ->
                require(!NativeCalculator.calculate("1.414213562373095", false).exact.contains("sqrt"), "Guessed an irrational identity"));
        check("a proven large radical does not require a finite original argument", () -> {
            CalculationResult result = NativeCalculator.calculateMode("sqrt(10^6000)", false, false);
            require(result.text.equals("1e3000") && !result.exactDisplay && result.exact.equals("1"+repeat("0",3000)),
                    "Exact root proof was replaced by intermediate numerical overflow");
        });
        check("numerical mode never substitutes a symbolic-only out-of-range value", () -> {
            CalculationResult exact = NativeCalculator.calculateMode("10^6000*sqrt(2)", false, true);
            require(exact.exact.contains("sqrt(2)") && exact.precise.isEmpty(), "The large exact radical was lost");
            try { NativeCalculator.calculateMode("10^6000*sqrt(2)", false, false); }
            catch (EvaluationException issue) {
                require(issue.status == EvaluationException.Status.OUT_OF_RANGE, "Wrong numerical range diagnostic"); return;
            }
            throw new AssertionError("Numerical mode displayed an unevaluated exact result");
        });
        check("decimal pi is never a symbolic angle", () ->
                require(!NativeCalculator.calculate("sin(3.141592653589793)", false).exact.equals("0"), "Guessed a trigonometric identity"));
        check("exact trigonometric values respect the angle unit", () -> {
            require(NativeCalculator.calculate("cos(45)", true).exact.equals("sqrt(2)/2"), "Missing DEG identity");
            require(!NativeCalculator.calculate("cos(45)", false).exact.equals("sqrt(2)/2"), "DEG identity leaked into RAD");
        });
        for (String[] sample : new String[][]{{"1/(sqrt(8)-2*sqrt(2))", "UNDEFINED"},
                {"(sqrt(8)-2*sqrt(2))/(sqrt(8)-2*sqrt(2))", "INDETERMINATE"},
                {"0*sqrt(-1)", "NOT_REAL"}, {"(sqrt(8)-2*sqrt(2))^0", "INDETERMINATE"},
                {"tan(pi/2)", "UNDEFINED"}, {"log(1;1)", "UNDEFINED"}}) {
            for (boolean exact : new boolean[]{false, true}) check("mode-independent diagnostic " + sample[0] + " / " + exact, () -> {
                try { NativeCalculator.calculateMode(sample[0], false, exact); }
                catch (EvaluationException issue) {
                    require(issue.status.name().equals(sample[1]) && issue.getMessage().contains("position"), "Wrong exact diagnostic: " + issue);
                    return;
                }
                throw new AssertionError("A domain failure was hidden by simplification");
            });
        }
        Random random = new Random(0x53555244L);
        int[] primes = {2, 3, 5, 7, 11};
        for (int i = 0; i < 25; ++i) {
            int a = 1+random.nextInt(1000), b = 1+random.nextInt(1000), d = primes[random.nextInt(primes.length)];
            String root = "sqrt("+(a*a*d)+")";
            check("square-factor identity " + i, () -> {
                CalculationResult result = NativeCalculator.calculate(root, false);
                String expected = (a == 1 ? "" : a+"*")+"sqrt("+d+")";
                require(result.exact.equals(expected), "Wrong extracted square factor: " + result.exact);
                require(NativeCalculator.calculate("("+result.continuation+"/"+a+")^2", false).exact.equals(""+d),
                        "Extracted root fails the exact square identity");
            });
            check("conjugate division identity " + i, () -> {
                String denominator = "("+a+"+"+b+"*sqrt("+d+"))";
                CalculationResult inverse = NativeCalculator.calculate("1/"+denominator, false);
                require(NativeCalculator.calculate(inverse.continuation+"*"+denominator, false).exact.equals("1"),
                        "Rationalization failed its exact inverse identity");
            });
        }
    }
    private static List<View> findViews(View root, Predicate<View> predicate) {
        List<View> result = new ArrayList<>();
        if (predicate.test(root)) result.add(root);
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); ++i)
            result.addAll(findViews(((ViewGroup) root).getChildAt(i), predicate));
        return result;
    }
    private void press(Activity activity, String... descriptions) {
        press(activity.getWindow().getDecorView(), descriptions);
    }
    private void press(View root, String... descriptions) {
        onUi(() -> {
            for (String description : descriptions) {
                List<View> matches = findViews(root,
                        view -> view.getContentDescription() != null && description.contentEquals(view.getContentDescription()));
                require(!matches.isEmpty() && matches.get(0).performClick(), "Missing control: " + description);
            }
        });
    }
    private void waitForState(FormulaView view, String source, String answer) {
        long deadline = SystemClock.uptimeMillis() + 15000;
        while (SystemClock.uptimeMillis() < deadline) {
            JSONObject state = inspect(view);
            if (source.equals(state.optString("source")) && answer.equals(state.optString("result"))) {
                require(state.optInt("expressions") == 1 && state.optInt("answers") == (answer.isEmpty() ? 0 : 1),
                        "Duplicate mathematical display: " + state);
                return;
            }
            CountDownLatch tick = new CountDownLatch(1);
            new Handler(Looper.getMainLooper()).postDelayed(tick::countDown, 100);
            await(tick, "UI scheduling timed out");
        }
        throw new AssertionError("Expected rendered input " + source + " and result " + answer + ": " + inspect(view));
    }
    private void checkRenderedCalculator() {
        Activity activity = startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            FormulaView[] formula = {null};
            List<MathButton> buttons = new ArrayList<>();
            onUi(() -> {
                View root = activity.getWindow().getDecorView();
                require(findViews(root, view -> view instanceof EditText && view.isShown()).isEmpty(),
                        "Raw input must not be displayed alongside its typeset expression");
                require(findViews(root, view -> view instanceof FormulaView).size() == 1,
                        "Use a shared renderer rather than one WebView per key");
                for (View button : findViews(root, view -> view instanceof MathButton)) buttons.add((MathButton) button);
                require(buttons.size() >= 29, "Mathematical controls must use typeset labels");
                formula[0] = activity.findViewById(R.id.formula_display);
            });
            CountDownLatch ready = new CountDownLatch(1);
            long deadline = SystemClock.uptimeMillis() + 15000;
            Runnable[] poll = {null};
            poll[0] = () -> {
                boolean all = true;
                for (MathButton button : buttons) all &= button.hasGlyph();
                if (all || SystemClock.uptimeMillis() >= deadline) ready.countDown();
                else new Handler(Looper.getMainLooper()).postDelayed(poll[0], 100);
            };
            onUi(poll[0]); await(ready, "Control labels timed out");
            onUi(() -> {
                for (MathButton button : buttons) require(button.hasGlyph(), "Missing MathJax label: " + button.latex());
            });
            check("single rendered expression and number result", () -> {
                press(activity, "Clear all", "1", "Divide", "3", "Calculate");
                waitForState(formula[0], "1÷3", "1/3");
                JSONObject state = inspect(formula[0]);
                require(state.optInt("answerFractions") == 1 && state.optString("answerDescription").equals("Exact result: 1/3"),
                        "The exact fraction was not typeset or correctly announced: " + state);
            });
            check("equals swaps sizes and editing restores the input", () -> {
                press(activity, "Clear all", "2", "Multiply", "4");
                waitForState(formula[0], "2×4", "8");
                JSONObject preview = waitForLayout(formula[0], value -> !value.optBoolean("completed"));
                press(activity, "Calculate");
                JSONObject result = waitForLayout(formula[0], value -> value.optBoolean("completed"));
                require(Math.abs(result.optDouble("expressionScale") / preview.optDouble("expressionScale") - .5) < .01
                        && Math.abs(result.optDouble("answerScale") / preview.optDouble("answerScale") - 2) < .01,
                        "Equals did not halve the expression and double the result: " + result);
                require(Math.abs(result.optDouble("answerOpacity") - 1) < .01, "Completed result is dimmed");
                press(activity, "7"); waitForState(formula[0], "7", "");
                JSONObject edited = inspect(formula[0]);
                require(!edited.optBoolean("completed") && Math.abs(edited.optDouble("expressionScale") - 1) < .01,
                        "New input retained completed sizing: " + edited);
                press(activity, "Clear all", "1", "Divide", "3", "Calculate");
                waitForState(formula[0], "1÷3", "1/3");
            });
            check("rendered unfinished continuation", () -> {
                press(activity, "Add"); waitForState(formula[0], "(1/3)+", "");
            });
            check("rendered grouped arithmetic", () -> {
                press(activity, "Clear all", "Parentheses", "2", "Add", "3", "Parentheses", "Multiply", "4", "Calculate");
                waitForState(formula[0], "(2+3)×4", "20");
            });
            check("rendered new entry after equals", () -> {
                press(activity, "7"); waitForState(formula[0], "7", "");
                press(activity, "Backspace"); waitForState(formula[0], "", "");
            });
            check("rendered scientific input and exact pi", () -> {
                press(activity, "Show scientific functions");
                onUi(() -> {
                    View angle = activity.findViewById(R.id.angle_mode_button);
                    if (getTargetContext().getString(R.string.angle_degrees).contentEquals(angle.getContentDescription()))
                        angle.performClick();
                });
                press(activity, "Clear all", "Insert sin"); waitForState(formula[0], "sin()", "");
                press(activity, "Insert π", "Parentheses", "Calculate"); waitForState(formula[0], "sin(π)", "0");
            });
            check("rendered square root", () -> {
                press(activity, "Clear all", "Insert √", "9", "Parentheses", "Calculate");
                waitForState(formula[0], "sqrt(9)", "3");
            });
            check("30-digit constant is rendered and stays within the display", () -> {
                selectResultMode(activity, false);
                press(activity, "Clear all", "Insert e", "Calculate");
                waitForState(formula[0], "e", "2.71828182845904523536028747135");
                JSONObject state = inspect(formula[0]);
                require(state.optBoolean("answerSvg") && state.optDouble("answerLeft") >= -1
                        && state.optDouble("answerRight") <= state.optDouble("viewport") + 1,
                        "The expanded precision result is clipped: " + state);
                selectResultMode(activity, true);
            });
            check("rendered tangent at pi stays zero", () -> {
                press(activity, "Clear all", "Insert tan", "Insert π", "Parentheses", "Calculate");
                waitForState(formula[0], "tan(π)", "0");
            });
            check("rendered undefined tangent in preview and final result", () -> {
                press(activity, "Clear all", "Insert tan", "Insert π", "Divide", "2", "Parentheses");
                waitForState(formula[0], "tan(π÷2)", "Undefined");
                press(activity, "Calculate"); waitForState(formula[0], "tan(π÷2)", "Undefined");
                JSONObject state = inspect(formula[0]);
                require(state.optBoolean("answerSvg"), "Status must be typeset");
                require(state.optString("answerDescription").equals("Result: Undefined"),
                        "A diagnostic was announced as an approximate number");
            });
            check("new entry after undefined result", () -> {
                press(activity, "7"); waitForState(formula[0], "7", "");
            });
            check("rendered zero divided by zero", () -> {
                press(activity, "Clear all", "0", "Divide", "0", "Calculate");
                waitForState(formula[0], "0÷0", "Indeterminate");
            });
            check("rendered non-real square root", () -> {
                press(activity, "Clear all", "Insert √", "Subtract", "1", "Parentheses", "Calculate");
                waitForState(formula[0], "sqrt(−1)", "Not real");
                press(activity, "Clear all"); waitForState(formula[0], "", "");
            });
            check("number entry waits for an operation before preview", () -> {
                press(activity, "Clear all", "1", "2"); waitForState(formula[0], "12", "");
                press(activity, "Divide", "2"); waitForState(formula[0], "12÷2", "6");
                press(activity, "Backspace"); waitForState(formula[0], "12÷", "");
                press(activity, "Backspace"); waitForState(formula[0], "12", "");
                press(activity, "Calculate"); waitForState(formula[0], "12", "12");
            });
            check("signed decimal entry has no duplicate preview", () -> {
                press(activity, "Clear all", "Subtract", "1", "Decimal point", "5");
                waitForState(formula[0], "−1.5", "");
            });
            check("grouped literal waits for an operation", () -> {
                press(activity, "Clear all", "Parentheses", "2", "Parentheses");
                waitForState(formula[0], "(2)", "");
                press(activity, "Multiply", "3"); waitForState(formula[0], "(2)×3", "6");
            });
            check("percentage still previews its calculation", () -> {
                press(activity, "Clear all", "5", "0", "Percent"); waitForState(formula[0], "50%", "1/2");
            });
        } finally { onUi(activity::finish); }
    }
    private void checkExactCalculator() {
        Activity[] activity = {startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))};
        FormulaView[] formula = {activity[0].findViewById(R.id.formula_display)};
        android.content.SharedPreferences preferences = getTargetContext()
                .getSharedPreferences("calculation_history", android.content.Context.MODE_PRIVATE);
        String previous = preferences.getString("entries", "[]");
        CalculationHistory history = new CalculationHistory(getTargetContext());
        try {
            history.clear();
            check("exact fraction preview and keypad continuation", () -> {
                setSource(activity[0], "1/3"); waitForState(formula[0], "1/3", "1/3");
                press(activity[0], "Calculate", "Multiply", "3", "Calculate");
                waitForState(formula[0], "(1/3)×3", "1");
                require(history.entries().get(0).result.exact.equals("1"), "History recorded rounded continuation");
            });
            check("history retains exact and approximate representations", () -> {
                setSource(activity[0], "1/3"); press(activity[0], "Calculate");
                waitForState(formula[0], "1/3", "1/3");
                CalculationHistory.Entry saved = new CalculationHistory(getTargetContext()).entries().get(0);
                require(saved.result.exact.equals("1/3") && saved.result.precise.equals("0.3333333333333333333333333333333333333333")
                        && saved.result.latex.equals("\\frac{1}{3}") && saved.result.exactDisplay,
                        "Persisted history lost exact result data");
            });
            check("activity recreation retains exact result without recording it twice", () -> {
                int count = history.entries().size();
                ActivityMonitor monitor = addMonitor(MainActivity.class.getName(), null, false);
                try {
                    onUi(activity[0]::recreate);
                    Activity replacement = monitor.waitForActivityWithTimeout(15000);
                    require(replacement != null, "Activity was not recreated");
                    activity[0] = replacement;
                    onUi(() -> formula[0] = activity[0].findViewById(R.id.formula_display));
                    waitForState(formula[0], "1/3", "1/3");
                    require(history.entries().size() == count, "Recreation duplicated history");
                    press(activity[0], "Multiply", "3", "Calculate");
                    waitForState(formula[0], "(1/3)×3", "1");
                } finally { removeMonitor(monitor); }
            });
            check("legacy decimal history remains readable and restores an exact result", () -> {
                preferences.edit().putString("entries", "[{\"expression\":\"1/3\",\"precise\":\"0.33333333333333333333\","
                        + "\"display\":\"0.333333333333333\",\"degrees\":false}]").apply();
                CalculationHistory.Entry legacy = history.entries().get(0);
                require(legacy.result.exact.isEmpty() && legacy.result.display.equals("0.333333333333333"),
                        "An older history record was lost or falsely certified exact");
                press(activity[0], "Clear all");
                restoreFirstHistoryEntry(activity[0]);
                waitForState(formula[0], "1/3", "1/3");
                press(activity[0], "Multiply", "3", "Calculate");
                waitForState(formula[0], "(1/3)×3", "1");
            });
            check("history restores the saved angle mode through the native pipeline", () -> {
                history.clear();
                history.record("sin(90)", NativeCalculator.calculate("sin(90)", true), true);
                restoreFirstHistoryEntry(activity[0]);
                waitForState(formula[0], "sin(90)", "1");
                onUi(() -> require(getTargetContext().getString(R.string.angle_degrees).contentEquals(
                        activity[0].findViewById(R.id.angle_mode_button).getContentDescription()), "History lost DEG mode"));
            });
            check("out-of-range exact history preserves its complete value", () -> {
                history.clear();
                CalculationResult large = NativeCalculator.calculate("10^6000", false);
                history.record("10^6000", large, false);
                CalculationHistory.Entry saved = new CalculationHistory(getTargetContext()).entries().get(0);
                require(saved.result.exact.equals(large.exact) && saved.result.precise.equals("1e6000"),
                        "Large exact history lost its decimal approximation");
                restoreFirstHistoryEntry(activity[0]);
                waitForState(formula[0], "10^6000", "1e6000");
                press(activity[0], "Divide");
                setSource(activity[0], "(10^6000)/10^6000"); press(activity[0], "Calculate");
                waitForState(formula[0], "(10^6000)/10^6000", "1");
            });
            check("exact diagnostic is preserved after activity recreation", () -> {
                setSource(activity[0], "1/(0.1+0.2-0.3)"); press(activity[0], "Calculate");
                waitForState(formula[0], "1/(0.1+0.2-0.3)", "Undefined");
                ActivityMonitor monitor = addMonitor(MainActivity.class.getName(), null, false);
                try {
                    onUi(activity[0]::recreate);
                    Activity replacement = monitor.waitForActivityWithTimeout(15000);
                    require(replacement != null, "Diagnostic activity was not recreated"); activity[0] = replacement;
                    onUi(() -> formula[0] = activity[0].findViewById(R.id.formula_display));
                    waitForState(formula[0], "1/(0.1+0.2-0.3)", "Undefined");
                } finally { removeMonitor(monitor); }
                require(history.entries().size() == 2, "A diagnostic was stored as a calculation result");
            });
        } finally {
            preferences.edit().putString("entries", previous).apply();
            onUi(activity[0]::finish);
        }
    }
    private void checkResultSettings() {
        CalculatorSettings settings = new CalculatorSettings(getTargetContext());
        boolean previousMode = settings.exactResults();
        android.content.SharedPreferences historyPreferences = getTargetContext()
                .getSharedPreferences("calculation_history", android.content.Context.MODE_PRIVATE);
        String previousHistory = historyPreferences.getString("entries", "[]");
        CalculationHistory history = new CalculationHistory(getTargetContext());
        Activity[] activity = {startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))};
        FormulaView[] formula = {activity[0].findViewById(R.id.formula_display)};
        SettingsDialog[] dialog = {null};
        try {
            history.clear(); selectResultMode(activity[0], true);
            check("Settings is reachable from the overflow menu", () -> onUi(() -> {
                try {
                    View anchor = findViews(activity[0].getWindow().getDecorView(), view -> view.getContentDescription() != null
                            && "More options".contentEquals(view.getContentDescription())).get(0);
                    java.lang.reflect.Method method = MainActivity.class.getDeclaredMethod("showOptions", View.class);
                    method.setAccessible(true);
                    android.widget.PopupMenu popup = (android.widget.PopupMenu) method.invoke(activity[0], anchor);
                    android.view.MenuItem item = popup.getMenu().findItem(5);
                    require(item != null && item.getTitle().toString().equals("Settings") && item.getIcon() != null,
                            "Missing Settings entry or own gear icon");
                    require(popup.getMenu().performIdentifierAction(5, 0), "Settings menu action failed"); popup.dismiss();
                    dialog[0] = (SettingsDialog) activityField(activity[0], "settingsDialog");
                    require(dialog[0] != null && dialog[0].isShowing(), "Settings did not open");
                    require(((android.widget.RadioButton)dialog[0].findViewById(R.id.result_exact)).isChecked(), "Wrong default result format");
                    dialog[0].dismiss();
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            }));
            check("format selection updates a completed result without duplicate history", () -> {
                setSource(activity[0], "2+1/2"); press(activity[0], "Calculate"); waitForState(formula[0], "2+1/2", "5/2");
                int count = history.entries().size(); selectResultMode(activity[0], false);
                waitForState(formula[0], "2+1/2", "2.5");
                require(!settings.exactResults() && history.entries().size() == count, "Settings were not persisted or added history");
                selectResultMode(activity[0], true); waitForState(formula[0], "2+1/2", "5/2");
            });
            check("settings and selected numerical result survive recreation", () -> {
                selectResultMode(activity[0], false); onUi(() -> dialog[0] = openSettings(activity[0]));
                ActivityMonitor monitor = addMonitor(MainActivity.class.getName(), null, false);
                try {
                    onUi(activity[0]::recreate);
                    Activity replacement = monitor.waitForActivityWithTimeout(15000);
                    require(replacement != null, "Settings activity did not recreate"); activity[0] = replacement;
                    onUi(() -> {
                        formula[0] = activity[0].findViewById(R.id.formula_display);
                        dialog[0] = (SettingsDialog) activityField(activity[0], "settingsDialog");
                        require(dialog[0] != null && dialog[0].isShowing(), "The open Settings dialog was lost");
                        require(((android.widget.RadioButton)dialog[0].findViewById(R.id.result_numerical)).isChecked(), "Numerical selection was lost");
                        dialog[0].dismiss();
                    });
                    waitForState(formula[0], "2+1/2", "2.5");
                } finally { removeMonitor(monitor); }
            });
            check("result format survives a fresh activity launch", () -> {
                onUi(activity[0]::finish);
                activity[0] = startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                onUi(() -> formula[0] = activity[0].findViewById(R.id.formula_display));
                setSource(activity[0], "sqrt(8)"); press(activity[0], "Calculate");
                waitForState(formula[0], "sqrt(8)", "2.82842712474619009760337744842");
                JSONObject state = inspect(formula[0]);
                require(state.optInt("answerRoots") == 0 && state.optString("answerDescription").startsWith("Approximate result:"),
                        "Numerical mode typeset an exact radical");
            });
            check("exact mode renders a simplified radical and perfect square", () -> {
                selectResultMode(activity[0], true); waitForState(formula[0], "sqrt(8)", "2*sqrt(2)");
                JSONObject state = inspect(formula[0]);
                require(state.optInt("answerRoots") == 1 && state.optString("answerDescription").equals("Exact result: 2*sqrt(2)"),
                        "The exact radical is not typeset or correctly announced");
                setSource(activity[0], "sqrt(81)"); press(activity[0], "Calculate"); waitForState(formula[0], "sqrt(81)", "9");
                require(inspect(formula[0]).optInt("answerRoots") == 0, "A perfect square still displays a radical");
            });
            check("numerical preview can switch to an exact fraction", () -> {
                selectResultMode(activity[0], false); setSource(activity[0], "2+1/2"); waitForState(formula[0], "2+1/2", "2.5");
                selectResultMode(activity[0], true); waitForState(formula[0], "2+1/2", "5/2");
            });
            check("keypad continuation uses the whole exact radical", () -> {
                press(activity[0], "Clear all", "Show scientific functions", "Insert √", "8", "Parentheses", "Calculate");
                waitForState(formula[0], "sqrt(8)", "2*sqrt(2)");
                press(activity[0], "Multiply", "Insert √", "2", "Parentheses", "Calculate");
                waitForState(formula[0], "(2*sqrt(2))×sqrt(2)", "4");
            });
            check("numerical history retains exact data and restores using the current format", () -> {
                history.clear(); selectResultMode(activity[0], false);
                setSource(activity[0], "sqrt(8)"); press(activity[0], "Calculate");
                waitForState(formula[0], "sqrt(8)", "2.82842712474619009760337744842");
                CalculationHistory.Entry entry = new CalculationHistory(getTargetContext()).entries().get(0);
                require(entry.result.exact.equals("2*sqrt(2)") && !entry.result.exactDisplay, "History discarded exact data");
                selectResultMode(activity[0], true); press(activity[0], "Clear all"); restoreFirstHistoryEntry(activity[0]);
                waitForState(formula[0], "sqrt(8)", "2*sqrt(2)");
            });
            check("result mode never changes mathematical diagnostics", () -> {
                setSource(activity[0], "1/(sqrt(8)-2*sqrt(2))"); press(activity[0], "Calculate");
                waitForState(formula[0], "1/(sqrt(8)-2*sqrt(2))", "Undefined");
                selectResultMode(activity[0], false); waitForState(formula[0], "1/(sqrt(8)-2*sqrt(2))", "Undefined");
            });
        } finally {
            settings.setExactResults(previousMode); historyPreferences.edit().putString("entries", previousHistory).apply();
            onUi(() -> { if (dialog[0] != null) dialog[0].dismiss(); activity[0].finish(); });
        }
    }
    private static Object activityField(Activity activity, String name) {
        try {
            java.lang.reflect.Field field = MainActivity.class.getDeclaredField(name); field.setAccessible(true); return field.get(activity);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static SettingsDialog openSettings(Activity activity) {
        try {
            java.lang.reflect.Method method = MainActivity.class.getDeclaredMethod("showSettings"); method.setAccessible(true);
            return (SettingsDialog) method.invoke(activity);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private void selectResultMode(Activity activity, boolean exact) {
        onUi(() -> {
            SettingsDialog dialog = openSettings(activity);
            dialog.findViewById(exact ? R.id.result_exact : R.id.result_numerical).performClick(); dialog.dismiss();
        });
    }
    private void setSource(Activity activity, String text) {
        onUi(() -> {
            try {
                java.lang.reflect.Field field = MainActivity.class.getDeclaredField("input"); field.setAccessible(true);
                EditText input = (EditText) field.get(activity); input.setText(text); input.setSelection(input.length());
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
    }
    private void restoreFirstHistoryEntry(Activity activity) {
        onUi(() -> {
            try {
                java.lang.reflect.Method method = MainActivity.class.getDeclaredMethod("showHistory"); method.setAccessible(true);
                android.app.AlertDialog dialog = (android.app.AlertDialog) method.invoke(activity);
                android.widget.ListView list = dialog.getListView();
                require(list != null && list.getCount() > 0, "Missing history rows");
                list.performItemClick(list.getAdapter().getView(0, null, list), 0, 0);
                dialog.dismiss();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
    }
    private void checkSourceKeyboard() {
        Activity activity = startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ExpressionEditor[] dialog = {null};
        EditText[] editor = {null};
        String[] applied = {null};
        int[] calls = {0};
        try {
            onUi(() -> {
                dialog[0] = new ExpressionEditor(activity, new CalculatorStyle(true), "", 0, 0,
                        (text, start, end) -> { applied[0] = text; ++calls[0]; });
                dialog[0].show(); editor[0] = dialog[0].findViewById(R.id.source_editor);
                require(!editor[0].getShowSoftInputOnFocus(), "The source editor must not open the system keyboard");
            });
            View root = dialog[0].getWindow().getDecorView();
            check("complete QWERTY alphabet and numbers", () -> onUi(() -> {
                for (char key : "qwertyuiopasdfghjklñzxcvbnm1234567890".toCharArray()) {
                    String description = getTargetContext().getString(R.string.insert_literal, String.valueOf(key));
                    require(findViews(root, view -> view.getContentDescription() != null
                            && description.contentEquals(view.getContentDescription())).size() == 1,
                            "Missing or duplicated literal key: " + key);
                }
            }));
            check("keyboard uppercase and lowercase", () -> {
                press(root, "Use uppercase letters", "Type A", "Type X", "Type Ñ", "Use lowercase letters", "Type i", "Type ñ");
                onUi(() -> require(editor[0].getText().toString().equals("AXÑiñ"), "Case toggle changed literal input"));
            });
            check("keyboard symbols cover LaTeX punctuation", () -> {
                press(root, "Symbols");
                onUi(() -> {
                    for (char key : "\\{}[]()|/=%$&#_^~*+-,;:.<>!?'\"".toCharArray()) {
                        String description = getTargetContext().getString(R.string.insert_literal, String.valueOf(key));
                        require(findViews(root, view -> view.getContentDescription() != null
                                && description.contentEquals(view.getContentDescription())).size() == 1,
                                "Missing or duplicated symbol: " + key);
                    }
                });
            });
            check("keyboard writes a LaTeX logarithm", () -> {
                onUi(() -> editor[0].setText(""));
                press(root, "Type \\", "Letters", "Type l", "Type o", "Type g", "Symbols", "Type _", "Type {",
                        "Type 2", "Type }", "Type (", "Type 8", "Type )");
                onUi(() -> require(editor[0].getText().toString().equals("\\log_{2}(8)"), "LaTeX was rewritten"));
            });
            check("keyboard inserts over a selection", () -> {
                onUi(() -> { editor[0].setText("12345"); editor[0].setSelection(1, 4); });
                press(root, "Type /");
                onUi(() -> require(editor[0].getText().toString().equals("1/5"), "Selection was not replaced"));
            });
            check("keyboard preserves literal operator sequences", () -> {
                onUi(() -> { editor[0].setText("2^"); editor[0].setSelection(2); });
                press(root, "Type -", "Type 3");
                onUi(() -> require(editor[0].getText().toString().equals("2^-3"), "Source keyboard normalized operators"));
            });
            check("keyboard cursor and Unicode deletion", () -> {
                onUi(() -> { editor[0].setText("a\uD83D\uDE00b"); editor[0].setSelection(3); });
                press(root, "Backspace", "Move cursor left", "Move cursor right");
                onUi(() -> require(editor[0].getText().toString().equals("ab") && editor[0].getSelectionStart() == 1,
                        "Cursor or deletion split a Unicode character"));
            });
            check("keyboard space and line break", () -> {
                onUi(() -> editor[0].setText(""));
                press(root, "Insert space", "Insert line break");
                onUi(() -> require(editor[0].getText().toString().equals(" \n"), "Whitespace keys were not literal"));
            });
            check("keyboard input budget", () -> {
                onUi(() -> { editor[0].setText(repeat("1", 2048)); editor[0].setSelection(2048); });
                press(root, "Type 2");
                onUi(() -> require(editor[0].length() == 2048 && editor[0].getSelectionStart() == 2048,
                        "Literal keyboard exceeded the source limit"));
            });
            check("keyboard draft restoration", () -> onUi(() -> {
                editor[0].setText("\\log_{2}(8)"); editor[0].setSelection(6, 7);
                Bundle state = dialog[0].onSaveInstanceState();
                editor[0].setText("discard"); dialog[0].onRestoreInstanceState(state);
                require(editor[0].getText().toString().equals("\\log_{2}(8)")
                        && editor[0].getSelectionStart() == 6 && editor[0].getSelectionEnd() == 7,
                        "Draft text or selection was lost");
            }));
            check("keyboard apply evaluates the written logarithm", () -> {
                press(root, "Apply");
                require(calls[0] == 1 && applied[0].equals("\\log_{2}(8)"), "Apply lost the source");
                decimal(applied[0], "3", "3");
            });
            check("keyboard cancel keeps the prior expression", () -> {
                onUi(() -> {
                    dialog[0] = new ExpressionEditor(activity, new CalculatorStyle(true), "7", 1, 1,
                            (text, start, end) -> ++calls[0]);
                    dialog[0].show();
                });
                press(dialog[0].getWindow().getDecorView(), "Type x", "Cancel");
                require(calls[0] == 1, "Cancel applied an unfinished draft");
            });
        } finally { onUi(() -> { if (dialog[0] != null) dialog[0].dismiss(); activity.finish(); }); }
    }
    private void checkRenderer() {
        Activity activity = startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        FormulaView[] view = {null};
        try {
            onUi(() -> {
                FrameLayout host = new FrameLayout(activity);
                view[0] = new FormulaView(activity, Color.WHITE);
                host.addView(view[0], new FrameLayout.LayoutParams(-1, 200));
                activity.setContentView(host);
            });
            // Abort the renderer group on startup failure instead of repeating timeouts.
            render(view[0], "1+2", true);
            for (String source : new String[]{"1/2+sqrt(9)", "(2+3)^(-4)", "1/(2/(3/4))", "sin(pi/2)+1e-3", "(2+3)%"}) {
                check("MathJax native LaTeX " + source, () -> render(view[0], NativeCalculator.parse(source).latex, true));
            }
            for (String[] sample : multiplicationNotationCases()) {
                check("MathJax contextual product " + sample[0], () -> {
                    render(view[0], NativeCalculator.parse(sample[0]).latex, true);
                    if (sample[0].equals("3*sqrt(6)") || sample[0].equals("e*pi"))
                        require(inspect(view[0]).optInt("productDots") == 0, "Unexpected multiplication dot in SVG");
                    if (sample[0].equals("2*3"))
                        require(inspect(view[0]).optInt("productDots") > 0, "Missing numerical multiplication dot in SVG");
                    render(view[0], NativeCalculator.calculateMode(sample[0], false, true).latex, true);
                });
            }
            check("MathJax vector cross product remains explicit", () -> {
                String tex = "\\vec{a}\\times\\vec{b}";
                render(view[0], tex, true);
                require(inspect(view[0]).optInt("crossProducts") > 0, "Missing vector cross glyph");
            });
            for (String tex : new String[]{"\\int_0^1 x^2\\,dx", "\\frac{d}{dx}\\sin(x^2)",
                    "\\sum_{n=1}^{\\infty}\\frac{1}{n^2}", "\\begin{pmatrix}1&2\\\\3&4\\end{pmatrix}",
                    "\\mathbb{R}+\\mathfrak{g}", "\\alpha+\\Omega"}) {
                check("MathJax presentation only " + tex, () -> render(view[0], tex, true));
            }
            check("MathJax invalid input", () -> render(view[0], "\\DefinitelyUnknown{1}", false));
            check("MathJax recovery", () -> render(view[0], "\\sqrt{9}", true));
            check("MathJax input is data", () -> {
                render(view[0], "\\text{<script>window.pwned=1</script>}", true);
                require(!inspect(view[0]).optBoolean("injected"), "TeX was executed as JavaScript");
            });
            check("MathJax latest request wins", () -> {
                onUi(() -> { view[0].setLatex("\\frac{1}{3}", "old"); view[0].setLatex("", ""); });
                render(view[0], "x^2", true);
            });
            check("MathJax clearing", () -> render(view[0], "", true));
            for (String source : new String[]{"sin(", "sqrt()", "2^", "(2+", "\\frac{1}{", "#_%&"}) {
                check("typeset unfinished input " + source, () -> render(view[0], MathText.unfinished(source), true));
            }
            check("bounded malformed input preview", () -> {
                String tex = MathText.unfinished(repeat("(", 2048));
                require(tex.length() <= 16384 && tex.endsWith("\\ldots"), "Input preview exceeded renderer limits");
                render(view[0], tex, true);
            });
            check("typeset scientific result", () -> render(view[0], MathText.number("1.234567890123456e-40"), true));
            check("MathJax control label images", () -> {
                MathGlyphs.Glyph[] glyphs = new MathGlyphs.Glyph[3];
                CountDownLatch finished = new CountDownLatch(3);
                onUi(() -> {
                    MathGlyphs cache = new MathGlyphs(view[0]);
                    String[] labels = {"f", "f(x)", "(\\,)"};
                    for (int i = 0; i < labels.length; ++i) {
                        final int index = i;
                        cache.request(labels[i], glyph -> { glyphs[index] = glyph; finished.countDown(); });
                    }
                });
                await(finished, "MathJax label export timed out");
                require(glyphs[1].bitmap.getWidth() > glyphs[0].bitmap.getWidth() * 2,
                        "A multi-atom label was truncated to its first SVG fragment");
                for (MathGlyphs.Glyph glyph : glyphs) {
                    boolean ink = false;
                    for (int y = 0; y < glyph.bitmap.getHeight() && !ink; ++y)
                        for (int x = 0; x < glyph.bitmap.getWidth(); ++x)
                            if (Color.alpha(glyph.bitmap.getPixel(x, y)) > 0) { ink = true; break; }
                    require(ink, "A control label image is blank");
                }
            });
            onUi(() -> {
                ViewGroup.LayoutParams params = view[0].getLayoutParams();
                params.width = Math.round(240 * activity.getResources().getDisplayMetrics().density);
                view[0].setLayoutParams(params);
            });
            check("expression shrinks before overflowing", () -> {
                render(view[0], repeat("8", 18), true);
                JSONObject state = waitForLayout(view[0], value -> value.optDouble("expressionScale") < .99);
                require(state.optDouble("expressionScale") > .70, "Expression shrank too far: " + state);
                require(state.optDouble("scrollMax") <= 2, "A moderately long number should still fit: " + state);
            });
            check("preview and completed results swap the size hierarchy", () -> {
                for (String source : new String[]{"49*8", "\\frac{1+3\\sqrt{6}}{\\sqrt{42}}",
                        repeat("8", 64) + "/2"}) {
                    Formula formula = NativeCalculator.parse(source);
                    for (boolean exact : new boolean[]{false, true}) {
                        CalculationResult result = NativeCalculator.calculateMode(source, false, exact);
                        double previewScale = 0;
                        for (boolean completed : new boolean[]{false, true, true, false}) {
                            onUi(() -> view[0].setCalculation(formula.latex, source, result.latex,
                                    result.text, completed, false, exact));
                            waitForState(view[0], source, result.text);
                            JSONObject state = waitForLayout(view[0], value -> value.optBoolean("completed") == completed);
                            double expressionScale = state.optDouble("expressionScale");
                            if (previewScale == 0) previewScale = expressionScale;
                            require(Math.abs(expressionScale - previewScale * (completed ? .5 : 1)) < .01,
                                    "Equals or editing compounded the input scale: " + state);
                            double ratio = state.optDouble("answerScale") / state.optDouble("expressionScale");
                            double targetRatio = completed ? 2 : .5;
                            if (source.equals("49*8") || (exact && !source.startsWith("8")))
                                require(Math.abs(ratio - targetRatio) < .01, "Wrong result/expression size ratio: " + state);
                            else require(ratio > 0 && ratio <= targetRatio + .01,
                                    "A long result must fit within its target size: " + state);
                            require(Math.abs(state.optDouble("answerOpacity") - (completed ? 1 : .75)) < .01,
                                    "Wrong preview/completed opacity: " + state);
                            require(state.optDouble("answerLeft") >= -2
                                    && state.optDouble("answerRight") <= state.optDouble("viewport") + 2,
                                    "The smaller result is clipped: " + state);
                        }
                    }
                }
            });
            check("long expression stays readable and reveals the last digit", () -> {
                render(view[0], repeat("8", 64), true);
                JSONObject state = waitForLayout(view[0], value -> value.optDouble("scrollX") > 20);
                require(state.optDouble("expressionScale") >= .65, "Expression became too small: " + state);
                require(Math.abs(state.optDouble("scrollX") - state.optDouble("scrollMax")) <= 2,
                        "Overflow did not follow the trailing digit: " + state);
                require(Math.abs(state.optDouble("expressionRight") - state.optDouble("viewport")) <= 3,
                        "The end of the expression is clipped: " + state);
            });
            check("editing an overflowing expression follows its new end", () -> {
                CountDownLatch moved = new CountDownLatch(1);
                onUi(() -> view[0].evaluateJavascript("document.getElementById('viewport').scrollLeft=0", ignored -> moved.countDown()));
                await(moved, "Could not pan the formula");
                waitForLayout(view[0], value -> value.optDouble("scrollX") == 0);
                render(view[0], repeat("8", 65), true);
                JSONObject state = waitForLayout(view[0], value -> value.optDouble("scrollX") > 20);
                require(Math.abs(state.optDouble("scrollX") - state.optDouble("scrollMax")) <= 2,
                        "A new digit remained offscreen: " + state);
            });
            check("result remains visible under an overflowing calculation", () -> {
                String source = repeat("8", 64) + "/2";
                Formula formula = NativeCalculator.parse(source);
                Approximation result = NativeCalculator.evaluate(source, false);
                onUi(() -> view[0].setCalculation(formula.latex, source, MathText.number(result.display),
                        result.display, false, false));
                waitForState(view[0], source, result.display);
                JSONObject state = waitForLayout(view[0], value -> value.optDouble("scrollX") > 20);
                require(state.optDouble("answerLeft") >= -2
                        && state.optDouble("answerRight") <= state.optDouble("viewport") + 2,
                        "Expression overflow hid the result: " + state);
                require(state.optDouble("answerScale") <= state.optDouble("expressionScale") / 2 + .01,
                        "Overflowing input enlarged its result: " + state);
                require(state.optDouble("tailLeft") >= -2 && state.optDouble("tailRight") <= state.optDouble("viewport") + 2,
                        "The last denominator digit is offscreen: " + state);
            });
            check("short entry restores normal size and scroll position", () -> {
                render(view[0], "7", true);
                JSONObject state = waitForLayout(view[0], value -> value.optDouble("scrollX") == 0);
                require(Math.abs(state.optDouble("expressionScale") - 1) < .01,
                        "Short input retained the reduced font size: " + state);
                require(state.optDouble("scrollMax") <= 2, "Short input retained horizontal overflow: " + state);
            });
        } finally {
            onUi(() -> { if (view[0] != null) view[0].release(); activity.finish(); });
        }
    }
    private JSONObject waitForLayout(FormulaView view, Predicate<JSONObject> condition) {
        long deadline = SystemClock.uptimeMillis() + 10000;
        JSONObject state = null;
        while (SystemClock.uptimeMillis() < deadline) {
            state = inspect(view);
            if (condition.test(state)) return state;
            CountDownLatch tick = new CountDownLatch(1);
            new Handler(Looper.getMainLooper()).postDelayed(tick::countDown, 50);
            await(tick, "Display layout scheduling timed out");
        }
        throw new AssertionError("Display layout did not settle: " + state);
    }
    private void render(FormulaView view, String latex, boolean expectedSuccess) {
        CountDownLatch finished = new CountDownLatch(1);
        boolean[] successful = {false};
        onUi(() -> {
            view.setOnRenderListener(success -> { successful[0] = success; finished.countDown(); });
            view.setLatex(latex, latex);
        });
        try { await(finished, "MathJax render timed out: " + latex); }
        catch (AssertionError failure) { throw new AssertionError(failure.getMessage() + "\n" + view.diagnostics(), failure); }
        require(successful[0] == expectedSuccess, "Unexpected rendering status for " + latex + ": " + inspect(view));
        onUi(() -> require(latex.contentEquals(view.getContentDescription()), "Renderer request was replaced"));
        JSONObject state = inspect(view);
        require("4.1.3".equals(state.optString("version")), "Wrong MathJax version: " + state);
        if (expectedSuccess) {
            require(latex.equals(state.optString("latex")), "Stale formula was rendered: " + state);
            require(latex.isEmpty() ? state.optInt("glyphs") == 0 : state.optInt("glyphs") > 0,
                    "Expected SVG glyphs: " + state);
            require(state.optDouble("assistiveWidth") <= 1 && state.optDouble("assistiveHeight") <= 1,
                    "Assistive MathML must not appear as a second visual formula");
        }
        onUi(() -> require(latex.contentEquals(view.getContentDescription()), "Missing accessible expression"));
    }
    private JSONObject inspect(FormulaView view) {
        // A recreated WebView can exist before its new document defines MathJax.
        // Wait for that document without changing any result/layout assertions.
        long documentDeadline = SystemClock.uptimeMillis() + 15000;
        boolean documentReady = false;
        while (!documentReady && SystemClock.uptimeMillis() < documentDeadline) {
            CountDownLatch queried = new CountDownLatch(1);
            boolean[] ready = {false};
            onUi(() -> view.evaluateJavascript("typeof MathJax!=='undefined' && !!document.getElementById('formula')",
                    value -> { ready[0] = "true".equals(value); queried.countDown(); }));
            await(queried, "Renderer readiness query timed out");
            documentReady = ready[0];
            if (!documentReady) {
                CountDownLatch tick = new CountDownLatch(1);
                new Handler(Looper.getMainLooper()).postDelayed(tick::countDown, 100);
                await(tick, "Renderer readiness scheduling timed out");
            }
        }
        require(documentReady, "MathJax document did not become ready within 15 seconds");
        CountDownLatch finished = new CountDownLatch(1);
        JSONObject[] result = {null};
        onUi(() -> view.evaluateJavascript("JSON.stringify({version:MathJax.version,"
                + "latex:document.getElementById('formula').dataset.latex||'',"
                + "source:document.getElementById('formula').dataset.source||'',"
                + "result:document.getElementById('formula').dataset.result||'',"
                + "answerSvg:!!document.querySelector('#answer svg'),"
                + "answerFractions:document.querySelectorAll('#answer [data-mml-node=mfrac]').length,"
                + "answerRoots:document.querySelectorAll('#answer [data-mml-node=msqrt]').length,"
                + "answerDescription:document.getElementById('answer')?.getAttribute('aria-label')||'',"
                + "viewport:document.getElementById('viewport')?.clientWidth||document.documentElement.clientWidth,"
                + "scrollX:document.getElementById('viewport')?.scrollLeft||0,"
                + "scrollMax:document.getElementById('viewport')?Math.max(0,document.getElementById('viewport').scrollWidth-document.getElementById('viewport').clientWidth):0,"
                + "tailLeft:(()=>{const nodes=document.querySelectorAll('#expression svg [data-mml-node=mn],#expression svg [data-mml-node=mi],#expression svg [data-mml-node=mo]');return nodes.length?nodes[nodes.length-1].getBoundingClientRect().left:0;})(),"
                + "tailRight:(()=>{const nodes=document.querySelectorAll('#expression svg [data-mml-node=mn],#expression svg [data-mml-node=mi],#expression svg [data-mml-node=mo]');return nodes.length?nodes[nodes.length-1].getBoundingClientRect().right:0;})(),"
                + "expressionScale:document.getElementById('expression')?parseFloat(getComputedStyle(document.getElementById('expression')).fontSize)/parseFloat(getComputedStyle(document.body).fontSize):0,"
                + "answerScale:document.getElementById('answer')?parseFloat(getComputedStyle(document.getElementById('answer')).fontSize)/parseFloat(getComputedStyle(document.body).fontSize):0,"
                + "answerOpacity:document.getElementById('answer')?parseFloat(getComputedStyle(document.getElementById('answer')).opacity):0,"
                + "completed:document.getElementById('expression')?.dataset.completed==='true',"
                + "expressionRight:document.getElementById('expression')?.getBoundingClientRect().right||0,"
                + "answerLeft:document.getElementById('answer')?.getBoundingClientRect().left||0,"
                + "answerRight:document.getElementById('answer')?.getBoundingClientRect().right||0,"
                + "expressions:document.querySelectorAll('#expression').length,answers:document.querySelectorAll('#answer').length,"
                + "glyphs:document.querySelectorAll('svg path,svg use,svg text').length,"
                + "productDots:document.querySelectorAll('#expression [data-c=\"22C5\"]').length,"
                + "crossProducts:document.querySelectorAll('#expression [data-c=\"D7\"]').length,"
                + "markup:document.getElementById('formula').innerHTML.slice(0,1800),"
                + "assistiveWidth:document.querySelector('mjx-assistive-mml')?.getBoundingClientRect().width||0,"
                + "assistiveHeight:document.querySelector('mjx-assistive-mml')?.getBoundingClientRect().height||0,"
                + "injected:window.pwned===1})", value -> {
                    try { result[0] = new JSONObject((String) new JSONTokener(value).nextValue()); }
                    catch (Exception ignored) { /* Report the unreadable state below. */ }
                    finished.countDown();
                }));
        await(finished, "Could not inspect MathJax output");
        require(result[0] != null, "Unreadable MathJax state"); return result[0];
    }
    private static void await(CountDownLatch latch, String message) {
        try { require(latch.await(15, TimeUnit.SECONDS), message); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }
    private static void edited(String source, int start, int end, String key, String expected, int cursor) {
        CalculatorInput.Edit edit = CalculatorInput.edit(source, start, end, key, false, null);
        require(expected.equals(edit.text) && cursor == edit.cursor,
                "Expected " + expected + " @" + cursor + ", got " + edit.text + " @" + edit.cursor);
    }
    private static String repeat(String text, int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; ++i) result.append(text);
        return result.toString();
    }
    private void onUi(Runnable action) {
        Throwable[] failure = {null};
        runOnMainSync(() -> {
            try { action.run(); } catch (Throwable error) { failure[0] = error; }
        });
        if (failure[0] != null) throw new AssertionError("UI test failed: " + failure[0], failure[0]);
    }
}
