# Parsing a Walmart order page

This is the file to edit when Walmart changes its layout. Everything described here lives in
`core/src/main/java/com/householdsplitter/core/parse/walmart/`, is plain Java, and is tested
against fixtures traced off real screenshots in
`core/src/test/java/com/householdsplitter/core/parse/`.

Two properties are worth knowing before changing anything:

- **All geometry is integer permille of the image dimensions.** Not pixels, and not
  floating point fractions. A tablet screenshot and a phone screenshot behave identically,
  and the results are exactly reproducible. Every constant lives in `ParseTuning`.
- **Text recognition is the only non-deterministic part.** Given the recognised text and its
  boxes, which line belongs to which row and which amount is the line price are decided by
  the rules below with no guessing. If a name comes out truncated, that is a bug here, not
  an OCR limitation. What is genuinely outside this code is glyph recognition: a recogniser
  may read `oz` as `0Z`. No attempt is made to correct characters inside a product name,
  because that would be inventing content. The editable review screen exists for it, and the
  on-device test folds only known lookalike pairs so that a glyph misread cannot masquerade
  as a parsing bug and a parsing bug cannot hide behind one.

---

## The pipeline

For each screenshot, in the order the user chose:

1. Load, downscale so the long edge is at most 2048px, run ML Kit text recognition, and
   convert the result into plain `OcrElement` records. This is the only Android-aware step.
2. Crop the top 5% and bottom 3%, the status bar and the gesture bar.
3. Group the surviving elements into **bands**: everything whose vertical spans overlap by
   at least half the shorter box's height, sorted left to right.
4. Classify every band.
5. Discard the header zone.
6. Discard the rating carousel.
7. Propagate section names downward.
8. Find where the item region ends.
9. Find the line prices.
10. Assemble the item blocks.
11. Extract the order-level fields.

Then stitch the screenshots together.

The band, not the text line, is the unit of reasoning. A Walmart row's price is right
aligned against the first line of a name that wraps, and the summary block's amounts sit on
the same horizontal band as their labels. Both are band statements.

---

## The one figure that matters

The price for a row is the final amount billed for it. Nothing else on the row is a price as
far as this app is concerned.

That has two consequences worth stating before the algorithm below.

**A struck-through original loses, silently.** Where the price column prints two amounts, the
right-most is what was charged. There is no warning and no review flag for it, because taking
the charged figure is the rule rather than an exception worth interrupting anyone about. This
is also what turns the free-delivery line's `$9.95 $0` into zero.

**A left-hand amount is never a price.** That column carries unit prices such as `$3.94/lb`,
which describe what a pound costs rather than what the household paid. They are captured into
`unitPriceText` because SPEC 8.3.3 asks for it, kept out of the name, and not shown anywhere
in the interface: a second figure beside the real one only invites the reader to wonder which
they are paying.

---

## Row grouping, the key algorithm

A Walmart item row is not one line. The name wraps over two to four lines, metadata lines
sit beneath it, and the price is right aligned against the **first** line of the block:

```
  [thumb]   Great Value Triple Cheddar          $1.97
            Finely Shredded Cheese, 8 oz
            Bag
            $3.94/lb
            Qty 1
                                    + Add
  Review item
```

Pairing a price with the text at its own y-coordinate captures `Great Value Triple Cheddar`
and silently drops the rest. So instead:

1. A **price token** is an element matching `^-?\$?\d{1,3}(,\d{3})*\.\d{2}$`.
2. It is a **line price** when its horizontal centre is in the right-most 30% of the width
   and its glyph height is at least 85% of the median element height. OCR reports no font
   weight, so relative height stands in for the spec's "larger or bolder than body text".
   The threshold sits under the median on purpose: Walmart's price and body text are often
   the same size, and missing a price loses an entire row.
