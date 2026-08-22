#!/usr/bin/env bash
#
# Shared helpers + paths for the STBI OpenMRS BM25 project scripts.
# Source this file from the other scripts in this directory.
#
set -euo pipefail

# Absolute path to the repository root (one level up from this directory).
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# ---- Data / eval paths -----------------------------------------------------
CORPUS="$REPO_ROOT/dataset/mtsamples/corpus.jsonl"
QUERIES="$REPO_ROOT/eval/queries.tsv"
QRELS="$REPO_ROOT/eval/qrels.txt"

# ---- OpenMRS module --------------------------------------------------------
MODULE_DIR="$REPO_ROOT/openmrs-module-ir-bm25"
MODULE_VERSION="1.0.0-SNAPSHOT"
OMOD="$MODULE_DIR/omod/target/ir-bm25-$MODULE_VERSION.omod"

# ---- Docker (OpenMRS 3.x reference application) ----------------------------
BACKEND_CONTAINER="${BACKEND_CONTAINER:-$(docker ps --format '{{.Names}}' 2>/dev/null | grep -E 'backend' | head -n 1)}"
DB_CONTAINER="${DB_CONTAINER:-$(docker ps --format '{{.Names}}' 2>/dev/null | grep -E '(^|-)db(-|$)' | head -n 1)}"

# Paths inside the backend container.
CORPUS_REMOTE="/openmrs/data/irbm25/corpus.jsonl"
INDEX_REMOTE="/openmrs/data/irbm25/index"

# Global properties used by the module.
GP_CORPUS="irbm25.corpusPath"
GP_INDEX="irbm25.indexDir"

DB_USER="${DB_USER:-openmrs}"
DB_PASS="${DB_PASS:-openmrs}"
DB_NAME="${DB_NAME:-openmrs}"

# Resolve a possibly-relative path against REPO_ROOT (unless already absolute).
abs_path() {
	local p="$1"
	case "$p" in
		/*) printf '%s' "$p" ;;
		*) printf '%s' "$REPO_ROOT/$p" ;;
	esac
}
