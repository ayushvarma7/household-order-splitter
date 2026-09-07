# Architecture proposal (PROMPT §7)

Status: proposed, awaiting confirmation. No UI code is generated until this is agreed.

---

## 1. Module layout

Two Gradle modules rather than one. This is the only structural decision that is not
spelled out in the spec, and it exists to turn two of the hard rules into compiler
errors instead of code-review discipline.

| Module | Plugin | Why |
|---|---|---|
| `:core` | `java-library`, Java 17 | No Android SDK on the classpath, so an `android.*` import cannot compile. SPEC §6.6 and PROMPT hard rule 5 become mechanically enforced. Tests here are plain JUnit with no Robolectric and no device. |
| `:app` | `com.android.application`, Java 17, `minSdk 26` | Everything that touches Android: Room, ML Kit, Fragments, ViewBinding, Navigation. |

`:app` depends on `:core`. `:core` depends on nothing but JUnit (test-only) and Gson.

Consequence worth stating: SPEC §12.1, §12.2, §12.3 and §12.4 all run in `:core` under
`./gradlew :core:test`, with no emulator and no images.

---

## 2. Package structure

Application id and root package: `com.householdsplitter`. No person's name appears
anywhere in it (PROMPT §6).

### `:core`, pure Java, no `android.*`

```
com.householdsplitter.core
├── money/
│   ├── MoneySplitter.java          §6.3  largest remainder, asserts sum == amount
│   ├── Cents.java                  §6.2  string <-> long cents, no float/double
│   └── CurrencyFormat.java         §10.1.1, §11.13  BigDecimal only at the print boundary
├── calc/
│   ├── SplitCalculator.java        §6.4  the 13 steps, in order
│   ├── input/  CalcOrder, CalcLineItem, CalcMember, CalcAssignment, Adjustments
│   ├── result/ SplitResult, MemberSplit, AdjustmentShare, Reconciliation
│   ├── AllocationMode.java         §6.4.9, §7.14.1  PROPORTIONAL | EQUAL
│   ├── Scope.java                  §2.6  (+ see open question 3)
│   └── NoParticipantsException.java, UnassignedItemsException   §6.4.1, §7.10.6
├── parse/
│   ├── ReceiptParser.java          interface, §8.1 / §8.10.1
│   ├── model/  ParsedOrder, ParsedItem, ParsedAdjustments, OcrElement, ReviewReason
│   ├── walmart/
│   │   ├── WalmartLayoutParser     drives the pipeline over a List<OcrElement>
│   │   ├── PriceTokens             §8.3.1 – §8.3.3   line price vs unit price
│   │   ├── BlockAssembler          §8.3.4 – §8.3.10  the row-grouping algorithm
│   │   ├── ChromeFilter            §8.4  incl. carousel §8.4.8 and timer §8.4.7
│   │   ├── SectionHeaders          §8.5  incl. the unit-count trap §8.5.4
│   │   ├── OrderFieldExtractor     §8.6  incl. struck-through §8.6.4, Total vs Subtotal §8.6.8
│   │   ├── Stitcher                §8.7  dedupe, edge fragments, summary preference
│   │   ├── ConfidenceRules         §8.8
│   │   └── ParseTuning             every geometric fraction as a named constant §8.2.3, §11.9
│   └── Reconciler.java             §8.9  advisory only, never a gate
├── suggest/
│   └── NameNormalizer.java         §9.1
└── export/
    ├── CsvExporter.java            §10.1  member columns generated from the actual list
    └── ShareTextBuilder.java       §10.2
```

`OcrElement` is our own POJO (text, left/top/right/bottom as fractions of image size,
confidence, image index). ML Kit's `Text.Element` never crosses into `:core`, which is
what lets §12.4 run against captured fixture element lists.

### `:app`, Android

