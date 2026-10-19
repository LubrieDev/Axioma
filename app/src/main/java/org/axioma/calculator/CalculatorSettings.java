package org.axioma.calculator;

import android.content.Context;
import android.content.SharedPreferences;

/** Persistent on-device calculator preferences, independent of activity state. */
final class CalculatorSettings {
    private final SharedPreferences preferences;
    CalculatorSettings(Context context) {
        preferences = context.getSharedPreferences("calculator_settings", Context.MODE_PRIVATE);
    }
    boolean exactResults() { return preferences.getBoolean("exact_results", true); }
    void setExactResults(boolean exact) { preferences.edit().putBoolean("exact_results", exact).apply(); }
}
