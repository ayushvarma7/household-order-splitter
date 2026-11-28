package com.householdsplitter.ui.insights;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.householdsplitter.R;
import com.householdsplitter.core.insight.Patterns;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * A square per day, a column per week, shaded by how much went out that day. Four steps of
 * shade rather than a continuous ramp, because a person can tell four shades apart at a
 * glance and cannot tell forty.
 */
public class ShoppingCalendarView extends View {

    private static final String[] ROW_LABELS = {"M", "", "W", "", "F", "", "S"};

    private final Paint cell = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final int accent;
    private final int empty;

    private Patterns.Calendar calendar;

    public ShoppingCalendarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        accent = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary,
                ContextCompat.getColor(context, R.color.primary));
        empty = ContextCompat.getColor(context, R.color.surface_container_high);
        label.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11,
                getResources().getDisplayMetrics()));
        label.setColor(ContextCompat.getColor(context, R.color.on_surface_variant));
    }

    public void setCalendar(Patterns.Calendar calendar) {
        this.calendar = calendar;
        requestLayout();
        invalidate();
    }

    private float gutter() {
        return dp(18);
    }

    private float header() {
        return dp(18);
    }

    private float cellSize(int width) {
        int weeks = calendar == null ? 1 : calendar.weeks();
        float fit = (width - gutter()) / weeks;
        return Math.min(fit, dp(26));
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = (int) (header() + 7 * cellSize(width) + dp(2));
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (calendar == null) {
            return;
        }
        float size = cellSize(getWidth());
        float pad = Math.max(1f, size * 0.12f);
        long max = Math.max(1L, calendar.maxDay());

        for (int row = 0; row < 7; row++) {
            canvas.drawText(ROW_LABELS[row], 0, header() + row * size + size * 0.72f, label);
        }
        int lastMonth = -1;
        for (int week = 0; week < calendar.weeks(); week++) {
            LocalDate monday = calendar.start.plusWeeks(week);
            float x = gutter() + week * size;
            if (monday.getMonthValue() != lastMonth && monday.getDayOfMonth() <= 7) {
                canvas.drawText(monday.getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        x, header() - dp(6), label);
                lastMonth = monday.getMonthValue();
            }
            for (int row = 0; row < 7; row++) {
                LocalDate day = monday.plusDays(row);
                if (day.isAfter(calendar.end)) {
                    continue;
                }
                Long cents = calendar.centsByDay.get(day);
                if (cents == null || cents <= 0L) {
                    cell.setColor(empty);
                } else {
                    int level = (int) Math.min(4L, (cents * 4L + max - 1L) / max);
                    cell.setColor(withAlpha(accent, 0.25f + 0.1875f * level));
                }
                float y = header() + row * size;
                box.set(x + pad, y + pad, x + size - pad, y + size - pad);
                canvas.drawRoundRect(box, size * 0.2f, size * 0.2f, cell);
            }
        }
    }

    private static int withAlpha(int colour, float alpha) {
        return Color.argb(Math.round(255 * Math.min(1f, alpha)),
                Color.red(colour), Color.green(colour), Color.blue(colour));
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
