# Building and verifying a release

Everything here is done except one step, which needs a secret and so cannot be committed:
generating the signing key. The rest is in place.

## The one step left: the signing key

```bash
keytool -genkey -v -keystore household-splitter-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias household-splitter
```

Put it somewhere durable and outside the repository, then create `keystore.properties` in
the project root:

```properties
storeFile=/absolute/path/to/household-splitter-release.jks
storePassword=...
keyAlias=household-splitter
keyPassword=...
```

That file, and any `.jks` or `.keystore`, is gitignored. Nothing else needs changing.

Without it the release build still works, signed with the debug key so it can be built and
run locally. That cannot reach the store by accident: Play rejects a debug-signed upload.

When you first upload, opt in to **Play App Signing**. Google then holds the real signing
key and this one becomes an upload key, which means losing it is recoverable. Losing a
signing key without that is not: it ends the ability to update the app, ever.

## Building

```bash
./gradlew bundleRelease
```

The bundle lands in `app/build/outputs/bundle/release/`. Play requires an `.aab`; it no
longer accepts an `.apk` for a new app. For installing on your own phone, `assembleRelease`
gives you an APK.

## Verifying, in the order that matters

**1. The suites.**

```bash
./gradlew :core:test                        # 239 tests, pure JVM
./gradlew :app:connectedAndroidTest         # 147 tests, on a device
```

**2. The suite against the minified build.** This is the one people skip and then find out
in production. R8 rewrites the app; these tests run against what R8 produced.

```bash
./gradlew :app:connectedAndroidTest -PtestRelease \
  -Pandroid.testInstrumentationRunnerArguments.notClass=com.householdsplitter.MigrationTest
```

`MigrationTest` is excluded because `room-testing`'s schema reader cannot resolve under
minification. Migrations are raw SQL strings that R8 has no way to alter, and they are
covered by the ordinary debug run above.

**3. That R8 did not rename anything written to disk.** This app persists by name: every
enum goes into SQLite as `.name()` and comes back through `valueOf`, and backups are JSON
keyed on Java field names. A rename here is not a crash, it is a wrong number in somebody's
bill, found weeks later.

```bash
grep -E "AMAZON_FRESH|WALMART|PARSED|MANUAL" app/build/outputs/mapping/release/mapping.txt
```

Those constants must appear unchanged on the right hand side. The same goes for the entity
fields `parsedName`, `parsedCents`, `missVerdict`, `lineTotalCents` and `store`.

**4. That the shipped manifest asks for nothing.**

```bash
aapt2 dump xmltree --file AndroidManifest.xml app/build/outputs/apk/release/app-release.apk | grep -i permission
```

The only entry should be AndroidX's own generated receiver self-permission. The string
`INTERNET` should not occur at all. The privacy policy makes a claim about this, and a
claim about a build should be checked against the build.

**5. Lint.**

```bash
./gradlew :app:lintDebug
```

## Known gap

`minSdk` is 24, so the app is offered to Android 7.0 and up. That is verified statically:
lint's `NewApi` detector reports zero violations, `java.time` is backported by core library
desugaring, and the adaptive launcher icon now has rendered PNG fallbacks for launchers
that predate it.

It has **not** been run on a real Android 7 device or emulator, because no system image
below API 36 is installed here and the SDK command line tools are not present to fetch one.
Before a public release, install an API 24 image from Android Studio's SDK Manager and run
the instrumented suite against it. Until then, treat Android 7 and 8 support as argued
rather than demonstrated.
