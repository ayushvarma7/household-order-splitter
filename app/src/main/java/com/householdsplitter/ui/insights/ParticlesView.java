package com.householdsplitter.ui.insights;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The backdrop of a full-screen fact: a large, soft glow in the fact's colour that drifts
 * slowly, and a few faint motes rising through it. Enough that the page feels lit, not so
 * much that it competes with the number.
 *
 * <p>Purely decoration: it is hidden from screen readers, and with the system's "remove
 * animations" setting on it draws one still frame instead of moving.
 */
public class ParticlesView extends View {

    private static final int COUNT = 26;

    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowLow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix move = new Matrix();
    private RadialGradient high;
    private RadialGradient low;
    private final float[] x = new float[COUNT];
    private final float[] y = new float[COUNT];
    private final float[] speed = new float[COUNT];
    private final float[] radius = new float[COUNT];
    private final float[] phase = new float[COUNT];
    private final boolean reduceMotion;
    private final long born = System.nanoTime();
    private long last;
    private int color = 0xFF6A3FC0;

    public ParticlesView(Context context) {
        this(context, null);
    }

    public ParticlesView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        reduceMotion = Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        float density = context.getResources().getDisplayMetrics().density;
        for (int i = 0; i < COUNT; i++) {
            speed[i] = (8f + (float) Math.random() * 16f) * density;
            radius[i] = (1f + (float) Math.random() * 2.2f) * density;
            phase[i] = (float) (Math.random() * 6.28);
        }
    }

    /** The fact's colour, fully opaque; the view decides how faint to draw it. */
    public void setColor(int color) {
        this.color = color | 0xFF000000;
        buildGlows();
        invalidate();
    }

    /**
     * The two glows are made once per colour and size, centred on the origin, and only moved
     * while drawing, so a frame allocates nothing.
     */
    private void buildGlows() {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }
        high = new RadialGradient(0f, 0f, Math.max(w, h) * .62f,
                new int[] {alpha(color, .26f), alpha(color, .08f), alpha(color, 0f)},
                new float[] {0f, .5f, 1f}, Shader.TileMode.CLAMP);
        low = new RadialGradient(0f, 0f, Math.max(w, h) * .5f,
                new int[] {alpha(color, .14f), alpha(color, 0f)}, null, Shader.TileMode.CLAMP);
        glow.setShader(high);
        glowLow.setShader(low);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        buildGlows();
        for (int i = 0; i < COUNT; i++) {
            x[i] = (float) Math.random() * w;
            y[i] = (float) Math.random() * h;
        }
    }

    private static int alpha(int color, float a) {
        return (color & 0x00FFFFFF) | (Math.round(Math.max(0f, Math.min(1f, a)) * 255f) << 24);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0f : Math.min(.05f, (now - last) / 1e9f);
        last = now;
        float t = reduceMotion ? 0f : (now - born) / 1e9f;
        int w = getWidth();
        int h = getHeight();

        // Two overlapping glows, one high and one low, each circling slowly.
        if (high != null) {
            move.setTranslate(w * (.78f + .06f * (float) Math.sin(t * .23f)),
                    h * (.22f + .04f * (float) Math.cos(t * .19f)));
            high.setLocalMatrix(move);
            canvas.drawRect(0, 0, w, h, glow);
            move.setTranslate(w * (.1f + .05f * (float) Math.cos(t * .17f)),
                    h * (.9f + .03f * (float) Math.sin(t * .21f)));
            low.setLocalMatrix(move);
            canvas.drawRect(0, 0, w, h, glowLow);
        }

        for (int i = 0; i < COUNT; i++) {
            if (!reduceMotion) {
                x[i] += (float) Math.sin(t * .6f + phase[i]) * 4f * dt;
                y[i] -= speed[i] * dt;
                if (y[i] < -12f) {
                    y[i] = h + 10f;
                    x[i] = (float) Math.random() * w;
                }
            }
            float twinkle = .5f + .5f * (float) Math.sin(t * 1.3f + phase[i]);
            dot.setColor(alpha(color, .18f + .22f * twinkle));
            canvas.drawCircle(x[i], y[i], radius[i], dot);
        }
        if (!reduceMotion) {
            postInvalidateOnAnimation();
        }
    }
}
