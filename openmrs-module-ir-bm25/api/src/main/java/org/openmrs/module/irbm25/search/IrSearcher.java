package org.openmrs.module.irbm25.search;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;
import org.openmrs.module.irbm25.ClinicalNormalizer;
import org.openmrs.module.irbm25.IrDocument;
import org.openmrs.module.irbm25.IrSearchResult;
import org.openmrs.module.irbm25.SearchVariant;

/**
 * Executes ranked retrieval against the multi-field index using BM25 scoring. The selected
 * {@link SearchVariant} determines the index field and, where applicable, the query-side expansion
 * / pseudo-relevance feedback strategy.
 */
public class IrSearcher implements AutoCloseable {
	
	private static final int SNIPPET_RADIUS = 90;
	
	private static final int PRF_DOCS = 5;
	
	private static final int PRF_TERMS = 10;
	
	private static final float PRF_BOOST = 0.3f;
	
	private final ClinicalNormalizer normalizer;
	
	private final IndexReader reader;
	
	private final IndexSearcher searcher;
	
	public IrSearcher(Path indexDir, ClinicalNormalizer normalizer) throws IOException {
		this.normalizer = normalizer;
		this.reader = DirectoryReader.open(FSDirectory.open(indexDir));
		this.searcher = new IndexSearcher(reader);
		this.searcher.setSimilarity(new BM25Similarity(IrAnalyzers.K1, IrAnalyzers.B));
	}
	
