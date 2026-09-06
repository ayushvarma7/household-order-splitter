# SPEC — Household Order Splitter (Android, Java)

Version 1.0. This document is the single source of truth for behaviour. Every requirement is numbered so it can be cited, checked off, and tested. `MUST` = mandatory, `SHOULD` = strong default that may be varied with a stated reason, `MAY` = optional.

---

## 1. Product overview

**1.1** The app splits a shared shop (Walmart grocery order) between the members of a household and tells each person exactly what they owe.

**1.2** The input is one or more screenshots of a Walmart order page taken on the user's phone.

**1.3** The output is a per-person total: their share of the household-common items, plus their own items, plus their proportional share of tax and fees.

**1.4** The app replaces a manual spreadsheet in which shared-by-a-subset items had to be hand-divided and typed once per sharer. Eliminating that manual duplication is the core value of the product.

**1.5** The app is offline, single-user, single-device. There is no account, no server, no sync.

**1.6** The app MUST ship with **no household, no members, no sample orders, and no seeded item data**. Every name in the system is entered by the user. No fixture, placeholder, demo record, or test name may reach production code, Room seeds, migrations, or layout defaults.

---

## 2. Glossary

**2.1 Household** — the group. Has a user-supplied name and two or more members.

**2.2 Member** — one person in the household. Has a user-supplied name.

**2.3 Order** — one Walmart shop, imported from screenshots.

**2.4 Participant** — a member who is in on a specific order. Participants are chosen per order and may be a subset of the household.

**2.5 Line item** — one row from the order: a name, a quantity, and a line total.

**2.6 Scope** — how a line item is shared. One of:
- `COMMON` — split across all participants of the order.
- `SUBSET` — split across two or more named members, but not all participants.
- `PERSONAL` — one member only.
- `EXCLUDED` — present but not charged to anyone (refunded, cancelled, wrongly parsed but kept for reference).

**2.7 Common bucket** — the sum of all `COMMON` line items for an order.

**2.8 Per-head share** — common bucket divided by the participant count.

**2.9 Pre-tax total** — a member's per-head share plus their share of every `SUBSET` and `PERSONAL` item.

**2.10 Adjustment** — an order-level amount that is not a line item: tax, delivery fee, tip, service fee, bag fee, discount.

**2.11 Final total** — pre-tax total plus that member's allocated share of every adjustment.

**2.12 Stated total** — the total printed on the Walmart order screenshot. The app's computed totals MUST sum to it.

---

## 3. Non-goals

**3.1** No payment or settlement integration (no Venmo, UPI, PayPal).
**3.2** No Walmart login, API, or scraping. Screenshots only.
**3.3** No cloud sync, no multi-device, no shared/collaborative editing.
**3.4** No receipt storage beyond the local image URI.
**3.5** No support for stores other than Walmart in v1, though the parser SHOULD be written so a second store can be added without changing the domain layer.

---

## 4. Platform and technical constraints

**4.1** Language: **Java 17**. No Kotlin source files anywhere in the project.

**4.2** Because Jetpack Compose is Kotlin-only, the UI MUST use the Android View system: XML layouts + **ViewBinding**. Do not use Data Binding expressions in XML.

**4.3** UI toolkit: Material Components (Material 3 theme), `RecyclerView` with `ListAdapter` + `DiffUtil`, `MaterialToolbar`, `Chip`/`ChipGroup`, `TextInputLayout`.

**4.4** Architecture: single `MainActivity`, Fragments, AndroidX **Navigation Component** with a single nav graph.

**4.5** Pattern: Fragment → `ViewModel` (+ `LiveData`) → Repository → Room DAO. Fragments MUST contain no business logic and no money math.

**4.6** Persistence: **Room**. DAO read methods return `LiveData<...>`; write methods run on a background executor.

**4.7** Concurrency: a shared `AppExecutors` class exposing `diskIO()`, `parsing()`, and `mainThread()`. No `AsyncTask`. No work on the main thread.

**4.8** Dependency injection: **Hilt**, or a hand-written `ServiceLocator`. Pick one, use it everywhere, do not mix.

**4.9** `minSdk 26`, `targetSdk` latest stable, `compileSdk` matching. Gradle Kotlin DSL with a version catalog (`libs.versions.toml`).

**4.10** No network permission is required for the default build. If the optional LLM parser is enabled, `INTERNET` is added in that product flavour only.

**4.11** Third-party libraries limited to: AndroidX, Material Components, Room, Hilt, ML Kit text recognition, and a JSON library. Anything else needs a stated justification.

---

## 5. Data model

**5.1 `Household`**
| Field | Type | Notes |
|---|---|---|
| `id` | long PK autogen | |
| `name` | String NOT NULL | user-supplied, 1–60 chars |
| `createdAt` | long | epoch millis |

**5.2 `Member`**
| Field | Type | Notes |
|---|---|---|
| `id` | long PK autogen | |
| `householdId` | long FK | index |
| `name` | String NOT NULL | user-supplied, 1–40 chars |
| `colorHex` | String | assigned from a palette at creation |
| `sortOrder` | int | display order |
| `isArchived` | boolean | default false |

