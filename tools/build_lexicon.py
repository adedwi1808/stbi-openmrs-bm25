#!/usr/bin/env python3
"""Generate the clinical lexicon resources consumed by the IR BM25 module.

This script turns curated, domain-informed dictionaries into the plain-text /
TSV resource files that the Java (Lucene) analyzers load at runtime. It also
measures how much of the MTSamples corpus each dictionary covers, so the
"clinical tokenization matters" claim is backed by evidence from the corpus
itself rather than assumption.

Output files (see --out-dir, default: the module's api resources dir):

  clinical/negation_terms.txt   one negation marker per line (lowercase)
  clinical/multiword_terms.txt  one multi-word phrase per line (lowercase)
  clinical/abbreviations.tsv    ABBREV<TAB>EXPANSION   (index+query expansion)
  clinical/synonyms.tsv         TERM<TAB>SYN1|SYN2|... (query-side expansion)

The dictionaries are deliberately conservative and curated; they are a
starting point, not an exhaustive UMLS/SNOMED mapping.
"""

import argparse
import json
import os
import re
import sys

# ---------------------------------------------------------------------------
# Curated dictionaries
# ---------------------------------------------------------------------------

# Negation markers: single-word terms that MUST NOT be dropped by a stopword
# list, because removing them would flip the clinical meaning ("no fever" vs
# "fever"). These are terms we keep even though a generic English stopword list
# would usually discard them.
NEGATION_TERMS = [
    "no",
    "not",
    "denies",
    "denied",
    "deny",
    "without",
    "absence",
    "absent",
    "negative",
    "none",
    "never",
    "unremarkable",
    "noncontributory",
]

# Multi-word clinical terms that a single-word tokenizer would shred into
# tokens that no longer carry the concept as a whole. Also includes multi-word
# negation phrases (handled as one concept so negation stays attached).
MULTIWORD_TERMS = [
    "shortness of breath",
    "chest pain",
    "blood pressure",
    "heart rate",
    "respiratory rate",
    "range of motion",
    "family history",
    "past medical history",
    "history of present illness",
    "physical examination",
    "review of systems",
    "discharge summary",
    "allergic rhinitis",
    "urinary tract infection",
    "upper respiratory infection",
    "lower extremity",
    "upper extremity",
    "lymph node",
    "lymph nodes",
    "computed tomography",
    "magnetic resonance imaging",
    "emergency room",
    "emergency department",
    "no known drug allergies",
    "no evidence of",
    "ruled out",
    "free of",
    "denies any",
]

# Abbreviations and their full expansion. Applied symmetrically (index + query)
# so that a query for "c/o" matches a note writing "complains of" and vice
# versa. The surface (left) form is matched as a token, case-sensitively.
ABBREVIATIONS = {
    "c/o": "complains of",
    "DM": "diabetes mellitus",
    "HTN": "hypertension",
    "SOB": "shortness of breath",
    "CHF": "congestive heart failure",
    "MI": "myocardial infarction",
    "CAD": "coronary artery disease",
    "COPD": "chronic obstructive pulmonary disease",
    "UTI": "urinary tract infection",
    "URI": "upper respiratory infection",
    "CVA": "cerebrovascular accident",
    "TIA": "transient ischemic attack",
    "GERD": "gastroesophageal reflux disease",
    "BP": "blood pressure",
    "HR": "heart rate",
    "RR": "respiratory rate",
    "PE": "pulmonary embolism",
    "DVT": "deep vein thrombosis",
    "CKD": "chronic kidney disease",
    "ESRD": "end-stage renal disease",
    "BPH": "benign prostatic hyperplasia",
    "PSA": "prostate-specific antigen",
    "MRI": "magnetic resonance imaging",
    "CT": "computed tomography",
    "ER": "emergency room",
    "ED": "emergency department",
    "H&P": "history and physical",
    "NKDA": "no known drug allergies",
}

