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

Turn device animations off first, which is Espresso's documented setup step. With them on,
`closeSoftKeyboard` intermittently throws instead of typing:

```bash
adb shell settings put global window_animation_scale 0
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

It also answers the question a single order cannot: who is actually out of pocket. One person
fronts each shop, and over a run of orders those advances accumulate, so "Settling up" shows
each member's net balance and the shortest list of payments that would level everyone. The
balances always sum to zero, which is asserted, because money appearing or vanishing there
would be money somebody is wrongly asked for.

Every figure is a sum of figures the split calculator already produced, so the analytics can
never disagree with what somebody was actually asked to pay. Shares are integer permille for
the same reason money is `long` cents. The bars are ordinary weighted views rather than a
charting library, which keeps the dependency budget intact and means each bar carries its own
content description instead of the screen being one opaque canvas.

---

## First launch

Three pages before the setup screen: where the numbers come from, what you do with them,
and what you get back. SPEC 7.1.2 gives the setup screen one line of explanation, which is
enough for somebody who already knows what the app is and not enough for anybody else, and
the app asks for a group name before it has said what a group is for.

Skip sits top-right throughout, so somebody who does know can go straight to the field they
came for. It appears exactly once, tracked by its own setting rather than by whether a
household exists: those are different questions, and somebody who reads it and closes the
app before naming their group should land on setup next time, not read it again. It replaces
itself on the back stack, so Back from setup leaves the app rather than reopening an
introduction already dismissed.

---

## Finding an old order

Home groups orders under month headings carrying that month's own count and total, with
"This month" and "Last month" spelled out rather than named, because those answer the
question without any arithmetic. The month is worked out in the device's own time zone, so a
9pm order on the last day of a month is not filed under the next one.

Search looks inside orders, not just at their labels. "Which order had the coffee beans?" is
what a household actually asks months later, and "Weekly shop" never answers it, so the item
names and the participants' names are searchable too. Matching is forgiving in the ways
someone typing on a phone needs: partial words, any capitalisation, words in any order. Every
word has to match something, though, because a result matching only half of what was typed is
worse than no result, since you cannot tell which half was honoured.

Three filters, All by default. A month whose only order is filtered out loses its heading with
it, and "Nothing matches" is a separate state from "you have no orders", with a way back
rather than an invitation to start something.

---

## Standing rules

Settings, "Standing rules". "Ben is never on beer" is a fact about a household that otherwise
gets re-entered on every order containing beer, so it can be written down once.

Nothing is seeded. SPEC 7.9.13 forbids the app shipping opinions about which groceries are
usually shared, so every keyword here was typed by the household about itself. A rule is a
plain case-insensitive substring rather than a pattern, because someone writing "beer" should
not have to think about regular expressions.

Applying one is never silent. The assign screen names who was moved and by which keyword, and
touching a chip by hand hands control straight back: a rule is a starting point, not a veto.
A rule can never empty an item, which would turn a charge somebody owes into one nobody owes,
and it can never add somebody who is not in on the order.

---

## Splitting a multi-quantity row

A two-pack bought for two different people cannot be answered on one row, and the only
alternative was deleting it and typing two by hand. Tap the row on the review screen and
"Split into 2 separate rows" divides the line total through the same splitter the totals use,
so a $5.05 two-pack becomes $2.53 and $2.52 rather than two $2.52s and a lost penny. Any
answer already given for the row carries across to both halves.

---

## Backups

Settings, "Automatic backups". Pick a folder once and the app keeps a week of dated JSON
backups there, one a day, written after settling an order. A file that overwrites itself is
not a backup, since the failure it has to survive is a bad write, so each day is its own file
and only the app's own name pattern is ever pruned: you chose a folder, not a scratch space.
Pruning happens only after a successful write, so a failed backup never costs an old one too.

Not a scheduled job. WorkManager is outside SPEC 4.11's library budget, and a household's data
only changes when they use the app, so there is nothing for a background wake-up to find.

The backup is silent, including on failure, because it fires right after settling up. The
status line in Settings is where an absence becomes visible, so it says plainly when nothing
is being kept.

Restore reassigns every id and rewrites the references between rows. That is what makes the
merge of SPEC 7.14.4 work at all: the common case is restoring your own backup into the
install it came from, where every id in the file is already taken.

---

## The widget

A home-screen widget showing who owes whom. The question between shops is not "what did we
spend?" but "am I square with anyone?", and answering it meant opening the app and finding the
Spending screen.

It shows the suggested transfers rather than net balances, because "Ben pays Ana $12.40" is an
instruction and "Ben: -12.40" is a puzzle. The figures come from the same `Balances` the
Spending screen uses, so the two cannot disagree. It redraws after settling an order or
recording a payment, which are the only two things that can change the answer.

Long-pressing the launcher icon gives two shortcuts, new order and who owes what. Only two,
because a list of shortcuts nobody reads is worse than none.

---

## Design and accessibility

Spacing runs on a 4dp scale in `values/dimens.xml`, and three qualified folders override the
same names: `values-sw600dp` and `values-sw840dp` for tablets, and `values-w600dp` for any
window at least 600dp wide. That last one matters because "smallest width" keys off the
shorter edge, which for a phone stays around 390dp however you hold it, so a landscape phone
would otherwise stretch the compact reading column across 870dp. Every screen lays its
content in a column constrained to `content_max_width` and centred, so line length stays
readable as the window grows, with no duplicated layouts and no size fixed to a device.

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
