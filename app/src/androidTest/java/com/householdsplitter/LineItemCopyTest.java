package com.householdsplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.core.quality.ItemOrigin;
import com.householdsplitter.data.entity.LineItem;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link LineItem#copy()} has to copy everything.
 *
 * <p>It exists because the edit sheet used to write straight into the object the list
 * adapter was holding. The list is diffed by content, so the old list already carried the
 * new name by the time the saved row came back from the database, the two compared equal,
 * and the view was never rebound: the name was saved and the screen went on showing the
 * old one, which reads as a save button that did nothing on the first press.
 *
 * <p>The danger with a hand-written copy is the field somebody adds later and forgets,
 * which would not fail a test that lists the fields it knows about. So this walks them by
 * reflection: every field gets a value that differs from the default, and the copy is
 * required to carry all of them.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class LineItemCopyTest {

    @Test
    public void everyFieldIsCarriedAcross() throws Exception {
        LineItem original = populated();
        LineItem copy = original.copy();

        assertNotSame("a copy that is the same object fixes nothing", original, copy);

        List<String> missed = new ArrayList<>();
        for (Field field : LineItem.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            Object mine = field.get(original);
            Object theirs = field.get(copy);
            if (mine == null ? theirs != null : !mine.equals(theirs)) {
                missed.add(field.getName() + " (" + mine + " became " + theirs + ")");
            }
        }
        assertTrue("copy() did not carry: " + missed, missed.isEmpty());
    }

    /** Editing the copy must leave the original exactly as it was. */
    @Test
    public void editingTheCopyDoesNotTouchTheOriginal() {
        LineItem original = populated();
        LineItem copy = original.copy();

        copy.name = "Something else entirely";
        copy.lineTotalCents = 9999L;
        copy.quantity = 7;

        assertEquals("Powerblend Hoodie", original.name);
        assertEquals(1699L, original.lineTotalCents);
        assertEquals(3, original.quantity);
    }

    /** Every field set to something distinguishable from its default. */
    private static LineItem populated() {
        LineItem item = new LineItem();
        item.id = 42L;
        item.orderId = 7L;
        item.name = "Powerblend Hoodie";
        item.rawOcrText = "CPM253FH68 POWERBLEND HOODIE";
        item.quantity = 3;
        item.lineTotalCents = 1699L;
        item.unitPriceText = "$5.66/ea";
        item.scope = Scope.PERSONAL;
        item.sourceSection = "delivered";
        item.needsReview = true;
        item.reviewReasonsCsv = "LOW_CONFIDENCE";
        item.position = 4;
        item.sourceImageIndex = 2;
        item.boundsLeftPermille = 110;
        item.boundsTopPermille = 220;
        item.boundsRightPermille = 880;
        item.boundsBottomPermille = 260;
        item.origin = ItemOrigin.MANUAL;
        item.parsedName = "POWERBLEND HOODIE";
        item.parsedCents = 1699L;
        item.missVerdict = "FILTERED_AS_CHROME";
        return item;
    }
}
