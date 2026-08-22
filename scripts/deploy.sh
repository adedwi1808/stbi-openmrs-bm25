#!/usr/bin/env bash
#
# Deploy the module to the running OpenMRS backend and (re)build the index.
#
# What it does:
#   1. Builds the .omod if it does not exist yet.
#   2. Copies the .omod and corpus.jsonl into the backend container.
#   3. Sets the irbm25.corpusPath / irbm25.indexDir global properties.
#   4. Restarts the backend so the module loads and the index builds on startup.
#
# Usage:
#   scripts/deploy.sh [--no-build] [--no-restart]
#
# Env overrides:
#   BACKEND_CONTAINER, DB_CONTAINER, DB_USER, DB_PASS, DB_NAME
#
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

NO_BUILD=0
NO_RESTART=0
for arg in "$@"; do
	case "$arg" in
		--no-build) NO_BUILD=1 ;;
		--no-restart) NO_RESTART=1 ;;
		*) echo "unknown option: $arg" >&2; exit 1 ;;
	esac
done

if [ -z "$BACKEND_CONTAINER" ]; then
	echo "error: no running backend container found (is 'docker compose up' running?)" >&2
	exit 1
fi
if [ -z "$DB_CONTAINER" ]; then
	echo "error: no running db container found" >&2
	exit 1
fi

echo "==> backend container: $BACKEND_CONTAINER"
echo "==> db container:      $DB_CONTAINER"

if [ ! -f "$OMOD" ]; then
	if [ "$NO_BUILD" -eq 1 ]; then
		echo "error: $OMOD not found and --no-build given" >&2
		exit 1
	fi
	echo "==> .omod not found, building..."
	mvn -q -f "$MODULE_DIR/pom.xml" package -DskipTests
fi

echo "==> copying module + corpus into backend"
docker exec "$BACKEND_CONTAINER" mkdir -p /openmrs/data/irbm25
docker cp "$OMOD" "$BACKEND_CONTAINER:/openmrs/data/modules/ir-bm25-$MODULE_VERSION.omod"
docker cp "$CORPUS" "$BACKEND_CONTAINER:$CORPUS_REMOTE"

echo "==> setting global properties ($GP_CORPUS, $GP_INDEX)"
docker exec "$DB_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "
INSERT INTO global_property (property, property_value, description, uuid)
VALUES ('$GP_CORPUS', '$CORPUS_REMOTE', 'IR BM25 corpus path', UUID())
ON DUPLICATE KEY UPDATE property_value = VALUES(property_value);
INSERT INTO global_property (property, property_value, description, uuid)
VALUES ('$GP_INDEX', '$INDEX_REMOTE', 'IR BM25 index dir', UUID())
ON DUPLICATE KEY UPDATE property_value = VALUES(property_value);
"

if [ "$NO_RESTART" -eq 0 ]; then
	echo "==> restarting backend (module load + index build on startup)"
	docker restart "$BACKEND_CONTAINER"
	echo "==> waiting for backend to become healthy..."
	for i in $(seq 1 40); do
		status="$(docker inspect --format '{{.State.Health.Status}}' "$BACKEND_CONTAINER" 2>/dev/null || echo 'none')"
		if [ "$status" = "healthy" ]; then
			break
		fi
		sleep 5
	done
	echo "==> backend status: $status"
fi

echo
echo "Done. Open:"
echo "  Legacy UI search page : http://localhost/openmrs/module/ir-bm25/ir-bm25.form"
echo "  JSON endpoint         : http://localhost/openmrs/module/ir-bm25/search.json?q=hypertension&variant=v2"
