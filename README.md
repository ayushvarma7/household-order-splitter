# Household Order Splitter

Splits a Walmart grocery order between the people in a household and tells each person
exactly what they owe. You screenshot the order page, share the screenshots into the app,
correct anything the reader got wrong, say who each item is for, and get per-person totals
that sum exactly to the printed bill.

It replaces a spreadsheet in which an item shared by a subset of the household had to be
divided by hand and typed once per sharer. Removing that duplication is the point of the
product (SPEC 1.4).

---

## Build and run

Requirements: JDK 17 or later, and the Android SDK with platform 36. Android Studio ships a
suitable JDK, so pointing Gradle at it is usually enough.

```bash
./gradlew :app:assembleStandardDebug
```

Install on a connected device or emulator:

```bash
./gradlew :app:installStandardDebug
```

Run the full JVM test suite, which needs no device:

```bash
./gradlew :core:test
```

Run the instrumented tests, which need a device or emulator:

```bash
./gradlew :app:connectedStandardDebugAndroidTest
```

These include the whole of SPEC 12.5, among them the happy path from a share intent
through parsing to a computed summary, and an on-device parse of three real order
screenshots that also proves text recognition works with no network permission at all.

If Gradle cannot find the SDK, create `local.properties` with `sdk.dir=/path/to/Android/sdk`.
That file is deliberately not committed.

---

## Architecture

Two Gradle modules. `:core` is a plain `java-library`: the Android SDK is not on its compile
classpath, so an `android.*` import there cannot compile, which is how SPEC 6.6's "plain
Java, runs under JUnit on the JVM" is enforced by the compiler rather than by review. It
holds the money arithmetic, the split calculator, the Walmart layout parser, name
normalisation and the two export formats. `:app` is the Android module and holds everything
else: Room, ML Kit, and a single Activity hosting Fragments in one Navigation graph, wired
Fragment to ViewModel to Repository to DAO with LiveData in between (SPEC 4.4, 4.5). There
is no Kotlin source anywhere; the Gradle build scripts are KTS because SPEC 4.9 and the
build brief both require the Kotlin DSL, and build scripts never ship in the APK.
Dependencies are wired by a hand-written `ServiceLocator` rather than Hilt (SPEC 4.8), which
keeps a Java-only project free of a second annotation processor.

---

## How the split works

All money is `long` cents. No `float` or `double` appears anywhere in `:core`, not merely in
the money path, and the test suite asserts that too.

Sharing an amount uses the **largest remainder method** (SPEC 6.3). Each share starts as the
truncated integer quotient, the leftover cents are handed out one each to the shares with
the largest fractional remainders, and ties break by ascending index so the same input
always produces the same output. `MoneySplitter` then asserts its own postcondition: the
parts sum to the whole, exactly, or it throws.

An order is calculated in the fixed order of SPEC 6.4, because each step's rounding depends
on the one before it:

1. Excluded rows drop out, and any row nobody has answered for stops the calculation and is
   reported back so the summary can list it.
2. The common bucket is the sum of the common rows, less any discount. It may go negative,
   which is legal.
3. The bucket is split equally across the participants.
4. Each subset or personal row is split across its own assignees, weighted by their share
   counts, so a double share takes twice as much.
5. Tax, then delivery, then tip, then other fees are allocated, in that order, weighted by
   each member's pre-tax total. A zero adjustment is skipped. If the weights cannot be used,
   because the pre-tax total is zero or a discount pushed someone negative, the allocation
   falls back to an equal split and says so on screen rather than hiding it.
6. The result carries the computed total and its delta against the total printed on the
   bill. A mismatch is reported, never silently corrected.

A person's total may land a cent away from a spreadsheet that used floating point. That is
expected and correct (SPEC 6.5). What may not vary is the sum: the members' totals always
add up to the pre-tax total plus every adjustment, and that is asserted in code.

---

## The Excel workbook

Settings, "Excel workbook". Pick a destination file once and the app keeps it up to date on
its own: settle an order and it turns up as a new sheet, with no export step to remember.

The workbook holds an overview sheet with a row per order and a column per member, then one
sheet per order behind it carrying the full detail. Amounts are real numbers with a currency
format, so a column sums in the spreadsheet rather than being text that looks like money.

It is written by hand from `java.util.zip`, so no spreadsheet library is involved. The whole
file is rewritten rather than appended to, which is deliberate: it needs nothing to read the
existing workbook, and it keeps the file a faithful picture of the database instead of an
append-only log that drifts when an order is edited or deleted. An order that cannot be
totalled yet, because something is still unassigned, is left out rather than written with
invented numbers.

---

## Spending

Home overflow, "Spending". Total spent, average and largest order, how much of the money went
on things everyone shared against things assigned to particular people, and a per-person
breakdown with each member's own colour.

Every figure is a sum of figures the split calculator already produced, so the analytics can
never disagree with what somebody was actually asked to pay. Shares are integer permille for
the same reason money is `long` cents. The bars are ordinary weighted views rather than a
charting library, which keeps the dependency budget intact and means each bar carries its own
content description instead of the screen being one opaque canvas.

---

## Design and accessibility

Spacing runs on a 4dp scale in `values/dimens.xml`, and `values-sw600dp` and `values-sw840dp`
override the same names, so a tablet gets wider margins and a capped reading column with no
duplicated layouts and no size fixed to a device. Every screen lays its content in a column
constrained to `content_max_width` and centred, so line length stays readable as the window
grows.

Colour is semantic and paired: `success`, `warning` and `danger` exist as container plus
on-container pairs in `values/colors.xml` and its `values-night` twin, and `StateColors`
resolves them at runtime. Nothing in the code carries an ARGB literal, which is what makes
dark mode work at all.

Every state that colour encodes also carries an icon and a word, so meaning survives for a
colour blind reader and in a greyscale screenshot. Touch targets are at least 48dp. The app
draws edge to edge and each screen insets itself from the real system bar heights, so no
title ends up under the clock on one device and floating on another.

---

## Reading the screenshots

See `PARSING.md` for the row-grouping algorithm and the chrome filter, which is the file to
edit when Walmart changes its layout.

The short version: text recognition runs on device, and the layout logic that turns the
recognised boxes into rows is pure Java, so it is tested against fixtures traced off real
screenshots with no images and no device involved.

Every parse lands in an editable review screen. Reconciliation warnings are shown
prominently and never block progress, because Walmart occasionally prints figures its own
line items cannot reproduce.

---

## The optional cloud parser

Off by default, and absent from the default build entirely. The `standard` flavour declares
no `INTERNET` permission and does not contain the cloud parser, so there is nothing to
enable by accident.

To use it, build the `cloud` flavour and add a key to `local.properties`:

```properties
llm.api.key=your-key-here
```

Then `./gradlew :app:assembleCloudDebug`. Without a key the Settings option disables itself
and explains why. `local.properties` is in `.gitignore` and the key is never committed or
hardcoded (SPEC 8.10.3). The cloud reader's output goes through the same review screen as
the on-device one and is never trusted more than it.

---

## Privacy

Everything stays on the device. There is no account, no server and no sync (SPEC 1.5).

The default build has no network permission at all, so it cannot send anything anywhere even
in principle. Screenshots are never copied into the app: only their URIs are stored, held
with a persistable permission so they still open after a restart. Household names, member
names and every order live in a local Room database and go nowhere else. Backup writes a
JSON file wherever you choose to put it, and wipe removes everything.

A fresh install contains no household, no members, no orders and no knowledge of any
grocery item. Assignment suggestions come only from assignments you have confirmed yourself
in this household; there is no shipped list of which products are "usually shared".
