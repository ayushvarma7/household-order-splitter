package com.householdsplitter.core.insight;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Sorts an item name into a category and a sub-category, so spending can be shown as a
 * zoomable picture instead of one long list.
 *
 * <p>The receipts carry no category at all, so this reads the words in the name. It is a
 * plain keyword table and not a model, on purpose: it is predictable (the same name always
 * lands in the same place), it runs offline, and a wrong placement can be fixed by adding a
 * word to a row of this table and a line to its test.
 *
 * <p>Rows are tried in order and the first match wins, so a specific phrase has to come
 * before the general word it contains: "ice cream" sits above "cream", and "peanut butter"
 * above "butter". Anything that matches nothing lands in {@link #OTHER}, which is shown
 * rather than hidden, because a bubble labelled "Everything else" is honest and a silently
 * dropped item would make the total look wrong.
 */
public final class ItemCategorizer {

    public static final String OTHER = "Other";
    public static final String OTHER_SUB = "Everything else";

    /**
     * Money that was spent but is not in any row: the bill printed more than the rows add
     * up to, because the reader missed items. It is a category of its own so that it shows
     * up in the picture, instead of the picture quietly adding up to less than was paid.
     */
    public static final String MISSING = "Missing from the read";
    public static final String MISSING_SUB = "Not itemised";
    public static final String MISSING_ITEM = "Unread items";

    /** Where a name belongs. */
    public static final class Placement {

        private final String category;
        private final String subCategory;

        public Placement(String category, String subCategory) {
            this.category = category;
            this.subCategory = subCategory;
        }

        public String category() {
            return category;
        }

        public String subCategory() {
            return subCategory;
        }
    }

    private static final class Rule {

        final String category;
        final String subCategory;
        final Pattern pattern;

        Rule(String category, String subCategory, String... words) {
            this.category = category;
            this.subCategory = subCategory;
            StringBuilder alternatives = new StringBuilder();
            for (String word : words) {
                if (alternatives.length() > 0) {
                    alternatives.append('|');
                }
                alternatives.append(Pattern.quote(word));
            }
            // A word, an optional plural, and nothing glued on either side, so "oat" does
            // not match "boat" and "pea" does not match "peach".
            this.pattern = Pattern.compile("(?<![a-z])(?:" + alternatives + ")(?:e?s)?(?![a-z])");
        }
    }

    private static final List<Rule> RULES = new ArrayList<>();

    private static void rule(String category, String sub, String... words) {
        RULES.add(new Rule(category, sub, words));
    }

    static {
        // Specific phrases first.
        rule("Snacks", "Frozen treats", "ice cream", "gelato", "popsicle", "frozen yogurt",
                "sorbet");
        rule("Pantry", "Spreads", "peanut butter", "almond butter", "jam", "jelly", "honey",
                "nutella", "maple syrup");
        rule("Drinks", "Coffee and tea", "coffee", "espresso", "tea", "kombucha");
        rule("Drinks", "Soda", "soda", "cola", "coke", "pepsi", "sprite", "dr pepper",
                "root beer", "ginger ale", "energy drink");
        rule("Drinks", "Water and juice", "sparkling water", "seltzer", "water", "juice",
                "lemonade", "gatorade");

        // Packaged snacks before the produce they are made from: "potato chips" and "corn
        // tortilla chips" are snacks, not vegetables.
        rule("Snacks", "Salty", "chips", "tortilla chips", "popcorn", "pretzel", "cracker",
                "nuts", "peanuts", "almonds", "cashews", "trail mix", "jerky");
        rule("Snacks", "Sweet", "cookie", "chocolate", "candy", "gummy", "gummies", "brownie",
                "cake", "donut", "doughnut", "pastry", "cupcake");
        rule("Snacks", "Bars", "granola bar", "protein bar", "snack bar", "cereal bar");

        rule("Vegetables", "Leafy greens", "spinach", "kale", "lettuce", "romaine", "arugula",
                "cabbage", "chard", "salad");
        rule("Vegetables", "Roots", "potato", "carrot", "onion", "garlic", "beet", "radish",
                "ginger", "turnip", "shallot", "yam");
        rule("Vegetables", "Tomatoes and peppers", "tomato", "pepper", "cucumber", "eggplant",
                "zucchini", "squash", "jalapeno");
        rule("Vegetables", "Cruciferous", "broccoli", "cauliflower", "brussels", "asparagus",
                "celery", "green bean", "corn", "okra", "mushroom");

        rule("Fruit", "Berries", "strawberry", "strawberries", "blueberry", "blueberries",
                "raspberry", "raspberries", "blackberry", "blackberries", "grape", "cherry",
                "cherries");
        rule("Fruit", "Citrus", "orange", "lemon", "lime", "grapefruit", "clementine",
                "mandarin", "tangerine");
        rule("Fruit", "Everyday fruit", "banana", "apple", "pear", "peach", "plum", "melon",
                "watermelon", "pineapple", "mango", "avocado", "kiwi", "papaya");

        rule("Meat and fish", "Poultry", "chicken", "turkey", "duck", "wings", "drumstick");
        rule("Meat and fish", "Beef and pork", "beef", "steak", "pork", "bacon", "sausage",
                "ham", "ground beef", "ribs", "hot dog", "lamb", "brisket");
        rule("Meat and fish", "Fish", "salmon", "tuna", "shrimp", "fish", "tilapia", "cod",
                "crab", "lobster", "prawn");

        rule("Dairy", "Milk and cream", "milk", "oat milk", "half and half", "cream",
                "creamer", "butter");
        rule("Dairy", "Cheese", "cheese", "cheddar", "mozzarella", "parmesan", "feta",
                "brie", "gouda");
        rule("Dairy", "Yogurt and eggs", "yogurt", "yoghurt", "egg", "kefir");

        rule("Bakery", "Bread", "bread", "bagel", "bun", "roll", "tortilla wrap", "pita",
                "croissant", "muffin", "baguette", "naan", "toast");

        rule("Pantry", "Grains and pasta", "rice", "pasta", "spaghetti", "noodle", "oats",
                "oatmeal", "cereal", "quinoa", "couscous", "macaroni", "granola");
        rule("Pantry", "Oils and sauces", "olive oil", "oil", "vinegar", "sauce", "ketchup",
                "mustard", "mayo", "mayonnaise", "dressing", "salsa", "soy sauce", "hot sauce");
        rule("Pantry", "Canned and dry", "beans", "chickpea", "lentil", "canned", "broth",
                "stock", "soup", "tomato paste", "peas");
        rule("Pantry", "Baking and spices", "flour", "sugar", "baking", "yeast", "spice",
                "salt", "cinnamon", "paprika", "cumin", "vanilla", "cocoa", "seasoning");

        rule("Frozen", "Frozen meals", "frozen", "pizza", "waffles", "fries", "nuggets",
                "burrito", "dumplings", "ice");

        rule("Household", "Cleaning", "detergent", "bleach", "cleaner", "soap", "dish",
                "sponge", "laundry", "disinfectant", "wipes");
        rule("Household", "Paper and bags", "paper towel", "toilet paper", "tissue", "napkin",
                "trash bag", "foil", "wrap", "zip");
        rule("Household", "Personal care", "shampoo", "toothpaste", "deodorant", "lotion",
                "razor", "conditioner", "floss", "mouthwash", "sunscreen");
    }

    private ItemCategorizer() {
    }

    /** The first row whose words appear in the name, or {@link #OTHER}. */
    public static Placement place(String itemName) {
        String text = itemName == null ? "" : itemName.toLowerCase().replaceAll("[^a-z ]", " ");
        for (Rule rule : RULES) {
            if (rule.pattern.matcher(text).find()) {
                return new Placement(rule.category, rule.subCategory);
            }
        }
        return new Placement(OTHER, OTHER_SUB);
    }
}
