package org.axioma.calculator;

public final class NativeCalculator {
    static { System.loadLibrary("axioma"); }
    private NativeCalculator() {}
    public static native Formula parse(String source);
    /** Finite real result, or a typed mathematical diagnostic; malformed input is a separate error. */
    public static native Approximation evaluate(String source, boolean degrees);
    /** Main pipeline defaults to exact presentation; all mathematical work stays native. */
    public static CalculationResult calculate(String source, boolean degrees) { return calculateMode(source, degrees, true); }
    /** Result format affects presentation; continuation retains exact expressions in either mode. */
    public static native CalculationResult calculateMode(String source, boolean degrees, boolean exact);
    /** Reduced integer or fraction; null means the exact subsystem does not support the expression yet. */
    public static native String evaluateExact(String source);
}
