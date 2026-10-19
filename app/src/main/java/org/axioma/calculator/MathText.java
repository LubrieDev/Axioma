package org.axioma.calculator;

/** Presentation of unfinished input and already-formatted numbers; no evaluation. */
final class MathText {
    private MathText() {}
    static String number(String value) {
        if (value.isEmpty()) return "";
        int exponent = value.indexOf('e');
        return exponent < 0 ? value : value.substring(0, exponent) + "\\times10^{" + value.substring(exponent + 1) + "}";
    }
    static String unfinished(String source) {
        if (source.isEmpty()) return "0";
        StringBuilder tex = new StringBuilder();
        for (int i = 0; i < source.length();) {
            if (tex.length() >= 12000) { tex.append("\\ldots"); break; }
            char c = source.charAt(i++);
            if (Character.isLetter(c) && c < 128) {
                int begin = i - 1;
                while (i < source.length() && Character.isLetter(source.charAt(i)) && source.charAt(i) < 128) ++i;
                String name = source.substring(begin, i);
                switch (name) {
                    case "sin": case "cos": case "tan": case "ln": case "log": case "exp":
                        tex.append('\\').append(name).append(' '); break;
                    case "pi": tex.append("\\pi "); break;
                    case "sqrt": tex.append("\\operatorname{sqrt}"); break;
                    default: tex.append(name);
                }
                continue;
            }
            switch (c) {
                case 'π': tex.append("\\pi "); break;
                case '√': tex.append("\\sqrt{\\phantom{x}}"); break;
                case '*': case '×': case '·': tex.append("\\times "); break;
                case '/': case '÷': tex.append("\\div "); break;
                case '−': tex.append('-'); break;
                case '^': tex.append("^{\\square}"); break;
                case '%': case '$': case '#': case '&': case '_': tex.append('\\').append(c); break;
                case '\\': tex.append("\\backslash "); break;
                case '{': tex.append("\\lbrace "); break;
                case '}': tex.append("\\rbrace "); break;
                case '~': tex.append("\\sim "); break;
                case '(': tex.append("\\mathopen{(}"); break;
                case ')': tex.append("\\mathclose{)}"); break;
                case ' ': case '\n': case '\r': case '\t': tex.append("\\,"); break;
                default: tex.append(c);
            }
        }
        return tex.toString();
    }
}
