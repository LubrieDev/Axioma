package org.axioma.calculator;

/** Selection-aware keypad editing, independent of Android views and numeric evaluation. */
public final class CalculatorInput {
    private CalculatorInput() {}

    public static final class Edit {
        public final String text;
        public final int cursor;
        Edit(String text, int cursor) { this.text = text; this.cursor = cursor; }
    }

    private static boolean operator(char c) { return "+-−*/×÷^".indexOf(c) >= 0; }
    private static boolean digit(char c) { return c >= '0' && c <= '9'; }

    public static Edit edit(String source, int start, int end, String key, boolean completed, String resultSource) {
        start = Math.max(0, Math.min(start, source.length()));
        end = Math.max(start, Math.min(end, source.length()));
        if (key.equals("AC")) return new Edit("", 0);
        if (completed && !key.equals("backspace")) {
            boolean continueResult = key.equals("%") || (key.length() == 1 && operator(key.charAt(0)));
            source = continueResult && resultSource != null ? resultSource : "";
            start = end = source.length();
        }
        if (key.equals("backspace")) {
            if (start == end && start > 0) start = Character.offsetByCodePoints(source, start, -1);
            return new Edit(source.substring(0, start) + source.substring(end), start);
        }
        String prefix = source.substring(0, start), suffix = source.substring(end);
        if (key.equals("()")) {
            int balance = 0;
            for (int i = 0; i < prefix.length(); ++i) {
                if (prefix.charAt(i) == '(') ++balance;
                else if (prefix.charAt(i) == ')') --balance;
            }
            boolean operand = !prefix.isEmpty() && !operator(prefix.charAt(prefix.length() - 1))
                    && prefix.charAt(prefix.length() - 1) != '(';
            if (balance > 0 && operand && suffix.startsWith(")")) return new Edit(source, start + 1);
            key = balance > 0 && operand ? ")" : "(";
        } else if (key.equals(".")) {
            int number = prefix.length();
            while (number > 0 && (digit(prefix.charAt(number - 1)) || ".,".indexOf(prefix.charAt(number - 1)) >= 0)) --number;
            String tail = prefix.substring(number);
            if (tail.contains(".") || tail.contains(",")) return new Edit(source, start);
            int exponent = number;
            if (exponent > 0 && "+-−".indexOf(prefix.charAt(exponent - 1)) >= 0) --exponent;
            if (exponent > 0 && "eE".indexOf(prefix.charAt(exponent - 1)) >= 0) return new Edit(source, start);
            if (tail.isEmpty()) key = "0.";
        } else if (key.length() == 1 && operator(key.charAt(0))) {
            char op = key.charAt(0);
            if (prefix.isEmpty() && op != '-' && op != '−') prefix = "0";
            if (!prefix.isEmpty() && operator(prefix.charAt(prefix.length() - 1))) {
                char previous = prefix.charAt(prefix.length() - 1);
                boolean negativeFactor = (op == '-' || op == '−') && "*/×÷^".indexOf(previous) >= 0;
                if (!negativeFactor) {
                    while (!prefix.isEmpty() && operator(prefix.charAt(prefix.length() - 1)))
                        prefix = prefix.substring(0, prefix.length() - 1);
                    if (prefix.isEmpty() && op != '-' && op != '−') prefix = "0";
                }
            }
        } else if (key.equals("e") && !prefix.isEmpty() &&
                (digit(prefix.charAt(prefix.length() - 1)) || prefix.endsWith(")") || prefix.endsWith("%"))) {
            key = "×e"; // Prevent the constant key from becoming a scientific-notation suffix.
        }
        String result = prefix + key + suffix;
        if (result.length() > 2048) return new Edit(source, start);
        int cursor = prefix.length() + key.length();
        if (key.endsWith("()")) --cursor;
        return new Edit(result, cursor);
    }
}