**5.3 `Order`**
| Field | Type | Notes |
|---|---|---|
| `id` | long PK autogen | |
| `householdId` | long FK | index |
| `label` | String | e.g. "Sep 03 Walmart", user-editable |
| `orderDate` | long | epoch millis |
| `externalOrderNo` | String nullable | parsed order number, unique index where non-null |
| `status` | enum | `DRAFT`, `ASSIGNED`, `SETTLED` |
| `payerMemberId` | long nullable | who fronted the money |
| `taxCents` | long | default 0 |
| `deliveryFeeCents` | long | default 0 |
| `tipCents` | long | default 0 |
| `otherFeeCents` | long | default 0 |
| `discountCents` | long | stored positive, applied negative |
| `statedSubtotalCents` | long | parsed |
| `statedTotalCents` | long | parsed |
| `createdAt` | long | |

**5.4 `OrderImage`** — `id`, `orderId`, `uri`, `position`. One row per screenshot.

**5.5 `OrderParticipant`** — composite PK (`orderId`, `memberId`).

**5.6 `LineItem`**
| Field | Type | Notes |
|---|---|---|
| `id` | long PK autogen | |
| `orderId` | long FK | index |
| `name` | String | editable |
| `rawOcrText` | String | preserved verbatim for debugging |
| `quantity` | int | default 1 |
| `lineTotalCents` | long | the charged amount for the row |
| `unitPriceText` | String nullable | e.g. "$3.94/lb", display only |
| `scope` | enum | §2.6 |
| `sourceSection` | String nullable | e.g. "shopped", "substituted" |
| `needsReview` | boolean | |
| `position` | int | display order |

**5.7 `ItemAssignment`** — `id`, `lineItemId` FK, `memberId` FK, `shares` int default 1. Unique index on (`lineItemId`, `memberId`).

**5.8 `AssignmentMemory`** — `id`, `householdId`, `normalizedName` (indexed), `scope`, `memberIdsCsv`, `lastUsedAt`, `useCount`. Populated only from the user's own confirmed assignments.

**5.9** `COMMON` items MUST NOT have `ItemAssignment` rows. They resolve against `OrderParticipant` at calculation time, so changing the participant list automatically recalculates them.

**5.10** Deleting a member MUST be a soft delete (`isArchived = true`) whenever that member has any assignment or participation row. Historical orders MUST keep rendering their names.

---

## 6. Money rules and algorithms

**6.1** All money is stored and computed as `long` **cents**. `float` and `double` MUST NOT appear anywhere in the money path, including intermediate values and test assertions.

**6.2** Parsing converts a displayed string like `$53.52` to `5352`. A negative or struck-through value is handled per §8.6.

**6.3 Largest remainder split — `MoneySplitter.split(long amount, int[] weights)`**

1. Compute `W = sum(weights)`. If `W <= 0`, throw `IllegalArgumentException`.
2. For each index `i`, compute `base[i] = (amount * weights[i]) / W` using integer division (Java truncates toward zero).
3. Compute `distributed = sum(base)`.
4. Compute `remainder = amount - distributed`.
5. Compute each index's fractional remainder `rem[i] = (amount * weights[i]) % W`.
6. Sort indices by `rem[i]` descending; break ties by ascending index so the result is deterministic.
7. If `remainder > 0`, add 1 cent to each of the first `remainder` indices in that order.
8. If `remainder < 0` (negative amount, e.g. a discount), subtract 1 cent from each of the first `|remainder|` indices.
9. Return the array. **Postcondition, asserted in code:** `sum(result) == amount` exactly.

**6.4 Order calculation — `SplitCalculator.calculate(...)`**, executed in this exact order:

1. Load participants `P` for the order. If `|P| < 1`, abort with `NoParticipantsException`.
2. Filter out every `EXCLUDED` line item.
3. `commonBucket = sum(lineTotalCents)` over all `COMMON` items.
4. If `discountCents > 0` and the discount is not assigned to specific members, subtract it from `commonBucket`. The bucket MAY go negative; this is legal.
5. `commonShares = MoneySplitter.split(commonBucket, ones(|P|))`. Assign `commonShares[i]` to participant `i`, ordered by `Member.sortOrder`.
6. For each `SUBSET` or `PERSONAL` item: read its assignments, build a weight array from `shares`, call `MoneySplitter.split(lineTotalCents, weights)`, and add each result to that member's running total.
7. `preTax[m] = commonShare[m] + sum(item shares for m)` for every member with any allocation.
8. `preTaxTotal = sum(preTax)`.
9. For each adjustment `A` in {tax, deliveryFee, tip, otherFee}, in that order:
   - If `A == 0`, skip it and record a zero share for everyone.
   - If the setting is **proportional** (default): `shareOf[A] = MoneySplitter.split(A, preTax[])` — the weights are the pre-tax totals themselves.
   - If the setting is **equal**: `shareOf[A] = MoneySplitter.split(A, ones(|P|))`.
   - If `preTaxTotal == 0` while `A != 0`, fall back to an equal split and flag it.
