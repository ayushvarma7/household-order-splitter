package com.householdsplitter.ui.insights;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.material.color.MaterialColors;
import com.householdsplitter.R;
import com.householdsplitter.core.insight.Patterns;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.ui.common.MemberPalette;

import java.util.HashMap;
import java.util.Map;

/**
 * The household as a web: a dot per person, sized by everything they had a share of, and a
 * line between two people that thickens with what they bought together without the rest of
 * the order. Drawn on a canvas, like the bubbles, so it needs no charting library; the
 * fragment writes the same facts out as text for anyone who cannot see it.
 */
public class SharingWebView extends View {

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint initials = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint name = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int accent;
    private final int faint;

    private Patterns.Web web;
    private Map<Long, Member> members = new HashMap<>();

    public SharingWebView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        accent = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary,
                ContextCompat.getColor(context, R.color.primary));
        faint = ContextCompat.getColor(context, R.color.outline_variant);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(dp(3));
        ring.setColor(ContextCompat.getColor(context, R.color.surface));
        Typeface bold = ResourcesCompat.getFont(context, R.font.inter_bold);
        Typeface medium = ResourcesCompat.getFont(context, R.font.inter_medium);
        initials.setTypeface(bold == null ? Typeface.DEFAULT_BOLD : bold);
        initials.setTextAlign(Paint.Align.CENTER);
        initials.setColor(ContextCompat.getColor(context, R.color.on_member_avatar));
        name.setTypeface(medium == null ? Typeface.DEFAULT : medium);
        name.setTextSize(sp(13));
        name.setColor(ContextCompat.getColor(context, R.color.on_surface));
    }

    public void setWeb(Patterns.Web web, Map<Long, Member> members) {
        this.web = web;
        this.members = members == null ? new HashMap<>() : members;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = (int) Math.min(width * 0.8f, dp(300));
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (web == null || web.nodes.isEmpty()) {
            return;
        }
        int count = web.nodes.size();
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) / 2f - dp(44);
        long biggest = 1L;
        for (Patterns.Node node : web.nodes) {
            biggest = Math.max(biggest, node.cents);
        }

        Map<Long, float[]> at = new HashMap<>();
        for (int i = 0; i < count; i++) {
            // Start at the top and go round clockwise; two people sit side by side.
            double angle = -Math.PI / 2 + (count == 2 ? Math.PI / 2 : 0) + 2 * Math.PI * i / count;
            float x = count == 1 ? cx : cx + (float) (radius * Math.cos(angle));
            float y = count == 1 ? cy : cy + (float) (radius * Math.sin(angle));
            at.put(web.nodes.get(i).memberId, new float[]{x, y, (float) angle});
        }

        long heaviest = Math.max(1L, web.heaviest());
        for (int i = web.edges.size() - 1; i >= 0; i--) {
            Patterns.Edge edge = web.edges.get(i);
            float[] a = at.get(edge.a);
            float[] b = at.get(edge.b);
            if (a == null || b == null) {
                continue;
            }
            float weight = (float) edge.cents / heaviest;
            line.setStrokeWidth(dp(2) + dp(14) * weight);
            line.setColor(i == 0 ? accent : withAlpha(accent, 0.25f + 0.5f * weight));
            canvas.drawLine(a[0], a[1], b[0], b[1], line);
        }

        for (Patterns.Node node : web.nodes) {
            float[] p = at.get(node.memberId);
            Member member = members.get(node.memberId);
            float r = dp(16) + dp(16) * (float) Math.sqrt((double) node.cents / biggest);
            dot.setColor(member == null ? faint
                    : MemberPalette.resolve(getContext(), member.colorHex));
            canvas.drawCircle(p[0], p[1], r, dot);
            canvas.drawCircle(p[0], p[1], r, ring);
            String label = member == null ? "?" : member.name;
            initials.setTextSize(r * 0.8f);
            canvas.drawText(initial(label), p[0], p[1] - (initials.descent() + initials.ascent()) / 2,
                    initials);
            // The name sits outside the dot, away from the middle, so lines never cross it.
            double angle = p[2];
            float lx = count == 1 ? p[0] : p[0] + (float) Math.cos(angle) * (r + dp(10));
            float ly = count == 1 ? p[1] + r + dp(18) : p[1] + (float) Math.sin(angle) * (r + dp(14));
            float cos = (float) Math.cos(angle);
            name.setTextAlign(count == 1 || Math.abs(cos) < 0.3f ? Paint.Align.CENTER
                    : cos > 0 ? Paint.Align.LEFT : Paint.Align.RIGHT);
            canvas.drawText(label, lx, ly - (name.descent() + name.ascent()) / 2, name);
        }
    }

    private static String initial(String label) {
        String trimmed = label.trim();
        return trimmed.isEmpty() ? "?" : trimmed.substring(0, 1).toUpperCase(java.util.Locale.getDefault());
    }

    private static int withAlpha(int colour, float alpha) {
        return Color.argb(Math.round(255 * Math.max(0f, Math.min(1f, alpha))),
                Color.red(colour), Color.green(colour), Color.blue(colour));
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                getResources().getDisplayMetrics());
    }
}
