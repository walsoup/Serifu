# ML Kit component discovery and registrars
-keep public class * implements com.google.firebase.components.ComponentRegistrar {
    public <init>();
}
-keep class com.google.mlkit.** { *; }
-keep interface com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }

# Keep JNI native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Kotlinx Serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Entry points and reflection-touched types only; the rest may be obfuscated.
-keep class app.walsoup.serifu.service.SerifuAccessibilityService { *; }
-keep class app.walsoup.serifu.ui.MainActivity { *; }
-keep class app.walsoup.serifu.ui.testpage.TestPageActivity { *; }
-keep class app.serifu.bergamot.BergamotNative { *; }
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# Release log hygiene: strip debug/verbose logging (errors stay).
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** w(...);
}