```
com.householdsplitter
├── SplitterApp.java                Application, owns the ServiceLocator
├── di/ServiceLocator.java          §4.8  (see decision 4.1)
├── util/AppExecutors.java          §4.7  diskIO() / parsing() / mainThread()
├── data/
│   ├── db/AppDatabase.java         §4.6, schema exported to app/schemas, version 1
│   ├── entity/                     §5.1 – §5.8, one class per table
│   ├── dao/                        LiveData reads, executor writes §4.6
│   ├── relation/                   OrderWithParticipants, LineItemWithAssignments, OrderRow
│   ├── converter/Converters.java   enums <-> String
│   ├── mapper/CalcMapper.java      entity -> :core calc input POJOs
│   └── repo/                       HouseholdRepository, OrderRepository, ParseRepository,
│                                   AssignmentRepository, ExportRepository, SettingsRepository
├── prefs/SettingsStore.java        §7.14.1 – §7.14.3, §7.14.6
├── ocr/MlKitTextSource.java        §8.2.1  the only ML Kit-aware class
├── parse/MlKitReceiptParser.java   glue: Uri -> bitmap -> OcrElement -> WalmartLayoutParser
├── suggest/AssignmentMemoryService.java   §9.2 – §9.5
├── backup/BackupService.java       §7.14.4, §7.14.5
└── ui/
    ├── MainActivity.java           §4.4  single activity, single nav graph
    ├── setup/       S1 §7.1, S2 §7.2
    ├── home/        S3 §7.3
    ├── members/     S13 §7.13
    ├── importer/    S4 §7.4  (+ the ACTION_SEND / ACTION_SEND_MULTIPLE target §8.1.2)
    ├── parsing/     S5 §7.5
    ├── review/      S6 §7.6
    ├── details/     S7 §7.7
    ├── participants/S8 §7.8
    ├── assign/      S9 §7.9
    ├── summary/     S10 §7.10, S11 §7.11
    ├── orderview/   S12 §7.12
    ├── settings/    S14 §7.14
    └── common/      AvatarView, MemberPalette §7.2.6, CurrencyTextWatcher §7.6.6,
                     BindingFragment, DiffCallbacks
```

Each `ui/<screen>/` package holds exactly `XFragment`, `XViewModel`, and its adapters.
Fragments hold no money math (§4.5).

### Source sets and flavours

`:app` gets two product flavours, per §4.10:

- `standard` (default): no `INTERNET` permission, no `LlmReceiptParser` on the classpath.
- `cloud`: adds `INTERNET`, and `src/cloud/java/.../parse/LlmReceiptParser.java` (§8.10).
  The key comes from `local.properties` into `BuildConfig`; `local.properties` is
  already in `.gitignore`.

---

## 3. Room schema, version 1

Exported to `app/schemas/com.householdsplitter.data.db.AppDatabase/1.json` and checked in.

### `households`  §5.1
| Column | Type | Constraint |
|---|---|---|
| `id` | INTEGER | PK autogenerate |
| `name` | TEXT | NOT NULL, 1..60 enforced in the repository |
| `createdAt` | INTEGER | NOT NULL, epoch millis |

### `members`  §5.2
| Column | Type | Constraint |
|---|---|---|
| `id` | INTEGER | PK autogenerate |
| `householdId` | INTEGER | NOT NULL, FK -> households(id) ON DELETE CASCADE, indexed |
| `name` | TEXT | NOT NULL, 1..40 |
| `colorHex` | TEXT | NOT NULL, round-robin from the palette §7.2.6 |
| `sortOrder` | INTEGER | NOT NULL |
| `isArchived` | INTEGER | NOT NULL DEFAULT 0  §5.10 |

Index: `(householdId, isArchived)` for the S2/S8 lists.
No unique index on `name`: §7.2.5 duplicate rejection is case-insensitive over
non-archived members only, which SQLite cannot express as cleanly as the repository can.

### `orders`  §5.3
Table is named `orders` because `order` is reserved in SQL.

