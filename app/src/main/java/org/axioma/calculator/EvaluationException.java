package org.axioma.calculator;

/** A native mathematical diagnostic; syntax and resource errors remain separate. */
public final class EvaluationException extends IllegalArgumentException {
    public enum Status { UNDEFINED, INDETERMINATE, NOT_REAL, OUT_OF_RANGE, UNSUPPORTED }
    public final Status status;

    // JNI passes stable status names, never localized messages to be parsed.
    public EvaluationException(String code, String detail) {
        super(detail);
        status = Status.valueOf(code);
    }

    public String label() {
        switch (status) {
            case UNDEFINED: return "Undefined";
            case INDETERMINATE: return "Indeterminate";
            case NOT_REAL: return "Not real";
            case OUT_OF_RANGE: return "Out of range";
            case UNSUPPORTED: return "Not supported";
            default: throw new AssertionError(status);
        }
    }
}
