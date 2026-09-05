# Scripts — STBI OpenMRS BM25

Helper scripts to build and run the IR BM25 project. All scripts are run from
anywhere; they resolve the repository root automatically.

> **Tip**: jalankan dari root repo lewat `make` (lihat `make help`) — lebih
> ringkas, e.g. `make build`, `make search QUERY="chest pain"`, `make openmrs`.

## Prerequisites

- Python 3 (data pipeline)
- JDK 8+ and Maven (module build)
- Docker + Docker Compose (OpenMRS deployment)

## Quick start

```bash
# 1. Build data (corpus, lexicon, qrels) + OpenMRS module (.omod)
scripts/build.sh

# 2. Run the IR engine locally (no Docker needed)
scripts/cli.sh index dataset/mtsamples/corpus.jsonl /tmp/idx
scripts/cli.sh search /tmp/idx v2 5 chest pain and shortness of breath
scripts/cli.sh eval dataset/mtsamples/corpus.jsonl /tmp/idx eval/queries.tsv eval/qrels.txt 10

# 3. Full evaluation (saves to eval/results.txt)
scripts/eval.sh

# 4. Run in OpenMRS (Docker)
scripts/openmrs.sh up          # start OpenMRS 3.x
scripts/deploy.sh              # deploy module + build index + restart backend
```

## Scripts

| Script | Purpose |
|---|---|
| `scripts/build.sh` | Build corpus, clinical lexicon, qrels, and the `.omod` module. |
| `scripts/cli.sh` | Run the IR engine CLI (`index` / `search` / `eval`) locally. |
| `scripts/eval.sh` | Run the full ablation evaluation (P@K, Recall@K, nDCG@K) and save results. |
| `scripts/deploy.sh` | Deploy the `.omod` + corpus into the running OpenMRS backend and rebuild the index. |
| `scripts/openmrs.sh` | Start/stop/status/logs for the OpenMRS Docker stack. |
| `scripts/common.sh` | Shared paths and helpers (sourced by the others, not run directly). |

## Accessing the running OpenMRS

- **Legacy UI search page** (has the sidebar link): `http://localhost/openmrs/module/ir-bm25/ir-bm25.form`
- **JSON endpoint**: `http://localhost/openmrs/module/ir-bm25/search.json?q=hypertension&variant=v2`
- **Login**: `admin` / `Admin123`

The `ir-bm25` sidebar link is added to the **Legacy UI** navigation (not the SPA),
via the `org.openmrs.gutter.tools` extension.

## Env overrides

| Variable | Default | Used by |
|---|---|---|
| `BACKEND_CONTAINER` | auto-detected (`*backend*`) | deploy.sh, openmrs.sh |
| `DB_CONTAINER` | auto-detected (`*db*`) | deploy.sh |
| `DB_USER` / `DB_PASS` / `DB_NAME` | `openmrs` / `openmrs` / `openmrs` | deploy.sh |
| `OPENMRS_TAG` | `qa` | openmrs.sh (tag image reference application) |