| Column | Type | Constraint |
|---|---|---|
| `id` | INTEGER | PK autogenerate |
| `householdId` | INTEGER | NOT NULL, FK -> households(id) ON DELETE CASCADE, indexed |
| `label` | TEXT | NOT NULL  §8.6.2 |
| `orderDate` | INTEGER | NOT NULL |
| `externalOrderNo` | TEXT | NULL, UNIQUE index |
| `status` | TEXT | NOT NULL, DRAFT / ASSIGNED / SETTLED |
| `payerMemberId` | INTEGER | NULL, FK -> members(id) ON DELETE SET NULL, indexed |
| `taxCents` | INTEGER | NOT NULL DEFAULT 0 |
| `deliveryFeeCents` | INTEGER | NOT NULL DEFAULT 0 |
| `tipCents` | INTEGER | NOT NULL DEFAULT 0 |
| `otherFeeCents` | INTEGER | NOT NULL DEFAULT 0 |
| `discountCents` | INTEGER | NOT NULL DEFAULT 0, stored positive |
| `statedSubtotalCents` | INTEGER | NOT NULL DEFAULT 0 |
| `statedTotalCents` | INTEGER | NOT NULL DEFAULT 0 |
| `createdAt` | INTEGER | NOT NULL |
| `draftStep` | TEXT | NOT NULL DEFAULT 'IMPORT', proposed, see open question 1 |
| `draftItemPosition` | INTEGER | NOT NULL DEFAULT 0, proposed, see open question 1 |

A SQLite UNIQUE index permits multiple NULLs, so a plain unique index on
`externalOrderNo` is exactly the "unique where non-null" §5.3 asks for, and it backs the
duplicate-import guard in §8.6.7.

### `order_images`  §5.4
`id` PK autogen · `orderId` FK -> orders ON DELETE CASCADE, indexed · `uri` TEXT NOT NULL ·
`position` INTEGER NOT NULL. Unique index on `(orderId, position)`.

### `order_participants`  §5.5
Composite PK `(orderId, memberId)`. `orderId` FK -> orders ON DELETE CASCADE,
`memberId` FK -> members ON DELETE NO ACTION (soft delete instead, §5.10). Index on `memberId`.

### `line_items`  §5.6
| Column | Type | Constraint |
|---|---|---|
| `id` | INTEGER | PK autogenerate |
| `orderId` | INTEGER | NOT NULL, FK -> orders ON DELETE CASCADE, indexed |
| `name` | TEXT | NOT NULL |
| `rawOcrText` | TEXT | NOT NULL, verbatim §8.3.9 |
| `quantity` | INTEGER | NOT NULL DEFAULT 1 |
| `lineTotalCents` | INTEGER | NOT NULL |
| `unitPriceText` | TEXT | NULL  §8.3.3 |
| `scope` | TEXT | NOT NULL, see open question 3 |
| `sourceSection` | TEXT | NULL  §8.5.2 |
| `needsReview` | INTEGER | NOT NULL DEFAULT 0 |
| `reviewReasons` | TEXT | NULL, proposed, see open question 2 |
| `position` | INTEGER | NOT NULL |

Index: `(orderId, position)`.

### `item_assignments`  §5.7
`id` PK autogen · `lineItemId` FK -> line_items ON DELETE CASCADE, indexed ·
`memberId` FK -> members ON DELETE NO ACTION, indexed · `shares` INTEGER NOT NULL DEFAULT 1.
Unique index on `(lineItemId, memberId)`.
§5.9 is enforced in `AssignmentRepository`: writing scope COMMON deletes that item's rows.

### `assignment_memory`  §5.8
`id` PK autogen · `householdId` FK -> households ON DELETE CASCADE ·
`normalizedName` TEXT NOT NULL · `scope` TEXT NOT NULL · `memberIdsCsv` TEXT NOT NULL ·
`lastUsedAt` INTEGER NOT NULL · `useCount` INTEGER NOT NULL DEFAULT 0.
Unique index on `(householdId, normalizedName)` so §9.2 is a real upsert.

