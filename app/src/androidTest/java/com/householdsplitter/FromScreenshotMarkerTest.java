package com.householdsplitter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.householdsplitter.core.parse.model.OrderField;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.ui.details.OrderDetailsViewModel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * SPEC 7.7.2: a field parsed from a screenshot shows a marker, and loses it once the user
 * edits it.
 *
 * <p>The marker is a claim about where a number came from. Leaving it on a field the user
 * has since typed into makes the screen lie about its own provenance, which matters when the
 * next question is why the total does not reconcile.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class FromScreenshotMarkerTest {

    private OrderDetailsViewModel model;
    private Order order;

    @Before
    public void setUp() {
        TestData.wipe();
        model = new OrderDetailsViewModel(TestData.locator().orderRepository(), 1L);
        order = new Order();
        // What the parser recorded: it read the subtotal and the tax, but not the total.
        order.parsedFieldsCsv = "SUBTOTAL,TAX";
    }

    @Test
    public void onlyTheFieldsTheParserReadAreMarked() {
        assertTrue(model.isFromScreenshot(order, OrderField.SUBTOTAL));
        assertTrue(model.isFromScreenshot(order, OrderField.TAX));
        assertFalse(model.isFromScreenshot(order, OrderField.TOTAL));
        assertFalse(model.isFromScreenshot(order, OrderField.TIP));
    }

    @Test
    public void editingAFieldClearsItsMarkerAndOnlyItsMarker() {
        model.markEdited(OrderField.TAX);

        assertFalse("the edited field must stop claiming it came from the screenshot",
                model.isFromScreenshot(order, OrderField.TAX));
        assertTrue("an untouched field keeps its marker",
                model.isFromScreenshot(order, OrderField.SUBTOTAL));
    }

    @Test
    public void editingIsRememberedAcrossRepeatedChecks() {
        model.markEdited(OrderField.SUBTOTAL);
        for (int redraw = 0; redraw < 5; redraw++) {
            assertFalse(model.isFromScreenshot(order, OrderField.SUBTOTAL));
        }
    }

    @Test
    public void anOrderWithNothingParsedIsMarkedNowhere() {
        Order manual = new Order();
        for (OrderField field : OrderField.values()) {
            assertFalse(model.isFromScreenshot(manual, field));
        }
    }

    @Test
    public void aNullOrderIsHandled() {
        for (OrderField field : OrderField.values()) {
            assertFalse(model.isFromScreenshot(null, field));
        }
    }
}
