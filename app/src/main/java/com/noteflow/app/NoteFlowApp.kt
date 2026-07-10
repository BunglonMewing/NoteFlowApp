package com.noteflow.app

import android.app.Application
import android.util.Log
import android.webkit.WebView
import androidx.appcompat.app.AppCompatDelegate

/**
 * Application class NoteFlow.
 *
 * UI/tema warna (teal, violet, rose, dst — 12 tema) sepenuhnya diatur
 * oleh JS di index.html lewat atribut `data-t` pada <html>, JADI di
 * level Android kita TIDAK memaksakan light/dark tertentu untuk WebView
 * itu sendiri. Yang diatur di sini hanya elemen sistem di luar WebView
 * (status bar, splash sebelum WebView siap) supaya konsisten mengikuti
 * pengaturan terang/gelap perangkat.
 */
class NoteFlowApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Status bar/UI chrome Android mengikuti sistem (bukan tema custom
        // NoteFlow yang tersimpan di localStorage/JS — dua hal ini terpisah
        // secara sengaja karena WebView mengatur temanya sendiri).
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
            Log.d("NoteFlowApp", "Debug WebView aktif — bisa di-inspect via chrome://inspect")
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        // Catatan tetap aman (sudah tersimpan lewat NativeStorageBridge),
        // yang dikosongkan hanya cache render WebView.
        try {
            WebView(this).clearCache(true)
        } catch (e: Exception) {
            Log.w("NoteFlowApp", "Gagal membersihkan cache WebView", e)
        }
    }
}
