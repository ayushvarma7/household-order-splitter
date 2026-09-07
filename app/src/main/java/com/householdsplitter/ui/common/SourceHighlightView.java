package com.householdsplitter.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;

/**
 * Rings one region of a screenshot, to answer "which row is this?".
 *
 * <p>Draws over an {@link ImageView}, not inside it, so the picture keeps its own scaling.
 * The region arrives in permille of the original image and has to be mapped through the
 * ImageView's own matrix: with {@code fitCenter} the bitmap is letterboxed, so a box placed
 * by view coordinates would sit somewhere near the row rather than on it.
 *
 * <p>Floats here are view geometry, not money. The rule against them holds in :core, where
 * amounts live; a canvas takes floats and rounding a stroke to whole pixels would only make
 * the outline crooked.
 */
public class SourceHighlightView extends View {

    private static final int NONE = -1;

    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF region = new RectF();

    @Nullable
    private ImageView target;
    private int leftPermille = NONE;
    private int topPermille;
    private int rightPermille;
    private int bottomPermille;

    public SourceHighlightView(Context context) {
        this(context, null);
    }

    public SourceHighlightView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(3f * density);
        // Fixed red rather than a theme colour. It is drawn on a photograph of somebody
        // else's interface, where nothing about the surface underneath is known, and red
        // is the one colour a reader will not mistake for part of the screenshot.
        outline.setColor(Color.parseColor("#FF3B30"));
        // Everything outside the region is dimmed, which is what makes the eye land inside
        // it. An outline alone gets lost against a busy page.
        dim.setColor(Color.parseColor("#99000000"));
        setWillNotDraw(false);
    }

    /**
     * Points the highlight at a region of {@code target}'s image.
     *
     * @param leftPermille pass a negative value to show nothing
     */
    public void highlight(ImageView target, int leftPermille, int topPermille,
                          int rightPermille, int bottomPermille) {
        this.target = target;
        this.leftPermille = leftPermille;
        this.topPermille = topPermille;
        this.rightPermille = rightPermille;
        this.bottomPermille = bottomPermille;
        invalidate();
    }

    public void clear() {
        this.leftPermille = NONE;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (target == null || leftPermille < 0
                || rightPermille <= leftPermille || bottomPermille <= topPermille) {
            return;
        }
        Drawable drawable = target.getDrawable();
        if (drawable == null) {
            return;
        }
        int sourceWidth = drawable.getIntrinsicWidth();
        int sourceHeight = drawable.getIntrinsicHeight();
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return;
        }

        region.set(
                leftPermille / 1000f * sourceWidth,
                topPermille / 1000f * sourceHeight,
                rightPermille / 1000f * sourceWidth,
                bottomPermille / 1000f * sourceHeight);

        Matrix matrix = target.getImageMatrix();
        if (matrix != null) {
            matrix.mapRect(region);
        }
        // The ImageView's content sits inside its padding, and this view sits over the
        // whole thing, so the two frames differ by exactly that much.
        region.offset(target.getLeft() + target.getPaddingLeft() - getLeft(),
                target.getTop() + target.getPaddingTop() - getTop());

        float radius = 6f * getResources().getDisplayMetrics().density;

        // Dim in four bands rather than a saved layer: cheaper, and it never touches the
        // pixels inside the region.
        canvas.drawRect(0, 0, getWidth(), region.top, dim);
        canvas.drawRect(0, region.bottom, getWidth(), getHeight(), dim);
        canvas.drawRect(0, region.top, region.left, region.bottom, dim);
        canvas.drawRect(region.right, region.top, getWidth(), region.bottom, dim);

        canvas.drawRoundRect(region, radius, radius, outline);
    }
}
