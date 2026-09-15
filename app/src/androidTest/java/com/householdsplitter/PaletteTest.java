package com.householdsplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.content.Context;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.householdsplitter.ui.theme.Palette;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * That choosing a colour scheme changes the accent and nothing that carries meaning.
 *
 * <p>Resolves the theme the way a view does, rather than reading the XML back, because the
 * question is what a button will actually be painted and that is decided by the overlay
 * winning or not winning against the base theme.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class PaletteTest {

    private static int resolve(int overlayRes, int attr) {
        Context base = new ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(), R.style.Theme_HouseholdSplitter);
        if (overlayRes != 0) {
            base.getTheme().applyStyle(overlayRes, true);
        }
        TypedValue value = new TypedValue();
        base.getTheme().resolveAttribute(attr, value, true);
        return value.data;
    }

    @Test
    public void theOriginalSchemeAppliesNothingAtAll() {
        // The only way to be certain the theme the app already had is untouched.
        assertEquals(0, Palette.AMETHYST.overlayRes());
    }

    @Test
    public void everyOtherSchemeChangesTheAccent() {
        int original = resolve(0, com.google.android.material.R.attr.colorPrimary);
        for (Palette palette : Palette.values()) {
            if (palette == Palette.AMETHYST) {
                continue;
            }
            int themed = resolve(palette.overlayRes(),
                    com.google.android.material.R.attr.colorPrimary);
            assertNotEquals(palette.name() + " did not change the accent", original, themed);
        }
    }

    @Test
    public void noSchemeDisturbsAColourThatMeansSomething() {
        // Green means settled, red means wrong, and the ten member colours were chosen so
        // people can tell each other apart. A preference about liking plum is not a reason
        // for any of those to move, so no overlay is allowed to touch them.
        Context context = ApplicationProvider.getApplicationContext();
        int success = context.getColor(R.color.success);
        int danger = context.getColor(R.color.danger);
        int warning = context.getColor(R.color.warning);
        int member = context.getColor(R.color.member_1);

        for (Palette palette : Palette.values()) {
            Context themed = new ContextThemeWrapper(context, R.style.Theme_HouseholdSplitter);
            if (palette.overlayRes() != 0) {
                themed.getTheme().applyStyle(palette.overlayRes(), true);
            }
            assertEquals(palette.name(), success, themed.getColor(R.color.success));
            assertEquals(palette.name(), danger, themed.getColor(R.color.danger));
            assertEquals(palette.name(), warning, themed.getColor(R.color.warning));
            assertEquals(palette.name(), member, themed.getColor(R.color.member_1));
        }
    }

    @Test
    public void anUnknownStoredNameFallsBackToTheOriginal() {
        assertEquals(Palette.AMETHYST, Palette.fromKey(null));
        assertEquals(Palette.AMETHYST, Palette.fromKey(""));
        assertEquals(Palette.AMETHYST, Palette.fromKey("teal-that-never-shipped"));
        for (Palette palette : Palette.values()) {
            assertEquals(palette, Palette.fromKey(palette.key()));
        }
    }
}
