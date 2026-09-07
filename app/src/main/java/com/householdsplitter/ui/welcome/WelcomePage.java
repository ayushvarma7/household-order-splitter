package com.householdsplitter.ui.welcome;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

/**
 * One page of the introduction.
 *
 * <p>Three of them, because the product takes three sentences to explain: where the numbers
 * come from, what the user does with them, and what they get back. A fourth page would be
 * something to swipe past rather than something to read.
 */
final class WelcomePage {

    @DrawableRes
    final int icon;
    @StringRes
    final int title;
    @StringRes
    final int body;

    private WelcomePage(int icon, int title, int body) {
        this.icon = icon;
        this.title = title;
        this.body = body;
    }

    static WelcomePage of(@DrawableRes int icon, @StringRes int title, @StringRes int body) {
        return new WelcomePage(icon, title, body);
    }
}