3. It is a **unit price** instead when the element itself is a complete unit price such as
   `$3.94/lb`, or when the element immediately after it is a bare suffix such as `/lb`. The
   suffix patterns are written tolerantly, because these are the confusions a recogniser
   actually makes on this text: the `l` of `/lb` gets dropped or read as `1` or `I`, and the
   `o` of `/oz` gets read as a zero. A unit price that goes unrecognised does not merely
   lose the unit price, it leaks the whole fragment into the product name.
   Unit prices are captured for display and never charged. Note the asymmetry: the backward
   neighbour test only fires for a *bare* suffix, never for a complete unit price sitting to
   the left of a line price, which would otherwise disqualify the line price itself.
4. A block **opens** at each line price, starting at that band's top.
5. It **ends** at whichever comes first: the next line price, a section header, a chrome
   band, or the start of the summary region.
6. Within the block, every element to the left of the **price column** is joined in reading
   order into the candidate name. The boundary is this block's own line price, less a small
   gutter, not a fixed fraction of the width. SPEC 8.3.6 says "the left 65%", but that
   fraction describes where the name column sits, it is not a place to cut words off: a long
   name line legitimately runs past it, and slicing at the fraction silently drops the tail.
   Real captures showed exactly that, losing the `12` from
   `Cheese Snack, 9 oz Bag, 12` and the `8 oz` from `Finely Shredded Cheese, 8 oz`. The
   fraction remains as a fallback for a block whose price sits somewhere unexpected.
7. Metadata lines are stripped: `Qty N`, `Multipack Quantity: N`, a bare unit price, and a
   bare size fragment that already appears inside the name.
8. `Qty N` sets the quantity; absent, it is 1.
9. What remains, whitespace normalised, is the name. The untouched original is kept in
   `rawOcrText` and shown read-only in the edit sheet.

A block whose name comes out empty is discarded rather than becoming a nameless row.

---

## The chrome filter

Interface furniture that must never become a line item. Matching is case-insensitive against
a normalised form with trailing punctuation removed, so stray OCR noise does not defeat it.
The full list is in `ChromeFilter`.

| Group | Examples |
|---|---|
| Buttons and links | `+ Add`, `Review item`, `View`, `View delivery photo`, `Reorder`, `Buy it again`, `Start a return`, `Get help` |
| Card headers | `Payment method`, `Charge history`, `Your transaction activity for this order` |
| Payment lines | `VISA`, `Mastercard`, `Amex`, `Discover`, `EBT`, anything ending `Ending in ####` |
| Delivery status | `Delivery dropped off on ...`, `Arriving ...`, `Want to see what was substituted?` |
| Status bar | clocks and screen-recording timers such as `02:53`, battery percentages, `LTE` / `5G` / `Wi-Fi` |

Three traps deserve their own note.

**The sticky header is never scanned.** The blue app bar is pinned to the top of every order
screenshot and carries a cart printing the live basket value, commonly `$0.00`, squarely in
the right-hand price column. That is current cart state, not this delivered order's data.
The order date is read off the bar first, then the whole zone above `headerZonePermille` is
discarded. This is zone based rather than detection based on purpose: relying on spotting
the bar would leave the cart readable whenever the date title failed to recognise, and
relying on the nameless-row guard to clean up afterwards would mean one stray word beneath
the bar turns the cart into a phantom purchase.

**The rating carousel is not a shopping list.** Walmart renders a horizontally scrolling
strip of product cards near the top of the page, each a product image, a name such as
`Fresh Banana, Each`, and five empty stars. They are rating prompts. Emitting them as line
items is the single most likely parsing failure. Two defences: structurally, a block only
opens at a line price and grows downward, so a card with no price in the right-hand column
can never open one; explicitly, any band carrying a star glyph and its immediate neighbours
are marked as chrome, so the rule holds even if the carousel moves.

**`N items delivered` counts units, not rows.** An order reading `33 items delivered` may
hold 18 rows, because quantities are summed. Nothing validates the parse against that
number. Reconciliation is done on money, always.

---

## Section headers

Recognised: `N items delivered`, `N substituted`, `N shopped`, `N unavailable`,
`N cancelled`, `N refunded`. A header opens a section and every row beneath carries its
name until the next header.

