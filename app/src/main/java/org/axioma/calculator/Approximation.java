package org.axioma.calculator;

/** Decimal results cross JNI as text so Java never narrows them to double. */
public final class Approximation {
    public final String precise;
    public final String display;

    public Approximation(String precise, String display) {
        this.precise = precise;
        this.display = display;
    }
}
