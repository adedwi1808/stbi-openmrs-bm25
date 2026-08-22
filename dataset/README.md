# Dataset — MTSamples

This directory holds the raw and derived data for the IR BM25 project. Nothing
under `dataset/` is committed to git (see `.gitignore` at the repo root) — the
raw CSV is 17 MB and third-party, and the derived corpus is fully
reproducible from it. This README documents provenance, integrity
verification, and how to rebuild everything.

## What MTSamples is

MTSamples (mtsamples.com) is a public collection of ~5,000 de-identified,
transcribed medical reports ("sample transcriptions") spanning ~40 medical
specialties (Surgery, Radiology, Cardiovascular/Pulmonary, Orthopedic,
Consult notes, etc.), originally published as a reference resource for
medical transcriptionists. mtsamples.com states names and dates in the
samples have been altered, which is what makes the corpus safe to
redistribute and use in a public academic project.

The dataset used here is the commonly-circulated tabular version of
MTSamples, sourced on Kaggle as `tboyle10/medicaltranscriptions` (scraped
from mtsamples.com). Kaggle requires an API token to download via its CLI;
to avoid that dependency this project fetched the same data from an
unauthenticated GitHub mirror of the same Kaggle dataset.

## Download command used

```bash
mkdir -p dataset/mtsamples && curl -L -o dataset/mtsamples/mtsamples.csv \
  https://raw.githubusercontent.com/eshza/medicalTranscriptsKaggle/master/mtsamples.csv
```

This produced `dataset/mtsamples/mtsamples.csv` (17 MB).

## Integrity verification

Because this came through an unauthenticated third-party mirror rather than
directly from Kaggle, it was checked against the officially reported
statistics for `tboyle10/medicaltranscriptions` before being used for
anything downstream.

| Check | Official/expected | Measured on this file | Match |
|---|---|---|---|
| Data rows (excl. header) | 4,999 | 4,999 | yes |
| Columns | 6 (`Unnamed: 0`, `description`, `medical_specialty`, `sample_name`, `transcription`, `keywords`) | Same 6; first header is the empty string `''` (pandas index artifact) | yes |
| `transcription` empty (`== ''`) | 33 | 33 | yes |
| `keywords` empty (`== ''`) | 1,068 | 1,068 | yes, under strict `== ''` |
| `medical_specialty` distinct values | 40 | 40 (values carry a **leading space**, e.g. `' Surgery'` — must be trimmed) | yes |
| `Surgery` count | 1,103 | 1,103 | yes |
| `Consult - History and Phy.` count | 516 | 516 | yes |
| `Cardiovascular / Pulmonary` count | 372 | 372 | yes |
| `Orthopedic` count | 355 | 355 | yes |
| `Radiology` count | 273 | 273 | yes |
| `description` empty | 0 strict / 6 whitespace-only | 0 strict / 6 whitespace-only | yes |
| `transcription` length | avg 3,052 chars, max 18,425, min 11 | avg 3,052 chars, max 18,425, min 11 | yes |

**Note on `keywords`:** the official "1,068 null" figure only holds under
strict `== ''` semantics. This file additionally has **81** rows where
`keywords` is whitespace-only (e.g. a single space), which are not caught by
a strict-empty check. Counting those too, **1,149** rows (23.0%) have
effectively empty keywords. `tools/build_corpus.py` normalizes whitespace
before deciding null-ness, so it correctly treats both as empty.

All checks passed — the mirror is confirmed byte-for-byte consistent with
the official dataset statistics for the columns/rows checked. No fallback to
the Kaggle CLI was needed.

`csv.field_size_limit(10**9)` is required when reading this file — a few
`transcription` fields are large enough to exceed Python's default limit.

## Rebuilding the corpus

```bash
python3 tools/build_corpus.py
```

This reads `dataset/mtsamples/mtsamples.csv` and writes:
- `dataset/mtsamples/corpus.jsonl` — one cleaned JSON document per line
- `dataset/mtsamples/corpus_stats.txt` — the same summary report printed to stdout

See the script's docstring / `--help` for the full cleaning pipeline (drops
empty transcriptions, normalizes whitespace, trims `medical_specialty`,
merges exact-duplicate transcriptions, drops merged documents under 50
tokens, and assigns a stable `doc_id` independent of the raw CSV's
pandas-index column).

Flags: `--input`, `--output`, `--min-tokens` (default 50).

