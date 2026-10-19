package org.axioma.calculator;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

/** Explicit source editor with an own literal keyboard; it never opens the system IME. */
final class ExpressionEditor extends Dialog {
    interface Listener { void apply(String source, int start, int end); }
    private final Activity activity;
    private final CalculatorStyle style;
    private final EditText editor;
    private final LinearLayout keys;
    private final int rowHeight;
    private boolean symbols, uppercase;

    ExpressionEditor(Activity activity, CalculatorStyle style, String source, int start, int end, Listener listener) {
        super(activity);
        this.activity = activity; this.style = style;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = getWindow();
        if (window != null) window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        rowHeight = dp(Math.max(28, Math.min(46,
                (activity.getResources().getConfiguration().screenHeightDp - 140) / 5 - 6)));
        LinearLayout content = column();
        content.setPadding(dp(10), dp(8), dp(10), dp(12));
        content.setBackgroundColor(style.background);
        LinearLayout header = row();
        TextView title = new TextView(activity);
        title.setText(R.string.expression); title.setTextSize(14); title.setTextColor(style.muted);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(36), 1));
        title.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton left = iconKey(R.drawable.ic_cursor_left, activity.getString(R.string.cursor_left));
        left.setBackground(style.control(android.graphics.Color.TRANSPARENT, style.border, dp(8), 0));
        header.addView(left, new LinearLayout.LayoutParams(dp(32), dp(32)));
        ImageButton right = iconKey(R.drawable.ic_cursor_right, activity.getString(R.string.cursor_right));
        right.setBackground(style.control(android.graphics.Color.TRANSPARENT, style.border, dp(8), 0));
        header.addView(right, new LinearLayout.LayoutParams(dp(32), dp(32)));
        Button cancel = textKey(activity.getString(R.string.cancel), activity.getString(R.string.cancel));
        cancel.setTextSize(13); cancel.setOnClickListener(view -> dismiss());
        cancel.setBackground(style.control(android.graphics.Color.TRANSPARENT, style.border, dp(8), 0));
        header.addView(cancel, new LinearLayout.LayoutParams(dp(56), dp(32)));
        Button apply = textKey(activity.getString(R.string.apply), activity.getString(R.string.apply));
        apply.setId(R.id.source_apply); apply.setTextSize(13);
        apply.setTextColor(android.graphics.Color.WHITE);
        apply.setBackground(style.control(CalculatorStyle.ACCENT, style.border, dp(8), 0));
        LinearLayout.LayoutParams applyParams = new LinearLayout.LayoutParams(dp(56), dp(32));
        applyParams.leftMargin = dp(6); header.addView(apply, applyParams);
        content.addView(header);

        editor = new EditText(activity);
        editor.setId(R.id.source_editor);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setShowSoftInputOnFocus(false);
        editor.setTextSize(18); editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextColor(style.ink); editor.setHintTextColor(style.muted);
        editor.setHint(R.string.source_hint); editor.setContentDescription(activity.getString(R.string.expression));
        editor.setMinLines(1); editor.setMaxLines(3); editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setPadding(dp(12), dp(10), dp(12), dp(10));
        editor.setBackground(style.surface(style.inset, style.border, dp(10), dp(1)));
        editor.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2048)});
        editor.setText(source);
        int first = Math.max(0, Math.min(start, source.length()));
        int last = Math.max(first, Math.min(end, source.length()));
        editor.setSelection(first, last);
        content.addView(editor, new LinearLayout.LayoutParams(-1, -2));
        left.setOnClickListener(view -> moveCursor(false));
        left.setOnLongClickListener(view -> { editor.setSelection(0); return true; });
        right.setOnClickListener(view -> moveCursor(true));
        right.setOnLongClickListener(view -> { editor.setSelection(editor.length()); return true; });
        apply.setOnClickListener(view -> {
            listener.apply(editor.getText().toString(), editor.getSelectionStart(), editor.getSelectionEnd());
            dismiss();
        });

        keys = column(); content.addView(keys);
        rebuild();
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false); scroll.addView(content);
        setContentView(scroll);
    }

    @Override public void show() {
        super.show();
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(style.background));
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(-1, -2);
            window.setDimAmount(.35f);
        }
        editor.requestFocus();
    }

    @Override public Bundle onSaveInstanceState() {
        Bundle state = super.onSaveInstanceState();
        state.putString("source", editor.getText().toString());
        state.putInt("start", editor.getSelectionStart()); state.putInt("end", editor.getSelectionEnd());
        state.putBoolean("symbols", symbols); state.putBoolean("uppercase", uppercase);
        return state;
    }
    @Override public void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        editor.setText(state.getString("source", ""));
        int start = Math.max(0, Math.min(state.getInt("start"), editor.length()));
        editor.setSelection(start, Math.max(start, Math.min(state.getInt("end"), editor.length())));
        symbols = state.getBoolean("symbols"); uppercase = state.getBoolean("uppercase"); rebuild();
    }

    private void rebuild() {
        keys.removeAllViews();
        addCharacters("1", "2", "3", "4", "5", "6", "7", "8", "9", "0");
        LinearLayout numbers = (LinearLayout) keys.getChildAt(0);
        for (int i = 0; i < numbers.getChildCount(); ++i) {
            ((TextView) numbers.getChildAt(i)).setTextSize(18);
            numbers.getChildAt(i).getLayoutParams().height = rowHeight - dp(6);
        }
        if (symbols) {
            addCharacters("\\", "{", "}", "[", "]", "(", ")", "|", "/", "=");
            addCharacters("%", "$", "&", "#", "_", "^", "~", "*", "+", "-");
            LinearLayout punctuation = row();
            for (String value : new String[]{";", ":", "<", ">", "!", "?", "'", "\""})
                addLiteral(punctuation, value, 1);
            addKey(punctuation, backspaceKey(), 2); keys.addView(punctuation);
        } else {
            addLetters("qwertyuiop");
            addLetters("asdfghjklñ");
            LinearLayout bottom = row();
            ImageButton shift = iconKey(R.drawable.ic_shift, activity.getString(uppercase
                    ? R.string.keyboard_lowercase : R.string.keyboard_uppercase));
            shift.setSelected(uppercase);
            shift.setImageTintList(ColorStateList.valueOf(uppercase ? android.graphics.Color.WHITE : style.ink));
            shift.setBackground(style.control(uppercase ? CalculatorStyle.ACCENT : style.operator,
                    style.border, dp(8), 0));
            shift.setOnClickListener(view -> { uppercase = !uppercase; rebuild(); });
            addKey(bottom, shift, 1.5f);
            for (char letter : "zxcvbnm".toCharArray()) addLiteral(bottom, letter(letter), 1);
            addKey(bottom, backspaceKey(), 1.5f);
            keys.addView(bottom);
        }
        LinearLayout actions = row();
        Button mode = textKey(symbols ? "ABC" : "\\{}[]", activity.getString(symbols
                ? R.string.keyboard_letters : R.string.keyboard_symbols));
        mode.setTextSize(13);
        mode.setBackground(style.control(style.operator, style.border, dp(10), 0));
        mode.setOnClickListener(view -> { symbols = !symbols; rebuild(); });
        addKey(actions, mode, 1.5f);
        addLiteral(actions, ",", 1);
        Button space = textKey(activity.getString(R.string.keyboard_space), activity.getString(R.string.insert_space));
        space.setTextSize(14); space.setOnClickListener(view -> insert(" ")); addKey(actions, space, 5);
        addLiteral(actions, ".", 1);
        ImageButton enter = iconKey(R.drawable.ic_enter, activity.getString(R.string.insert_line_break));
        enter.setImageTintList(ColorStateList.valueOf(android.graphics.Color.WHITE));
        enter.setBackground(style.control(CalculatorStyle.ACCENT, style.border, dp(12), 0));
        enter.setOnClickListener(view -> insert("\n")); addKey(actions, enter, 1.5f);
        keys.addView(actions);
    }
    private void addCharacters(String... characters) {
        LinearLayout line = row();
        for (String character : characters) addLiteral(line, character, 1);
        keys.addView(line);
    }
    private void addLetters(String letters) {
        LinearLayout line = row();
        for (char character : letters.toCharArray()) addLiteral(line, letter(character), 1);
        keys.addView(line);
    }
    private String letter(char letter) {
        String value = String.valueOf(letter);
        return uppercase ? value.toUpperCase(Locale.ROOT) : value;
    }
    private void addLiteral(LinearLayout line, String value, float weight) {
        Button button = textKey(value, activity.getString(R.string.insert_literal, value));
        button.setOnClickListener(view -> insert(value)); addKey(line, button, weight);
    }
    private void insert(String value) {
        editor.requestFocus();
        Editable text = editor.getText();
        int start = Math.max(0, editor.getSelectionStart()), end = Math.max(start, editor.getSelectionEnd());
        text.replace(start, end, value);
        editor.setSelection(Math.min(start + value.length(), text.length()));
    }
    private void delete() {
        int start = Math.max(0, editor.getSelectionStart()), end = Math.max(start, editor.getSelectionEnd());
        if (start == end && start > 0) start = Character.offsetByCodePoints(editor.getText(), start, -1);
        editor.getText().delete(start, end); editor.setSelection(start); editor.requestFocus();
    }
    private void moveCursor(boolean forward) {
        int start = Math.max(0, editor.getSelectionStart()), end = Math.max(start, editor.getSelectionEnd());
        int position = forward ? end : start;
        if (start == end && (forward ? position < editor.length() : position > 0))
            position = Character.offsetByCodePoints(editor.getText(), position, forward ? 1 : -1);
        editor.setSelection(position); editor.requestFocus();
    }
    private Button textKey(String label, String description) {
        Button button = new Button(activity);
        button.setText(label); button.setAllCaps(false); button.setTextSize(21);
        button.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        button.setTextColor(style.ink); button.setContentDescription(description);
        button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(0); button.setMinimumHeight(0);
        button.setPadding(0, 0, 0, 0); button.setStateListAnimator(null);
        button.setBackground(style.control(style.panel, style.border, dp(8), 0));
        return button;
    }
    private ImageButton iconKey(int resource, String description) {
        ImageButton button = new ImageButton(activity);
        button.setImageResource(resource); button.setImageTintList(ColorStateList.valueOf(style.ink));
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE); button.setPadding(dp(5), dp(5), dp(5), dp(5));
        button.setContentDescription(description); button.setStateListAnimator(null);
        button.setBackground(style.control(style.operator, style.border, dp(8), 0));
        return button;
    }
    private ImageButton backspaceKey() {
        ImageButton button = iconKey(R.drawable.ic_backspace, activity.getString(R.string.backspace));
        button.setOnClickListener(view -> delete()); return button;
    }
    private void addKey(LinearLayout row, View key, float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, rowHeight, weight);
        params.setMargins(dp(2), dp(3), dp(2), dp(3)); row.addView(key, params);
    }
    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(activity); layout.setOrientation(LinearLayout.VERTICAL); return layout;
    }
    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(activity); layout.setGravity(Gravity.CENTER_VERTICAL); return layout;
    }
    private int dp(float value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}
