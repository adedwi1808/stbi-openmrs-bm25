#!/usr/bin/env python3
"""Generate semi-automatic relevance judgments (qrels) for the IR evaluation.

Relevance is derived from the MTSamples `keywords` column plus `sample_name`
and `description`. A document is judged relevant to a query when it covers at
least N of the query's content concepts (N = number of concepts, relaxed to
N-1 only if nothing matches). A concept is "covered" when the document's
relevance text contains the query term OR any of its synonym / abbreviation
equivalents (loaded from the same clinical lexicon the module uses), so the
expansion variants are judged on the same footing as the baseline.

Output (TREC qrels format): <queryId> 0 <docId> <relevance>
"""

import argparse
import json
import re

STOPWORDS = {
    "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "has", "he",
    "in", "is", "it", "its", "of", "on", "or", "that", "the", "to", "was", "with",
    "she", "her", "his", "they", "this", "have", "had", "not", "no", "but", "if",
    "into", "than", "then", "there", "these", "those", "their", "will", "would",
}

TOKEN_RE = re.compile(r"[a-z0-9][a-z0-9/.\-]*")


def content_terms(text):
    terms = []
    for tok in TOKEN_RE.findall(text.lower()):
        if tok not in STOPWORDS and len(tok) > 1:
            terms.append(tok)
    return terms


def load_tsv(path):
    rows = []
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.rstrip("\n")
            if "\t" not in line:
                continue
            k, v = line.split("\t", 1)
            k = k.strip().lower()
            v = v.strip().lower()
            if k and v:
                rows.append((k, v))
    return rows


def build_equivalence(abbrev_path, syn_path):
    equiv = {}

    def link(a, b):
        equiv.setdefault(a, set()).add(b)
        equiv.setdefault(b, set()).add(a)

    for abbr, expansion in load_tsv(abbrev_path):
        link(abbr, expansion)
    for term, syns in load_tsv(syn_path):
        for s in syns.split("|"):
            s = s.strip()
            if s:
                link(term, s)
    return equiv


def load_queries(path):
    queries = []
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            qid, text = line.split("\t", 1)
            queries.append((qid.strip(), text.strip()))
    return queries


def build_qrels(corpus_path, queries_path, out_path, abbrev_path, syn_path):
    queries = load_queries(queries_path)
    equiv = build_equivalence(abbrev_path, syn_path)

    # Precompute each doc's relevance text (one string for phrase matching).
    doc_text = {}
    with open(corpus_path, encoding="utf-8") as fh:
        for line in fh:
            doc = json.loads(line)
            doc_id = doc["doc_id"]
            fields = [doc.get("keywords") or "", doc.get("sample_name") or "",
                      doc.get("description") or ""]
            doc_text[doc_id] = " ".join(fields).lower()

    lines = []
    total_relevant = 0
    fallback_used = 0
    for qid, text in queries:
        qterms = content_terms(text)
        if not qterms:
            continue
        groups = [equiv.get(t, {t}) | {t} for t in qterms]
        required = len(groups)
        relevant = [d for d in doc_text if covers(doc_text[d], groups, required)]
        if not relevant and required > 1:
            required -= 1
            fallback_used += 1
            relevant = [d for d in doc_text if covers(doc_text[d], groups, required)]
        for doc_id in relevant:
            lines.append(f"{qid} 0 {doc_id} 1\n")
        total_relevant += len(relevant)

    with open(out_path, "w", encoding="utf-8") as fh:
        fh.writelines(lines)

    return len(queries), total_relevant, len(doc_text), fallback_used


def covers(doc_text, groups, required):
    covered = 0
    for group in groups:
        for phrase in group:
            if re.search(r"\b" + re.escape(phrase) + r"\b", doc_text):
                covered += 1
                break
    return covered >= required


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--corpus", default="dataset/mtsamples/corpus.jsonl")
    parser.add_argument("--queries", default="eval/queries.tsv")
    parser.add_argument("--output", default="eval/qrels.txt")
    parser.add_argument("--abbreviations",
                        default="openmrs-module-ir-bm25/api/src/main/resources/clinical/abbreviations.tsv")
    parser.add_argument("--synonyms",
                        default="openmrs-module-ir-bm25/api/src/main/resources/clinical/synonyms.tsv")
    args = parser.parse_args(argv)

    n_q, n_rel, n_docs, fallback = build_qrels(
        args.corpus, args.queries, args.output, args.abbreviations, args.synonyms)
    print(f"queries: {n_q}")
    print(f"documents: {n_docs}")
    print(f"relevant judgments: {n_rel} (avg {n_rel / n_q:.1f} per query)")
    print(f"queries using relaxed (all-but-one-concept) matching: {fallback}")
    print(f"wrote: {args.output}")


if __name__ == "__main__":
    main()
