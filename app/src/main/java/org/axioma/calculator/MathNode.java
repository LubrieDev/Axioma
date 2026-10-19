package org.axioma.calculator;

/** Immutable tree produced by C++; these kind values match expression.hpp. */
public final class MathNode {
    public static final int NUMBER = 0, CONSTANT = 1, ADD = 2, SUBTRACT = 3,
            MULTIPLY = 4, DIVIDE = 5, POWER = 6, NEGATE = 7, FUNCTION = 8, GROUP = 9, PERCENT = 10;
    public final int kind;
    public final String text;
    /** A function's left child is its argument; log may have a right child for its base. */
    public final MathNode left, right;
    public MathNode(int kind, String text, MathNode left, MathNode right) {
        this.kind = kind; this.text = text; this.left = left; this.right = right;
    }
}
