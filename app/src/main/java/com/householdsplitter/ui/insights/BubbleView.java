package com.householdsplitter.ui.insights;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.ColorUtils;

import com.householdsplitter.R;
import com.householdsplitter.core.insight.InsightGenerator;
import com.householdsplitter.core.insight.InsightTree;
import com.householdsplitter.core.insight.InsightTree.Node;
import com.householdsplitter.core.money.CurrencyFormat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spending drawn as bubbles you can zoom into: categories, then sub-categories, then single
 * items, each sized by what was spent.
 *
 * <p>Colour is deliberately quiet. Every bubble is the app's violet, deepest for the biggest
 * and paler down the ranking, so size and depth tell the same story and nothing shouts. The
 * one exception is money the reader missed, which is amber and dashed because it is the only
 * thing on the picture that needs doing. Change since last month is written on the bubble as
 * a small arrow and percent, never as a tint.
 *
 * <p>This is one canvas, so on its own a screen reader would hear nothing useful. Everything
 * it shows is also reachable as ordinary rows beside it, and the spoken description is kept
 * current by the fragment. The picture is the quick way in, not the only one.
 *
 * <p>Geometry is float because it is pixels. No money is computed here: every figure drawn is
 * a {@code long} of cents that the core already summed, formatted for display.
 */
public class BubbleView extends View {

    /** What the picture tells its screen. */
    public interface Listener {

        /** A bubble was tapped to look inside it, or the empty space to step back out. */
        void onFocusChanged(@NonNull Node focus);
    }

    /** Where a node sits in a unit circle, and where in that circle it sits inside its parent. */
    private static final class Geo {
        float x;
        float y;
        float r;
        float rx;
        float ry;
        float rr;
        /** 0 for the biggest of its siblings, up to 1 for the smallest. */
        float rank;
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nameText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valueText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<Node, Geo> geometry = new HashMap<>();
    private final GestureDetector taps;
    private final boolean reduceMotion;
    private final float density;

    private final int deep;
    private final int pale;
    private final int stage;
    private final int surface;
    private final int inkDark;
    private final int inkLight;
    private final int shadow;
    private final int missingFill;
    private final int missingStroke;
    private final int missingInk;

    private InsightTree tree;
    private Node focus;
    private CurrencyFormat money;
    private Listener listener;

    private float camX;
    private float camY;
    private float camLogScale;
    private float targetX;
    private float targetY;
    private float targetLogScale;
    private float scale = 1f;
    private float centerX;
    private float centerY;
    private float ringRadius = 100f;
    private long lastFrame;
    private long introStart;
    private float introSeconds = 99f;

    public BubbleView(Context context) {
        this(context, null);
    }

    public BubbleView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        // Animator scale 0 is the system's "remove animations" setting.
        reduceMotion = Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        Typeface bold = ResourcesCompat.getFont(context, R.font.inter_bold);
        Typeface medium = ResourcesCompat.getFont(context, R.font.inter_medium);
        valueText.setTypeface(bold == null ? Typeface.DEFAULT_BOLD : bold);
        valueText.setTextAlign(Paint.Align.CENTER);
        valueText.setFontFeatureSettings("tnum");
        valueText.setLetterSpacing(-.02f);
        nameText.setTypeface(medium == null ? Typeface.DEFAULT : medium);
        nameText.setTextAlign(Paint.Align.CENTER);
        stroke.setStyle(Paint.Style.STROKE);

        deep = ContextCompat.getColor(context, R.color.bubble_deep);
        pale = ContextCompat.getColor(context, R.color.bubble_pale);
        stage = ContextCompat.getColor(context, R.color.surface_container_low);
        surface = ContextCompat.getColor(context, R.color.surface);
        inkDark = ContextCompat.getColor(context, R.color.bubble_ink_dark);
        inkLight = ContextCompat.getColor(context, R.color.bubble_ink_light);
        shadow = ContextCompat.getColor(context, R.color.bubble_shadow);
        missingFill = ContextCompat.getColor(context, R.color.warning_container);
        missingStroke = ContextCompat.getColor(context, R.color.warning);
        missingInk = ContextCompat.getColor(context, R.color.on_warning_container);

