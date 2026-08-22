#!/usr/bin/env bash
#
# Run the IR engine command-line tool (IrCli) without Docker.
# Relative paths are resolved against the repository root.
#
# Usage:
#   scripts/cli.sh index <corpus.jsonl> <indexDir>
#   scripts/cli.sh search <indexDir> <v0..v4> [limit] <query...>
#   scripts/cli.sh eval <corpus.jsonl> <indexDir> <queries.tsv> <qrels.txt> [k]
#
# Examples:
#   scripts/cli.sh index dataset/mtsamples/corpus.jsonl /tmp/idx
#   scripts/cli.sh search /tmp/idx v2 5 chest pain and shortness of breath
#   scripts/cli.sh eval dataset/mtsamples/corpus.jsonl /tmp/idx eval/queries.tsv eval/qrels.txt 10
#
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

cmd="${1:-}"
shift || true

build_args() {
	case "$cmd" in
		index)
			[ $# -ge 2 ] || { usage; exit 1; }
			echo "index $(abs_path "$1") $(abs_path "$2")"
			;;
		search)
			[ $# -ge 2 ] || { usage; exit 1; }
			local index_dir="$(abs_path "$1")"
			local variant="$2"
			local limit="${3:-10}"
			local query="${*:4}"
			[ -n "$query" ] || { echo "error: empty query" >&2; usage; exit 1; }
			echo "search $index_dir $variant $limit $query"
			;;
		eval)
			[ $# -ge 4 ] || { usage; exit 1; }
			local k="${5:-10}"
			echo "eval $(abs_path "$1") $(abs_path "$2") $(abs_path "$3") $(abs_path "$4") $k"
			;;
		*)
			usage
			exit 1
			;;
	esac
}

usage() {
	echo "usage:"
	echo "  $0 index  <corpus.jsonl> <indexDir>"
	echo "  $0 search <indexDir> <v0..v4> [limit] <query...>"
	echo "  $0 eval   <corpus.jsonl> <indexDir> <queries.tsv> <qrels.txt> [k]"
}

ARGS="$(build_args "$@")"
cd "$MODULE_DIR"
mvn -q -pl api compile exec:java -Dexec.args="$ARGS"
