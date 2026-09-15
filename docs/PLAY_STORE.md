# Play Store listing

Everything Play Console asks for, written out so it can be pasted rather than invented at
the keyboard. Character limits are Play's own.

## Identity

| Field | Value |
|---|---|
| App name (30 max) | `Order Splitter` |
| Package | `com.householdsplitter` |
| Category | Finance |
| Tags | Budgeting, Expense tracking |
| Content rating | Everyone, once the questionnaire is answered (no user content, no ads, no purchases) |
| Contains ads | No |
| In-app purchases | No |
| Price | Free |

## Short description (80 max)

```
Split a shared grocery order from a screenshot. No account, no internet.
```

72 characters, checked rather than counted by eye.

## Full description (4000 max)

```
Share a household grocery order and the arithmetic is nobody's favourite part. Somebody
bought oat milk for themselves, the coffee is shared three ways, and the delivery fee has
to land somewhere. Order Splitter reads the order from a screenshot and works out what
each person owes.

HOW IT WORKS

Take a screenshot of your Walmart or Amazon Fresh order. Order Splitter reads the items
and prices off it, on your phone, with no internet connection. You say who each item was
for: one person, a few people, or everybody. Fees, tax and tip are shared out in
proportion to what each person's items came to, or equally, whichever you choose.

You get a per person total, a settle-up list of who pays whom, and a shareable summary.

IT SHOWS ITS WORKING

Every row keeps a link back to the exact spot on the screenshot it was read from, ringed
in a red box. When a number looks wrong you can see where it came from rather than
guessing.

Rows it could not read confidently are flagged rather than hidden. A price it found with
no name attached is kept and marked, because quietly dropping it would leave the total
short with nothing to point at. Nothing is ever charged to anyone without you saying so.

ENTIRELY OFFLINE

Order Splitter has no internet permission. Not unused, not optional: the app cannot open
a network connection, because Android will not let it. There is no account, no sign in,
no sync, no analytics and no advertising. Your orders, your household and your prices
stay on your phone.

Receipt reading runs on the device with a text recognition model that ships inside the
app. It works in aeroplane mode.

WHAT ELSE IT DOES

- Two stores supported: Walmart and Amazon Fresh, each read with its own layout
- Search your order history, grouped by month
- Per person and per month spending
- Settle up, with payments recorded so balances stay right
- Export to CSV or to an Excel workbook
- Standing rules, so "Ayush never has the oat milk" only needs saying once
- Automatic backups to a folder you choose
- A home screen widget showing who owes what
- Dark mode, and a layout that respects your font size

MONEY IS HANDLED IN WHOLE PENCE

Every amount is an integer number of cents from the moment it is read to the moment it is
displayed. There is no floating point anywhere in the calculation, so a split cannot drift
by a penny. When an amount does not divide evenly the remainder is handed out one penny at
a time, and the parts always add back to the original exactly.
```

## Data safety form

The honest answers, all of which are the easy ones.

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not applicable, no data is transmitted |
| Do you provide a way for users to request that their data is deleted? | **Yes**, Settings offers erase all data, and uninstalling removes everything |

There is nothing to declare because nothing leaves the device. If Play asks you to justify
this, the verifiable fact is that the shipped manifest declares no `INTERNET` permission;
see `docs/RELEASE.md` for the command that proves it against the built artefact.

## Privacy policy URL

Play requires a reachable URL even when the app collects nothing. `docs/privacy-policy.html`
is written and ready to serve. The cheapest way to host it:

1. Repository **Settings** then **Pages**
2. Source: deploy from branch, `main`, folder `/docs`
3. The policy is then at
   `https://ayushvarma7.github.io/household-order-splitter/privacy-policy.html`

That URL goes in the Play Console listing and in the Data Safety section.

## Graphics

| Asset | Requirement | Status |
|---|---|---|
| App icon | 512 x 512 PNG, 32 bit | `docs/play-store-icon-512.png` |
| Feature graphic | 1024 x 500 PNG or JPG | `docs/play-feature-graphic.png` |
| Phone screenshots | 2 to 8, at least 320px on the short side | `docs/screenshots/`, see below |

Play shows screenshots in the order you upload them. A reasonable order, leading with the
thing that is actually unusual about this app:

1. `07-home.png`, the orders list
2. `14-red-box.png`, a charge traced back to the screenshot it came from
3. `13-review.png`, the review screen with flagged rows
4. `16-store-picker.png`, choosing the shop
5. `18-assign.png`, saying who an item was for
6. `19-summary.png`, who owes whom
7. `09-spending.png`, spending over time
8. `12-home-dark.png`, dark mode

## Before you submit

- [ ] Generate the signing key and `keystore.properties`, see `docs/RELEASE.md`
- [ ] Opt in to Play App Signing on first upload
- [ ] `./gradlew bundleRelease` and check the `.aab` exists
- [ ] Run the suites, including `-PtestRelease`
- [ ] Confirm the manifest asks for no permissions
- [ ] Turn on GitHub Pages and check the privacy policy URL loads
- [ ] Upload to **Internal testing** first, which is live in minutes with no review
- [ ] Note: a new developer account must run a closed test with at least 20 testers for 14
      days before production is unlocked. Start that clock early if you intend to go public
