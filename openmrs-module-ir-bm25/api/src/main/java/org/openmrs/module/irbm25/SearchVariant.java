package org.openmrs.module.irbm25;

/**
 * The retrieval strategy variants exposed by the search API and UI dropdown.
 * <p>
 * Each variant maps to a Lucene index field and, where applicable, a query-time expansion strategy.
 * All variants share the same BM25 scoring parameters (k1 = 1.2, b = 0.75), so the differences are
 * attributable purely to the tokenization / normalization layer under study.
 */
public enum SearchVariant {
	
	/** Baseline: lowercase + punctuation removal only (cf. Mikkelsen 2026). */
	NAIVE("v0", "Naive BM25", "content_naive", false, false),
	
	/**
	 * Clinical normalization: negation-preserving stopwords + multi-word collapse + abbreviation
	 * expansion.
	 */
	CLINICAL("v1", "Normalisasi klinis", "content_clinical", false, false),
	
	/** Clinical normalization plus query-side synonym expansion. */
	CLINICAL_EXPANDED("v2", "Klinis + ekspansi", "content_clinical", true, false),
	
	/** Character n-gram (3-5) indexing for fuzzy/typographic recall. */
	NGRAM("v3", "Char n-gram", "content_ngram", false, false),
	
	/** Clinical normalization + synonym expansion + pseudo-relevance feedback. */
	CLINICAL_PRF("v4", "Klinis + ekspansi + PRF", "content_clinical", true, true);
	
	private final String id;
	
	private final String label;
	
	private final String field;
	
	private final boolean queryExpansion;
	
	private final boolean pseudoRelevanceFeedback;
	
	SearchVariant(String id, String label, String field, boolean queryExpansion, boolean pseudoRelevanceFeedback) {
		this.id = id;
		this.label = label;
		this.field = field;
		this.queryExpansion = queryExpansion;
		this.pseudoRelevanceFeedback = pseudoRelevanceFeedback;
	}
	
	public String getId() {
		return id;
	}
	
	public String getLabel() {
		return label;
	}
	
	public String getField() {
		return field;
	}
	
	public boolean isQueryExpansion() {
		return queryExpansion;
	}
	
	public boolean isPseudoRelevanceFeedback() {
		return pseudoRelevanceFeedback;
	}
	
	/** Resolve a variant by its short id (e.g. "v0", "v2"). */
	public static SearchVariant fromId(String id) {
		if (id == null) {
			return null;
		}
		for (SearchVariant v : values()) {
			if (v.id.equalsIgnoreCase(id.trim())) {
				return v;
			}
		}
		return null;
	}
}
