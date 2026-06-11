# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep OpenCV
-keep class org.opencv.** { *; }

# Keep libgphoto2 JNI
-keep class cn.alittlecookie.lut2photo_n.jni.** { *; }

# Keep Room entities
-keep class cn.alittlecookie.lut2photo_n.data.database.entity.** { *; }

# Keep Hilt
-keep class dagger.hilt.** { *; }
