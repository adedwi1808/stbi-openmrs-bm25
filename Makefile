SHELL := /bin/bash

# ---------------------------------------------------------------------------
# STBI OpenMRS BM25 — Makefile
# Jalankan: `make help` untuk daftar target.
#
# Contoh:
#   make build                 # build data + modul
#   make search QUERY="chest pain" VARIANT=v2
#   make eval K=10
#   make openmrs               # start OpenMRS (Docker)
#   make deploy                # deploy modul ke OpenMRS
# ---------------------------------------------------------------------------

QUERY     ?= chest pain
VARIANT   ?= v2
LIMIT     ?= 5
INDEX_DIR ?= /tmp/irbm25-index
K         ?= 10

.PHONY: help build test index search eval deploy \
        openmrs openmrs-up openmrs-down openmrs-status openmrs-logs openmrs-restart

help: ## Tampilkan daftar target
	@echo "Target yang tersedia:"
	@echo ""
	@echo "  make build                  Build corpus + lexicon + qrels + modul .omod"
	@echo "  make test                   Jalankan unit test modul"
	@echo "  make index [INDEX_DIR=...]  Bangun indeks Lucene (default /tmp/irbm25-index)"
	@echo "  make search [QUERY=... VARIANT=... LIMIT=...]  Cari di indeks lokal"
	@echo "  make eval   [K=...]         Evaluasi ablation (hasil -> eval/results.txt)"
	@echo ""
	@echo "  make openmrs                Start stack OpenMRS (Docker)"
	@echo "  make openmrs-down           Hentikan stack"
	@echo "  make openmrs-status         Status container"
	@echo "  make openmrs-logs           Tail log backend"
	@echo "  make openmrs-restart        Restart backend"
	@echo "  make deploy                 Deploy modul + corpus ke OpenMRS, restart"
	@echo ""

build: ## Build semua (data + modul)
	scripts/build.sh

test: ## Unit test modul
	mvn -f openmrs-module-ir-bm25/pom.xml test

index: ## Bangun indeks Lucene lokal
	scripts/cli.sh index dataset/mtsamples/corpus.jsonl $(INDEX_DIR)

search: ## Cari di indeks lokal (QUERY / VARIANT / LIMIT)
	scripts/cli.sh search $(INDEX_DIR) $(VARIANT) $(LIMIT) "$(QUERY)"

eval: ## Evaluasi ablation lengkap
	scripts/eval.sh $(K)

deploy: ## Deploy modul ke OpenMRS
	scripts/deploy.sh

openmrs openmrs-up: ## Start stack OpenMRS (Docker)
	scripts/openmrs.sh up

openmrs-down: ## Hentikan stack OpenMRS
	scripts/openmrs.sh down

openmrs-status: ## Status container OpenMRS
	scripts/openmrs.sh status

openmrs-logs: ## Tail log backend
	scripts/openmrs.sh logs

openmrs-restart: ## Restart backend saja
	scripts/openmrs.sh restart