**Notable finding from the build:** MTSamples contains a large amount of
exact-duplicate `transcription` text — of the 4,966 rows with a non-empty
transcription, only 2,357 have a transcription that is not an exact
duplicate of another row's (2,609 duplicate rows collapse into those 2,357).
Of those 2,357, 41 have transcriptions under 50 tokens and are dropped. The
corpus produced by the default settings has **2,316** documents. This is a
real property of the raw data (verified directly against the CSV), not a
bug in the cleaning script — see `corpus_stats.txt` for the full breakdown:

```
input rows (excl. header): 4999
dropped, by reason (applied in order):
  1. empty/whitespace-only transcription: 33
     -> non-empty transcriptions:          4966
  2. exact-duplicate transcription rows:  2609  (collapsed into 2357 distinct transcriptions)
  3. distinct transcription < 50 tokens:    41
final document count: 2316
```

### Duplicates are merged, not discarded — and are multi-label

Earlier versions of `build_corpus.py` kept only the first occurrence of each
duplicate transcription and silently discarded the rest, including their
metadata. Analysis of the 2,150 duplicate groups (transcriptions with more
than one raw row) showed this was throwing away real information:

- **99.9%** of duplicate groups have a **different `medical_specialty`**
  across their rows, while **99.9%** share the same `sample_name`. This is
  not noise — mtsamples.com genuinely republishes the same transcription
  under multiple specialty categories on its site. **`medical_specialty` is
  therefore multi-label source data, not a single ground-truth category.**
- **76.9%** of duplicate groups have **different `keywords`** across rows —
  each occurrence was tagged independently.

`build_corpus.py` now **merges** each duplicate group into one document
instead of dropping the extra rows:

- `medical_specialty` — the first occurrence's trimmed value, kept for
  backward compatibility.
- `medical_specialties` (new) — the sorted, deduplicated list of every
  specialty seen across the group. In the current corpus, **2,113 of 2,316
  documents (91.2%)** carry more than one specialty in this field, and the
  largest merged group spans **5** specialties.
- `keywords` — the union of every member's comma-separated terms, split,
  stripped, deduplicated case-insensitively (first-seen surface form kept),
  and re-joined with `", "`.
- `description` — the first non-empty value in the group (6 rows overall
  have a whitespace-only description).
- `sample_name` — the first occurrence's value.
- `n_variants` (new) — how many raw CSV rows collapsed into the document (1
  if the transcription was unique).
- `source_row` — the first occurrence's raw CSV row index (unchanged
  meaning); `source_rows` (new) — the full list of raw row indices in the
  group.

**Keyword coverage before vs. after merging:** in this dataset, the
document-level "has any keywords at all" figure is unchanged at **77.3%**
(1,790 / 2,316) both before and after merging — across all 2,357 distinct
transcriptions there is exactly one duplicate group where the first
occurrence's keywords are empty but a later duplicate's are not, and that
one group is itself dropped by the 50-token filter before it ever reaches
the final corpus. The real effect of merging is at the **term** level, not
the document-count level: of the 1,790 documents that already had non-null
keywords, **1,609 (89.9%)** gained additional keyword terms pulled in from
duplicate siblings that the old first-occurrence-only logic silently
discarded.

**Implication for evaluation:** because `medical_specialty` is multi-label
(see above), it must **not** be used as a pseudo-relevance label for
retrieval evaluation — treating it as a single ground-truth category would
misjudge otherwise-correct results as irrelevant. Use `medical_specialties`
(the full set) if specialty-based relevance is needed for anything, and
even then treat it as a weak/noisy signal rather than ground truth.

## Citation guidance

For the project report / thesis, cite:
- **Source dataset:** Kaggle, `tboyle10/medicaltranscriptions`
  (https://www.kaggle.com/datasets/tboyle10/medicaltranscriptions)
- **Original origin:** mtsamples.com (the transcription samples were
  originally published there; Kaggle is a re-publication/scrape)

The GitHub mirror used for download
(`eshza/medicalTranscriptsKaggle`) is **only a retrieval convenience** used
because Kaggle's own download path requires an API token — it is not an
independent source and should not be cited as one. It was verified
byte-for-byte consistent with the official Kaggle statistics above before
use.

**Licensing caveat (report honestly, do not overclaim):** the CC0 license
tag on Kaggle is the uploader's own claim over scraped content; mtsamples.com
itself provides no explicit license, only a usage disclaimer that the
samples are for reference/educational purposes; a HuggingFace mirror of the
same data instead tags it Apache-2.0. Reports should state this ambiguity
plainly rather than asserting a single clean license.

## Committed to the repo

`dataset/` (including `mtsamples.csv`, `corpus.jsonl`, and
`corpus_stats.txt`) is tracked in git at the user's request, despite the
licensing ambiguity noted above under Citation guidance — read that section
before redistributing this data further.
