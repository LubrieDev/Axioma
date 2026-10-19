package org.axioma.calculator;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Bounded on-device history. No network services or database libraries. */
public final class CalculationHistory {
    private static final int LIMIT = 50;
    private final SharedPreferences preferences;
    public static final class Entry {
        public final String expression;
        public final CalculationResult result;
        public final boolean degrees;
        Entry(String expression, CalculationResult result, boolean degrees) {
            this.expression = expression; this.result = result; this.degrees = degrees;
        }
    }
    public CalculationHistory(Context context) {
        preferences = context.getSharedPreferences("calculation_history", Context.MODE_PRIVATE);
    }
    public List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString("entries", "[]"));
            for (int i = 0; i < Math.min(array.length(), LIMIT); ++i) {
                JSONObject entry = array.getJSONObject(i);
                String precise = entry.optString("precise", ""), display = entry.optString("display", "");
                // Older records contain only decimals. Keep them readable; selecting
                // any entry reevaluates its original source through the current engine.
                CalculationResult result = new CalculationResult(entry.optString("exact", ""), precise, display,
                        entry.optString("continuation", precise), entry.optString("text", display),
                        entry.optString("latex", MathText.number(display)), entry.optBoolean("exactDisplay", false));
                entries.add(new Entry(entry.getString("expression"), result, entry.getBoolean("degrees")));
            }
        } catch (JSONException ignored) {
            // A malformed saved history must not prevent the calculator from opening.
        }
        return entries;
    }
    public void record(String expression, CalculationResult value, boolean degrees) {
        List<Entry> previous = entries();
        if (!previous.isEmpty()) {
            Entry last = previous.get(0);
            if (last.expression.equals(expression) && last.result.exact.equals(value.exact)
                    && last.result.precise.equals(value.precise) && last.degrees == degrees) return;
        }
        previous.add(0, new Entry(expression, value, degrees));
        JSONArray array = new JSONArray();
        try {
            for (int i = 0; i < Math.min(previous.size(), LIMIT); ++i) {
                Entry entry = previous.get(i);
                JSONObject object = new JSONObject();
                CalculationResult result = entry.result;
                object.put("expression", entry.expression); object.put("precise", result.precise);
                object.put("display", result.display); object.put("degrees", entry.degrees);
                object.put("exact", result.exact); object.put("continuation", result.continuation);
                object.put("text", result.text); object.put("latex", result.latex);
                object.put("exactDisplay", result.exactDisplay);
                array.put(object);
            }
            preferences.edit().putString("entries", array.toString()).apply();
        } catch (JSONException ignored) {
            // Calculations remain available even if a history record cannot be encoded.
        }
    }
    public void clear() { preferences.edit().remove("entries").apply(); }
}
