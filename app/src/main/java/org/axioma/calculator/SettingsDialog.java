package org.axioma.calculator;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

/** Local calculator preferences. Mathematical result selection stays in C++. */
final class SettingsDialog extends Dialog {
    interface Listener { void onResultFormat(boolean exact); }
    private final CalculatorStyle style;
    private final boolean exact;
    private final Listener listener;

    SettingsDialog(Context context, CalculatorStyle style, boolean exact, Listener listener) {
        super(context); this.style = style; this.exact = exact; this.listener = listener;
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = new LinearLayout(getContext()); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(22), dp(24), dp(16));
        content.setBackground(style.surface(style.panel, style.border, dp(20), dp(1)));
        TextView title = label(R.string.settings, 24, style.ink); content.addView(title);
        TextView section = label(R.string.result_format, 17, style.ink);
        section.setTypeface(null, Typeface.BOLD); section.setPadding(0, dp(24), 0, dp(8)); content.addView(section);
        RadioGroup choices = new RadioGroup(getContext()); choices.setId(R.id.result_format_group);
        choices.setOrientation(RadioGroup.VERTICAL);
        choices.addView(choice(R.id.result_numerical, R.string.result_numerical));
        choices.addView(choice(R.id.result_exact, R.string.result_exact));
        choices.check(exact ? R.id.result_exact : R.id.result_numerical);
        choices.setOnCheckedChangeListener((group, id) -> listener.onResultFormat(id == R.id.result_exact));
        content.addView(choices);
        TextView help = label(R.string.result_format_help, 14, style.muted);
        help.setPadding(0, dp(10), 0, dp(20)); content.addView(help);
        Button close = new Button(getContext()); close.setText(R.string.close); close.setTextColor(style.ink);
        close.setTextSize(16); close.setAllCaps(false); close.setStateListAnimator(null);
        close.setBackground(style.control(style.inset, style.border, dp(10), dp(1)));
        close.setOnClickListener(view -> dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(100), dp(48));
        closeParams.gravity = Gravity.END; content.addView(close, closeParams);
        ScrollView scroll = new ScrollView(getContext()); scroll.addView(content);
        setContentView(scroll);
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            int width = Math.min(dp(420), getContext().getResources().getDisplayMetrics().widthPixels - dp(32));
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }
    private RadioButton choice(int id, int text) {
        RadioButton button = new RadioButton(getContext()); button.setId(id); button.setText(text);
        button.setTextSize(17); button.setTextColor(style.ink); button.setMinHeight(dp(48));
        button.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{CalculatorStyle.ACCENT, style.muted}));
        return button;
    }
    private TextView label(int text, int size, int color) {
        TextView view = new TextView(getContext()); view.setText(text); view.setTextSize(size); view.setTextColor(color);
        return view;
    }
    private int dp(int value) { return Math.round(value * getContext().getResources().getDisplayMetrics().density); }
}