	public List<IrSearchResult> search(String query, SearchVariant variant, int limit) throws IOException {
		if (variant == null) {
			variant = SearchVariant.CLINICAL_EXPANDED;
		}
		List<String> terms = queryTermsFor(normalizer, query, variant);
		Query booleanQuery = buildQuery(terms, variant.getField(), 1.0f);
		
		if (variant.isPseudoRelevanceFeedback()) {
			booleanQuery = applyPseudoRelevanceFeedback(booleanQuery, terms, variant.getField());
		}
		
		TopDocs topDocs = searcher.search(booleanQuery, limit);
		List<IrSearchResult> results = new ArrayList<>();
		for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
			results.add(toResult(scoreDoc, terms));
		}
		return results;
	}
	
	/**
	 * The normalized (and, where applicable, synonym-expanded) terms a variant actually searches
	 * with. Static because it needs only the normalizer, not the index: the detail view uses it to
	 * highlight matches without opening the index a second time.
	 */
	public static List<String> queryTermsFor(ClinicalNormalizer normalizer, String query, SearchVariant variant)
	        throws IOException {
		if (variant == null) {
			variant = SearchVariant.CLINICAL_EXPANDED;
		}
		if (variant == SearchVariant.NGRAM) {
			return collectTerms(IrAnalyzers.ngramAnalyzer(), IrAnalyzers.FIELD_NGRAM, query);
		}
		List<String> terms = variant == SearchVariant.NAIVE ? normalizer.normalizeNaive(query) : normalizer
		        .normalizeClinical(query);
		if (variant.isQueryExpansion()) {
			terms = normalizer.expandSynonyms(terms);
		}
		return dedupe(terms);
	}
	
	/**
	 * Looks up a single indexed document by its corpus id, including the full stored transcription.
	 * The id field is indexed as a {@code StringField}, so an exact term lookup is enough.
	 * 
	 * @return the document, or null when no document carries that id
	 */
	public IrDocument getDocument(String docId) throws IOException {
		if (docId == null || docId.trim().isEmpty()) {
			return null;
		}
		TopDocs hits = searcher.search(new TermQuery(new Term(IrAnalyzers.FIELD_ID, docId.trim())), 1);
		if (hits.scoreDocs.length == 0) {
			return null;
		}
		Document doc = searcher.doc(hits.scoreDocs[0].doc);
		return new IrDocument(doc.get(IrAnalyzers.FIELD_ID), doc.get(IrAnalyzers.FIELD_SAMPLE_NAME),
		        doc.get(IrAnalyzers.FIELD_DESCRIPTION), doc.get(IrAnalyzers.FIELD_SPECIALTIES),
		        doc.get(IrAnalyzers.FIELD_TEXT));
	}
	
	private Query buildQuery(List<String> terms, String field, float boost) {
		if (terms.isEmpty()) {
			return new BooleanQuery.Builder().build();
		}
		BooleanQuery.Builder builder = new BooleanQuery.Builder();
		for (String term : terms) {
			Query tq = new TermQuery(new Term(field, term));
			if (boost != 1.0f) {
				tq = new BoostQuery(tq, boost);
			}
			builder.add(tq, BooleanClause.Occur.SHOULD);
		}
		builder.setMinimumNumberShouldMatch(1);
		return builder.build();
	}
	
	private Query applyPseudoRelevanceFeedback(Query baseQuery, List<String> originalTerms, String field) throws IOException {
		Set<String> exclude = new HashSet<>(originalTerms);
		TopDocs initial = searcher.search(baseQuery, PRF_DOCS);
		Map<String, Integer> termFreq = new HashMap<>();
		for (ScoreDoc sd : initial.scoreDocs) {
			Document doc = searcher.doc(sd.doc);
			String text = doc.get(IrAnalyzers.FIELD_TEXT);
			for (String token : normalizer.normalizeClinical(text)) {
				if (!exclude.contains(token)) {
					termFreq.put(token, termFreq.getOrDefault(token, 0) + 1);
				}
			}
		}
		List<String> expansion = new ArrayList<>();
		termFreq.entrySet().stream()
		        .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
		        .limit(PRF_TERMS)
		        .forEach(e -> expansion.add(e.getKey()));
		
		if (expansion.isEmpty()) {
			return baseQuery;
		}
		BooleanQuery.Builder builder = new BooleanQuery.Builder();
		builder.add(baseQuery, BooleanClause.Occur.SHOULD);
		for (String term : expansion) {
			builder.add(new BoostQuery(new TermQuery(new Term(field, term)), PRF_BOOST),
			    BooleanClause.Occur.SHOULD);
		}
		builder.setMinimumNumberShouldMatch(1);
		return builder.build();
	}
	
	private IrSearchResult toResult(ScoreDoc scoreDoc, List<String> queryTerms) throws IOException {
		Document doc = searcher.doc(scoreDoc.doc);
		String docId = doc.get(IrAnalyzers.FIELD_ID);
		String text = doc.get(IrAnalyzers.FIELD_TEXT);
		String sampleName = doc.get(IrAnalyzers.FIELD_SAMPLE_NAME);
		String description = doc.get(IrAnalyzers.FIELD_DESCRIPTION);
		String specialties = doc.get(IrAnalyzers.FIELD_SPECIALTIES);
		String snippet = buildSnippet(text, queryTerms);
		return new IrSearchResult(docId, scoreDoc.score, sampleName, description, specialties, snippet);
	}
	
	private String buildSnippet(String text, List<String> queryTerms) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		String lower = text.toLowerCase();
		int best = -1;
		for (String term : queryTerms) {
			int idx = lower.indexOf(term);
			if (idx >= 0 && (best < 0 || idx < best)) {
				best = idx;
			}
		}
		if (best < 0) {
			best = 0;
		}
		int start = Math.max(0, best - SNIPPET_RADIUS);
		int end = Math.min(text.length(), best + SNIPPET_RADIUS);
		StringBuilder sb = new StringBuilder();
		if (start > 0) {
			sb.append("...");
		}
		sb.append(text, start, end);
		if (end < text.length()) {
			sb.append("...");
		}
		return sb.toString();
	}
	
	private static List<String> collectTerms(Analyzer analyzer, String field, String text) throws IOException {
		List<String> terms = new ArrayList<>();
		try (TokenStream stream = analyzer.tokenStream(field, new StringReader(text))) {
			CharTermAttribute attr = stream.addAttribute(CharTermAttribute.class);
			stream.reset();
			while (stream.incrementToken()) {
				terms.add(attr.toString());
			}
			stream.end();
		}
		return dedupe(terms);
	}
	
	private static List<String> dedupe(List<String> terms) {
		Set<String> seen = new LinkedHashSet<>(terms);
		return new ArrayList<>(seen);
	}
	
	public int numDocs() {
		return reader.numDocs();
	}
	
	@Override
	public void close() throws IOException {
		reader.close();
	}
}