10. `final[m] = preTax[m] + sum of that member's adjustment shares`.
11. `computedTotal = sum(final)`.
12. Compare `computedTotal` against `statedTotalCents`. Return the delta in the result; do not silently correct it.
13. **Postcondition, asserted:** `sum(final) == preTaxTotal + tax + deliveryFee + tip + otherFee`.

**6.5** Rounding differences from the old spreadsheet are expected and correct. A person's total may differ by a cent from a float-based sheet; the invariant that matters is §6.4.13.

**6.6** `SplitCalculator` and `MoneySplitter` MUST be plain Java in a package with no `android.*` imports, so they run on the JVM under plain JUnit.

---

## 7. Screens

Navigation graph: `SetupGroupFragment` → `SetupMembersFragment` → `HomeFragment` → `ImportFragment` → `ParsingFragment` → `ReviewItemsFragment` → `OrderDetailsFragment` → `ParticipantsFragment` → `AssignFragment` → `SummaryFragment`. Plus `OrderViewFragment`, `MembersFragment`, `SettingsFragment` reachable from Home.

### S1 — Group setup

**7.1.1 Entry:** first launch only, when no `Household` row exists.
**7.1.2 Layout, top to bottom:** app name; one line of explanatory text; a `TextInputLayout` labelled "Group name"; a "Continue" button.
**7.1.3** The field MUST be empty on load. No hint text that could be mistaken for a value, no pre-filled example.
**7.1.4** "Continue" is disabled until the trimmed input is 1–60 characters.
**7.1.5** On Continue: insert the `Household`, navigate to S2. The back stack MUST NOT allow returning to S1 after a household exists.
**7.1.6** Rotation preserves the typed value.

### S2 — Member setup

**7.2.1 Entry:** from S1, or from S13 for later edits.
**7.2.2 Layout:** title "Who's in <group name>?"; a `TextInputLayout` labelled "Name" with an "Add" button beside it; below, a `RecyclerView` of added members; at the bottom, a "Done" button.
**7.2.3** Each member row shows: a circular avatar with the member's initials on their assigned colour, the name, an edit icon, a delete icon.
**7.2.4** Adding: trimmed name of 1–40 characters. Pressing IME "Done" adds and keeps focus in the field so several names can be typed in a row without touching the screen.
**7.2.5** Duplicate names (case-insensitive, trimmed) MUST be rejected with an inline error.
**7.2.6** Colours are assigned round-robin from a palette of at least 8 visually distinct values.
**7.2.7** Edit opens an inline dialog with the current name pre-filled.
**7.2.8** Delete removes immediately if the member has no history, otherwise archives (§5.10) after a confirmation dialog.
**7.2.9** "Done" is disabled until at least **2** members exist. There is no upper limit and no assumption of any particular count.
**7.2.10** There MUST be no "add sample members", "quick add 4", or similar shortcut.

### S3 — Home

**7.3.1 Entry:** app launch when a household exists.
**7.3.2 Layout:** toolbar with the group name and an overflow menu (Members, Settings, Export all); a `RecyclerView` of orders newest first; a FAB "New order".
**7.3.3** Each order row shows: label, date, computed total, overlapping participant avatars, and a status chip (`Draft`, `Assigned`, `Settled`).
**7.3.4** Tapping a `DRAFT` order resumes it at the step it stopped at (§7.9.14). Tapping an `ASSIGNED` or `SETTLED` order opens S12.
**7.3.5** Empty state: an illustration, the text "No orders yet", and a button that does the same thing as the FAB.
**7.3.6** Long-press an order for a context menu: Rename, Duplicate assignments, Export, Delete (with confirmation).

### S4 — Import

**7.4.1 Entry:** the FAB on S3, or an external share (§8.1).
**7.4.2 Layout:** two large buttons, "Choose screenshots" and "Take photo"; below, a horizontal preview strip of selected images with a remove "×" on each and a drag handle to reorder; a "Continue" button showing the count.
**7.4.3** Picking uses `ActivityResultContracts.PickVisualMedia` / `PickMultipleVisualMedia`. Camera uses `ActivityResultContracts.TakePicture`.
**7.4.4** Order of images matters — it determines stitching order (§8.7). Default order is selection order; the user can drag to change it.
**7.4.5** At least one image is required to continue.
**7.4.6** Selected image URIs MUST be persisted with `takePersistableUriPermission` so they survive a restart.

### S5 — Parsing

**7.5.1** A determinate progress bar showing "Reading screenshot N of M", plus a Cancel button.
**7.5.2** Parsing runs on `AppExecutors.parsing()`. Cancel aborts cleanly and returns to S4 without creating an order.
**7.5.3** On failure, show the reason and offer Retry, Choose different images, or Enter manually.
**7.5.4** "Enter manually" creates an empty order and jumps straight to S6 with an empty list.

### S6 — Review items