# Synonyms: query-side expansion only (left form -> set of equivalent surface
# forms). Used to bridge terminology variants that are not pure abbreviations.
SYNONYMS = {
    "hypertension": ["high blood pressure", "htn"],
    "myocardial infarction": ["heart attack", "mi"],
    "shortness of breath": ["dyspnea", "sob"],
    "edema": ["swelling"],
    "fever": ["pyrexia", "febrile"],
    "cough": ["tussis"],
    "chest pain": ["angina"],
    "stroke": ["cva", "cerebrovascular accident"],
    "kidney failure": ["renal failure", "ckd", "esrd"],
    "diabetes": ["dm", "diabetes mellitus"],
    "urinary tract infection": ["uti", "bladder infection"],
    "headache": ["cephalgia"],
    "nausea": ["nauseated"],
    "fatigue": ["tiredness", "lethargy"],
    "dizziness": ["lightheadedness"],
}


# ---------------------------------------------------------------------------
# Corpus coverage measurement
# ---------------------------------------------------------------------------

TOKEN_RE = re.compile(r"[A-Za-z][A-Za-z0-9/.\-]*")


def tokens(text):
    return TOKEN_RE.findall(text or "")


def measure_coverage(corpus_path):
    n = 0
    n_neg = 0
    n_multi = 0
    n_abbr = 0
    neg_lower = {t.lower() for t in NEGATION_TERMS}
    multi_lower = {t.lower() for t in MULTIWORD_TERMS}

    with open(corpus_path, encoding="utf-8") as fh:
        for line in fh:
            doc = json.loads(line)
            trans = doc["transcription"]
            n += 1
            low = trans.lower()
            toks = set(tokens(trans))
            toks_lower = {t.lower() for t in toks}

            if neg_lower & toks_lower:
                n_neg += 1
            if any(m in low for m in multi_lower):
                n_multi += 1
            if any(a in toks for a in ABBREVIATIONS):
                n_abbr += 1

    return {
        "n": n,
        "negation": n_neg,
        "multiword": n_multi,
        "abbreviation": n_abbr,
    }


# ---------------------------------------------------------------------------
# Emission
# ---------------------------------------------------------------------------

def emit(out_dir):
    clin = os.path.join(out_dir, "clinical")
    os.makedirs(clin, exist_ok=True)

    with open(os.path.join(clin, "negation_terms.txt"), "w", encoding="utf-8") as fh:
        for term in NEGATION_TERMS:
            fh.write(term + "\n")

    with open(os.path.join(clin, "multiword_terms.txt"), "w", encoding="utf-8") as fh:
        for term in MULTIWORD_TERMS:
            fh.write(term + "\n")

    with open(os.path.join(clin, "abbreviations.tsv"), "w", encoding="utf-8") as fh:
        for abbr in sorted(ABBREVIATIONS):
            fh.write(f"{abbr}\t{ABBREVIATIONS[abbr]}\n")

    with open(os.path.join(clin, "synonyms.tsv"), "w", encoding="utf-8") as fh:
        for term in sorted(SYNONYMS):
            fh.write(f"{term}\t{'|'.join(SYNONYMS[term])}\n")

    return clin


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--corpus",
        default="dataset/mtsamples/corpus.jsonl",
        help="path to corpus.jsonl (for coverage measurement)",
    )
    parser.add_argument(
        "--out-dir",
        default="openmrs-module-ir-bm25/api/src/main/resources",
        help="directory to write the clinical/ resources into",
    )
    args = parser.parse_args(argv)

    cov = measure_coverage(args.corpus)
    clin = emit(args.out_dir)

    n = cov["n"]
    sys.stdout.write("clinical lexicon build report\n")
    sys.stdout.write(f"corpus documents scanned: {n}\n")
    sys.stdout.write(f"resources written to:      {clin}\n\n")
    sys.stdout.write(
        f"negation markers      : {cov['negation']} docs ({cov['negation'] / n * 100:.1f}%)\n"
    )
    sys.stdout.write(
        f"multi-word phrases    : {cov['multiword']} docs ({cov['multiword'] / n * 100:.1f}%)\n"
    )
    sys.stdout.write(
        f"medical abbreviations : {cov['abbreviation']} docs ({cov['abbreviation'] / n * 100:.1f}%)\n"
    )
    sys.stdout.write(
        f"\nlexicon sizes: {len(NEGATION_TERMS)} negation terms, "
        f"{len(MULTIWORD_TERMS)} multi-word terms, "
        f"{len(ABBREVIATIONS)} abbreviations, {len(SYNONYMS)} synonym groups\n"
    )


if __name__ == "__main__":
    main()