        taps = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(@NonNull MotionEvent e) {
                handleTap(e.getX(), e.getY());
                return true;
            }
        });
    }

    public void setMoney(CurrencyFormat money) {
        this.money = money;
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public Node focus() {
        return focus;
    }

    /** Shows a new picture, starting on the bubble at this path. */
    public void setTree(@Nullable InsightTree tree, @Nullable String focusPath) {
        this.tree = tree;
        geometry.clear();
        if (tree == null) {
            focus = null;
            invalidate();
            return;
        }
        Geo root = new Geo();
        root.r = 1f;
        geometry.put(tree.root(), root);
        layout(tree.root());
        focus = tree.find(focusPath);
        introStart = reduceMotion ? 0L : System.nanoTime();
        retarget(true);
        // Start a little zoomed out so the first thing you see is the picture settling in.
        camLogScale -= reduceMotion ? 0f : .18f;
        invalidate();
    }

    /** Zooms to a bubble, from outside (a row or "Show me"). */
    public void zoomTo(@NonNull Node node) {
        if (node == focus) {
            return;
        }
        focus = node;
        retarget(false);
        if (listener != null) {
            listener.onFocusChanged(node);
        }
        invalidate();
    }

    /**
     * Square on a phone held upright, and no taller than about half the screen otherwise, so
     * on a short or wide screen the picture never pushes the rest of the page out of reach.
     */
    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int tallest = Math.round(getResources().getDisplayMetrics().heightPixels * .5f);
        int height = Math.max(Math.round(240f * density), Math.min(width, tallest));
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        ringRadius = Math.max(60f * density, Math.min(w, h) / 2f - 4f * density);
        if (focus != null) {
            retarget(false);
        }
    }

    // ---------------------------------------------------------------- geometry

    private void layout(Node node) {
        Geo geo = geometry.get(node);
        List<Node> kids = node.children();
        if (kids.isEmpty()) {
            return;
        }
        pack(kids);
        rank(kids);
        for (Node kid : kids) {
            Geo child = geometry.get(kid);
            child.x = geo.x + child.rx * geo.r;
            child.y = geo.y + child.ry * geo.r;
            child.r = child.rr * geo.r;
            layout(kid);
        }
    }

    /** Biggest first: the shade of a bubble is its place in this order among its siblings. */
    private void rank(List<Node> kids) {
        List<Node> sorted = new ArrayList<>(kids);
        sorted.sort((a, b) -> Long.compare(b.thisCents(), a.thisCents()));
        int n = sorted.size();
        for (int i = 0; i < n; i++) {
            geometry.get(sorted.get(i)).rank = n == 1 ? 0f : i / (float) (n - 1);
        }
    }

    /**
     * Places the children as touching circles inside the unit circle, sized by the square
     * root of their spending so that area, not width, is what shows the amount.
     */
    private void pack(List<Node> kids) {
        int n = kids.size();
        float[] r = new float[n];
        float[] x = new float[n];
        float[] y = new float[n];
        float mean = 0f;
        for (int i = 0; i < n; i++) {
            r[i] = (float) Math.sqrt(kids.get(i).thisCents());
            mean += r[i];
        }
        mean /= n;
        if (n == 1) {
            Geo only = new Geo();
            geometry.put(kids.get(0), only);
            only.rr = .62f;
            return;
        }
        float gap = mean * .09f;
        for (int i = 1; i < n; i++) {
            boolean placed = false;
            for (float d = r[0] * .2f; !placed && d < mean * 40f; d += mean * .1f) {
                float bestX = 0f;
                float bestY = 0f;
                float bestScore = Float.MAX_VALUE;
                boolean found = false;
                for (int a = 0; a < 360; a += 8) {
                    float cx = (float) (d * Math.cos(Math.toRadians(a)));
                    float cy = (float) (d * Math.sin(Math.toRadians(a)));
                    boolean free = true;
                    for (int j = 0; j < i && free; j++) {
                        free = Math.hypot(cx - x[j], cy - y[j]) >= r[i] + r[j] + gap;
                    }
                    if (!free) {
                        continue;
                    }
                    float sx = 0f;
                    float sy = 0f;
                    float sw = 0f;
                    for (int j = 0; j < i; j++) {
                        sx += x[j] * r[j];
                        sy += y[j] * r[j];
                        sw += r[j];
                    }
                    float score = (float) Math.hypot(cx - sx / sw, cy - sy / sw);
                    if (score < bestScore) {
                        bestScore = score;
                        bestX = cx;
                        bestY = cy;
                        found = true;
                    }
                }
                if (found) {
                    x[i] = bestX;
                    y[i] = bestY;
                    placed = true;
                }
            }
        }
        // Centre the cluster on its weighted middle, then shrink to fit the unit circle.
        float ex = 0f;
        float ey = 0f;
        float w2 = 0f;
        for (int i = 0; i < n; i++) {
            ex += x[i] * r[i] * r[i];
            ey += y[i] * r[i] * r[i];
            w2 += r[i] * r[i];
        }
        ex /= w2;
        ey /= w2;
        float reach = 0f;
        for (int i = 0; i < n; i++) {
            reach = Math.max(reach, (float) Math.hypot(x[i] - ex, y[i] - ey) + r[i]);
        }
        float fit = 1f / (reach * 1.04f);
        for (int i = 0; i < n; i++) {
            Geo geo = new Geo();
            geo.rx = (x[i] - ex) * fit;
            geo.ry = (y[i] - ey) * fit;
            geo.rr = r[i] * fit;
            geometry.put(kids.get(i), geo);
        }
    }

    private void retarget(boolean snap) {
        Geo geo = focus == null ? null : geometry.get(focus);
        if (geo == null || getWidth() == 0) {
            return;
        }
        targetX = geo.x;
        targetY = geo.y;
        // A single item has nothing inside it, so it is shown a little smaller than a
        // category, with room around it to tap back out.
        float fill = focus.children().isEmpty() ? .72f : 1f;
        targetLogScale = (float) Math.log(ringRadius * fill / geo.r);
        if (snap) {
            camX = targetX;
            camY = targetY;
            camLogScale = targetLogScale;
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return taps.onTouchEvent(event) || super.onTouchEvent(event);
    }

    private void handleTap(float x, float y) {
        if (tree == null || focus == null) {
            return;
        }
        Node hit = null;
        double best = Double.MAX_VALUE;
        for (Node kid : focus.children()) {
            Geo g = geometry.get(kid);
            double d = Math.hypot(x - (centerX + (g.x - camX) * scale),
                    y - (centerY + (g.y - camY) * scale));
            if (d <= g.r * scale && d < best) {
                best = d;
                hit = kid;
            }
        }
        if (hit != null) {
            zoomTo(hit);
            return;
        }
        Geo focusGeo = geometry.get(focus);
        double outside = Math.hypot(x - (centerX + (focusGeo.x - camX) * scale),
                y - (centerY + (focusGeo.y - camY) * scale));
        if (focus.parent() != null && (outside > focusGeo.r * scale || focus.isLeaf())) {
            zoomTo(focus.parent());
        }
    }

    // ---------------------------------------------------------------- drawing

    private static int withAlpha(int color, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private static float smooth(float value, float from, float to) {
        float t = Math.max(0f, Math.min(1f, (value - from) / (to - from)));
        return t * t * (3f - 2f * t);
    }

    /** The face colour of a bubble: deep violet for the biggest, paler down the ranking. */
    private int faceOf(Node node, Geo g) {
        if (ItemNames.isMissing(node)) {
            return missingFill;
        }
        // Eased so the top two or three stay clearly apart and the long tail settles pale.
        float t = (float) Math.pow(g.rank, .7f);
        return ColorUtils.blendARGB(deep, pale, t);
    }

    /** Whichever of the two inks reads better on this face. */
    private int inkOn(int face, Node node) {
        if (ItemNames.isMissing(node)) {
            return missingInk;
        }
        return ColorUtils.calculateContrast(inkLight, face | 0xFF000000)
                >= ColorUtils.calculateContrast(inkDark, face | 0xFF000000) ? inkLight : inkDark;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0f : Math.min(.05f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        if (tree == null || focus == null) {
            return;
        }
        float ease = 1f - (float) Math.exp(-dt * (reduceMotion ? 40f : 6f));
        camX += (targetX - camX) * ease;
        camY += (targetY - camY) * ease;
        camLogScale += (targetLogScale - camLogScale) * ease;
        scale = (float) Math.exp(camLogScale);
        introSeconds = introStart == 0L ? 99f : (now - introStart) / 1e9f;

        drawNode(canvas, tree.root(), 1f);

        boolean settling = Math.abs(targetX - camX) > .0005f || Math.abs(targetY - camY) > .0005f
                || Math.abs(targetLogScale - camLogScale) > .002f;
        // Nothing idles or bobs: the picture moves only when it has somewhere to go.
        if (settling || introSeconds < 1.4f) {
            postInvalidateOnAnimation();
        }
    }

    /**
     * The categories rise in one after another when the picture first appears, biggest
     * first, with a gentle settle rather than a bounce.
     */
    private float introScale(Node node) {
        if (node.depth() != 1 || introSeconds >= 1.4f) {
            return 1f;
        }
        Geo g = geometry.get(node);
        float u = Math.max(0f, Math.min(1f, (introSeconds - g.rank * .45f) / .7f));
        float x = 1f - u;
        return 1f - x * x * x;
    }

    /** True for the bubble in front, what it sits inside, and what is inside it. */
    private boolean inView(Node node) {
        for (Node n = focus; n != null; n = n.parent()) {
            if (n == node) {
                return true;
            }
        }
        for (Node n = node; n != null; n = n.parent()) {
            if (n == focus) {
                return true;
            }
        }
        return false;
    }

    private void drawNode(Canvas canvas, Node node, float parentAlpha) {
        Geo g = geometry.get(node);
        float sx = centerX + (g.x - camX) * scale;
        float sy = centerY + (g.y - camY) * scale;
        float sr = g.r * scale * introScale(node);
        if (sr < 1.5f || sx + sr < 0 || sx - sr > getWidth() || sy + sr < 0
                || sy - sr > getHeight()) {
            return;
        }
        // Only the bubble in front, what holds it and what is inside it are drawn. Its
        // neighbours would only show as cut-off slivers at the edges of the frame.
        boolean related = inView(node);
        if (!related) {
            return;
        }
        float base = parentAlpha;
        // How far this bubble has opened up to show what is inside it.
        float opened = node.children().isEmpty() ? 0f
                : node.depth() == 0 ? 1f : smooth(sr, 90f * density, 170f * density);

        fill.setShader(null);
        if (opened > .01f) {
            // An open bubble becomes a quiet stage for its children: a soft disc, no outline.
            // Once it is far bigger than the frame it would only show as a flat box, so the
            // stages of the bubbles around the one in front fade away as they grow.
            float fits = 1f - smooth(sr, ringRadius * 1.08f, ringRadius * 1.5f);
            fill.setColor(withAlpha(stage, opened * base * fits));
            canvas.drawCircle(sx, sy, sr, fill);
        }
        if (node.depth() > 0 && opened < .99f) {
            drawFace(canvas, node, g, sx, sy, sr, (1f - opened) * base);
        }
        // The bubble's own label and what is inside it take turns rather than share the
        // space: the label is gone by the time the children start to show, so the two sets
        // of words are never on top of each other. A bubble resting part-way open keeps its
        // label and shows its children only faintly, if at all.
        float inside = smooth(opened, .3f, .8f);
        if (inside > .01f) {
            for (Node kid : node.children()) {
                drawNode(canvas, kid, base * inside);
            }
        }
        if (node.depth() > 0) {
            drawLabel(canvas, node, g, sx, sy, sr, (1f - smooth(opened, 0f, .3f)) * base);
        }
    }

    /** A bubble with a soft shadow under it and light falling from the top left. */
    private void drawFace(Canvas canvas, Node node, Geo g, float sx, float sy, float sr,
                          float alpha) {
        boolean missing = ItemNames.isMissing(node);
        int face = faceOf(node, g);
        if (!missing && sr > 6f * density) {
            float drop = Math.min(6f * density, sr * .07f);
            fill.setShader(new RadialGradient(sx, sy + drop, sr * 1.12f,
                    new int[] {withAlpha(shadow, .22f * alpha), withAlpha(shadow, .10f * alpha),
                            withAlpha(shadow, 0f)},
                    new float[] {.78f, .9f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(sx, sy + drop, sr * 1.12f, fill);
        }
        // The face is opaque surface first so the stage behind never shows through.
        fill.setShader(null);
        fill.setColor(withAlpha(surface, alpha));
        canvas.drawCircle(sx, sy, sr, fill);
        int lit = ColorUtils.blendARGB(face, 0xFFFFFFFF, missing ? .0f : .16f);
        int shade = ColorUtils.blendARGB(face, 0xFF000000, missing ? .0f : .06f);
        fill.setShader(new RadialGradient(sx - sr * .35f, sy - sr * .45f, sr * 1.6f,
                new int[] {withAlpha(lit, alpha), withAlpha(face, alpha),
                        withAlpha(shade, alpha)},
                new float[] {0f, .55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(sx, sy, sr, fill);
        fill.setShader(null);
        if (missing) {
            // Dashed, because this is money with no item behind it, not a thing bought.
            stroke.setStrokeWidth(1.5f * density);
            stroke.setPathEffect(new DashPathEffect(
                    new float[] {6f * density, 5f * density}, 0f));
            stroke.setColor(withAlpha(missingStroke, alpha));
            canvas.drawCircle(sx, sy, sr - .75f * density, stroke);
            stroke.setPathEffect(null);
        }
    }

    private void drawLabel(Canvas canvas, Node node, Geo g, float sx, float sy, float sr,
                           float alpha) {
        float dp = density;
        if (sr < 18f * dp || alpha < .02f || money == null) {
            return;
        }
        boolean showName = sr >= 30f * dp;
        boolean showChange = sr >= 44f * dp && tree.hasPreviousMonth() && !node.isMissing();
        float valueSize = Math.max(12f * dp, Math.min(30f * dp, sr * .30f));
        float nameSize = Math.max(10f * dp, Math.min(15f * dp, sr * .17f));
        float changeSize = Math.max(10f * dp, Math.min(13f * dp, sr * .14f));
        float maxWidth = sr * 1.5f;

        int ink = inkOn(faceOf(node, g), node);
        nameText.setColor(withAlpha(ink, alpha * .82f));
        valueText.setColor(withAlpha(ink, alpha));

        String[] nameLines = new String[0];
        if (showName) {
            nameText.setTextSize(nameSize);
            nameLines = splitName(ItemNames.shortName(node), maxWidth);
            float widest = 0f;
            for (String line : nameLines) {
                widest = Math.max(widest, nameText.measureText(line));
            }
            if (widest > maxWidth) {
                nameSize = Math.max(9f * dp, nameSize * maxWidth / widest);
                nameText.setTextSize(nameSize);
            }
        }
        String value = InsightText.compact(money, node.thisCents());
        valueText.setTextSize(valueSize);
        float valueWidth = valueText.measureText(value);
        if (valueWidth > sr * 1.6f) {
            valueSize *= sr * 1.6f / valueWidth;
            valueText.setTextSize(valueSize);
        }
        String change = showChange ? changeLabel(node) : "";
        float total = (showName ? nameLines.length * nameSize * 1.15f + 4f * dp : 0f)
                + valueSize * .76f + (change.isEmpty() ? 0f : changeSize + 7f * dp);
        float y = sy - total / 2f;
        if (showName) {
            for (String line : nameLines) {
                y += nameSize;
                canvas.drawText(line, sx, y, nameText);
                y += nameSize * .15f;
            }
            y += 4f * dp;
        }
        y += valueSize * .76f;
        canvas.drawText(value, sx, y, valueText);
        if (!change.isEmpty()) {
            nameText.setTextSize(changeSize);
            nameText.setColor(withAlpha(ink, alpha * .7f));
            canvas.drawText(change, sx, y + changeSize + 7f * dp, nameText);
        }
    }

    /** "↑ 12%", "↓ 4%", "New" or nothing for steady, so change never rests on colour. */
    private String changeLabel(Node node) {
        if (node.lastCents() <= 0L) {
            return getContext().getString(R.string.insights_bubble_new);
        }
        // The same threshold as the sentence above the picture, so a bubble never shows an
        // arrow for a change the screen calls "about the same".
        if (InsightGenerator.toneOf(node.thisCents(), node.lastCents())
                == com.householdsplitter.core.insight.Insight.Tone.STEADY) {
            return "";
        }
        int percent = InsightGenerator.percentChange(node.thisCents(), node.lastCents());
        return (percent > 0 ? "↑ " : "↓ ") + Math.abs(percent) + "%";
    }

    private String[] splitName(String name, float maxWidth) {
        if (nameText.measureText(name) <= maxWidth) {
            return new String[] {name};
        }
        String[] words = name.split(" ");
        if (words.length < 2) {
            return new String[] {name};
        }
        String[] best = {name};
        float bestWidth = Float.MAX_VALUE;
        for (int i = 1; i < words.length; i++) {
            String a = String.join(" ", java.util.Arrays.copyOfRange(words, 0, i));
            String b = String.join(" ", java.util.Arrays.copyOfRange(words, i, words.length));
            float width = Math.max(nameText.measureText(a), nameText.measureText(b));
            if (width < bestWidth) {
                bestWidth = width;
                best = new String[] {a, b};
            }
        }
        return best;
    }
}
