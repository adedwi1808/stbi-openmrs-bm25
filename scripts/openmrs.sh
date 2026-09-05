#!/usr/bin/env bash
#
# Manage the OpenMRS 3.x reference application (Docker Compose) stack.
#
# Usage:
#   scripts/openmrs.sh up       # start the stack (db + backend + frontend + gateway)
#   scripts/openmrs.sh down     # stop the stack
#   scripts/openmrs.sh status   # show container status
#   scripts/openmrs.sh logs     # tail backend logs
#   scripts/openmrs.sh restart  # restart the backend container only
#
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

DISTRO_DIR="$REPO_ROOT/openmrs-distro-referenceapplication"
DISTRO_REPO="https://github.com/openmrs/openmrs-distro-referenceapplication.git"
cmd="${1:-status}"

# The distro is an upstream clone (gitignored, own git history), so it is absent
# on a fresh checkout. Clone it on demand instead of failing inside docker compose.
if [ ! -f "$DISTRO_DIR/docker-compose.yml" ]; then
	echo "OpenMRS distro not found at $DISTRO_DIR" >&2
	echo "Cloning $DISTRO_REPO ..." >&2
	git clone --depth 1 "$DISTRO_REPO" "$DISTRO_DIR"
fi

case "$cmd" in
	up)
		TAG="$OPENMRS_TAG" docker compose -f "$DISTRO_DIR/docker-compose.yml" up -d
		echo "OpenMRS starting... Legacy UI: http://localhost/openmrs  SPA: http://localhost/openmrs/spa"
		;;
	down)
		TAG="$OPENMRS_TAG" docker compose -f "$DISTRO_DIR/docker-compose.yml" down
		;;
	status)
		TAG="$OPENMRS_TAG" docker compose -f "$DISTRO_DIR/docker-compose.yml" ps
		;;
	logs)
		docker logs --tail "${2:-100}" -f "$BACKEND_CONTAINER"
		;;
	restart)
		[ -n "$BACKEND_CONTAINER" ] || { echo "no backend container running" >&2; exit 1; }
		docker restart "$BACKEND_CONTAINER"
		;;
	*)
		echo "usage: $0 up|down|status|logs [lines]|restart" >&2
		exit 1
		;;
esac
