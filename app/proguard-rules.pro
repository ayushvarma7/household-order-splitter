# R8 rules for the release build.
#
# The theme running through all of these is that this app persists things by NAME. Enum
# constants go into SQLite as the result of .name() and come back through valueOf();
# backups go out as JSON whose field names are Java field names. R8's whole job is renaming
# things, and anything it renames that was written to disk under its old name is data this
# app can no longer read. That is not a crash, it is a wrong number in somebody's bill, so
# these rules are deliberately broader than the minimum that compiles.

# ---------------------------------------------------------------------------------------
# Enums. Every one of these is stored as text and read back by name.
#
# StoreKind, ItemOrigin, OrderStatus, DraftStep, Scope, MemberRule.Kind, ReviewReason,
# AllocationMode, MissDiagnosis.Verdict and OrderField all round-trip through their own
# names, in the database, in the review flags CSV, or in a backup file. Renaming any
# constant makes every existing row referring to it unreadable.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}
-keepnames class com.householdsplitter.core.** { *; }

# ---------------------------------------------------------------------------------------
# Room. Entities are constructed and populated by generated code that matches on field
# names, and the type converters are resolved the same way.
-keep class com.householdsplitter.data.entity.** { *; }
-keep class com.householdsplitter.data.relation.** { *; }
-keep class com.householdsplitter.data.converter.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**
# The open helper factory is reached by name from Room's generated code. The app resolves
# it today, but every screen in this app is a database read and there is no version of
# "R8 guessed wrong here" that is recoverable at runtime.
-keep class androidx.sqlite.db.** { *; }
-keep class androidx.sqlite.db.framework.** { *; }

# ---------------------------------------------------------------------------------------
# Gson, which reads and writes the backup file purely by reflection over field names.
# A renamed field here is a backup that cannot be restored, and a backup that cannot be
# restored is discovered at the worst possible moment.
-keep class com.householdsplitter.backup.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-dontwarn sun.misc.**
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# ---------------------------------------------------------------------------------------
# Fragments and ViewModels named in XML rather than called from code.
#
# The navigation graph names every destination as a string, and the layouts name custom
# views the same way. Neither is code as far as R8 is concerned, so nothing in the compiled
# app appears to reference them and they look dead.
-keep class * extends androidx.fragment.app.Fragment { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class com.householdsplitter.ui.** { *; }

# The widget and its provider are instantiated by the system from the manifest.
-keep class com.householdsplitter.widget.** { *; }
-keep class * extends android.appwidget.AppWidgetProvider { *; }
-keep class * extends android.app.Application { *; }

# FileProvider is named as a string in AndroidManifest.xml and instantiated by the system,
# and ImportFragment hands it the camera's output file. AGP generates keeps for
# manifest-declared components, but this one is a library class rather than one of ours and
# the consequence of getting it wrong is that taking a photo of a receipt crashes, which is
# the first thing a new user does.
-keep class androidx.core.content.FileProvider { *; }
-keep class * extends android.content.ContentProvider { *; }

# ---------------------------------------------------------------------------------------
# ML Kit ships its own consumer rules, but the bundled recogniser's model loader is
# reached reflectively and the warnings it produces are not actionable here.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_bundled.** { *; }
-dontwarn com.google.mlkit.**

# ---------------------------------------------------------------------------------------
# Keep line numbers so a stack trace from a released build can still be read, while still
# letting R8 rename everything it is allowed to.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
