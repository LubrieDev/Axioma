package org.axioma.calculator;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

/** Spaced rectangular keys with warm operators and a single accent action. */
@SuppressLint("ViewConstructor") // This programmatic keypad requires the shared glyph cache.
public final class CalculatorKeypad extends ViewGroup {
    public interface Listener { void onKey(String key); }
    private final float density;
    private final int gap;
    private int keyWidth, keyHeight;
    private int displayReserve;
    private static final String[] KEYS = {
            "AC", "()", "%", "÷", "7", "8", "9", "×", "4", "5", "6", "−", "1", "2", "3", "+", "0", ".", "backspace", "="
    };
    private static final String[] TEX = {
            "", "(\\,)", "\\%", "\\div", "7", "8", "9", "\\times",
            "4", "5", "6", "-", "1", "2", "3", "+", "0", ".", "", "="
    };
    public CalculatorKeypad(Context context, MathGlyphs glyphs, boolean dark, Listener listener) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        gap = Math.round(8 * density);
        CalculatorStyle style = new CalculatorStyle(dark);
        for (int i = 0; i < KEYS.length; ++i) {
            final String key = KEYS[i];
            boolean equals = key.equals("=");
            boolean operation = i % 4 == 3;
            int fill = equals ? CalculatorStyle.ACCENT : operation ? style.operator
                    : i < 3 ? style.inset : style.panel;
            int ink = equals ? Color.WHITE : operation ? style.operatorInk : style.ink;
            int description = key.equals("AC") ? R.string.clear_all : key.equals("()") ? R.string.parentheses
                    : key.equals("%") ? R.string.percent : key.equals("÷") ? R.string.divide
                    : key.equals("×") ? R.string.multiply : key.equals("−") ? R.string.subtract
                    : key.equals("+") ? R.string.add : key.equals("backspace") ? R.string.backspace
                    : equals ? R.string.calculate : key.equals(".") ? R.string.decimal_point : 0;
            View button;
            if (key.equals("backspace") || key.equals("AC")) {
                ImageButton icon = new ImageButton(context);
                icon.setImageResource(key.equals("AC") ? R.drawable.ic_clear_all : R.drawable.ic_backspace);
                icon.setImageTintList(ColorStateList.valueOf(ink));
                icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                button = icon;
            } else {
                button = new MathButton(context, glyphs, TEX[i],
                        description == 0 ? key : context.getString(description), ink);
            }
            button.setContentDescription(description == 0 ? key : context.getString(description));
            button.setPadding(0, 0, 0, 0);
            button.setBackground(style.control(fill, equals ? CalculatorStyle.ACCENT : style.border,
                    14 * density, Math.max(1, Math.round(density))));
            button.setStateListAnimator(null);
            button.setOnClickListener(view -> listener.onKey(key));
            addView(button);
        }
    }
    void reserveDisplayHeight(int pixels) { displayReserve = pixels; requestLayout(); }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int maximum = MeasureSpec.getSize(heightSpec);
        keyWidth = Math.max(1, (width - gap * 3) / 4);
        int preferred = Math.min(Math.round(68 * density), Math.round(keyWidth * .84f));
        int vertical = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED ? preferred
                : Math.max(1, (maximum - displayReserve - gap * 4) / 5);
        keyHeight = Math.min(preferred, vertical);
        setMeasuredDimension(resolveSize(width, widthSpec), resolveSize(keyHeight * 5 + gap * 4, heightSpec));
        int childWidth = MeasureSpec.makeMeasureSpec(keyWidth, MeasureSpec.EXACTLY);
        int childHeight = MeasureSpec.makeMeasureSpec(keyHeight, MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); ++i) {
            View child = getChildAt(i);
            float fraction = i == 0 || i == 1 ? .33f : .43f;
            if (child instanceof MathButton) {
                ((MathButton) child).setMathSize(Math.min(32,
                        keyHeight * fraction / getResources().getDisplayMetrics().scaledDensity));
            }
            child.measure(childWidth, childHeight);
        }
    }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int start = (getWidth() - (keyWidth * 4 + gap * 3)) / 2;
        for (int i = 0; i < getChildCount(); ++i) {
            int x = start + (i % 4) * (keyWidth + gap), y = (i / 4) * (keyHeight + gap);
            getChildAt(i).layout(x, y, x + keyWidth, y + keyHeight);
        }
    }
}