No `@Database` callback, no `prepopulate`, no seed asset anywhere (§1.6).

---

## 4. Decisions the spec left to us

**4.1 DI: hand-written `ServiceLocator`, not Hilt** (§4.8 offers the choice).
Reason: the codebase is Java-only by hard rule, and Hilt adds a Kotlin-based Gradle
plugin plus a second annotation processor to a single-Activity app with roughly eight
repositories. A `ServiceLocator` held by the `Application`, plus one
`ViewModelProvider.Factory`, is all Java, has no generated indirection to debug, and
lets the JVM tests in `:core` construct everything directly. Used everywhere, not mixed.

**4.2 JSON library: Gson** (§4.11 allows one).
Reason: needed for backup/restore (§7.14.4) and the cloud parser (§8.10.2). The bundled
`org.json` is stubbed out in JVM unit tests, so backup round-trip tests could not run.

**4.3a The second-store seam is a named interface** (§3.5 requires that a second store be
addable without changing the domain layer, while ruling one out in v1).
`core.parse.LayoutParser` is that seam: it takes positioned OCR text and returns a
`ParsedOrder`, and it is the only thing above the OCR that knows what a store's page looks
like. `WalmartLayoutParser` implements it; `MlKitReceiptParser` takes one as a constructor
argument rather than building a Walmart one, because the OCR step is store-independent, so
a second store is an argument and not an edit.

`SecondStoreTest` checks the claim by trying it: a fictional store with prices on the left
and totals in a footer, run through the real `SplitCalculator`. The test contains no cast,
no branch on which store it is, and no new type. If store knowledge is later pushed into
the domain layer, it stops compiling.

No real second store is shipped. Guessing at a layout with no screenshots to check against
is how a parser ends up silently wrong about money.

**4.3 No floating point in the money path.** `Cents` parses and formats through `long`
and, only at the display boundary, `BigDecimal` with `NumberFormat` for §11.13. No
`float` or `double` token appears in `core/money`, `core/calc`, or their tests.

---

## 5. Open questions before Phase 0

**1. Where a draft resumes.** §7.3.4 and §7.9.14 require a `DRAFT` order to resume at the
step and the item it stopped at, but §5.3 has no column for it. Proposed:
`draftStep` (IMPORT / REVIEW / DETAILS / PARTICIPANTS / ASSIGN / SUMMARY) and
`draftItemPosition` on `orders`, both written after every step. Confirm, or say where
you would rather this live.

**2. Why a row needs review.** §8.8.2 requires every flagged row to state its reason, but
§5.6 stores only the boolean `needsReview`. Proposed: a `reviewReasons` column holding a
CSV of enum names, rendered into the §7.6.4 content description. Confirm.

**3. The unassigned state.** §2.6 defines four scopes, none of which means "not yet
answered". §7.10.6 needs to detect unassigned items and §7.9.8 needs to disable Next
while nothing is selected, so the state has to be representable. Two options:
  a. add `UNASSIGNED` to the scope enum as the persisted default for a freshly parsed
     item (recommended: it is one value, it is explicit, and `SplitCalculator` can raise
     the §7.10.6 blocking condition on it directly), or
  b. make `scope` nullable, with NULL meaning unanswered.
This one touches the calculation layer, so I would rather you pick than assume.

**4. Discount assignment.** §6.4.4 says "if the discount is not assigned to specific
members", but no field anywhere records such an assignment. Proposal for v1: the
discount is always order-level, so that branch is always taken and the condition is
constant. Confirm, or tell me the discount should be assignable and I will add the
field before Phase 0 rather than migrate later.

**5. Package root.** `com.householdsplitter` unless you want something else. It is baked
into the application id, so it is cheapest to change now.
