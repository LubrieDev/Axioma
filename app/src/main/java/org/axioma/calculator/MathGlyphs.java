package org.axioma.calculator;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** One MathJax instance supplies cached, device-resolution labels to native controls. */
final class MathGlyphs {
    static final class Glyph {
        final Bitmap bitmap;
        final float em;
        Glyph(Bitmap bitmap, float em) { this.bitmap = bitmap; this.em = em; }
    }
    private final FormulaView renderer;
    private final Map<String, Glyph> cache = new HashMap<>();
    private final Map<String, List<Consumer<Glyph>>> waiting = new HashMap<>();
    MathGlyphs(FormulaView renderer) {
        this.renderer = renderer;
        renderer.setGlyphListener((tex, bitmap, em) -> {
            Glyph glyph = new Glyph(bitmap, em);
            cache.put(tex, glyph);
            List<Consumer<Glyph>> callbacks = waiting.remove(tex);
            if (callbacks != null) for (Consumer<Glyph> callback : callbacks) callback.accept(glyph);
        });
    }
    void request(String tex, Consumer<Glyph> callback) {
        Glyph glyph = cache.get(tex);
        if (glyph != null) { callback.accept(glyph); return; }
        boolean first = !waiting.containsKey(tex);
        waiting.computeIfAbsent(tex, key -> new ArrayList<>()).add(callback);
        if (first) renderer.requestGlyph(tex);
    }
}
