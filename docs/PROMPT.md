# PROMPT — build the Household Order Splitter

Paste this together with `SPEC.md`. The spec is the source of truth; this sheet tells you how to work through it.

---

## 0. Your task

Build a native Android application in **Java** that splits a Walmart grocery order between the members of a household and reports what each person owes. The user imports screenshots of the Walmart order page, corrects the parsed item list, says who each item is for, and gets per-person totals that sum exactly to the bill.

Implement `SPEC.md` in full, in the phase order in §13, stopping at every checkpoint.

---

## 1. Hard rules — violating any of these fails the build

1. **Java only.** Java 17. No Kotlin source files. This rules out Jetpack Compose, so use XML layouts with ViewBinding, Fragments, and the Navigation Component.
2. **No seeded identity.** The app ships with no household, no members, no sample orders, no demo data. The group name and every member name are typed by the user at first launch (SPEC §7.1, §7.2). No placeholder that can be saved, no "add 4 members" shortcut, no example names in layouts, strings, seeds, or migrations.
3. **No built-in item knowledge.** Do not ship a list of which groceries are "usually shared". Assignment suggestions come only from the user's own confirmed history (SPEC §9).
4. **Money is `long` cents.** No `float` or `double` anywhere in the money path, including intermediates and test assertions. Splits use the largest remainder method (SPEC §6.3) and every split asserts that its parts sum exactly to the whole.
5. **The calculation layer is pure Java.** `MoneySplitter` and `SplitCalculator` import nothing from `android.*` and run under plain JUnit on the JVM.
6. **Any member count from two upwards works.** Never assume four, or any other number, in layouts, adapters, exports, or math.
7. **Parsing never blocks on its own confidence.** Every parse lands in an editable review screen. Reconciliation warnings are advisory, never gates.
8. **No network in the default build.** The cloud parser is optional, off by default, keyed from `local.properties`, and never committed.

---

## 2. How to work

1. **Read `SPEC.md` end to end before writing any code.** It is numbered; cite section numbers in your commit messages and in code comments where a rule is non-obvious.
2. **Start each phase by listing what you are about to build**, mapped to spec section numbers, and wait for confirmation before generating files.
3. **Stop at every checkpoint in SPEC §13.** Show what runs, what passes, and what is stubbed. Do not run ahead into the next phase.
4. **Write the tests for a phase in the same pass as its code**, not afterwards.
5. **When the spec is silent on something, ask** rather than inventing a convention — especially anything touching money, names, or defaults.
6. **When the spec is wrong or self-contradictory, say so and stop.** Do not quietly work around it.

---

## 3. Phase-by-phase instructions

### Phase 0 — Skeleton
1. Create the Gradle project: Kotlin DSL build files, `libs.versions.toml`, `minSdk 26`, Java 17 toolchain.
2. Add dependencies: AndroidX core/appcompat/fragment/navigation, Material Components, Room (runtime + compiler), Hilt (or write the `ServiceLocator` — pick one and state which), ML Kit text recognition.
3. Define every entity in SPEC §5 with its indices and foreign keys, plus the DAOs. Export the Room schema to a checked-in JSON.
4. Write `AppExecutors` with `diskIO()`, `parsing()`, `mainThread()`.
5. Create `MainActivity` and an empty nav graph.
6. **Checkpoint:** the app launches, the schema file exists, `./gradlew build` is clean.

### Phase 1 — Group and members
1. Build S1 exactly as SPEC §7.1, including the disabled-until-valid Continue button and the empty field.
2. Build S2 exactly as SPEC §7.2, including duplicate rejection, the colour palette, IME-Done add-and-stay, and the two-member minimum.
3. Build S13 as the reusable version of S2 reached from Home.
4. Implement soft-delete (SPEC §5.10).
5. Write the Espresso tests from SPEC §12.5.1–12.5.3.
6. **Checkpoint:** create a group and members, kill the app, relaunch, and land on Home with them intact.

### Phase 2 — The math, with no UI
1. Implement `MoneySplitter.split(long, int[])` following SPEC §6.3 step by step, including the negative-amount branch and the deterministic tie-break.
2. Implement `SplitCalculator.calculate(...)` following SPEC §6.4 in the exact order given, returning a result object that carries per-member common share, item shares, adjustment shares, final total, computed total, and the delta against the stated total.
3. Write every test in SPEC §12.1, §12.2, and §12.3.
4. **Checkpoint:** the §12.2 acceptance test passes with the four totals summing to exactly `14653`. Show the test output.

