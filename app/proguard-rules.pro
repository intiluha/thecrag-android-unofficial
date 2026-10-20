# kotlinx.serialization, Retrofit, OkHttp, Coil and MapLibre ship their own R8 rules
# inside their artifacts; only app-specific keeps live here.

# Serializable classes are looked up by reflection (Retrofit converter, RecentStore).
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class org.thecragunofficial.android.**$$serializer { *; }
-keepclassmembers class org.thecragunofficial.android.** { *** Companion; }
-keepclasseswithmembers class org.thecragunofficial.android.** { kotlinx.serialization.KSerializer serializer(...); }

# Retrofit interface methods are resolved via reflection on the suspend signature.
-keepattributes Signature, Exceptions
-keep,allowobfuscation,allowshrinking interface org.thecragunofficial.android.data.remote.TheCragApi
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
