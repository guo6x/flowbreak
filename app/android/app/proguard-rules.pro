-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class com.flowbreak.app.NativeFlowPlugin { *; }
-keep class com.flowbreak.app.FlowDatabase_Impl { *; }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * {
    @com.getcapacitor.PluginMethod <methods>;
}
-dontwarn org.conscrypt.**
