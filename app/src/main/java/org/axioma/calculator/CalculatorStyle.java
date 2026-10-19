package org.axioma.calculator;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;

/** Axioma's shared palette and quiet, bordered surfaces. Dimensions are pixels. */
final class CalculatorStyle {
    static final int ACCENT = Color.rgb(114, 100, 95); // #72645F
    static final int CHARCOAL = Color.rgb(28, 28, 31); // #1C1C1F
    final int background, panel, inset, border, ink, muted, operator, operatorInk;

    CalculatorStyle(boolean dark) {
        background = dark ? CHARCOAL : Color.rgb(242, 240, 237);
        panel = Color.parseColor(dark ? "#28282D" : "#FAF8F5");
        inset = Color.parseColor(dark ? "#222225" : "#E9E6E2");
        border = Color.parseColor(dark ? "#3C3B40" : "#D3CEC8");
        ink = Color.parseColor(dark ? "#F1ECE7" : "#1C1C1F");
        muted = Color.parseColor(dark ? "#B8ADA7" : "#72645F");
        operator = Color.parseColor(dark ? "#38322F" : "#E3DAD4");
        operatorInk = Color.parseColor(dark ? "#E4D3C8" : "#574A44");
    }

    GradientDrawable surface(int fill, int outline, float radius, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill); shape.setCornerRadius(radius);
        if (stroke > 0) shape.setStroke(stroke, outline);
        return shape;
    }

    RippleDrawable control(int fill, int outline, float radius, int stroke) {
        return new RippleDrawable(ColorStateList.valueOf(0x2272645F),
                surface(fill, outline, radius, stroke), surface(Color.WHITE, 0, radius, 0));
    }
}
