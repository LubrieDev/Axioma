package org.axioma.calculator;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Matrix;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** Native touch target with a MathJax-generated label rather than platform text. */
@SuppressLint("ViewConstructor") // Programmatic controls share one renderer-owned glyph cache.
final class MathButton extends FrameLayout {
    private final MathGlyphs glyphs;
    private final ImageView image;
    private MathGlyphs.Glyph glyph;
    private String latex = "";
    private float size = 26;
    MathButton(Context context, MathGlyphs glyphs, String tex, String description, int ink) {
        super(context);
        this.glyphs = glyphs;
        setClickable(true); setFocusable(true);
        setContentDescription(description);
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.MATRIX);
        image.setImageTintList(ColorStateList.valueOf(ink));
        image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(image, new LayoutParams(-1, -1));
        setLatex(tex);
    }
    void setLatex(String tex) {
        latex = tex;
        glyphs.request(tex, value -> {
            if (!latex.equals(tex)) return;
            glyph = value;
            image.setImageBitmap(value.bitmap);
            updateMatrix();
        });
    }
    void setMathSize(float sp) { size = sp; updateMatrix(); }
    String latex() { return latex; }
    boolean hasGlyph() { return glyph != null; }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom); updateMatrix();
    }
    private void updateMatrix() {
        if (glyph == null || image.getWidth() == 0 || image.getHeight() == 0) return;
        float inset = 6 * getResources().getDisplayMetrics().density;
        float width = glyph.bitmap.getWidth(), height = glyph.bitmap.getHeight();
        float nominal = size * getResources().getDisplayMetrics().scaledDensity / glyph.em;
        float scale = Math.max(0, Math.min(nominal,
                Math.min((image.getWidth() - 2 * inset) / width, (image.getHeight() - 2 * inset) / height)));
        Matrix matrix = new Matrix(); matrix.setScale(scale, scale);
        matrix.postTranslate((image.getWidth() - width * scale) / 2, (image.getHeight() - height * scale) / 2);
        image.setImageMatrix(matrix);
    }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info); info.setClassName("android.widget.Button");
    }
}
