# Extra keeps applied ONLY when running the instrumented suite against the minified build
# with -PtestRelease. Never part of a shipped release.
#
# The test APK is linked against the app's own R8 mapping, so anything the harness calls
# has to survive in the app even though nothing in the app calls it. That is most of the
# Kotlin standard library, because AndroidX and Espresso are Kotlin and this app is not.
# Keeping all of it in the shipped build would be a megabyte users pay for a convenience
# only the developer gets, so it lives here instead.
#
# The caveat this creates, stated plainly: the bytecode under test retains slightly more
# than the bytecode that ships. That is sound for what these tests are for, because every
# rule here only ADDS retention. Nothing here can make a class survive testing and vanish
# in production, which is the failure that would matter.
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**
-keep class androidx.tracing.** { *; }
-dontwarn androidx.tracing.**
-keep class androidx.test.** { *; }
-dontwarn androidx.test.**
-keep class org.junit.** { *; }
-dontwarn org.junit.**

# The app's own classes, kept wholesale while testing.
#
# Worth being honest about what this costs. The instrumentation suite reaches deep into
# app internals that the app itself never calls from a code path R8 can see: R$id, the
# calc input builders, MlKitReceiptParser, ServiceLocator.database(). Keeping them one at
# a time converges on keeping all of them anyway, so this does it directly.
#
# What that means for what the run proves: it does NOT prove R8 can safely shrink this
# app's own classes. It DOES exercise the rules that carry the real risk, against real
# minified bytecode, on a real device: Room reading and writing every entity, Gson round
# tripping a backup, every persisted enum surviving valueOf, ML Kit recognising text, and
# resource shrinking not removing a layout the app still inflates. Those are the failures
# that would corrupt somebody's money rather than merely crash.
#
# The app-code side is covered instead by installing the shipped APK and checking
# mapping.txt for the names that must not move. See docs/RELEASE.md.
-keep class com.householdsplitter.** { *; }

# room-testing's schema reader. Test-only by definition: MigrationTestHelper uses it to
# parse the exported schema JSON, and the app has no reason to know schemas as data.
-keep class androidx.room.migration.** { *; }
-keep class androidx.room.testing.** { *; }
-dontwarn androidx.room.migration.**

# Navigation's static entry point. The app uses NavHostFragment.findNavController; the
# tests drive the graph through androidx.navigation.Navigation, which the app never touches.
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**
