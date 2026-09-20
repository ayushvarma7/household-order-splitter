# Privacy Policy

**Order Splitter**
Last updated: 15 September 2026

## The short version

Order Splitter collects nothing, sends nothing, and has no way to do either. It has no
network permission. Everything it knows lives on your phone and goes no further.

## What the app stores

Order Splitter keeps the following on your device only, in a private database that other
apps cannot read:

- The names you give the people in your household
- Your orders: the shop, the date, the items, the prices, and who each item was for
- Which screenshots an order was read from, referenced by their location on your device
- Your settings, such as your currency symbol and how you prefer costs allocated
- Backups you choose to create, written to a folder you pick

## What the app sends

Nothing. There is no account, no sign in, no sync, no analytics, no crash reporting, no
advertising and no third party SDK that transmits anything.

This is not a policy statement, it is a property of the build. The app declares no
`INTERNET` permission. Android will not let an app open a network connection without one,
so the app could not send your data anywhere even if it were asked to.

You can check this yourself rather than take our word for it. On the app's Settings screen
under "Reading", the app states that it works offline. In the published source, the network
permission is not merely left out but explicitly removed, because a library the app uses
declares one that would otherwise be inherited.

## Reading your receipts

Order Splitter reads your order screenshots using Google's ML Kit text recognition, running
entirely on your device with a model that ships inside the app. No image and no recognised
text is uploaded. The recognition works with the phone in aeroplane mode.

## Photos and files

The app can open screenshots you choose and can take a photo with your camera, in both
cases through Android's own picker, which grants access to the specific items you select
and nothing else. It does not browse your photo library.

If you turn on backups, the app writes backup files to a folder you choose. Those files
stay wherever you put them.

## Payment card details

A photograph of a restaurant bill often catches the customer copy, which prints a masked
card number, an authorisation code and a signature line.

The app removes those before anything is stored. Recognised text is stripped of card
numbers, last four digits, and authorisation, approval and reference codes at the moment
it comes out of the recogniser, so none of it reaches the database, the diagnostics or a
backup file. Amounts are deliberately left alone, since a total is not card data.

The photograph itself is not altered. It is your picture, kept where you took it, and the
app only holds a reference to it.

## Deleting your data

Uninstalling Order Splitter deletes everything it stored. Settings also offers an option to
erase all data while keeping the app installed. Backup files you created are yours and are
not removed by either, because the app does not delete files it did not write.

## Children

The app is not directed at children and collects no data from anyone.

## Changes to this policy

If this policy changes, the updated version will be published here and the date above will
change. Since the app collects nothing, any change is likely to be a clarification rather
than a new use of your data.

## Contact

Questions about this policy can be raised as an issue on the project's GitHub repository:
https://github.com/ayushvarma7/household-order-splitter/issues