### Phase 3 — Import and parsing
1. Define `ReceiptParser` and the `ParsedOrder` model.
2. Build S4 (SPEC §7.4) with the picker, camera, reorderable preview strip, and persistable URI permissions.
3. Register the share-target intent filter (SPEC §8.1.2) and handle both single and multiple images, including the add-to-draft prompt.
4. Implement `MlKitReceiptParser` against SPEC §8.2 through §8.9. Build it in this order:
   a. OCR and normalised geometry (§8.2)
   b. price-token classification, line price vs unit price (§8.3.1–8.3.3)
   c. block assembly and multi-line name joining (§8.3.4–8.3.10)
   d. the chrome filter, including the rating carousel and the status-bar recording timer (§8.4)
   e. section headers, with the unit-count trap handled (§8.5)
   f. order-level fields, including struck-through prices and the `Subtotal`/`Total` distinction (§8.6)
   g. multi-screenshot stitching, dedupe, and edge fragments (§8.7)
   h. confidence flags (§8.8) and reconciliation (§8.9)
5. Build S5, S6, S7 (SPEC §7.5–7.7).
6. Write every parser test in SPEC §12.4 against captured fixture element lists — no images and no network in unit tests.
7. **Checkpoint:** feed it real screenshots and show the resulting item list, the extracted order-level fields, and the reconciliation verdict.

### Phase 4 — Participants and assignment
1. Build S8 (SPEC §7.8), including recalculation when participants change later.
2. Build S9 (SPEC §7.9) with all three quick actions, the chip group, the live per-head readout, `×2` shares, exclude, jump-to-item, and per-item persistence.
3. Implement `AssignmentMemory` and prefill (SPEC §9), suggestion-only and never auto-advancing.
4. **Checkpoint:** assign every item in a real order, kill the app mid-way, and resume on the same item.

### Phase 5 — Summary and output
1. Build S10 (SPEC §7.10), including the expandable breakdown, the payer selector, the reconciliation verdict, and the blocking panel for unassigned items.
2. Build S11 with the share text in SPEC §10.2 and the CSV in SPEC §10.1, generating member columns dynamically.
3. Build S12, the read-only order view with the screenshot thumbnails.
4. **Checkpoint:** a full end-to-end run from share-intent to a per-person breakdown that sums exactly to the printed bill total.

### Phase 6 — Polish
1. S3 with its empty state and context menu, S14 settings, JSON backup and restore, wipe.
2. Loading, empty, and error states on every screen.
3. Accessibility: content descriptions on every avatar and chip, 48dp minimum touch targets, no meaning carried by colour alone.
4. Run the remaining Espresso tests from SPEC §12.5.

### Phase 7 — Optional
1. `LlmReceiptParser` behind the settings toggle, per SPEC §8.10.

---

## 4. What the screenshots actually look like

Before writing the parser, understand the input. A Walmart order page contains, in rough order down the screen:

1. A status bar — clock, battery, and sometimes a **screen-recording timer that reads like a number**.
2. A blue app bar titled `<Mon> DD, YYYY order` — this is the order date.
3. A **horizontally scrolling carousel of product cards with rating stars and no prices**. These are rating prompts, *not* purchased items. Emitting them as line items is the single most likely parsing failure.
4. A delivery status block with a photo thumbnail.
5. A collapsible `N items delivered` section, subdivided by `N substituted`, `N shopped`, `N unavailable`.
6. Item rows: a thumbnail on the left, a **name wrapping across two to four lines**, metadata lines beneath it (`Qty 1`, `Multipack Quantity: 1`, `$3.94/lb`), and the **line price right-aligned against the first line of the name**. Between rows sit an `+ Add` button and a `Review item` link.
7. A payment card (`VISA Ending in ####`).
8. The summary block: `Subtotal`, a delivery line that may print a **struck-through price beside the charged one**, `Taxes`, `Driver tip`, `Total`.
9. `Charge history`, `Order# ...`, and a barcode.

Two consequences worth stating plainly, because they are where naive parsers break:

- **The price belongs to the whole block, not to the line it sits beside.** Pairing a price with the single text line at the same y-coordinate captures a fragment like `Great Value Triple Cheddar` and drops the rest of the name.
- **`N items delivered` counts units, not rows.** An order labelled `33 items delivered` may hold 18 rows. Never validate the parse against that number; reconcile on money instead.

---

## 5. Deliverables

1. A compiling Android Studio project, Java only, with the package structure proposed and confirmed before any UI is generated.
2. Gradle KTS build files and a version catalog.
3. Phases 0–6 implemented in order, each demonstrated at its checkpoint.
4. The full test suite from SPEC §12, passing.
5. A `README.md` covering: how to build and run, the architecture in a paragraph, how the split algorithm works, how to enable the optional cloud parser, and the privacy statement that all data stays on the device.
6. A short `PARSING.md` documenting the row-grouping algorithm and the chrome filter list, so the parser can be tuned when Walmart changes its layout.

---

## 6. Definition of done

- Every test in SPEC §12 passes.
- A fresh install contains zero household data.
- Searching the repository for any real person's name returns nothing.
- No `float` or `double` appears in any file under the money or calculation packages.
- A real order imported from screenshots produces per-person totals summing exactly to the printed bill total.

---

## 7. Start here

Propose the package structure and the Room schema. List which spec sections each package implements. Then stop and wait for confirmation before generating the UI layer.
