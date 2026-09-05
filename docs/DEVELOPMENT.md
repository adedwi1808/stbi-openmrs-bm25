# Developer Guide — Pengembangan Lanjut & Modifikasi

## 1. Prasyarat build
- Python 3.x
- JDK 8+
- Maven 3.x
- Docker (opsional, untuk menjalankan OpenMRS)

## 2. Layout repo
- `dataset/`: Berisi dataset mentah dan hasil pemrosesan.
- `tools/`: Skrip Python untuk pemrosesan data dan evaluasi.
- `eval/`: Data evaluasi (query dan qrels).
- `openmrs-module-ir-bm25/`: Source code modul Java/OpenMRS.
- `scripts/`: Skrip bash untuk build dan deploy.
- `laporan/`: Dokumen laporan proyek.
- `presentasi/`: File presentasi proyek.
- `paper/`: Naskah final IEEE (`paper_final.docx`, `laporan_final.pdf`).

## 3. Alur build penuh
- Jalankan `scripts/build.sh` untuk menghasilkan `corpus.jsonl`, lexicon, qrels, dan file `.omod`.
- Atau gunakan wrapper: `make build`.
- Output file `.omod` akan berada di `openmrs-module-ir-bm25/omod/target/`.

## 4. Menjalankan mesin IR tanpa OpenMRS (CLI)
Gunakan skrip `scripts/cli.sh` untuk menjalankan fungsi index, search, atau eval melalui command line.

## 5. Menjalankan test
- Jalankan `make test` (atau `mvn -f openmrs-module-ir-bm25/pom.xml test`).
- Test yang tersedia meliputi: `ClinicalNormalizerTest`, `IrBm25ServiceTest`, `AdminListExtensionTest`.

## 6. Cara MENAMBAH varian baru (mis. V3)
- Tambahkan enum baru di `SearchVariant.java` (tentukan field dan strategi).
- Jika memerlukan field index baru: daftarkan analyzer di `IrAnalyzers.java`, tambahkan field di `IrIndexer.java`, lalu lakukan re-index.
- Tangani logika query-side di `IrSearcher.java`.
- Tambahkan opsi varian baru di dropdown UI (`ir-bm25.jsp`) dan controller.
- Tambahkan varian ke `EvalRunner` agar ikut dievaluasi.

## 7. Cara MEMPERBARUI lexicon klinis
- Edit sumber data lexicon, lalu jalankan `tools/build_lexicon.py`.
- Output akan disimpan di `openmrs-module-ir-bm25/api/src/main/resources/clinical/` (termasuk `negation_terms.txt`, `multiword_terms.txt`, `abbreviations.tsv`, `synonyms.tsv`).
- Rebuild file `.omod` dan lakukan re-deploy.

## 8. Soal shaded Lucene
- Lucene 8.11.2 di-relokasi (shaded) ke `org.openmrs.module.irbm25.shaded.lucene` menggunakan `maven-shade-plugin`.
- Alasan: Untuk menghindari konflik (bentrok) dengan versi Lucene bawaan OpenMRS.
- Konfigurasi ini terdapat di `openmrs-module-ir-bm25/api/pom.xml`.

## 9. Deploy ke OpenMRS
- `scripts/openmrs.sh up` (menjalankan stack Docker OpenMRS).
- `scripts/deploy.sh` (menyalin `.omod` dan corpus, mengatur global property, dan merestart backend).
- Global property yang digunakan: `irbm25.corpusPath`, `irbm25.indexDir`.
- Index akan dibangun secara otomatis saat modul startup.
- Direktori `openmrs-distro-referenceapplication/` adalah clone upstream yang di-gitignore;
  `scripts/openmrs.sh` meng-clone-nya otomatis bila belum ada.
- Compose memakai tag image `${TAG:-qa}`. `qa` adalah tag berjalan, jadi mesin lain bisa
  mendapat versi berbeda. Override dengan `OPENMRS_TAG=<tag> make openmrs`.
  Kombinasi image yang sudah diverifikasi jalan dengan modul ini (5 Sep 2026):

  ```
  backend  @sha256:b90294dc5905195a4e146d0824ad90cb7c60a70afa653a86483c3fcf7a37c6d9
  frontend @sha256:cddd488d775302334dcad8d5bd216f442dbb962630db384ba98ba159f1424bd9
  gateway  @sha256:003209be27cabcc3250fba762f9c25c34beafed4499bb1cb4ce5c6db5e2a26ff
  db       mariadb:10.11.7
  ```

## 10. Konvensi kode / kontribusi
- Ikuti gaya kode standar Java.
- Buat branch baru untuk setiap fitur atau perbaikan bug.
- Ajukan Pull Request (PR) untuk direview oleh anggota tim lain sebelum di-merge ke branch utama.