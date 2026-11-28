package com.householdsplitter.ui.insights;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.householdsplitter.R;
import com.householdsplitter.core.insight.Patterns;
import com.householdsplitter.core.money.CurrencyFormat;
import com.householdsplitter.core.parse.StoreKind;
import com.householdsplitter.ui.common.StorePill;

/**
 * One item's price, every time it was bought: a line through the purchases in date order,
 * each point coloured by the store it came from, with the cheapest and dearest prices
 * marked on the left. The store colours are the store pills' own, so a Costco dot here is
 * the colour of the Costco pill on the orders list.
 */
public class PriceTrackerView extends View {

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint point = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final int neutral;

    private Patterns.PriceSeries series;
    private CurrencyFormat money;

    public PriceTrackerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        neutral = ContextCompat.getColor(context, R.color.on_surface_variant);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(2));
        line.setColor(neutral);
        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(dp(1));
        grid.setColor(ContextCompat.getColor(context, R.color.outline_variant));
        grid.setPathEffect(new DashPathEffect(new float[]{dp(4), dp(4)}, 0));
        halo.setColor(ContextCompat.getColor(context, R.color.surface_container_low));
        label.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11,
                getResources().getDisplayMetrics()));
        label.setColor(neutral);
    }

    public void setSeries(Patterns.PriceSeries series, CurrencyFormat money) {
        this.series = series;
        this.money = money;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), (int) dp(170));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (series == null || series.points.isEmpty() || money == null) {
            return;
        }
        String high = money.format(series.highest());
        String low = money.format(series.lowest());
        float left = Math.max(label.measureText(high), label.measureText(low)) + dp(10);
        float right = getWidth() - dp(10);
        float top = dp(12);
        float bottom = getHeight() - dp(12);
        long lo = series.lowest();
        long hi = series.highest();
        long first = series.points.get(0).dateMillis;
        long last = series.points.get(series.points.size() - 1).dateMillis;

        canvas.drawLine(left, top, right, top, grid);
        canvas.drawLine(left, bottom, right, bottom, grid);
        canvas.drawText(high, 0, top - (label.ascent() + label.descent()) / 2, label);
        if (hi != lo) {
            canvas.drawText(low, 0, bottom - (label.ascent() + label.descent()) / 2, label);
        }

        path.reset();
        float[][] at = new float[series.points.size()][2];
        for (int i = 0; i < series.points.size(); i++) {
            Patterns.PricePoint p = series.points.get(i);
            float x = last == first ? (left + right) / 2f
                    : left + dp(8) + (right - left - dp(16)) * (p.dateMillis - first) / (float) (last - first);
            float y = hi == lo ? (top + bottom) / 2f
                    : bottom - (bottom - top) * (p.cents - lo) / (float) (hi - lo);
            at[i][0] = x;
            at[i][1] = y;
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        canvas.drawPath(path, line);
        for (int i = 0; i < at.length; i++) {
            StoreKind store = StorePill.byDisplayName(series.points.get(i).store);
            point.setColor(store == null ? neutral : StorePill.colour(getContext(), store));
            canvas.drawCircle(at[i][0], at[i][1], dp(7), halo);
            canvas.drawCircle(at[i][0], at[i][1], dp(5), point);
        }
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
