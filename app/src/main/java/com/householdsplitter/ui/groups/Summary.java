package com.householdsplitter.ui.groups;

import android.content.Context;

import com.householdsplitter.R;
import com.householdsplitter.core.money.CurrencyFormat;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.entity.OrderStatus;
import com.householdsplitter.di.ServiceLocator;

import java.util.List;
import java.util.Locale;

/**
 * What one group is worth, in a line.
 *
 * <p>Counts rather than balances. A balance means reading every settled order and running
 * the whole split, which is what the analytics screen is for and far too much to do for
 * each of several groups on a list that is glanced at. People, orders and what they added
 * up to are three reads and answer the question this screen is actually asked: which of
 * these is the one I want?
 */
final class Summary {

    final Household group;
    private final int people;
    private final int orders;
    private final int drafts;
    private final long totalCents;

    private Summary(Household group, int people, int orders, int drafts, long totalCents) {
        this.group = group;
        this.people = people;
        this.orders = orders;
        this.drafts = drafts;
        this.totalCents = totalCents;
    }

    /** Reads this group's counts. Call from a disk executor. */
    static Summary of(ServiceLocator locator, Household group) {
        int people = locator.database().memberDao().getActiveSync(group.id).size();
        List<Order> orders = locator.database().orderDao().getAllSync(group.id);
        int drafts = 0;
        long total = 0L;
        for (Order order : orders) {
            if (order.status == OrderStatus.SETTLED) {
                total += order.statedTotalCents;
            } else {
                drafts++;
            }
        }
        return new Summary(group, people, orders.size(), drafts, total);
    }

    /**
     * The line under the group's name.
     *
     * <p>A draft count is mentioned only when there is one, because "0 drafts" is noise
     * about a thing that is not happening, and an unfinished order is the single most
     * useful thing this screen can remind somebody of.
     */
    String describe(Context context, String currencySymbol, Locale locale) {
        CurrencyFormat money = new CurrencyFormat(currencySymbol, locale);
        String base = context.getResources().getQuantityString(
                R.plurals.groups_people, people, people);
        if (orders == 0) {
            return base + context.getString(R.string.groups_meta_separator)
                    + context.getString(R.string.groups_no_orders);
        }
        String spend = base
                + context.getString(R.string.groups_meta_separator)
                + context.getResources().getQuantityString(
                        R.plurals.groups_orders, orders, orders)
                + context.getString(R.string.groups_meta_separator)
                + money.format(totalCents);
        if (drafts > 0) {
            spend += context.getString(R.string.groups_meta_separator)
                    + context.getResources().getQuantityString(
                            R.plurals.groups_drafts, drafts, drafts);
        }
        return spend;
    }
}