**7.6.1 Purpose:** OCR is never perfect. This screen is where the user makes the parse correct before any splitting happens. It MUST be fast to scan and fully editable.
**7.6.2 Layout:** a header banner reporting the reconciliation state (§8.9); a `RecyclerView` of parsed items; a sticky footer with "Add item" and "Continue".
**7.6.3** Each row shows: position number, name (single line, ellipsized), quantity chip if > 1, unit-price text in small grey if present, and the line total right-aligned.
**7.6.4** Rows with `needsReview = true` are tinted and carry a warning icon with a content description explaining why.
**7.6.5** Tapping a row opens an edit sheet with: name field, quantity field, price field (in currency format), a "sourced from" read-only display of `rawOcrText`, and Delete.
**7.6.6** The price field MUST accept only valid currency input and MUST convert to cents without floating point.
**7.6.7** "Add item" appends a blank row and opens the edit sheet on it.
**7.6.8** Swipe-left on a row deletes it with an Undo snackbar lasting at least 5 seconds.
**7.6.9** "Continue" is blocked while any item has an empty name or a zero price, with the offending rows scrolled into view.

### S7 — Order details and adjustments

**7.7.1** Fields, each pre-filled from the parse and each editable: order label, order date (date picker), subtotal, tax, delivery fee, tip, other fees, discount, stated total.
**7.7.2** Fields parsed with confidence show a small "from screenshot" marker; fields the user edits lose it.
**7.7.3** A live reconciliation strip: `items + tax + fees + tip − discount = X` versus `stated total = Y`, with the delta shown in red when non-zero and green with a tick when zero.
**7.7.4** A non-zero delta MUST NOT block progress — it is a warning, because Walmart occasionally reports figures the line items cannot reproduce. It MUST be surfaced again on S10.
**7.7.5** Zero-valued adjustments collapse into a "Add fee / tip / discount" link rather than showing empty fields.

### S8 — Participants

**7.8.1** Title: "Who's in on this order?"
**7.8.2** All non-archived members are listed with checkboxes, **all checked by default**.
**7.8.3** A live line reads "Common items split N ways".
**7.8.4** Continue is disabled below 1 participant.
**7.8.5** Changing participants later (from S10) MUST recalculate the common bucket and MUST preserve every existing item assignment, except that assignments belonging to a removed participant are dropped and their items flagged for reassignment.

### S9 — Assignment

**7.9.1 Purpose:** the core loop. One item at a time, answer "who is this for?".
**7.9.2 Layout:** a linear progress bar and "Item N of M" at the top; a card with the item name, quantity, and price; three quick-action buttons; a `ChipGroup` of participants; a live per-head readout; Back and Next.
**7.9.3 Quick actions, in this order:**
   1. **Common** — everyone in the order. One tap, advances immediately.
   2. **Same as previous** — copies the previous item's assignment. Hidden on item 1.
   3. **Just <member>** — a menu of participants for single-person assignment.
**7.9.4** Selecting chips manually sets `SUBSET`; selecting exactly one sets `PERSONAL`; selecting all participants MUST automatically convert to `COMMON` and say so.
**7.9.5** The readout updates live: with two chips selected on a $3.32 item it reads "$1.66 each".
**7.9.6** Long-press a selected chip to increment that member's `shares`, shown as "×2" on the chip. Long-press again cycles back to ×1.
**7.9.7** An "Exclude" action marks the item `EXCLUDED` and advances.
**7.9.8** "Next" is disabled while nothing is selected, unless the item is excluded.
**7.9.9** "Back" restores the previous item with its selection intact and is available on every item after the first.
**7.9.10** An overflow action "Assign all remaining as common" applies to every unassigned item from the current position onward, after a confirmation dialog naming the count.
**7.9.11** A list icon opens a jump-to-item sheet showing every item with its current assignment, so the user can revisit any item out of order.
**7.9.12 Prefill:** before showing an item, look up `AssignmentMemory` by normalized name (§9). If found, pre-select that assignment and show a subtle "suggested from your last order" label. The suggestion MUST be visibly overridable and MUST never auto-advance.
**7.9.13** There MUST be no built-in list of which products are "usually common". Suggestions come only from this household's own confirmed history.
**7.9.14** Progress MUST be persisted after every item so a kill-and-relaunch resumes on the same item.

### S10 — Summary

**7.10.1 Layout:** order label and date; a per-member list; an order-level footer; action buttons.
**7.10.2** Each member row shows avatar, name, and final total, and expands to show four lines: common share, own items (each with its share), adjustment shares broken out by type, and the final total.
**7.10.3** Footer shows: common bucket and per-head figure, item subtotal, each non-zero adjustment, computed total, stated total, and the reconciliation verdict — a green tick with "Matches bill amount" or a red delta.
**7.10.4** A payer selector; once set, each row reads "<member> owes <payer> $X.XX" and the payer's own row reads "paid $Y.YY".
**7.10.5** Actions: **Share as text**, **Export CSV**, **Edit assignments** (back to S9), **Edit participants** (back to S8), **Mark as settled**.
**7.10.6** If any item is unassigned, S10 MUST NOT compute. It shows a blocking panel listing the unassigned items, each tappable to jump straight to that item in S9.
**7.10.7** "Mark as settled" sets status `SETTLED` and makes the order read-only; unlocking requires an explicit "Reopen" from the overflow with a confirmation.

