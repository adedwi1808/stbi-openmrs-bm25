# Sistem Information Retrieval Berbasis BM25 pada OpenMRS

[![OpenMRS](https://img.shields.io/badge/OpenMRS-2.8.x-005d5d)](#)
[![Java](https://img.shields.io/badge/Java-8%2B-orange)](#)
[![Lucene](https://img.shields.io/badge/Apache%20Lucene-8.11.2-blue)](#)
[![Dataset](https://img.shields.io/badge/Dataset-MTSamples-green)](#)

Proyek **Sistem Temu Balik Informasi (STBI)** yang mengimplementasikan mesin pencarian berbasis
**BM25** untuk catatan medis teks bebas (*free-text clinical notes*) dan mengintegrasikannya ke
dalam platform **OpenMRS** sebagai modul tambahan.

Kontribusi utama proyek ini bukan sekadar "BM25 di dataset medis", melainkan studi **ablasi**
terhadap **lapisan normalisasi & ekspansi klinis** (penanganan negasi, istilah majemuk, singkatan,
dan sinonim medis) yang diuji langsung terhadap *baseline* BM25 naif pada dataset **MTSamples**.

---

## Daftar Isi

1. [Latar Belakang & Gap Penelitian](#latar-belakang--gap-penelitian)
2. [Fitur Utama](#fitur-utama)
3. [Varian Retrieval (Ablation Study)](#varian-retrieval-ablation-study)
4. [Arsitektur Sistem](#arsitektur-sistem)
5. [Struktur Repositori](#struktur-repositori)
6. [Prasyarat](#prasyarat)
7. [Mulai Cepat (Scripts)](#mulai-cepat-scripts)
8. [Pipeline Data](#pipeline-data)
9. [Mesin IR (Java / Lucene)](#mesin-ir-java--lucene)
10. [Integrasi OpenMRS](#integrasi-openmrs)
11. [Evaluasi](#evaluasi)
12. [Dataset & Catatan Lisensi](#dataset--catatan-lisensi)
13. [Kontributor](#kontributor)
14. [Referensi](#referensi)

---

## Latar Belakang & Gap Penelitian

Pencarian pada sistem Rekam Medis Elektronik (RME) konvensional umumnya hanya mendukung
*exact-match* berbasis query SQL sederhana. Akibatnya, tenaga medis kesulitan menemukan kasus
klinis yang relevan ketika catatan memakai istilah yang berbeda — misalnya mencari "gangguan
ginjal kronis" tetapi catatan memakai singkatan **"CKD"**.

BM25 sendiri merupakan model **leksikal** (*bag-of-words*): ia tidak dapat menjembatani sinonim
atau singkatan tanpa lapisan normalisasi/ekspansi tambahan. Tinjauan literatur menunjukkan bahwa
BM25 pada teks klinis umumnya hanya dijadikan *baseline* naif:

| Studi | Fokus | Perlakuan terhadap BM25 |
|---|---|---|
| Mikkelsen (2026) — *Multi-Corpus Benchmark* | Bandingkan *embedding* vs BM25 | BM25 sebagai baseline (lowercase + hapus tanda baca) |
| Zhang (2021) — *Improved BM25* | Optimasi fungsi skor (co-word + Cuckoo Search) | Perbaikan di tahap skor, bukan tokenisasi |
| Gayen dkk. (2023) — *Essie → Solr* | Porting tokenisasi klinis ke Solr | Tokenisasi klinis, tapi bukan untuk BM25 di RME |
| Griffon dkk. (2012) — *UMLS synonyms* | Ekspansi sinonim UMLS | Untuk PubMed, bukan untuk RME |

**Gap yang diisi proyek ini:** belum ada studi yang secara khusus mengevaluasi **lapisan
normalisasi + ekspansi sinonim/singkatan klinis** untuk **BM25** pada dataset **MTSamples**, apalagi
yang diintegrasikan langsung ke **OpenMRS**. Gap ini divalidasi secara empiris dari dataset sendiri
(lihat tabel di bawah) dan diuji sebagai ablation study (V0–V4).

### Bukti dari dataset (2.316 dokumen)

| Sinyal klinis | Cakupan dokumen | Contoh |
|---|---|---|
| Penanda negasi | **88,0%** | `no`, `denies`, `without` — dibuang stopword list umum → makna terbalik |
| Istilah majemuk | **60,9%** | `shortness of breath` dipecah jadi token lepas |
| Singkatan medis | **28,8%** | `c/o`, `DM`, `HTN`, `CKD` |

---

## Fitur Utama

- **Ranked retrieval BM25** (`k1 = 1.2`, `b = 0.75`) di atas indeks terbalik **Apache Lucene 8**.
- **Normalisasi klinis**: stopword list yang mempertahankan penanda negasi, deteksi istilah
  majemuk, ekspansi singkatan medis (simetris di *index* & *query*).
- **Ekspansi sinonim** (query-side) untuk menjembatani variasi terminologi.
- **Ablation study** dengan 5 varian yang bisa dibandingkan berdampingan di UI.
- **Integrasi OpenMRS**: modul `.omod` + endpoint JSON + halaman UI dengan *dropdown* varian.
- **Pipeline evaluasi** TREC-style (query set + qrels + metrik P@K, Recall@K, nDCG@K).
- **CLI mandiri** untuk indexing/pencarian/evaluasi tanpa perlu OpenMRS.

---

## Varian Retrieval (Ablation Study)

Semua varian memakai parameter BM25 yang sama; perbedaannya hanya pada lapisan tokenisasi/normalisasi.

| ID | Label | Strategi |
|---|---|---|
| **V0** | Naive BM25 | lowercase + hapus tanda baca (baseline, setara Mikkelsen 2026) |
| **V1** | Normalisasi klinis | V0 + negasi dipertahankan + deteksi istilah majemuk + ekspansi singkatan |
| **V2** | Klinis + ekspansi | V1 + ekspansi sinonim query-side |
| **V3** | Char n-gram | indexing karakter n-gram (3–5) untuk recall tipografi |
| **V4** | Klinis + ekspansi + PRF | V2 + *pseudo-relevance feedback* |

---

## Arsitektur Sistem

```mermaid
flowchart LR
    subgraph Data["Data Pipeline (Python)"]
        A[mtsamples.csv] --> B[build_corpus.py<br/>dedup + normalisasi]
        B --> C[corpus.jsonl<br/>2.316 dokumen]
        C --> D[build_lexicon.py<br/>lexicon klinis]
        C --> E[build_qrels.py<br/>query set + qrels]
    end

    subgraph Engine["IR Engine (Java / Lucene 8 shaded)"]
        F[ClinicalNormalizer] --> G[IrIndexer<br/>index multi-field]
        G --> H[IrSearcher<br/>BM25 k1=1.2 b=0.75]
    end

    subgraph OpenMRS["OpenMRS (modul ir-bm25)"]
        I[IrBm25Service] --> J[REST / JSON endpoint]
        J --> K[UI dropdown V0-V4]
    end

    C --> G
    D --> F
    H --> I
    E --> L[EvalRunner<br/>P@K Recall@K nDCG@K]
    H --> L
```

**Index multi-field** (satu index, banyak *field* per *analyzer*):
`content_naive` · `content_clinical` · `content_ngram` — sehingga perbandingan antar varian adil
(satu dataset, parameter BM25 identik, hanya *field*/*query* yang berbeda).

---

## Struktur Repositori

```
stbi-openmrs-bm25/
├── dataset/                         # Data mentah + hasil pemrosesan
│   ├── README.md                    #   provenance + verifikasi integritas
│   └── mtsamples/
│       ├── mtsamples.csv            #   dataset mentah (Kaggle mirror)
│       ├── corpus.jsonl             #   dataset final (2.316 dokumen)
│       ├── corpus_stats.txt         #   laporan statistik
│       └── ingest_map.json          #   pemetaan doc → UUID OpenMRS
├── tools/                           # Pipeline data (Python)
│   ├── build_corpus.py              #   dedup + normalisasi → corpus.jsonl
│   ├── build_lexicon.py             #   → resource lexicon klinis
│   └── build_qrels.py               #   → qrels TREC (sadar-sinonim)
├── eval/
│   ├── queries.tsv                  #   50 query evaluasi
│   ├── qrels.txt                    #   relevance judgment (TREC format)
│   └── results.txt                  #   hasil evaluasi (auto-generate)
├── openmrs-module-ir-bm25/          # Modul OpenMRS (Maven multi-module)
│   ├── api/                         #   mesin IR + service (Lucene shaded)
│   │   └── src/main/java/org/openmrs/module/irbm25/
│   │       ├── SearchVariant.java   #     enum V0..V4
│   │       ├── ClinicalLexicon.java #     loader lexicon
│   │       ├── ClinicalNormalizer.java
│   │       ├── search/              #     IrIndexer, IrSearcher, IrAnalyzers
│   │       ├── eval/EvalRunner.java
│   │       └── tool/IrCli.java      #     CLI
│   └── omod/                        #   controller, JSP, extension sidebar
├── openmrs-distro-referenceapplication/  # OpenMRS 3.x via Docker (clone)
├── scripts/                         # Script bantu menjalankan proyek
├── laporan/                         # Laporan progress (PDF)
└── presentasi/                      # Slide presentasi (LaTeX Beamer)
```

---

## Prasyarat

| Kebutuhan | Versi | Untuk |
|---|---|---|
| Python | 3.x | pipeline data (`tools/`) |
| JDK | 8+ | build modul & CLI |
| Maven | 3.x | build modul OpenMRS |
| Docker + Compose | — | menjalankan OpenMRS (opsional) |

> **Catatan:** modul ditulis menargetkan OpenMRS platform **1.11.6**, namun diuji berjalan pada
> **OpenMRS 2.8.8** (Reference Application 3.x). Lucene 8.11.2 di-*shade* (relokasi package ke
> `org.openmrs.module.irbm25.shaded.lucene`) agar tidak bentrok dengan Lucene bawaan OpenMRS.

---

## Mulai Cepat (Makefile)

Cara termudah lewat `make` (lihat `make help`):

```bash
make build                    # build data + modul .omod
make search QUERY="chest pain" VARIANT=v2 LIMIT=5
make eval K=10                # evaluasi ablation → eval/results.txt

make openmrs                  # start OpenMRS (Docker)
make deploy                   # deploy modul + corpus, restart backend
make openmrs-status           # cek status container
```

### Alternatif: Script langsung

Semua script di bawah `scripts/` bisa juga dipanggil langsung dari direktori mana pun
(root repo di-resolve otomatis).

```bash
# 1) Build data (corpus, lexicon, qrels) + modul .omod
scripts/build.sh

# 2) Jalankan mesin IR lokal (tanpa OpenMRS)
scripts/cli.sh index  dataset/mtsamples/corpus.jsonl /tmp/idx
scripts/cli.sh search /tmp/idx v2 5 chest pain and shortness of breath
scripts/cli.sh eval   dataset/mtsamples/corpus.jsonl /tmp/idx eval/queries.tsv eval/qrels.txt 10

# 3) Evaluasi ablation lengkap (hasil → eval/results.txt)
scripts/eval.sh

# 4) Jalankan di OpenMRS (Docker)
scripts/openmrs.sh up      # start db + backend + frontend + gateway
scripts/deploy.sh          # deploy .omod + corpus + restart backend
```

### Script yang tersedia

| Script | Fungsi |
|---|---|
| `scripts/build.sh` | Build corpus, lexicon, qrels, dan modul `.omod` |
| `scripts/cli.sh` | CLI mesin IR (`index` / `search` / `eval`) |
| `scripts/eval.sh` | Evaluasi ablation + simpan hasil |
| `scripts/deploy.sh` | Deploy `.omod` + corpus ke OpenMRS, set global property, restart |
| `scripts/openmrs.sh` | `up` / `down` / `status` / `logs` / `restart` stack Docker |

Detail lebih lanjut ada di [`scripts/README.md`](scripts/README.md).

---

## Pipeline Data

### 1. Membangun dataset (`tools/build_corpus.py`)

Dari `mtsamples.csv` (4.999 baris) dihasilkan `corpus.jsonl` (2.316 dokumen):

| Langkah | Drop |
|---|---|
| transkripsi kosong/whitespace | 33 |
| baris duplikat (transkripsi identik) | 2.609 → 2.357 transkripsi unik |
| transkripsi < 50 token | 41 |
| **Total** | **2.683 → 2.316 dokumen final** |

Baris duplikat **digabung** (bukan dibuang): `medical_specialties` (multi-label), `keywords`
(gabungan), `source_rows`, dan `n_variants` dipertahankan. Temuan penting: **91,2%** dokumen
bersifat **multi-spesialisasi**, sehingga `medical_specialty` **tidak valid** dijadikan label
relevansi untuk evaluasi.

### 2. Lexicon klinis (`tools/build_lexicon.py`)

Menghasilkan resource yang dibaca modul (`openmrs-module-ir-bm25/api/src/main/resources/clinical/`):

| File | Isi | Jumlah |
|---|---|---|
| `negation_terms.txt` | penanda negasi yang dipertahankan | 13 |
| `multiword_terms.txt` | istilah majemuk | 28 |
| `abbreviations.tsv` | singkatan → ekspansi | 28 |
| `synonyms.tsv` | term → sinonim | 15 grup |

### 3. Query set & qrels (`tools/build_qrels.py`)

- `eval/queries.tsv` — 50 query klinis.
- `eval/qrels.txt` — *relevance judgment* **semi-otomatis** format TREC
  (`<queryId> 0 <docId> <relevance>`). Relevansi diturunkan dari kolom `keywords` + `sample_name`
  + `description`, **sadar-sinonim** (menggunakan lexicon yang sama) agar varian ekspansi dinilai
  setara dengan baseline. Catatan: qrels manual oleh klinisi tetap direkomendasikan untuk laporan akhir.

---

## Mesin IR (Java / Lucene)

### Komponen inti

| Kelas | Peran |
|---|---|
| `SearchVariant` | enum V0–V4 (field + strategi ekspansi) |
| `ClinicalLexicon` | memuat resource lexicon dari classpath |
| `ClinicalNormalizer` | tokenisasi naive/clinical + ekspansi sinonim |
| `IrIndexer` | membangun index multi-field dari `corpus.jsonl` |
| `IrSearcher` | ranked retrieval BM25 (+ PRF + snippet) |
| `IrAnalyzers` | analyzer Lucene per field (whitespace+lowercase, n-gram) |
| `EvalRunner` | menghitung P@K / Recall@K / nDCG@K / response time |

### Normalisasi klinis (urutan)

1. lowercase + strip tanda baca
2. ekspansi singkatan (case-sensitive, simetris index & query)
3. kolaps istilah majemuk (`shortness of breath` → `shortness_of_breath`)
4. stopword removal **kecuali** penanda negasi

Ekspansi sinonim dilakukan **query-side** (menghindari index bloat & false match).

### CLI

```bash
scripts/cli.sh index  <corpus.jsonl> <indexDir>
scripts/cli.sh search <indexDir> <v0..v4> [limit] <query...>
scripts/cli.sh eval   <corpus.jsonl> <indexDir> <queries.tsv> <qrels.txt> [k]
```

---

## Integrasi OpenMRS

### Endpoint

```
GET /openmrs/module/ir-bm25/search.json?q=<query>&variant=<v0..v4>&limit=<k>
```

Contoh respons (array JSON):

```json
[
  {
    "docId": "mts-01808",
    "score": 5.3792,
    "sampleName": "Nephrology Office Visit - 2",
    "description": "Nephrology office visit for followup of CKD.",
    "specialties": "Nephrology, Office Notes",
    "snippet": "...presents for a nephrology followup for his chronic kidney disease..."
  }
]
```

### UI

- **Legacy UI search page**: `http://localhost/openmrs/module/ir-bm25/ir-bm25.form`
  (login `admin` / `Admin123`).
- Dropdown varian V0–V4 + mode **"Bandingkan semua"** (side-by-side).
- Link sidebar ditambahkan lewat extension `org.openmrs.gutter.tools`
  (`GutterList`), dan link di halaman Administration lewat `org.openmrs.admin.list`.
- Index dibangun otomatis saat modul *startup* berdasarkan global property:
  - `irbm25.corpusPath` — path `corpus.jsonl` di dalam container
  - `irbm25.indexDir` — direktori indeks Lucene

### Deploy

```bash
scripts/openmrs.sh up
scripts/deploy.sh
```

`deploy.sh` menyalin `.omod` + `corpus.jsonl` ke container, menulis global property ke DB,
lalu restart backend agar modul ter-load dan index terbangun.

---

## Evaluasi

Metrik: **Precision@10, Recall@10, nDCG@10** dan **Mean Response Time** (50 query, qrels TREC).

Hasil (`eval/results.txt`):

| Varian | P@10 | Recall@10 | nDCG@10 |
|---|---|---|---|
| V0 · Naive BM25 | 0.5400 | **0.4312** | 0.6832 |
| **V1 · Normalisasi klinis** | **0.5420** | 0.4284 | **0.6865** |
| V2 · Klinis + ekspansi | 0.5120 | 0.4192 | 0.6509 |
| V3 · Char n-gram | 0.5280 | 0.4055 | 0.6446 |
| V4 · Klinis + ekspansi + PRF | 0.5020 | 0.4139 | 0.6186 |

**Interpretasi:**

- **V1 > V0** pada P@10 dan nDCG@10, tetapi selisihnya sangat kecil: +0.0020 P@10 setara hanya
  **satu dokumen** yang berpindah masuk top-10 (270 → 271 dari 500 posisi terambil), dan +0.0033
  nDCG setara +0.48% relatif. **Recall@10 justru turun** (0.4284 < 0.4312), jadi perbaikannya tidak
  konsisten di ketiga metrik. Belum ada uji signifikansi — `EvalRunner` belum mengeluarkan skor
  per-query. Klaim yang aman: normalisasi klinis **tidak menurunkan** kualitas peringkat sambil
  memangkas waktu respons 3.6× (0.68 ms vs 2.48 ms).
- **Recall@10 punya plafon 0.6762**, bukan 1.0 — rata-rata 22.3 dokumen relevan per query
  (median 13.5, maksimum 98) tidak muat di cutoff K=10. Angka 0.4312 pada V0 setara ~64% plafon.
- **V2/V3/V4** menurun presisi, tetapi sebagian besar dapat dijelaskan oleh keterbatasan protokol
  qrels, bukan kegagalan varian: relevansi dinilai dari kolom metadata (`keywords`, `sample_name`,
  `description`, rata-rata **44 token**) sedangkan yang diindeks adalah `transcription`
  (rata-rata **466 token**) — ketimpangan **10.6×**. Sebanyak 526 dokumen (22.7%) tak punya
  `keywords` sama sekali, dan hanya 841 dokumen (36.3%) pernah dinilai relevan untuk query mana pun.
  Bias ini menghukum tepat varian yang dirancang menembus permukaan teks. Tindak lanjut: qrels
  dinilai langsung pada isi `transcription` oleh penilai berlatar klinis.

Menjalankan ulang evaluasi:

```bash
scripts/eval.sh 10
```

---

## Dataset & Catatan Lisensi

- **Sumber**: MTSamples (*Medical Transcription Samples*) — ~5.000 transkripsi medis dari 40
  spesialisasi. Kaggle: [`tboyle10/medicaltranscriptions`](https://www.kaggle.com/datasets/tboyle10/medicaltranscriptions).
- **Asal data**: `mtsamples.com` (nama & tanggal telah dianonimkan).
- **Verifikasi**: file diperiksa *byte-for-byte* terhadap statistik resmi Kaggle sebelum dipakai
  (lihat `dataset/README.md`).

---

## Kontributor

Proyek tugas STBI — Program Studi Ilmu Komputer, Departemen Ilmu Komputer dan Elektronika,
FMIPA, Universitas Gadjah Mada (2026).

| Nama | NIM |
|---|---|
| Ade Dwi Prayitno | 25/563076/PPA/07097 |
| Aldi Indrawan | 25/557923/PPA/07038 |
| Ari Rudiatama | 25/569437/PPA/07170 |
| Dimas Ihdam Maulana | 25/562999/PPA/07090 |
| Wiladahtul Awaliah | 25/569610/PPA/07174 |

---

## Referensi

1. Robertson, S., & Zaragoza, H. (2009). *The Probabilistic Relevance Framework: BM25 and Beyond.*
   Foundations and Trends in Information Retrieval, 3(4).
2. Manning, C. D., Raghavan, P., & Schütze, H. (2008). *Introduction to Information Retrieval.*
   Cambridge University Press.
3. Mikkelsen, Y. (2026). *Clinical Context Variables Collectively Rival Model Choice in
   Embedding-Based Retrieval.* JMIR Medical Informatics, 14(1).
4. Zhang, Z. (2021). *An improved BM25 algorithm for clinical decision support in Precision
   Medicine based on co-word analysis and Cuckoo Search.* BMC Medical Informatics and Decision
   Making, 21:81.
5. Gayen, S., et al. (2023). *Effects of Porting Essie Tokenization and Normalization to Solr.*
   AMIA Annual Symposium Proceedings.
6. Griffon, N., et al. (2012). *Performance evaluation of UMLS synonyms expansion to query
   PubMed.* BMC Medical Informatics and Decision Making, 12:12.
7. Syzdykova, A., et al. (2017). *Open-Source Electronic Health Record Systems for Low-Resource
   Settings: Systematic Review.* JMIR Medical Informatics, 5(4).
8. [Apache Lucene Documentation](https://lucene.apache.org/) — [OpenMRS](https://github.com/openmrs)
   — [MTSamples](https://www.kaggle.com/datasets/tboyle10/medicaltranscriptions).
