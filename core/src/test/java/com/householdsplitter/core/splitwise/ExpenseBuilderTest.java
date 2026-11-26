package com.householdsplitter.core.splitwise;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

public class ExpenseBuilderTest {

    private static Map<String, String> build(long payer, ExpenseBuilder.Share... shares) {
        return ExpenseBuilder.build("Walmart", null, "2026-11-01T12:00:00Z", "USD", 77L, payer,
                Arrays.asList(shares)).form();
    }

    @Test
    public void costIsTheSumOfOwedSharesAndPayerPaysIt() {
        Map<String, String> form = build(1L,
                new ExpenseBuilder.Share(1L, 1145), new ExpenseBuilder.Share(2L, 1355));
        assertEquals("25.00", form.get("cost"));
        assertEquals("25.00", form.get("users__0__paid_share"));
        assertEquals("11.45", form.get("users__0__owed_share"));
        assertEquals("0.00", form.get("users__1__paid_share"));
        assertEquals("13.55", form.get("users__1__owed_share"));
        assertEquals("77", form.get("group_id"));
        assertEquals("USD", form.get("currency_code"));
    }

    @Test
    public void payerOutsideTheSharesIsAddedWithNothingOwed() {
        Map<String, String> form = build(9L, new ExpenseBuilder.Share(2L, 500));
        assertEquals("9", form.get("users__1__user_id"));
        assertEquals("5.00", form.get("users__1__paid_share"));
        assertEquals("0.00", form.get("users__1__owed_share"));
    }

    @Test
    public void membersMappedToTheSameUserMergeIntoOneLine() {
        Map<String, String> form = build(1L,
                new ExpenseBuilder.Share(2L, 101), new ExpenseBuilder.Share(2L, 102),
                new ExpenseBuilder.Share(1L, 1));
        assertEquals("2.04", form.get("cost"));
        assertEquals("2.03", form.get("users__0__owed_share"));
        assertFalse(form.containsKey("users__3__user_id"));
        assertFalse(form.containsKey("users__2__user_id"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void zeroTotalIsRefused() {
        build(1L, new ExpenseBuilder.Share(1L, 0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeShareIsRefused() {
        build(1L, new ExpenseBuilder.Share(1L, -5), new ExpenseBuilder.Share(2L, 500));
    }

    @Test(expected = IllegalArgumentException.class)
    public void blankDescriptionIsRefused() {
        ExpenseBuilder.build(" ", null, "2026-11-01T12:00:00Z", "USD", 1L, 1L,
                Arrays.asList(new ExpenseBuilder.Share(1L, 100)));
    }
}
