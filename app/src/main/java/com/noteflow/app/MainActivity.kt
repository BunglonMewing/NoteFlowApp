package com.noteflow.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.noteflow.app.databinding.ActivityMainBinding

/**
 * MainActivity adalah "shell" native yang menampilkan UI NoteFlow
 * (index.html/CSS/JS asli) di dalam WebView.
 *
 * Semua logika UI (render catatan, editor, chat AI, tema, dsb) TETAP
 * berjalan di JavaScript persis seperti file HTML aslinya — kita
 * hanya mengganti lapisan penyimpanan `window.NativeStorage` dengan
 * implementasi Kotlin asli (lihat NativeStorageBridge.kt), sehingga
 * data tersimpan permanen di penyimpanan internal Android + backup,
 * bukan hanya localStorage.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var bridge: NativeStorageBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView: WebView = binding.webview
        // Pakai `this` (Activity), bukan applicationContext, supaya
        // exportFile() bisa startActivity() Share sheet dengan transisi
        // normal tanpa perlu FLAG_ACTIVITY_NEW_TASK.
        bridge = NativeStorageBridge(this, webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true          // fallback localStorage tetap aktif
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        // Ikuti tema terang/gelap sistem untuk WebView (Android 13+/WebView modern)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, false)
            // false karena index.html sudah mengurus tema gelapnya sendiri (data-t="dark")
        }

        // Nama objek "NativeStorage" HARUS sama dengan yang dipanggil di index.html:
        // window.NativeStorage.saveData(...), .loadData(), .backupSD(...), dst.
        webView.addJavascriptInterface(bridge, "NativeStorage")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                // Semua navigasi tetap di dalam WebView (SPA satu halaman)
                return false
            }
        }
        webView.webChromeClient = WebChromeClient()

        // PENTING: letakkan index.html di app/src/main/assets/index.html
        // (bukan assets/assets/index.html)
        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && binding.webview.canGoBack()) {
            // index.html mengelola navigasi internal via goBack()/goTo() sendiri
            // (transisi antar <div class="view">), jadi tombol back Android
            // cukup diteruskan ke JS lewat history WebView bila ada,
            // atau biarkan sistem yang menutup activity.
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        binding.webview.destroy()
        super.onDestroy()
    }
}
