# The app does not use reflection-based serializers or network SDKs.
# Keep rules can remain intentionally minimal.

# uCrop
-dontwarn com.yalantis.ucrop**
-keep class com.yalantis.ucrop** { *; }
-keep interface com.yalantis.ucrop** { *; }