### S11 — Share and export

**7.11.1** "Share as text" builds the message in §10.2 and fires `ACTION_SEND` with `text/plain`.
**7.11.2** "Export CSV" writes the layout in §10.1 via `ACTION_CREATE_DOCUMENT`, defaulting the filename to `<label>-split.csv`.
**7.11.3** Home's overflow "Export all" writes one CSV containing every order, separated by a blank line and a header row per order.

### S12 — Order view (read-only)

**7.12.1** Same content as S10 with every control disabled except Share, Export, Duplicate, and Reopen.
**7.12.2** A thumbnail strip of the original screenshots, tappable to view full-screen.

### S13 — Members management

**7.13.1** Same list and rules as S2, reached from Home's overflow, with a "Rename group" action at the top.

### S14 — Settings

**7.14.1** Fee and tip allocation: Proportional (default) / Equal.
**7.14.2** Parser: On-device (default) / Cloud.
**7.14.3** Currency symbol, defaulting to the device locale's.
**7.14.4** Backup: export all data as JSON; restore from JSON with a merge-or-replace choice.
**7.14.5** Wipe all data, behind a typed confirmation.
**7.14.6** About: version, and a plainly worded line stating that all data stays on the device.

---

## 8. Parsing specification

This section is written against real Walmart order screenshots. The traps below are observed, not hypothetical.

### 8.1 Entry points

**8.1.1** In-app picker and camera (§7.4).
**8.1.2** A share target: `ACTION_SEND` and `ACTION_SEND_MULTIPLE` with `image/*`, so the user screenshots the Walmart app and shares directly into this app. This is the primary real-world path and MUST land the user on S5 with the images already loaded.
**8.1.3** When shared into an existing `DRAFT` order, offer "Add to current draft" or "Start new order".

### 8.2 Pipeline

**8.2.1** For each image, in order: load → downscale to a max dimension of 2048px preserving aspect ratio → run ML Kit Text Recognition v2 → collect `Text.Element`s with their bounding boxes.
**8.2.2** Work in image pixel coordinates. Normalise every y-coordinate to a fraction of image height so multiple screenshots can be compared.
**8.2.3** Discard the top 5% and bottom 3% of each image by default — the status bar and the gesture bar. This MUST be a tunable constant, not a magic number buried in a loop.

### 8.3 Row grouping — the key algorithm

Walmart item rows are **not** single lines. The name wraps across two to four lines, metadata lines sit below it, and the price is right-aligned against the **first** line of the block. A naive same-band pairing therefore captures only the first fragment of the name.

**8.3.1** Identify **price tokens**: elements matching `^\$?\d{1,3}(,\d{3})*\.\d{2}$`.
**8.3.2** Classify a price token as a **line price** if its horizontal centre lies in the right-most 30% of the content width and it is rendered in a larger or bolder style than body text.
**8.3.3** Classify a price token as a **unit price** if the element or its immediate neighbour matches `/lb`, `/oz`, `/ea`, `/each`, or `¢/`. Unit prices MUST be captured into `unitPriceText` and MUST NOT be used as `lineTotalCents`.
**8.3.4** For each line price, open an item block starting at that price's top y-coordinate.
**8.3.5** The block ends at whichever comes first: the top of the next line price, a section header (§8.5), or a chrome element (§8.4).
**8.3.6** Within the block, all left-column text elements (horizontal centre in the left 65% of content width) are joined in reading order — sorted by y, then x — to form the candidate name.
**8.3.7** Strip metadata lines from the candidate name: any line matching `^Qty \d+$`, `^Multipack Quantity: \d+$`, a bare unit-price pattern, or a bare weight/size fragment that already appears within the name.
**8.3.8** Extract `quantity` from `Qty N`. Default to 1 when absent.
**8.3.9** The remaining joined text, with newlines collapsed to single spaces and whitespace normalised, becomes `name`. Preserve the untouched original in `rawOcrText`.
**8.3.10** Names of two or more lines are normal and MUST NOT be truncated. `"Great Value Triple Cheddar Finely Shredded Cheese, 8 oz Bag"` is one name, not three items.

### 8.4 Chrome to discard

The following MUST be recognised as interface furniture and excluded from item extraction. Matching SHOULD be case-insensitive and tolerant of OCR noise.

