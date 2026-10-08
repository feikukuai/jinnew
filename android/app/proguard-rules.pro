-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.traework.jygoldenfinger.**$$serializer { *; }
-keepclassmembers class com.traework.jygoldenfinger.** {
    *** Companion;
}
-keepclasseswithmembers class com.traework.jygoldenfinger.** {
    kotlinx.serialization.KSerializer serializer(...);
}