package com.noteflow.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.noteflow.app.MainActivity
import com.noteflow.app.NativeStorageBridge
import com.noteflow.app.R

/**
 * Widget home-screen NoteFlow.
 *
 * Alur datanya:
 *  1. Di index.html, setiap kali save() dipanggil (auto-save tiap ketik,
 *     atau saat simpan catatan/to-do), JS memanggil:
 *       window.NativeStorage.saveToWidget(judul, isiSingkat)
 *  2. NativeStorageBridge.kt menyimpan itu ke SharedPreferences lalu
 *     mem-broadcast ACTION_APPWIDGET_UPDATE.
 *  3. Class ini (NoteFlowWidgetProvider) menerima broadcast tsb lewat
 *     onUpdate(), lalu membaca SharedPreferences dan menggambar ulang
 *     RemoteViews-nya.
 *
 * Widget bersifat read-only (tap = buka app), sesuai gaya "Mini Note"
 * pada fitur Widget di HTML asli. Menambahkan checklist to-do
 * interaktif di widget bisa jadi peningkatan lanjutan (perlu
 * PendingIntent per-item + RemoteViewsService/ListView).
 */
class NoteFlowWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle?
    ) {
        // Redraw ulang saat ukuran widget diubah user (resize di homescreen)
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        private const val MAX_BODY_CHARS = 140

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val prefs = context.getSharedPreferences(
                NativeStorageBridge.PREFS_NAME,
                Context.MODE_PRIVATE
            )
            val title = prefs.getString(NativeStorageBridge.KEY_WIDGET_TITLE, null)
            val bodyRaw = prefs.getString(NativeStorageBridge.KEY_WIDGET_BODY, null)

            val views = RemoteViews(context.packageName, R.layout.widget_note)

            if (title.isNullOrBlank() && bodyRaw.isNullOrBlank()) {
                views.setTextViewText(R.id.widgetTitle, context.getString(R.string.widget_empty_title))
                views.setTextViewText(R.id.widgetBody, context.getString(R.string.widget_empty_body))
            } else {
                views.setTextViewText(
                    R.id.widgetTitle,
                    if (title.isNullOrBlank()) context.getString(R.string.widget_untitled) else title
                )
                val body = bodyRaw.orEmpty().let {
                    if (it.length > MAX_BODY_CHARS) it.take(MAX_BODY_CHARS) + "…" else it
                }
                views.setTextViewText(R.id.widgetBody, body)
            }

            // Tap widget -> buka MainActivity (langsung ke daftar catatan)
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                widgetId,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
