package com.householdsplitter.core.suggest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Set;

/** SPEC 9.1. */
public class NameNormalizerTest {

    @Test
    public void lowercasesTrimsAndCollapsesWhitespace() {
        assertEquals("whole milk", NameNormalizer.normalize("  Whole   MILK  "));
    }

    @Test
    public void stripsTrailingSizeFragment() {
        assertEquals("great value triple cheddar finely shredded cheese",
                NameNormalizer.normalize("Great Value Triple Cheddar Finely Shredded Cheese, 8 oz Bag"));
        assertEquals("orange juice", NameNormalizer.normalize("Orange Juice, 24 oz"));
        assertEquals("sparkling water", NameNormalizer.normalize("Sparkling Water, 12 Count"));
    }

    @Test
    public void collapsesSimplePlurals() {
        assertEquals("banana", NameNormalizer.normalize("Bananas"));
        assertEquals("fresh strawberry", NameNormalizer.normalize("Fresh Strawberries"));
        // Not everything ending in s is a plural.
        assertEquals("swiss", NameNormalizer.normalize("Swiss"));
    }

    @Test
    public void matchesTheSameProductAcrossTwoOrders() {
        String first = NameNormalizer.normalize("Great Value Whole Milk, 1 Gal");
        String second = NameNormalizer.normalize("great value whole milk,  1 gal ");
        assertEquals(first, second);
    }

    /** SPEC 9.1: the brand prefix comes from the household's own history, nowhere else. */
    @Test
    public void stripsBrandPrefixOnlyWhenTheHouseholdRepeatsIt() {
        Set<String> brands = NameNormalizer.inferBrandPrefixes(Arrays.asList(
                "Great Value Whole Milk",
                "Great Value Shredded Cheese",
                "Great Value White Bread",
                "Marketside Croissants"), 3);

        assertTrue(brands.contains("great value"));
        assertFalse("one purchase is not a brand", brands.contains("marketside"));

        assertEquals("whole milk", NameNormalizer.normalize("Great Value Whole Milk", brands));
        assertEquals("marketside croissant",
                NameNormalizer.normalize("Marketside Croissants", brands));
    }

    @Test
    public void neverStripsTheEntireName() {
        Set<String> brands = NameNormalizer.inferBrandPrefixes(Arrays.asList(
                "Great Value Milk", "Great Value Bread", "Great Value Eggs"), 3);
        assertEquals("great value", NameNormalizer.normalize("Great Value", brands));
    }

    @Test
    public void handlesNullAndEmpty() {
        assertEquals("", NameNormalizer.normalize(null));
        assertEquals("", NameNormalizer.normalize("   "));
    }
}
