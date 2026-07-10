package com.noteflow.app

import android.content.Context
import android.content.Intent
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.appwidget.AppWidgetManager
import androidx.core.content.FileProvider
import com.noteflow.app.widget.NoteFlowWidgetProvider
import java.io.File
import java.io.IOException

/**
 * Jembatan JavaScript <-> Kotlin.
 *
 * Method-method di sini dipanggil langsung dari index.html melalui
 * `window.NativeStorage.<nama_method>(...)` — jadi NAMA method dan
 * jumlah/tipe parameter di bawah ini harus persis sama dengan yang
 * dipakai di JS (lihat fungsi save(), loadNative(), openExport() pada
 * index.html).
 *
 * Pemetaan penyimpanan:
 *  - saveData()/loadData()   -> file internal app (selalu ada, tidak perlu izin)
 *  - backupSD()/loadBackupSD() -> app-specific external storage
 *      (Android/data/com.noteflow.app/files/NoteFlow/backup.json)
 *      Ini pengganti aman dari "/sdcard/NoteFlow/" di komentar HTML asli,
 *      karena Android 10+ (scoped storage) tidak lagi mengizinkan tulis
 *      bebas ke /sdcard tanpa MANAGE_EXTERNAL_STORAGE (izin sensitif yang
 *      sebaiknya dihindari untuk app catatan biasa).
 *  - saveToWidget()          -> SharedPreferences + broadcast update widget
 *  - exportFile()            -> tulis ke cache/exports/ lalu buka Share/Save
 *      sheet Android (pengganti `a.download` yang tidak berfungsi di WebView).
 *      Lihat catatan patch untuk index.html di file terpisah
 *      "index_html_export_patch.md".
 *
 * Catatan threading: method @JavascriptInterface dipanggil oleh WebView
 * di background thread (bukan main thread), jadi aman melakukan I/O file
 * di sini secara sinkron tanpa nge-block UI. Untuk exportFile() yang
 * memanggil startActivity(), Android tetap menangani perpindahan ke
 * main thread secara otomatis.
 *
 * PENTING: `context` yang di-inject sebaiknya Activity (bukan
 * applicationContext) supaya Share sheet punya animasi transisi yang
 * benar dan tidak perlu FLAG_ACTIVITY_NEW_TASK. Lihat MainActivity.kt.
 */
class NativeStorageBridge(
    private val context: Context,
    @Suppress("unused") private val webView: WebView
) {
    companion object {
        private const val TAG = "NativeStorageBridge"
        private const val INTERNAL_FILE_NAME = "noteflow_data.json"
        private const val BACKUP_DIR_NAME = "NoteFlow"
        private const val BACKUP_FILE_NAME = "backup.json"
        private const val EXPORT_DIR_NAME = "exports"
        const val PREFS_NAME = "noteflow_widget_prefs"
        const val KEY_WIDGET_TITLE = "widget_title"
        const val KEY_WIDGET_BODY = "widget_body"
    }

    private val internalFile: File
        get() = File(context.filesDir, INTERNAL_FILE_NAME)

    private val backupFile: File
        get() {
            val dir = File(context.getExternalFilesDir(null), BACKUP_DIR_NAME)
            if (!dir.exists()) dir.mkdirs()
            return File(dir, BACKUP_FILE_NAME)
        }

    /** Dipanggil dari JS: window.NativeStorage.saveData(jsonString) */
    @JavascriptInterface
    fun saveData(json: String) {
        try {
            internalFile.writeText(json)
        } catch (e: IOException) {
            Log.e(TAG, "Gagal menyimpan data internal", e)
        }
    }

    /** Dipanggil dari JS: window.NativeStorage.loadData() -> String */
    @JavascriptInterface
    fun loadData(): String {
        return try {
            if (internalFile.exists()) internalFile.readText() else ""
        } catch (e: IOException) {
            Log.e(TAG, "Gagal membaca data internal", e)
            ""
        }
    }

    /** Dipanggil dari JS: window.NativeStorage.backupSD(jsonString) */
    @JavascriptInterface
    fun backupSD(json: String) {
        try {
            backupFile.writeText(json)
        } catch (e: IOException) {
            Log.e(TAG, "Gagal menyimpan backup", e)
        }
    }

    /** Dipanggil dari JS: window.NativeStorage.loadBackupSD() -> String */
    @JavascriptInterface
    fun loadBackupSD(): String {
        return try {
            if (backupFile.exists()) backupFile.readText() else ""
        } catch (e: IOException) {
            Log.e(TAG, "Gagal membaca backup", e)
            ""
        }
    }

    /**
     * Dipanggil dari JS setiap kali save() dijalankan:
     * window.NativeStorage.saveToWidget(title, body)
     * Menyimpan cuplikan catatan teratas untuk ditampilkan di home-screen widget,
     * lalu memicu AppWidgetProvider untuk redraw.
     */
    @JavascriptInterface
    fun saveToWidget(title: String, body: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_WIDGET_TITLE, title)
                .putString(KEY_WIDGET_BODY, body)
                .apply()

            val intent = Intent(context, NoteFlowWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val ids = AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(android.content.ComponentName(context, NoteFlowWidgetProvider::class.java))
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal update widget", e)
        }
    }

    /**
     * Dipanggil dari JS: window.NativeStorage.exportFile(fileName, mimeType, content)
     *
     * Menulis `content` ke file sementara di cache app, lalu membuka Android
     * Share sheet (ACTION_SEND) supaya user bisa "Simpan ke Files/Drive",
     * kirim via WhatsApp, email, dsb — menggantikan `a.download` dari HTML
     * asli yang tidak berfungsi di WebView Android.
     *
     * Dipakai untuk 3 tombol export di sheet "Simpan & Ekspor":
     *  - fileName="noteflow-data-....json"  mimeType="application/json"
     *  - fileName="noteflow-export.html"    mimeType="text/html"
     *  - fileName="noteflow-export.csv"     mimeType="text/csv"
     */
    @JavascriptInterface
    fun exportFile(fileName: String, mimeType: String, content: String) {
        try {
            val exportDir = File(context.cacheDir, EXPORT_DIR_NAME).apply {
                if (!exists()) mkdirs()
            }
            // Sanitasi nama file dasar-dasar (hindari path traversal dari JS)
            val safeName = fileName.replace(Regex("[/\\\\]"), "_")
            val file = File(exportDir, safeName)
            file.writeText(content, Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            val chooser = Intent.createChooser(shareIntent, "Simpan atau bagikan $safeName")
            if (context !is android.app.Activity) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal export file: $fileName", e)
        }
    }
}
