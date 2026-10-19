package org.axioma.calculator;

/** Native result data. Exact values and optional decimal approximations stay distinct. */
public final class CalculationResult {
    public final String exact, precise, display, continuation, text, latex;
    public final boolean exactDisplay;

    public CalculationResult(String exact, String precise, String display, String continuation,
                             String text, String latex, boolean exactDisplay) {
        this.exact = exact; this.precise = precise; this.display = display;
        this.continuation = continuation; this.text = text; this.latex = latex;
        this.exactDisplay = exactDisplay;
    }
}
