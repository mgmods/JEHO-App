# Strong obfuscation that must keep Zego JNI + app startup working.

-allowaccessmodification

-obfuscationdictionary proguard-dictionary.txt
-classobfuscationdictionary proguard-dictionary.txt
-packageobfuscationdictionary proguard-dictionary.txt

-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses,Exceptions,SourceFile,LineNumberTable
-renamesourcefileattribute ''

# ---- Native / JNI (critical for Zego) ----
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keepclassmembers class * {
    native <methods>;
}

# ---- Zego Express (JNI registers Java classes by exact name) ----
-keep class im.zego.** { *; }
-keep interface im.zego.** { *; }
-keepclassmembers class im.zego.** { *; }
-keepnames class im.zego.**
-dontwarn im.zego.**
-keep class com.Dramizo.Series.zego.** { *; }

# ---- Gson ----
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.Dramizo.Series.data.remote.dto.** { <fields>; <init>(...); }
-keep class com.Dramizo.Series.domain.model.** { <fields>; <init>(...); }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-dontwarn com.google.gson.**

# ---- Retrofit ----
-keep,allowobfuscation interface com.Dramizo.Series.data.remote.api.** { *; }
-keepclassmembers,allowobfuscation interface * {
  @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# ---- Room / Enums ----
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**
-keepclassmembers enum * {
  public static **[] values();
  public static ** valueOf(java.lang.String);
}

# ---- App graph ----
-keep class com.Dramizo.Series.di.AppContainer { *; }
-keep class com.Dramizo.Series.data.local.prefs.SessionManager { *; }
-keep class com.Dramizo.Series.BuildConfig { *; }
-keep class com.Dramizo.Series.util.ApiOrigin { *; }

# ---- SDKs ----
-keep class io.socket.** { *; }
-dontwarn io.socket.**
-dontwarn org.json.**
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.** { *; }
-keep class com.android.vending.billing.** { *; }
-keep class com.android.billingclient.** { *; }
-keep class com.airbnb.lottie.** { *; }
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { *; }

# ---- VAP (Tencent AnimPlayer) + SVGA + Media3 ----
-keep class com.tencent.qgame.animplayer.** { *; }
-keep interface com.tencent.qgame.animplayer.** { *; }
-dontwarn com.tencent.qgame.animplayer.**
-keep class com.opensource.svgaplayer.** { *; }
-dontwarn com.opensource.svgaplayer.**
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ---- CameraX / ML Kit face (identity verify) ----
-keep class androidx.camera.** { *; }
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ---- Play Billing / Updates / Ads ----
-keep class com.google.android.play.core.** { *; }
-dontwarn com.google.android.play.core.**
-keep class com.google.android.gms.ads.** { *; }

# ---- Socket.IO ack callbacks ----
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ---- Android components ----
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends androidx.fragment.app.Fragment

-keepclassmembers class ** implements androidx.viewbinding.ViewBinding {
  public static ** inflate(...);
  public static ** bind(...);
}