**8.4.1** Buttons and links: `Add`, `+ Add`, `Review item`, `View`, `View delivery photo`, `Reorder`, `Buy it again`, `Start a return`, `Get help`.
**8.4.2** Card headers: `Payment method`, `Charge history`, `Your transaction activity for this order`.
**8.4.3** Card and masked payment lines: `VISA`, `Mastercard`, `Ending in ####`.
**8.4.4** Delivery status lines: `Delivery dropped off on <date>`, `Arriving <date>`, `Want to see what was substituted?`.
**8.4.5** Order identifiers: `Order# <digits-digits>` — captured into `externalOrderNo` (§8.6.6), then excluded from items.
**8.4.6** Barcodes and any dense stripe region — ignore purely graphical areas.
**8.4.7** The status bar: clock, battery percentage, signal bars, and **screen-recording timers such as `02:53`**, which OCR happily reads as text near a number.
**8.4.8 The recommendation carousel.** Walmart renders a horizontally scrolling strip of product cards near the top of the order page — a product image, a name such as `Fresh Banana, Each`, and a row of five empty rating stars — that are **rating prompts and suggestions, not purchased items**. These cards have no price in the right-hand column and sit above the `Payment method` card. The parser MUST NOT emit them as line items. Detect them by: presence of star glyphs, absence of a right-aligned line price, a card layout wider than it is tall, and position above the payment/summary block.

### 8.5 Section headers

**8.5.1** Recognise headers matching `^\d+ items? delivered$`, `^\d+ substituted$`, `^\d+ shopped$`, `^\d+ unavailable$`, `^\d+ cancell?ed$`, `^\d+ refunded$`.
**8.5.2** A header opens a section; items below it carry that section name in `sourceSection` until the next header.
**8.5.3** `substituted` and `shopped` items are charged normally. `unavailable`, `cancelled`, and `refunded` items MUST default to `scope = EXCLUDED` and appear in review clearly marked, so the user can include them if they were in fact charged.
**8.5.4** **The `N items delivered` count is a unit count, not a line count.** An order reading `33 items delivered` may contain only 18 rows, because quantities are summed. The parser MUST NOT use it to validate the number of extracted rows. Reconciliation is done on money (§8.9), never on item count.

### 8.6 Order-level fields

**8.6.1** Parse the order date from the app-bar title matching `^<Mon> \d{1,2}, \d{4} order$`, e.g. `Sep 03, 2026 order`.
**8.6.2** Label the order `"<Mon> <DD> Walmart"` by default, matching the existing spreadsheet naming, and leave it editable.
**8.6.3** Recognise these summary labels and capture the amount on the same horizontal band:

| Label as printed | Field |
|---|---|
| `Subtotal` | `statedSubtotalCents` |
| `Taxes` or `Tax` | `taxCents` |
| `Driver tip`, `Tip` | `tipCents` |
| `Delivery fee`, `Free delivery from store`, `Shipping` | `deliveryFeeCents` |
| `Service fee`, `Bag fee`, `Below minimum fee` | `otherFeeCents` (summed) |
| `Savings`, `Discount`, `Promo` | `discountCents` |
| `Total` | `statedTotalCents` |

**8.6.4 Struck-through prices.** A row may print two amounts, the original struck through and the charged one beside it — observed as a `$9.95` struck through followed by `$0` on the free-delivery line. The parser MUST take the **right-most / last** amount on the band as the charged value, and MUST tolerate an amount written without cents (`$0`), normalising it to `0`.
**8.6.5** The same rule applies to discounted item rows: last amount on the band wins, and the struck amount MAY be retained in `rawOcrText`.
**8.6.6** Capture `Order# ...` into `externalOrderNo`.
**8.6.7 Duplicate-import guard.** If an order with the same `externalOrderNo` already exists, warn before creating a second one and offer to open the existing order instead.
**8.6.8** `Total` MUST be distinguished from `Subtotal` by exact label match, not by substring — `Subtotal` contains `total`.

### 8.7 Multi-screenshot stitching

**8.7.1** Screenshots are processed in the user's chosen order and concatenated into one candidate list.
**8.7.2** Consecutive screenshots **overlap deliberately** — the user scrolls a little and shoots again. A section header such as `16 shopped` will legitimately appear at the bottom of one image and the top of the next.
**8.7.3** De-duplicate by `(normalizedName, lineTotalCents)`. Two rows matching on both, appearing within a short window across an image boundary, are one item.
**8.7.4** Do not de-duplicate rows with the same name and price that are separated by other items — a household can genuinely buy two identical things listed separately.
**8.7.5 Edge fragments.** An item cut by the top or bottom of a screenshot yields a partial block — for example a lone `Qty 1` with no name or price at the top of the next image. Such fragments MUST be either merged with their counterpart from the adjacent image or discarded, and MUST NOT produce a nameless or zero-price line item.
**8.7.6** A section header repeated across the boundary MUST NOT restart or duplicate its section.
**8.7.7** After stitching, the summary block (subtotal through total) MUST be taken from whichever screenshot contains it. If two screenshots disagree on a value, prefer the later one and flag the conflict.

### 8.8 Confidence

**8.8.1** Set `needsReview = true` when any of the following holds: ML Kit confidence below the threshold for any element in the name; the name is under 3 characters; quantity is greater than 1; a struck-through price was resolved; the row came from an `unavailable`/`cancelled`/`refunded` section; the row was assembled from an edge fragment; or the price is above a configurable outlier threshold.
**8.8.2** Every `needsReview` row MUST state its reason in the review UI.

### 8.9 Reconciliation

