# NoteFlow — Android (Kotlin + WebView + Widget)

Wrapper Android native untuk aplikasi NoteFlow yang sebelumnya berupa
HTML/CSS/JS murni. Semua UI/UX (editor rich-text, chat AI, tema, focus
mode, dsb) **tetap 100% jalan di WebView** memakai file HTML aslinya —
yang diganti hanya lapisan penyimpanan (`localStorage` → file Android
asli) dan fitur export (`a.download` → Share sheet native), plus
tambahan **home-screen widget**.

## 1. Struktur folder (taruh persis seperti ini)

```
NoteFlow/                              <- root project, buka ini di Android Studio
├── build.gradle.kts                   <- root
├── settings.gradle.kts
├── gradle.properties
├── app/
│   ├── build.gradle.kts               <- module app
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   └── index.html             <- ⚠️ TARUH DI SINI (bukan assets/assets/)
│       ├── java/com/noteflow/app/
│       │   ├── MainActivity.kt
│       │   ├── NativeStorageBridge.kt
│       │   ├── NoteFlowApp.kt
│       │   └── widget/
│       │       └── NoteFlowWidgetProvider.kt
│       └── res/
│           ├── drawable/
│           │   ├── ic_launcher_background.xml
│           │   ├── ic_launcher_foreground.xml
│           │   ├── ic_widget_note.xml
│           │   ├── widget_background.xml
│           │   └── widget_preview.xml
│           ├── layout/
│           │   ├── activity_main.xml
│           │   └── widget_note.xml
│           ├── mipmap-anydpi-v26/
│           │   ├── ic_launcher.xml
│           │   └── ic_launcher_round.xml
│           ├── values/
│           │   ├── colors.xml
│           │   ├── strings.xml
│           │   └── themes.xml
│           └── xml/
│               ├── file_paths.xml
│               ├── network_security_config.xml
│               └── widget_info.xml
└── index_html_export_patch.md         <- instruksi edit manual index.html
```

## 2. Langkah setup

1. **Copy `index.html`** (versi HTML aslinya, lengkap dengan CSS & JS
   inline) ke `app/src/main/assets/index.html`.
2. **Terapkan patch export** — buka `index_html_export_patch.md`, ganti
   3 fungsi export (`Simpan ke File HP`, `Ekspor HTML`, `Ekspor CSV`) di
   `openExport()` persis sesuai instruksi. Tanpa ini, tombol export akan
   diam-diam tidak berfungsi di WebView Android.
3. Buka project ini di **Android Studio (Koala/2024.1 ke atas)**, biarkan
   Gradle sync otomatis (butuh koneksi internet untuk download
   dependency pertama kali).
4. **(Opsional tapi disarankan)** Generate launcher icon final lewat
   klik kanan `res` → *New → Image Asset*, ganti ikon sementara yang
   sudah saya sediakan (`ic_launcher_foreground.xml`), sekaligus supaya
   otomatis ter-generate fallback PNG untuk Android 7.0–7.1 (API 24–25)
   yang belum support adaptive icon.
5. Run ke device/emulator (`minSdk 24` / Android 7.0+).

## 3. Peta fitur HTML asli ↔ implementasi native

| Fitur di HTML/JS                      | Implementasi native                                  |
|----------------------------------------|--------------------------------------------------------|
| `window.NativeStorage.saveData/loadData` | File internal app (`NativeStorageBridge.kt`)         |
| `window.NativeStorage.backupSD/loadBackupSD` | App-specific external storage, tanpa perlu izin  |
| `window.NativeStorage.saveToWidget`    | `SharedPreferences` + broadcast update widget           |
| Tombol "Simpan ke File HP / Ekspor HTML / CSV" | `window.NativeStorage.exportFile()` → Share sheet Android (perlu patch manual, lihat poin 2) |
| Fitur "Widget" di sheet (`openWidget()`) | `NoteFlowWidgetProvider.kt` (home-screen widget nyata, bukan simulasi di dalam app) |
| 12 tema (`applyTheme`, CSS var `--p` dst) | Tetap murni di JS/CSS — tidak disentuh sama sekali |
| Chat AI (`fetch(AI_API)`)              | Tetap `fetch()` di JS, domain di-whitelist di `network_security_config.xml` |

## 4. Checklist testing sebelum rilis

- [ ] Buka app pertama kali → data seed (contoh catatan AI, Android 15,
      dst) muncul normal, tersimpan setelah app di-*kill* dan dibuka lagi
      (verifikasi `saveData`/`loadData` jalan).
- [ ] Tulis catatan baru, tutup app dari recent apps, buka lagi → catatan
      tidak hilang.
- [ ] Tekan "Simpan ke File HP" / "Ekspor HTML" / "Ekspor CSV" → share
      sheet Android muncul (bukan diam saja) — pastikan sudah menerapkan
      `index_html_export_patch.md`.
- [ ] Tambah widget NoteFlow ke home screen → judul & cuplikan catatan
      terbaru muncul; tap widget membuka app.
- [ ] Edit/simpan catatan baru → widget ter-update otomatis dalam
      beberapa detik (tanpa perlu buka ulang app).
- [ ] Tes di perangkat/emulator **Android 7.1 (API 25)** — pastikan ikon
      launcher tidak crash/kosong (lihat poin 4 di setup: perlu fallback
      PNG mipmap).
- [ ] Tes mode gelap sistem ON/OFF → status bar & navigation bar Android
      ikut menyesuaikan (temanya sendiri di dalam WebView tetap dikontrol
      manual lewat tombol tema NoteFlow, ini disengaja/terpisah).
- [ ] Tes Chat AI dengan koneksi data seluler & WiFi → pastikan tidak
      diblokir oleh `network_security_config.xml` (kalau AI_API pindah
      domain, update domain di file itu).
- [ ] Build **release** (`./gradlew assembleRelease`) → install APK hasil
      minify, ulangi checklist di atas (memastikan Proguard/R8 tidak
      merusak JS interface).

## 5. Batasan yang perlu diketahui klien

- Widget bersifat **read-only** (tampil info saja, tap = buka app).
  Checklist to-do interaktif langsung dari widget belum termasuk scope
  ini — butuh `RemoteViewsService` terpisah kalau mau ditambahkan nanti.
- Backup "SD" tidak lagi menulis ke `/sdcard/NoteFlow/` secara harfiah
  (dibatasi kebijakan scoped storage Android 10+), melainkan ke folder
  privat app di partisi eksternal — tetap aman dari uninstall-wipe.
