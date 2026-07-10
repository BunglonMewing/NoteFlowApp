# ══════════════════════════════════════════
# NoteFlow — Proguard/R8 rules
# ══════════════════════════════════════════

# WAJIB: method @JavascriptInterface HARUS tetap ada nama aslinya,
# kalau di-obfuscate maka window.NativeStorage.saveData() dkk di
# index.html akan gagal total (silent failure, note tidak tersimpan).
-keepclassmembers class com.noteflow.app.NativeStorageBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.noteflow.app.NativeStorageBridge { *; }

# Class yang dipanggil lewat JS interface juga wajib di-keep utuh
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# WebView internal (hindari warning/crash R8 di beberapa versi WebView)
-keepclassmembers class android.webkit.WebView {
    public *;
}

# org.json dipakai untuk baca/tulis data (defensif, walau umumnya sudah aman)
-keep class org.json.** { *; }
-dontwarn org.json.**

# FileProvider (dipakai fitur export) butuh nama class provider tetap utuh
-keep class androidx.core.content.FileProvider { *; }

# AppWidgetProvider harus tetap ada nama class-nya persis, karena
# direferensikan lewat string di AndroidManifest.xml (android:name)
-keep class com.noteflow.app.widget.NoteFlowWidgetProvider { *; }

# Hilangkan Log.d/Log.v di build release (opsional, sedikit bantu performa)
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
