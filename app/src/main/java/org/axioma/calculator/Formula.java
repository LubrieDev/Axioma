package org.axioma.calculator;

public final class Formula {
    public final MathNode root;
    public final String latex;
    public Formula(MathNode root, String latex) { this.root = root; this.latex = latex; }
}
