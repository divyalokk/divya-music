# Add project-specific ProGuard/R8 keep rules here.
# These are applied on top of the bundled defaults (proguard-android-optimize.txt) when the
# build type has minifyEnabled = true.
#
# Keep a class that is referenced only by reflection / from XML, e.g.:
# -keep class com.example.SomeClass { *; }
#
# Preserve line numbers for readable crash stack traces, then hide the original file name:
# -keepattributes SourceFile,LineNumberTable
# -renamesourcefileattribute SourceFile

# ---- DivyaMusic release keep rules ----

# In-app update data class, parsed and used in the update dialog
-keep class com.divyamusic.divyalokk.UpdateInfo { *; }

# org.json is part of the Android framework, but keep the names used by JSON parsing code
-keep class org.json.** { *; }

# Foreground playback service, started by the system and referenced from the manifest
-keep class com.divyamusic.divyalokk.PlaybackService { *; }

# Compose runtime relies on these names for reflection-based lookups
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**
