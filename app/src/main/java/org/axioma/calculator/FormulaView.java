package org.axioma.calculator;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.net.Uri;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.ConsoleMessage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import org.json.JSONObject;
import java.util.LinkedHashSet;
import java.util.Set;

/** Offline MathJax presentation. Mathematical evaluation stays in the C++ engine. */
public final class FormulaView extends WebView {
    public interface RenderListener { void onRendered(boolean success); }
    interface GlyphListener { void onGlyph(String tex, Bitmap bitmap, float em); }
    private static final String ORIGIN = "https://axioma.invalid";
    private String latex = "";
    private String resultLatex = "", resultText = "";
    private boolean finalResult, diagnosticResult, exactResult;
    private GlyphListener glyphListener;
    private Runnable editListener;
    private final Set<String> pendingGlyphs = new LinkedHashSet<>();
    private int revision;
    private boolean ready, released;
    private RenderListener listener;
    private final int ink;
    private final StringBuffer diagnostics = new StringBuffer();
    String diagnostics() { return diagnostics.toString(); }
    private void recordDiagnostic(String message) {
        if (diagnostics.length() < 4096) diagnostics.append(message).append('\n');
    }

    public FormulaView(Context context) { this(context, Color.BLACK); }

    // JavaScript runs only the packaged presentation code, with no CAS bridge.
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    public FormulaView(Context context, int ink) {
        super(context);
        this.ink = ink;
        setBackgroundColor(Color.TRANSPARENT);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(true);
        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setDomStorageEnabled(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setTextZoom(100);
        addJavascriptInterface(new Bridge(), "AxiomaHost");
        setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage message) {
                recordDiagnostic(message.message()); return true;
            }
        });
        setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String path = uri.getPath();
                if ("https".equals(uri.getScheme()) && "axioma.invalid".equals(uri.getHost())
                        && path != null && path.startsWith("/renderer/") && !path.contains("..")
                        && path.matches("/[A-Za-z0-9_./-]+")) {
                    try {
                        String mime = path.endsWith(".js") ? "application/javascript"
                                : path.endsWith(".html") ? "text/html" : "application/octet-stream";
                        return new WebResourceResponse(mime, "UTF-8", getContext().getAssets().open(path.substring(1)));
                    } catch (IOException ignored) {
                        return blocked();
                    }
                }
                return blocked();
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (!released && (ORIGIN + "/renderer/index.html").equals(url))
                    evaluateJavascript("window.Axioma.start()", null);
            }
        });
        loadUrl(ORIGIN + "/renderer/index.html");
    }

    private static WebResourceResponse blocked() {
        return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
                Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
    }

    public void setFormula(Formula formula, String accessibleText) {
        setLatex(formula == null ? "" : formula.latex, accessibleText);
    }

    public void setLatex(String value, String accessibleText) {
        resultLatex = ""; resultText = ""; diagnosticResult = false; exactResult = false;
        update(value, accessibleText);
    }
    void setCalculation(String value, String source, String answer, String display, boolean completed, boolean diagnostic) {
        setCalculation(value, source, answer, display, completed, diagnostic, false);
    }
    void setCalculation(String value, String source, String answer, String display, boolean completed,
                        boolean diagnostic, boolean exact) {
        resultLatex = answer; resultText = display; finalResult = completed; diagnosticResult = diagnostic;
        exactResult = exact;
        update(value, source);
    }
    private void update(String value, String accessibleText) {
        if (value == null || value.length() > 16384) throw new IllegalArgumentException("Formula exceeds the rendering limit.");
        latex = value; ++revision;
        setContentDescription(accessibleText);
        if (ready && !released) render();
    }

    public void setOnRenderListener(RenderListener value) { listener = value; }
    void setGlyphListener(GlyphListener value) { glyphListener = value; }
    void setOnEditListener(Runnable value) { editListener = value; }
    void requestGlyph(String tex) {
        pendingGlyphs.add(tex);
        if (ready && !released) sendGlyphs();
    }
    private void sendGlyphs() {
        for (String tex : pendingGlyphs)
            evaluateJavascript("window.Axioma.glyph(" + JSONObject.quote(tex) + ")", null);
        pendingGlyphs.clear();
    }

    private void render() {
        evaluateJavascript("window.Axioma.render(" + JSONObject.quote(latex) + "," + revision + ","
                + JSONObject.quote(resultLatex) + "," + JSONObject.quote(resultText) + "," + finalResult + ","
                + JSONObject.quote(getContentDescription() == null ? "" : getContentDescription().toString()) + ","
                + diagnosticResult + "," + exactResult + ")", null);
    }

    private final class Bridge {
        @JavascriptInterface public void ready() {
            post(() -> {
                if (released) return;
                ready = true;
                String color = String.format(Locale.ROOT, "#%06X", ink & 0xFFFFFF);
                float size = 30 * getResources().getDisplayMetrics().scaledDensity
                        / getResources().getDisplayMetrics().density;
                evaluateJavascript("window.Axioma.configure(" + JSONObject.quote(color) + "," + size + ")", null);
                render();
                sendGlyphs();
            });
        }
        @JavascriptInterface public void edit() {
            post(() -> { if (!released && editListener != null) editListener.run(); });
        }
        @JavascriptInterface public void glyph(String tex, String png, double em) {
            if (png.length() > 4 * 1024 * 1024 || !Double.isFinite(em) || em <= 0) return;
            try {
                byte[] bytes = Base64.decode(png, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap == null) return;
                bitmap.setDensity(Bitmap.DENSITY_NONE);
                post(() -> {
                    if (!released && glyphListener != null) glyphListener.onGlyph(tex, bitmap, (float) em);
                });
            } catch (IllegalArgumentException ignored) { recordDiagnostic("Invalid glyph image."); }
        }
        @JavascriptInterface public void glyphFailed(String tex) { recordDiagnostic("Could not render control: " + tex); }
        @JavascriptInterface public void rendered(int generation, int height, boolean success) {
            post(() -> {
                if (released || generation != revision) return;
                float density = getResources().getDisplayMetrics().density;
                int pixels = Math.max(1, Math.round(Math.min(1500, Math.max(1, height)) * density));
                ViewGroup.LayoutParams params = getLayoutParams();
                if (params != null && params.height != pixels) { params.height = pixels; setLayoutParams(params); }
                if (listener != null) listener.onRendered(success);
            });
        }
    }

    public void release() {
        released = true; listener = null; glyphListener = null; editListener = null; pendingGlyphs.clear();
        stopLoading(); removeJavascriptInterface("AxiomaHost");
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
        destroy();
    }
}
