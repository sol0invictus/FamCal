# Keep FamCal Firestore model classes intact — Firestore maps documents to these by
# field name via reflection, so they must not be renamed or stripped in release builds.
-keep class com.famcal.app.data.model.** { *; }
-keepclassmembers class com.famcal.app.data.model.** {
    <init>();
    <fields>;
}

# Keep no-arg constructors used by Firestore deserialization.
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <methods>;
}
