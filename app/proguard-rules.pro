-keep class ru.uust.schedule.domain.** { *; }
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class ru.uust.schedule.**$$serializer { *; }
-keepclassmembers class ru.uust.schedule.** { *** Companion; }
-dontwarn org.jsoup.**
-keep class org.jsoup.** { *; }

# commons-compress поддерживает много форматов сжатия, а jbsdiff использует
# только bzip2 (формат патчей bsdiff). Остальные кодеки — необязательные
# зависимости, которых в проекте нет и которые никогда не вызываются в рантайме.
-dontwarn com.github.luben.zstd.**
-dontwarn org.apache.commons.codec.digest.PureJavaCrc32C
-dontwarn org.apache.commons.codec.digest.XXHash32
-dontwarn org.brotli.dec.**
-dontwarn org.tukaani.xz.**