**8.9.1** After parsing, compute `sum(lineTotalCents)` over non-excluded items.
**8.9.2** Compare it against `statedSubtotalCents`. Report the delta.
**8.9.3** Compare `statedSubtotal + tax + fees + tip − discount` against `statedTotal`. Report the delta.
**8.9.4** Both checks are **advisory**: they are displayed prominently but never block the user. A worked example from a real order — subtotal 53.52, delivery 0.00, taxes 0.50, tip 0.00 — sums to 54.02 and matches the printed total exactly; that is the green-tick case.
**8.9.5** Reconciliation is always on money, never on the `N items delivered` count (§8.5.4).

### 8.10 Optional cloud parser

**8.10.1** `LlmReceiptParser` implements the same `ReceiptParser` interface and is selected in Settings.
**8.10.2** It sends the images to a multimodal model with a prompt demanding strict JSON matching `ParsedOrder`, with no prose and no markdown fences; the client strips fences defensively before parsing.
**8.10.3** The API key is read from `local.properties` into `BuildConfig`. It MUST NOT be hardcoded or committed. Absent a key, the option is disabled with an explanatory note.
**8.10.4** Its output goes through the same review screen. The cloud parser is never trusted more than the on-device one.

---

## 9. Assignment memory

**9.1** Normalisation: lowercase, trim, collapse whitespace, strip a leading brand prefix where it repeats across the household's history, strip trailing size/pack fragments (`, 8 oz Bag`, `, 24 oz`), and collapse simple plurals.
**9.2** On confirming an assignment, upsert `AssignmentMemory` with the normalised name, the scope, the member ids, and an incremented `useCount`.
**9.3** On prefill, require an exact normalised match and at least one prior use. Prefer the most recent when scopes conflict.
**9.4** Suggestions are per household. There is no global or shipped default list (§7.9.13).
**9.5** Settings MUST offer "Clear suggestion history".

---

## 10. Output formats

### 10.1 CSV export

Mirrors the spreadsheet layout being replaced. Member columns are generated from the household's actual members, however many there are.

```
Common,Cost,,,<Member1>,,,<Member2>,,,<Member3>,...
Item,Cost,,,Item,Cost,,Item,Cost,,Item,Cost
<common item>,<amount>,,,<item>,<amount>,,<item>,<amount>,,<item>,<amount>
...
Total,<commonBucket>
Split between <N>,<perHead>
Tax,<tax>
...
,,,,Total,<preTax1>,,Total,<preTax2>,,Total,<preTax3>
,,,,Tax share,<taxShare1>,,Tax share,<taxShare2>,,Tax share,<taxShare3>
,,,,Total with tax,<final1>,,Total with tax,<final2>,,Total with tax,<final3>
Total Bill,<computedTotal>,<"Matches bill amount" | "Off by <delta>">
```

**10.1.1** Amounts are written as plain decimals with two places, no currency symbol, so spreadsheets parse them as numbers.
**10.1.2** Commas inside item names MUST be quoted per RFC 4180.

### 10.2 Share text

```
<Group name> — <Order label>
Total: $<computedTotal>  (paid by <payer>)

<Member 1>: $<final>
  common $<share> · items $<sum> · tax & fees $<sum>
<Member 2>: $<final>
  common $<share> · items $<sum> · tax & fees $<sum>
...
```

**10.2.1** Plain text only — no markdown, no emoji, no table characters, since it is pasted into a group chat.
**10.2.2** Under 1200 characters where possible; if the household is large, drop the per-member detail line.

---

## 11. Edge cases

**11.1** Quantity greater than 1 on one row: split the full line total, with an option in the edit sheet to break the row into `N` separate rows of equal price.
**11.2** Items sold by weight where the charged price differs from the shelf price — the charged price always wins; the unit price is display-only.
**11.3** Bottle deposits, bag fees, and container charges appearing as their own rows: treat as ordinary line items, assignable like any other.
**11.4** A discount larger than the common bucket, making it negative — legal; the negative distributes normally.
**11.5** Tax of exactly zero — the proportional allocation MUST NOT divide by zero (§6.4.9).
**11.6** An order where one member is the only participant — all items are effectively personal and the common bucket goes entirely to them.
**11.7** A member archived after an order was settled — that order still renders their name and amount.
**11.8** Screenshots that are rotated, cropped, low-contrast, or dark-mode. Dark mode inverts the colour scheme; detection MUST NOT rely on absolute colour values.
**11.9** Screenshots from a tablet or a different device density — all geometry rules are expressed as fractions of image dimensions (§8.2.2), never as pixel constants.
**11.10** A duplicated screenshot selected twice — de-duplicated by content hash before parsing.
**11.11** An order with zero items after parsing — S6 opens empty with a clear message rather than crashing.
**11.12** Very long item names — displayed ellipsized, stored in full.
**11.13** Device locale using `,` as the decimal separator — parsing and formatting MUST both respect it.
**11.14** Process death at any point in the flow — every step's state is persisted (§7.9.14).

---

## 12. Test plan

### 12.1 `MoneySplitter` unit tests

