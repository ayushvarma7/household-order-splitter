package com.householdsplitter.core.insight;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ItemCategorizerTest {

    private static void assertPlaced(String name, String category, String sub) {
        ItemCategorizer.Placement place = ItemCategorizer.place(name);
        assertEquals(name + " category", category, place.category());
        assertEquals(name + " sub-category", sub, place.subCategory());
    }

    @Test
    public void aSpecificPhraseBeatsTheGeneralWordInsideIt() {
        assertPlaced("Vanilla Ice Cream, 48 oz", "Snacks", "Frozen treats");
        assertPlaced("Creamy Peanut Butter", "Pantry", "Spreads");
        assertPlaced("Whole Milk, 1 Gallon", "Dairy", "Milk and cream");
    }

    @Test
    public void snacksMadeFromProduceStaySnacks() {
        assertPlaced("Corn Tortilla Chips", "Snacks", "Salty");
        assertPlaced("Kettle Potato Chips", "Snacks", "Salty");
    }

    @Test
    public void aWordIsNotMatchedInsideAnotherWord() {
        // "oat" is a grain but not part of "boat", and "pea" is not part of "peach".
        assertPlaced("Boat Cleaning Kit", "Other", "Everything else");
        assertPlaced("Peaches", "Fruit", "Everyday fruit");
    }

    @Test
    public void pluralsMatchTheirSingular() {
        assertPlaced("Bananas", "Fruit", "Everyday fruit");
        assertPlaced("Roma Tomatoes", "Vegetables", "Tomatoes and peppers");
    }

    @Test
    public void drinksAreSortedByWhatTheyAre() {
        assertPlaced("Diet Coke 12 pack", "Drinks", "Soda");
        assertPlaced("Orange Juice", "Drinks", "Water and juice");
        assertPlaced("Coffee Beans", "Drinks", "Coffee and tea");
    }

    @Test
    public void whatMatchesNothingIsKeptAsOther() {
        assertPlaced("Gift card", ItemCategorizer.OTHER, ItemCategorizer.OTHER_SUB);
        assertPlaced("", ItemCategorizer.OTHER, ItemCategorizer.OTHER_SUB);
        assertPlaced(null, ItemCategorizer.OTHER, ItemCategorizer.OTHER_SUB);
    }
}
