package org.axioma.calculator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.BaseAdapter;
import android.view.ViewGroup;
import java.util.List;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable preview = this::updatePreview;
    private EditText input;
    private TextView error;
    private String resultText = "";
    private FormulaView formulaView;
    private MathGlyphs glyphs;
    private ScrollView displayArea;
    private LinearLayout scientific;
    private ImageButton angleButton;
    private MathButton expandButton;
    private ExpressionEditor expressionEditor;
    private SettingsDialog settingsDialog;
    private CalculatorStyle style;
    private CalculationHistory history;
    private CalculatorSettings settings;
    private CalculationResult last;
    private EvaluationException evaluationIssue;
    private boolean degrees, completed, landscape, expanded, exactResults;
    private String latex = "";
    private int ink, muted, surface, background, errorColor;
    private boolean finalResult;

    @Override public void onCreate(Bundle state) {
        setTheme(R.style.Theme_Axioma);
        super.onCreate(state);
        LaunchScreen.install(this, state);
        Configuration configuration = getResources().getConfiguration();
        boolean dark = (configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE;
        style = new CalculatorStyle(dark);
        background = style.background; surface = style.panel;
        ink = style.ink; muted = style.muted;
        errorColor = Color.parseColor(dark ? "#FFB4AB" : "#A82020");
        history = new CalculationHistory(this);
        degrees = state != null ? state.getBoolean("degrees")
                : getPreferences(MODE_PRIVATE).getBoolean("degrees", false);
        settings = new CalculatorSettings(this);
        exactResults = settings.exactResults();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        LinearLayout root = column();
        root.setBackgroundColor(background);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();
        root.addView(toolbar(), new LinearLayout.LayoutParams(-1, dp(64)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(6), dp(18), dp(14));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout panel = calculationPanel();
        LinearLayout.LayoutParams panelParams = landscape ? new LinearLayout.LayoutParams(0, -1, 1)
                : new LinearLayout.LayoutParams(-1, 0, 1);
        if (landscape) panelParams.rightMargin = dp(18); else panelParams.bottomMargin = dp(14);
        body.addView(panel, panelParams);

        LinearLayout keys = column();
        body.addView(keys, landscape ? new LinearLayout.LayoutParams(0, -1, 1)
                : new LinearLayout.LayoutParams(-1, -2));
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        expandButton = smallButton("f(x)");
        expandButton.setContentDescription(getString(R.string.show_scientific));
        controls.addView(expandButton, new LinearLayout.LayoutParams(dp(72), dp(44)));
        controls.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        ImageButton keyboardButton = smallIconButton(R.drawable.ic_keyboard, R.string.open_keyboard);
        keyboardButton.setId(R.id.open_keyboard);
        keyboardButton.setOnClickListener(view -> editExpression());
        LinearLayout.LayoutParams keyboardParams = new LinearLayout.LayoutParams(dp(72), dp(44));
        keyboardParams.rightMargin = dp(8); controls.addView(keyboardButton, keyboardParams);
        angleButton = smallIconButton(R.drawable.ic_unit_radians, R.string.angle_mode);
        angleButton.setId(R.id.angle_mode_button);
        updateAngleIcon();
        angleButton.setOnClickListener(view -> toggleAngles());
        controls.addView(angleButton, new LinearLayout.LayoutParams(dp(72), dp(44)));
        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(-1, dp(44));
        controlsParams.bottomMargin = dp(12);
        keys.addView(controls, controlsParams);
        scientific = scientificPanel();
        scientific.setVisibility(View.GONE);
        keys.addView(scientific, new LinearLayout.LayoutParams(-1, -2));
        expandButton.setOnClickListener(view -> {
            if (landscape) { scientificMenu(view); return; }
            expanded = !expanded;
            scientific.setVisibility(expanded ? View.VISIBLE : View.GONE);
            expandButton.setContentDescription(getString(expanded ? R.string.hide_scientific : R.string.show_scientific));
        });
        CalculatorKeypad keypad = new CalculatorKeypad(this, glyphs, dark, this::key);
        if (!landscape) keypad.reserveDisplayHeight(dp(220));
        keys.addView(keypad, landscape ? new LinearLayout.LayoutParams(-1, 0, 1)
                : new LinearLayout.LayoutParams(-1, -2));

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                completed = false; last = null; evaluationIssue = null; latex = "";
                resultText = ""; error.setText("");
                error.setVisibility(View.GONE);
                handler.removeCallbacks(preview); handler.postDelayed(preview, 90);
            }
            @Override public void afterTextChanged(Editable text) {}
        });
        input.setText(state == null ? "" : state.getString("expression", ""));
        input.setSelection(input.length());
        if (state != null && state.getBoolean("completed")) {
            handler.removeCallbacks(preview);
            calculate(false);
        }
        if (state != null && state.getBundle("source_editor_state") != null) {
            editExpression(); expressionEditor.onRestoreInstanceState(state.getBundle("source_editor_state"));
        }
        if (state != null && state.getBoolean("settings_open")) showSettings();
    }

    private LinearLayout toolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(22), 0, dp(12), 0);
        TextView wordmark = new TextView(this);
        wordmark.setText(R.string.app_name); wordmark.setTextColor(ink); wordmark.setTextSize(27);
        wordmark.setTypeface(Typeface.create("serif", Typeface.NORMAL)); wordmark.setLetterSpacing(.025f);
        bar.addView(wordmark, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton historyButton = icon(R.drawable.ic_history, R.string.history);
        historyButton.setOnClickListener(view -> showHistory());
        bar.addView(historyButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        ImageButton more = icon(R.drawable.ic_more, R.string.more_options);
        more.setOnClickListener(this::showOptions);
        bar.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return bar;
    }

    private LinearLayout calculationPanel() {
        LinearLayout panel = column();
        panel.setBackground(style.surface(style.inset, style.border, dp(20), dp(1)));
        panel.setClipToOutline(true);
        panel.addView(display(), new LinearLayout.LayoutParams(-1, 0, 1));
        return panel;
    }

    private ScrollView display() {
        ScrollView scroll = new ScrollView(this);
        displayArea = scroll;
        scroll.setFillViewport(true);
        LinearLayout content = column();
        content.setGravity(Gravity.BOTTOM);
        content.setPadding(dp(20), dp(4), dp(20), dp(18));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        input = new EditText(this);
        // The source editor holds selection state; only its typeset view is displayed.
        input.setSingleLine(true); input.setShowSoftInputOnFocus(false);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2048)});
        formulaView = new FormulaView(this, ink);
        glyphs = new MathGlyphs(formulaView);
        formulaView.setId(R.id.formula_display);
        formulaView.setOnRenderListener(success -> revealResult());
        formulaView.setOnEditListener(this::editExpression);
        content.addView(formulaView, new LinearLayout.LayoutParams(-1, dp(56)));
        error = new TextView(this);
        error.setTextColor(errorColor); error.setTextSize(14); error.setGravity(Gravity.END);
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        error.setVisibility(View.GONE);
        content.addView(error, new LinearLayout.LayoutParams(-1, -2));
        return scroll;
    }

    private void key(String key) {
        if (key.equals("=")) { calculate(); return; }
        String source = input.getText().toString();
        int start = input.getSelectionStart(), end = input.getSelectionEnd();
        if (start < 0) start = end = source.length();
        if (completed && last != null && last.continuation.isEmpty()
                && (key.equals("%") || (key.length() == 1 && "+-−*/×÷^".contains(key)))) {
            Toast.makeText(this, R.string.continuation_limit, Toast.LENGTH_SHORT).show();
            return;
        }
        CalculatorInput.Edit edit = CalculatorInput.edit(source, start, end, key, completed,
                last == null ? null : last.continuation);
        if (!edit.text.equals(source)) input.setText(edit.text);
        input.setSelection(edit.cursor);
    }

    private void updatePreview() {
        handler.removeCallbacks(preview);
        String source = input.getText().toString();
        if (source.isEmpty()) { last = null; evaluationIssue = null; latex = ""; resultText = ""; renderCurrent(); return; }
        try {
            Formula formula = NativeCalculator.parse(source);
            latex = formula.latex;
            if (!completed) {
                evaluationIssue = null;
                if (isNumberLiteral(formula.root)) {
                    last = null; resultText = ""; finalResult = false;
                } else try {
                    last = NativeCalculator.calculateMode(source, degrees, exactResults);
                    showResult(last.text, false);
                } catch (EvaluationException issue) {
                    showIssue(issue, false);
                } catch (IllegalArgumentException ignored) { last = null; resultText = ""; }
            }
        } catch (IllegalArgumentException ignored) {
            // An unfinished expression is expected while typing. Show errors only on equals.
            last = null; evaluationIssue = null; latex = ""; resultText = "";
        }
        renderCurrent();
    }

    private static boolean isNumberLiteral(MathNode node) {
        while (node.kind == MathNode.GROUP || node.kind == MathNode.NEGATE) node = node.left;
        return node.kind == MathNode.NUMBER;
    }

    private void calculate() {
        calculate(true);
    }
    private void calculate(boolean record) {
        handler.removeCallbacks(preview);
        String source = input.getText().toString();
        if (source.trim().isEmpty()) return;
        try {
            Formula formula = NativeCalculator.parse(source);
            latex = formula.latex;
            evaluationIssue = null;
            last = NativeCalculator.calculateMode(source, degrees, exactResults);
            completed = true; error.setText("");
            error.setVisibility(View.GONE);
            showResult(last.text, true);
            if (record) history.record(source, last, degrees);
        } catch (EvaluationException issue) {
            completed = true; showIssue(issue, true);
        } catch (IllegalArgumentException exception) {
            completed = false; last = null; evaluationIssue = null; resultText = ""; error.setText(exception.getMessage());
            error.setVisibility(View.VISIBLE);
            renderCurrent(); revealResult();
        }
    }

    private void showIssue(EvaluationException issue, boolean finalResult) {
        last = null; evaluationIssue = issue;
        error.setText(""); error.setVisibility(View.GONE);
        showResult(issue.label(), finalResult);
    }

    private void showResult(String value, boolean finalResult) {
        this.finalResult = finalResult;
        resultText = value; renderCurrent(); revealResult();
    }
    private void renderCurrent() {
        String source = input.getText().toString();
        formulaView.setCalculation(latex.isEmpty() ? MathText.unfinished(source) : latex, source,
                evaluationIssue != null ? "\\text{" + evaluationIssue.label() + "}"
                        : last != null && !resultText.isEmpty() ? last.latex : "",
                resultText, finalResult, evaluationIssue != null, last != null && last.exactDisplay);
    }
    private void revealResult() {
        displayArea.post(() -> {
            if (displayArea.getChildCount() > 0)
                displayArea.scrollTo(0, displayArea.getChildAt(0).getHeight());
        });
    }

    private void toggleAngles() {
        degrees = !degrees; completed = false;
        updateAngleIcon();
        error.setText(""); error.setVisibility(View.GONE); updatePreview();
        getPreferences(MODE_PRIVATE).edit().putBoolean("degrees", degrees).apply();
    }

    private void updateAngleIcon() {
        angleButton.setImageResource(degrees ? R.drawable.ic_unit_degrees : R.drawable.ic_unit_radians);
        String description = getString(degrees ? R.string.angle_degrees : R.string.angle_radians);
        angleButton.setContentDescription(description);
        angleButton.setTooltipText(description);
    }

    private static final String[] SCIENCE_LABELS = {"sin", "cos", "tan", "√", "xʸ", "ln", "log", "exp", "π", "e"};
    private static final String[] SCIENCE_KEYS = {"sin()", "cos()", "tan()", "sqrt()", "^", "ln()", "log()", "exp()", "π", "e"};
    private static final String[] SCIENCE_TEX = {"\\sin", "\\cos", "\\tan", "\\sqrt{\\phantom{x}}", "x^y",
            "\\ln", "\\log", "\\exp", "\\pi", "e"};
    private LinearLayout scientificPanel() {
        LinearLayout panel = column();
        for (int row = 0; row < 2; ++row) {
            LinearLayout line = new LinearLayout(this);
            for (int col = 0; col < 5; ++col) {
                final int index = row * 5 + col;
                MathButton button = smallButton(SCIENCE_TEX[index]);
                button.setContentDescription(getString(R.string.insert_function, SCIENCE_LABELS[index]));
                button.setOnClickListener(view -> key(SCIENCE_KEYS[index]));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1);
                params.setMargins(dp(2), 0, dp(2), dp(8));
                line.addView(button, params);
            }
            panel.addView(line);
        }
        return panel;
    }
    private void scientificMenu(View anchor) {
        String[] labels = new String[SCIENCE_TEX.length + 1], descriptions = new String[labels.length];
        System.arraycopy(SCIENCE_TEX, 0, labels, 0, SCIENCE_TEX.length);
        System.arraycopy(SCIENCE_LABELS, 0, descriptions, 0, SCIENCE_LABELS.length);
        labels[labels.length - 1] = degrees ? "\\mathrm{DEG}\\to\\mathrm{RAD}" : "\\mathrm{RAD}\\to\\mathrm{DEG}";
        descriptions[descriptions.length - 1] = getString(R.string.angle_mode);
        new AlertDialog.Builder(this).setTitle(R.string.functions)
                .setAdapter(mathRows(labels, descriptions, 56), (dialog, position) -> {
                    if (position == SCIENCE_TEX.length) toggleAngles(); else key(SCIENCE_KEYS[position]);
                }).setPositiveButton(R.string.close, null).show();
    }
    private BaseAdapter mathRows(String[] labels, String[] descriptions, int height) {
        return new BaseAdapter() {
            @Override public int getCount() { return labels.length; }
            @Override public Object getItem(int position) { return labels[position]; }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                MathButton row = new MathButton(MainActivity.this, glyphs, labels[position], descriptions[position], ink);
                row.setMathSize(20); row.setClickable(false); row.setFocusable(false);
                row.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1, dp(height)));
                return row;
            }
        };
    }

    private PopupMenu showOptions(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 0, 0, R.string.edit_expression);
        menu.getMenu().add(0, 1, 1, R.string.copy_expression).setEnabled(input.length() > 0);
        menu.getMenu().add(0, 2, 2, R.string.copy_latex).setEnabled(!latex.isEmpty());
        menu.getMenu().add(0, 3, 3, R.string.copy_precise).setEnabled(last != null && !last.precise.isEmpty());
        menu.getMenu().add(0, 4, 4, R.string.copy_exact).setEnabled(last != null && !last.exact.isEmpty());
        menu.getMenu().add(0, 5, 5, R.string.settings).setIcon(R.drawable.ic_settings).getIcon().setTint(muted);
        if (android.os.Build.VERSION.SDK_INT >= 29) menu.setForceShowIcon(true);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0: editExpression(); break;
                case 1: copy(input.getText().toString()); break;
                case 2: copy(latex); break;
                case 3: if (last != null) copy(last.precise); break;
                case 4: if (last != null) copy(last.exact); break;
                case 5: showSettings(); break;
                default: return false;
            }
            return true;
        });
        menu.show(); return menu;
    }
    private SettingsDialog showSettings() {
        if (settingsDialog != null && settingsDialog.isShowing()) return settingsDialog;
        SettingsDialog dialog = new SettingsDialog(this, style, exactResults, exact -> {
            if (exactResults == exact) return;
            exactResults = exact;
            settings.setExactResults(exact);
            handler.removeCallbacks(preview);
            if (completed) calculate(false); else updatePreview();
        });
        settingsDialog = dialog;
        dialog.setOnDismissListener(value -> { if (settingsDialog == dialog) settingsDialog = null; });
        dialog.show(); return dialog;
    }
    private void editExpression() {
        if (expressionEditor != null && expressionEditor.isShowing()) return;
        ExpressionEditor dialog = new ExpressionEditor(this, style, input.getText().toString(),
                input.getSelectionStart(), input.getSelectionEnd(), (source, start, end) -> {
                    input.setText(source); input.setSelection(start, end);
        });
        expressionEditor = dialog;
        dialog.setOnDismissListener(value -> { if (expressionEditor == dialog) expressionEditor = null; });
        dialog.show();
    }
    private void copy(String value) {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Axioma", value));
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        }
    }
    private AlertDialog showHistory() {
        List<CalculationHistory.Entry> entries = history.entries();
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle(R.string.history)
                .setPositiveButton(R.string.close, null);
        if (entries.isEmpty()) dialog.setMessage(R.string.history_empty);
        else {
            String[] labels = new String[entries.size()], descriptions = new String[labels.length];
            for (int i = 0; i < labels.length; ++i) {
                CalculationHistory.Entry entry = entries.get(i);
                String expression;
                try { expression = NativeCalculator.parse(entry.expression).latex; }
                catch (IllegalArgumentException ignored) { expression = MathText.unfinished(entry.expression); }
                String relation = entry.result.exactDisplay ? "=" : "\\approx ";
                // A large exact value may already include an approximation marker.
                String answer = entry.result.latex.replaceFirst("^\\\\approx\\s+", "");
                labels[i] = "\\begin{gathered}" + expression + "\\\\" + relation + answer + "\\end{gathered}";
                descriptions[i] = entry.expression + (entry.result.exactDisplay ? " = " : " approximately ") + entry.result.text;
            }
            dialog.setAdapter(mathRows(labels, descriptions, 96), (value, position) -> {
                CalculationHistory.Entry entry = entries.get(position);
                degrees = entry.degrees; updateAngleIcon();
                getPreferences(MODE_PRIVATE).edit().putBoolean("degrees", degrees).apply();
                input.setText(entry.expression); input.setSelection(input.length());
                handler.removeCallbacks(preview); calculate(false);
            });
            dialog.setNegativeButton(R.string.clear_history, (value, which) -> history.clear());
        }
        return dialog.show();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("expression", input.getText().toString()); state.putBoolean("degrees", degrees);
        state.putBoolean("completed", completed);
        state.putBoolean("settings_open", settingsDialog != null && settingsDialog.isShowing());
        if (expressionEditor != null && expressionEditor.isShowing())
            state.putBundle("source_editor_state", expressionEditor.onSaveInstanceState());
    }
    @Override protected void onDestroy() {
        if (expressionEditor != null) expressionEditor.dismiss();
        if (settingsDialog != null) settingsDialog.dismiss();
        handler.removeCallbacks(preview); formulaView.release(); super.onDestroy();
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout;
    }
    private ImageButton icon(int drawable, int description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(drawable); button.setImageTintList(ColorStateList.valueOf(muted));
        button.setContentDescription(getString(description)); button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setBackground(style.control(Color.TRANSPARENT, style.border, dp(12), 0));
        button.setStateListAnimator(null);
        return button;
    }
    private ImageButton smallIconButton(int drawable, int description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(drawable); button.setImageTintList(ColorStateList.valueOf(ink));
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(6), dp(6), dp(6), dp(6));
        button.setContentDescription(getString(description)); button.setTooltipText(getString(description));
        button.setBackground(style.control(surface, style.border, dp(10), dp(1)));
        button.setStateListAnimator(null); return button;
    }
    private MathButton smallButton(String value) {
        MathButton button = new MathButton(this, glyphs, value, value, ink);
        button.setMathSize(17);
        button.setBackground(style.control(surface, style.border, dp(10), dp(1)));
        button.setStateListAnimator(null); return button;
    }
}
