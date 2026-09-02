# User Manual — IR BM25 Module untuk OpenMRS

## 1. Untuk siapa dokumen ini
Dokumen ini ditujukan untuk tenaga medis atau operator yang menggunakan fitur pencarian pada sistem OpenMRS. Dokumen ini bukan untuk developer.

## 2. Prasyarat
- OpenMRS sudah berjalan dengan modul `ir-bm25` ter-install (rujuk ke `DEVELOPMENT.md` untuk panduan instalasi).
- Memiliki akun login (contoh: `admin` / `Admin123` di lingkungan demo).

## 3. Membuka halaman pencarian
- Buka URL: `http://<host>/openmrs/module/ir-bm25/ir-bm25.form`
- Atau akses melalui link di sidebar Legacy UI.

## 4. Melakukan pencarian dasar
- Ketik kata kunci di kolom query (contoh: "chest pain").
- Pilih jumlah hasil yang ingin ditampilkan (limit).
- Klik tombol "Search".

## 5. Membaca hasil
Hasil pencarian akan menampilkan kolom-kolom berikut:
- **Sample Name**: Nama sampel dokumen.
- **Description**: Deskripsi singkat dokumen.
- **Specialties**: Spesialisasi medis terkait.
- **Score**: Skor BM25 (semakin besar nilainya, semakin relevan dokumen tersebut dengan query).
- **Snippet**: Potongan teks dari dokumen yang cocok dengan kata kunci pencarian.
Urutan hasil pencarian didasarkan pada ranking relevansi (skor tertinggi di atas).

## 6. Memilih varian pencarian
Anda dapat memilih varian pencarian melalui dropdown yang tersedia:
- **V0 (Naive BM25)**: Baseline — hanya mengubah ke huruf kecil dan menghapus tanda baca.
- **V1 (Normalisasi klinis)**: **(Disarankan)** V0 + negasi dipertahankan + istilah majemuk + ekspansi singkatan medis (misal: mencari "CKD" akan menemukan dokumen yang menulis "chronic kidney disease").
- **V2 (Klinis + ekspansi)**: V1 + ekspansi sinonim di sisi query.
- **V3 (Char n-gram)**: Pengindeksan berbasis karakter n-gram (3–5), tahan terhadap variasi morfologi dan salah ketik.
- **V4 (Klinis + ekspansi + PRF)**: V2 + pseudo-relevance feedback.

## 7. Mode "Bandingkan semua varian"
Mode ini memungkinkan Anda untuk melihat hasil dari semua varian (V0–V4) secara berdampingan untuk query yang sama. Ini sangat berguna untuk melihat bagaimana varian yang lebih kaya (V1–V4) dapat menemukan dokumen yang terlewat oleh varian lain.

## 8. Memakai endpoint JSON (untuk integrasi)
Sistem ini juga menyediakan endpoint JSON untuk integrasi dengan aplikasi lain:
`GET /openmrs/module/ir-bm25/search.json?q=hypertension&variant=v1&limit=10`

## 9. Troubleshooting
- **Hasil kosong / "index belum siap"**: Tunggu beberapa saat hingga modul selesai melakukan proses startup. Pastikan global property `irbm25.corpusPath` sudah diatur dengan benar.
- **Skor semua 0**: Query Anda mungkin tersaring habis oleh daftar stopword (kata umum yang diabaikan). Coba gunakan istilah medis yang lebih spesifik.