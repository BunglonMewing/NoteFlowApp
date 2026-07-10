# Patch `index.html` — Fitur Export (WAJIB diterapkan manual)

`a.download` (link `<a href="data:...">` + `.click()`) **tidak memicu
download** di dalam Android `WebView` seperti di Chrome desktop/HP.
Karena itu, 3 fungsi export di `openExport()` (sheet "Simpan & Ekspor")
perlu diubah supaya memanggil `window.NativeStorage.exportFile(fileName,
mimeType, content)` — method baru yang sudah diimplementasikan di
`NativeStorageBridge.kt` (batch 4). Method ini menulis file ke cache app
lalu membuka **Share/Save sheet Android native** (bisa simpan ke
Files/Drive, kirim WhatsApp, email, dst).

Kode tetap ada fallback ke `a.download` lama, supaya `index.html` ini
masih bisa dites langsung di browser biasa (di luar WebView) tanpa error.

Cari 3 blok berikut di dalam fungsi `openExport()` dan ganti sesuai.

---

## 1) "Simpan ke File HP" (export JSON)

**SEBELUM:**
```js
{ic:'💾',nm:'Simpan ke File HP',sb:'Simpan data.json ke /sdcard/NoteFlow/',fn(){
  const d=JSON.stringify({notes,todos,trash,savedAt:new Date().toISOString()},null,2);
  // Coba native bridge dulu
  if(window.NativeStorage&&window.NativeStorage.backupSD){
    try{window.NativeStorage.backupSD(d);snack('✓ Disimpan ke /sdcard/NoteFlow/backup.json');closeSheet('expOv');return;}catch(e){}
  }
  // Fallback: download file
  const a=document.createElement('a');
  a.href='data:application/json,'+encodeURIComponent(d);
  a.download='noteflow-data-'+new Date().toISOString().slice(0,10)+'.json';
  a.click();snack('✓ File data.json diunduh');closeSheet('expOv');
}},
```

**SESUDAH:**
```js
{ic:'💾',nm:'Simpan ke File HP',sb:'Simpan / bagikan sebagai file .json',fn(){
  const d=JSON.stringify({notes,todos,trash,savedAt:new Date().toISOString()},null,2);
  const fname='noteflow-data-'+new Date().toISOString().slice(0,10)+'.json';
  // Tetap simpan salinan backup senyap ke app storage
  if(window.NativeStorage&&window.NativeStorage.backupSD){
    try{window.NativeStorage.backupSD(d);}catch(e){}
  }
  // Buka Share/Save sheet Android native
  if(window.NativeStorage&&window.NativeStorage.exportFile){
    window.NativeStorage.exportFile(fname,'application/json',d);
    snack('✓ Pilih aplikasi untuk menyimpan/berbagi');closeSheet('expOv');return;
  }
  // Fallback untuk testing di browser biasa (bukan WebView Android)
  const a=document.createElement('a');
  a.href='data:application/json,'+encodeURIComponent(d);
  a.download=fname;
  a.click();snack('✓ File data.json diunduh');closeSheet('expOv');
}},
```

---

## 2) "Ekspor HTML"

**SEBELUM:**
```js
{ic:'📄',nm:'Ekspor HTML',sb:'Semua catatan sebagai webpage',fn(){
  const body=notes.map(n=>`<article><h2>${n.title}</h2>${n.html}<p style="color:#888;font-size:12px">${n.tag} · ${n.date}</p></article><hr>`).join('');
  const h=`<!DOCTYPE html>...</html>`;
  const a=document.createElement('a');a.href='data:text/html,'+encodeURIComponent(h);a.download='noteflow-export.html';a.click();snack('Diekspor!');closeSheet('expOv');
}},
```

**SESUDAH** (hanya baris terakhir yang berubah, isi `h` tetap sama):
```js
{ic:'📄',nm:'Ekspor HTML',sb:'Semua catatan sebagai webpage',fn(){
  const body=notes.map(n=>`<article><h2>${n.title}</h2>${n.html}<p style="color:#888;font-size:12px">${n.tag} · ${n.date}</p></article><hr>`).join('');
  const h=`<!DOCTYPE html>...</html>`; // <-- isi persis sama seperti aslinya, tidak diubah
  if(window.NativeStorage&&window.NativeStorage.exportFile){
    window.NativeStorage.exportFile('noteflow-export.html','text/html',h);
    snack('✓ Pilih aplikasi untuk menyimpan/berbagi');closeSheet('expOv');return;
  }
  const a=document.createElement('a');a.href='data:text/html,'+encodeURIComponent(h);a.download='noteflow-export.html';a.click();snack('Diekspor!');closeSheet('expOv');
}},
```

---

## 3) "Ekspor CSV"

**SEBELUM:**
```js
{ic:'📊',nm:'Ekspor CSV',sb:'Semua catatan sebagai spreadsheet',fn(){
  const rows=[['ID','Judul','Isi','Tag','Tanggal','Disematkan']];
  notes.forEach(n=>rows.push([n.id,'"'+n.title.replace(/"/g,'""')+'"','"'+strip(n.html).replace(/"/g,'""')+'"',n.tag,n.date,n.pinned?'Ya':'Tidak']));
  const csv=rows.map(r=>r.join(',')).join('\n');
  const a=document.createElement('a');a.href='data:text/csv;charset=utf-8,\uFEFF'+encodeURIComponent(csv);a.download='noteflow-export.csv';a.click();snack('CSV diunduh!');closeSheet('expOv');
}},
```

**SESUDAH:**
```js
{ic:'📊',nm:'Ekspor CSV',sb:'Semua catatan sebagai spreadsheet',fn(){
  const rows=[['ID','Judul','Isi','Tag','Tanggal','Disematkan']];
  notes.forEach(n=>rows.push([n.id,'"'+n.title.replace(/"/g,'""')+'"','"'+strip(n.html).replace(/"/g,'""')+'"',n.tag,n.date,n.pinned?'Ya':'Tidak']));
  const csv='\uFEFF'+rows.map(r=>r.join(',')).join('\n'); // BOM digabung ke content, bukan ke data URI
  if(window.NativeStorage&&window.NativeStorage.exportFile){
    window.NativeStorage.exportFile('noteflow-export.csv','text/csv',csv);
    snack('✓ Pilih aplikasi untuk menyimpan/berbagi');closeSheet('expOv');return;
  }
  const a=document.createElement('a');a.href='data:text/csv;charset=utf-8,'+encodeURIComponent(csv);a.download='noteflow-export.csv';a.click();snack('CSV diunduh!');closeSheet('expOv');
}},
```

---

## Kenapa `backupSD` dan `loadBackupSD` di fungsi "Restore dari File" TIDAK perlu diubah?

Karena keduanya cuma baca/tulis ke penyimpanan **internal app** lewat
`NativeStorageBridge` (batch 1) — tidak melibatkan link download sama
sekali, jadi sudah berfungsi normal apa adanya di WebView.

## Ringkasan method bridge baru yang dipakai

```
window.NativeStorage.exportFile(fileName: string, mimeType: string, content: string)
```
Tidak mengembalikan nilai (`void`). Efeknya langsung membuka Android
Share sheet berisi file yang baru ditulis ke
`context.cacheDir/exports/<fileName>`.
