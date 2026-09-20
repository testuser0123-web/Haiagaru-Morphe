-dontobfuscate
-dontoptimize
-keepattributes *
-keep class app.morphe.extension.chmate.** {
    *;
}
-keep class rikka.shizuku.** {
    *;
}

# Rhino uses reflective interpreter/builtin discovery. Only interpreted mode is used.
-keep class org.mozilla.javascript.** { *; }
# JVM-only optimizer paths are never selected by NgScriptEngine.
-dontwarn jdk.dynalink.**
