# Retrofit 3 and kotlinx.serialization provide their own consumer rules.
# Typed navigation serializes these route classes, including in optimized builds.
-keepclassmembers,allowoptimization class com.reality.android.ui.navigation.** {
    *** Companion;
}