1. `split(1000, [1,1,1])` → `[334, 333, 333]`, sum exactly 1000.
2. `split(1000, [1,1,1,1])` → four values of 250.
3. `split(272, [1,1,1])` → `[91, 91, 90]`, sum exactly 272.
4. `split(332, [1,1])` → `[166, 166]`.
5. `split(100, [2,1])` → `[67, 33]`, sum exactly 100.
6. `split(-127, [1,1,1])` → sums to exactly −127.
7. `split(0, [1,1])` → `[0, 0]`.
8. `split(x, [])` and `split(x, [0,0])` → throw.
9. Determinism: the same inputs produce identical output across 1000 runs.
10. Property test: for random amounts and weights, `sum(result) == amount` always.

### 12.2 `SplitCalculator` acceptance test

Members are **fixtures built inside the test**, named `P1`–`P4`. They MUST NOT appear in application code, seeds, or resources.

Setup: 4 participants, tax `$1.82`, stated total `$146.53`.
- Common items: 17.94, 5.64, 1.96, 1.17, 6.84 → bucket **33.55**, per head **8.3875**
- P1: 7.62, 6.46 · P2: 2.86, 7.44, 3.67, 33.74 · P3: 28.68, 3.22 · P4: 7.94
- An item at 2.72 split three ways between P1, P2, P3
- Items at 2.98, 3.00 and 0.83, each split two ways between P1 and P4

Expected pre-tax: P1 26.78, P2 57.00, P3 41.19, P4 19.73 — sum 144.71.
Expected final: P1 ≈ 27.12, P2 ≈ 57.72, P3 ≈ 41.71, P4 ≈ 19.98.
**Assert `sum(final) == 14653` exactly.** Individual members may land a cent from the figures above; the sum may not.

### 12.3 Further calculator tests

1. Two participants only — common bucket halved.
2. Equal-allocation setting instead of proportional.
3. Zero tax and zero fees.
4. A discount larger than the common bucket.
5. A weighted `×2` share on one item.
6. An `EXCLUDED` item contributing nothing.
7. An unassigned item raising the blocking condition (§7.10.6).

### 12.4 Parser unit tests

Run against fixture element lists — text plus bounding boxes — captured from real screenshots. No network, no images in unit tests.

1. A four-line wrapped name with a right-aligned price yields one item with the full name.
2. `$3.94/lb` next to a `$1.97` line price yields `lineTotalCents = 197` and `unitPriceText = "$3.94/lb"`.
3. `Qty 1` and `Multipack Quantity: 1` are stripped from the name.
4. `+ Add` and `Review item` never appear in any item name.
5. A struck `$9.95` followed by `$0` resolves to 0.
6. `Subtotal` is not matched by the `Total` rule.
7. A rating carousel card with stars and no price emits no item (§8.4.8).
8. `33 items delivered` does not fail a parse that produced 18 rows (§8.5.4).
9. A section header repeated across two screenshots produces one section.
10. An identical row at the boundary of two screenshots is de-duplicated; two identical rows separated by other items are not.
11. A recording timer `02:53` in the status bar is not read as a price or a name.
12. An order-number line is captured to `externalOrderNo` and excluded from items.

### 12.5 UI tests (Espresso)

1. Fresh install → S1 shows an empty group field; Continue disabled.
2. Cannot leave S2 with fewer than two members.
3. Duplicate member name is rejected.
4. Full happy path from share-intent to summary using a bundled test image.
5. Process death mid-assignment resumes on the same item.
6. Unassigned item blocks the summary and the blocking panel navigates to that item.
7. Rotation preserves state on every screen.

### 12.6 Definition of done

A build is done when: every test above passes; a fresh install contains zero household data; `grep` for any real person's name across the repo returns nothing; and no `double` or `float` appears in any file under the money or calculation packages.

---

## 13. Build order

Each phase ends at a checkpoint that must be demonstrable before the next begins.

**13.1 Phase 0** — project skeleton, Gradle, version catalog, Room database, `AppExecutors`, DI, empty nav graph. *Checkpoint: app launches to a blank screen; Room schema is generated.*

**13.2 Phase 1** — S1, S2, S13, `Household` and `Member` persistence. *Checkpoint: a user can create a group and members, kill the app, and see them again.*

**13.3 Phase 2** — `MoneySplitter` and `SplitCalculator` with the full test suite from §12.1–12.3, no UI. *Checkpoint: the §12.2 acceptance test passes.*

**13.4 Phase 3** — S4, S5, S6, S7, the `ReceiptParser` interface, `MlKitReceiptParser`, the share-target intent filter, and the §12.4 parser tests. *Checkpoint: a real screenshot produces an editable, reconciled item list.*

**13.5 Phase 4** — S8, S9, `AssignmentMemory`. *Checkpoint: every item in a real order can be assigned and the draft resumes after a kill.*

**13.6 Phase 5** — S10, S11, S12, share text and CSV export. *Checkpoint: an end-to-end run produces per-person totals summing exactly to the bill.*

**13.7 Phase 6** — S3 polish, S14, backup/restore, accessibility pass, empty and error states everywhere.

**13.8 Phase 7 (optional)** — `LlmReceiptParser` behind the settings toggle.
