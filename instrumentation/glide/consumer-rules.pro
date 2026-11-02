# Keep the Glide module annotation so the generated AppGlideModule registry entry is preserved.
-keep @com.bumptech.glide.annotation.GlideModule class * { *; }
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public class com.bumptech.glide.GeneratedAppGlideModuleImpl
