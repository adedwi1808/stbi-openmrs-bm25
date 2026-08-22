#!/usr/bin/env bash
#
# Run the full ablation evaluation locally (no Docker) and save the results.
#
# Usage:
#   scripts/eval.sh [k] [indexDir]
#
#   k         : rank cutoff (default 10)
#   indexDir  : where to build the Lucene index (default: /tmp/irbm25-eval-index)
#
# Results are printed to stdout and saved to eval/results.txt.
#
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

K="${1:-10}"
INDEX_DIR="$(abs_path "${2:-/tmp/irbm25-eval-index}")"

cd "$MODULE_DIR"
mvn -q -pl api compile exec:java \
	-Dexec.args="eval $CORPUS $INDEX_DIR $QUERIES $QRELS $K" \
	| tee "$REPO_ROOT/eval/results.txt"
