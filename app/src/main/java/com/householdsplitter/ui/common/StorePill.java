package com.householdsplitter.ui.common;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;

import com.householdsplitter.R;
import com.householdsplitter.core.parse.StoreKind;

/**
 * The store chip on an order row: a coloured outline and a coloured label, never a fill.
 *
 * <p>The fill is what makes this safe. A row already carries a filled status chip in one of
 * the semantic container colours, and those own green, amber and red. Drawing store
 * identity as a second filled chip would put it on the same visual axis as status, so a
 * green "Amazon Fresh" pill beside a "Draft" order would say settled to anyone reading
 * colour before text. Measured, a pale green store fill sits 10.9 CIEDE2000 from
 * {@code success_container}, which is not a distinction; these outlined colours sit above
 * 44 from every status container because they are dark and saturated where the status fills
 * are pale.
 *
 * <p>So: filled means state, outlined means identity. The two never compete, and Walmart
 * keeps its blue and Amazon Fresh its green.
 *
 * <p>Colour is not the only signal either. The chip contains the store's name, so it
 * survives greyscale and colour blindness on its own.
 */
public final class StorePill {

    private StorePill() {
    }

    /** Paints {@code chip} as the outlined identity of {@code store}. */
    public static void apply(TextView chip, StoreKind store) {
        Context context = chip.getContext();
        StoreKind kind = store == null ? StoreKind.WALMART : store;
        @ColorInt int colour = ContextCompat.getColor(context, colourRes(kind));

        GradientDrawable outline = new GradientDrawable();
        outline.setShape(GradientDrawable.RECTANGLE);
        outline.setCornerRadius(context.getResources().getDimension(R.dimen.radius_pill));
        outline.setColor(Color.TRANSPARENT);
        outline.setStroke(
                context.getResources().getDimensionPixelSize(R.dimen.store_pill_stroke), colour);

        chip.setBackground(outline);
        chip.setTextColor(colour);
        chip.setText(kind.displayName());
        chip.setContentDescription(
                context.getString(R.string.store_pill_description, kind.displayName()));
    }

    private static int colourRes(StoreKind store) {
        switch (store) {
            case AMAZON_FRESH:
                return R.color.store_amazon_fresh;
            case RESTAURANT:
                return R.color.store_restaurant;
            case WALMART:
            default:
                return R.color.store_walmart;
        }
    }
}
