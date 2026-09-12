# ML Kit / Play Services
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.mlkit.**

# Room
-keep class androidx.room.** { *; }

# Modeles de donnees serialises manuellement (org.json) : rien a garder de special.
