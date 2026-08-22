#!/usr/bin/env bash
#
# Build everything: corpus, lexicon, qrels, and the OpenMRS module (.omod).
#
# Usage:
#   scripts/build.sh [--skip-module]
#
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

SKIP_MODULE="${SKIP_MODULE:-0}"
for arg in "$@"; do
	case "$arg" in
		--skip-module) SKIP_MODULE=1 ;;
		*) echo "unknown option: $arg" >&2; exit 1 ;;
	esac
done

cd "$REPO_ROOT"

echo "==> [1/4] build corpus (dataset/mtsamples/corpus.jsonl + corpus_stats.txt)"
python3 tools/build_corpus.py

echo "==> [2/4] build clinical lexicon (module resources clinical/*)"
python3 tools/build_lexicon.py

echo "==> [3/4] build qrels (eval/qrels.txt)"
python3 tools/build_qrels.py

if [ "$SKIP_MODULE" -eq 1 ]; then
	echo "==> skipping module build (--skip-module)"
else
	echo "==> [4/4] build OpenMRS module ($OMOD)"
	mvn -q -f "$MODULE_DIR/pom.xml" package -DskipTests
	echo "    -> $OMOD"
fi

echo
echo "Done."