Rows from `unavailable`, `cancelled` and `refunded` sections default to excluded, so nobody
is charged for them, but they still appear in review clearly marked in case they were in
fact charged.

---

## Order-level fields

| Printed label | Field |
|---|---|
| `Subtotal` | stated subtotal |
| `Taxes`, `Tax` | tax |
| `Driver tip`, `Tip` | tip |
| `Delivery fee`, `Free delivery from store`, `Shipping` | delivery fee |
| `Service fee`, `Bag fee`, `Below minimum fee` | other fees, summed |
| `Savings`, `Discount`, `Promo` | discount, stored positive |
| `Total` | stated total |

Labels are matched **exactly**, never as substrings, because `Subtotal` contains `total` and
a substring test would read the subtotal as the order total and corrupt every figure
downstream.

Where a band prints two amounts, the right-most one wins and the other is treated as a
struck-through original. This is what turns the free-delivery line's `$9.95 $0` into zero,
and it tolerates an amount written without cents. Both the resolution and any disagreement
between screenshots are surfaced as warnings rather than applied silently.

The order date comes from the app-bar title, `<Mon> DD, YYYY order`, matched anywhere on
that band rather than anchored to the whole of it, because the bar also carries a back
chevron and the cart. The default order label is `<Mon> DD Walmart`, and it is editable.

`Order# ...` is captured and then excluded from the items. It backs the duplicate-import
guard, which warns before saving a second order with the same number.

---

## Stitching several screenshots

Consecutive screenshots overlap deliberately, because the user scrolls a little and shoots
again. That makes two jobs necessary and one forbidden.

- **De-duplicate across the boundary.** Two rows with the same normalised name and the same
  price, from different screenshots, within a short window of each other, are one row.
- **Do not de-duplicate within a screenshot,** and do not de-duplicate rows separated by
  other items. A household can genuinely buy the same thing twice, and it will be listed
  twice.
- **A repeated section header does not restart its section.** `16 shopped` legitimately
  appears at the bottom of one screenshot and the top of the next.

An item cut by a screenshot edge yields a partial block, often a lone `Qty 1` with no name
and no price. Because a block only opens at a line price and a nameless block is discarded,
such fragments never become rows.

The summary block is taken from whichever screenshot contains it. If two disagree, the later
one wins and the conflict is recorded as a warning.

---

## Tuning constants

All in `ParseTuning`, all permille of the image dimensions unless stated.

| Constant | Default | Meaning |
|---|---|---|
| `topCropPermille` | 50 | status bar |
| `bottomCropPermille` | 30 | gesture bar |
| `headerZonePermille` | 120 | sticky app bar, including the cart |
| `linePriceZoneStartPermille` | 700 | a line price sits right of this |
| `nameZoneEndPermille` | 650 | name text sits left of this |
| `linePriceMinHeightPermilleOfMedian` | 850 | how large a price must be relative to body text |
| `dedupeWindow` | 4 | how many rows back a cross-boundary duplicate may sit |
| `minConfidencePercent` | 50 | below this the row is flagged for review |
| `minNameLength` | 3 | shorter names are flagged |
| `priceOutlierCents` | 30000 | larger prices are flagged |
| `maxImageDimensionPx` | 2048 | downscale target |
| `bandOverlapPermille` | 500 | vertical overlap needed to join a band |

---

## When Walmart changes something

- **A name comes out truncated:** the block ended early. Check whether a new element between
  the name lines is being classified as chrome, and check `nameZoneEndPermille` against the
  new left column.
- **A button appears as an item:** add it to `ChromeFilter`.
- **Prices are missed entirely:** check `linePriceZoneStartPermille` against the new right
  column, and lower `linePriceMinHeightPermilleOfMedian` if the price font shrank.
- **A summary figure is wrong:** check the label spelling against the exact-match table in
  `OrderFieldExtractor`.

In every case, trace the new screenshot into a fixture first and write the failing test.
`RealOrderFixtureTest` shows the pattern: place each string where it appears, then assert
the extraction by string equality rather than approximately.
