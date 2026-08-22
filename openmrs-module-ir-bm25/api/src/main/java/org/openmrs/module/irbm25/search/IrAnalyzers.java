package org.openmrs.module.irbm25.search;

import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.ngram.NGramTokenFilter;

/**
 * The Lucene analyzers used by the index. A single {@link PerFieldAnalyzerWrapper} routes the
 * {@code content_ngram} field to a character n-gram analyzer and all other fields to a simple
 * whitespace + lowercase analyzer (the naive/clinical fields are already normalized in
 * {@code ClinicalNormalizer} before indexing).
 */
public final class IrAnalyzers {
	
	public static final String FIELD_ID = "id";
	
	public static final String FIELD_TEXT = "text";
	
	public static final String FIELD_SAMPLE_NAME = "sample_name";
	
	public static final String FIELD_DESCRIPTION = "description";
	
	public static final String FIELD_SPECIALTIES = "specialties";
	
	public static final String FIELD_NAIVE = "content_naive";
	
	public static final String FIELD_CLINICAL = "content_clinical";
	
	public static final String FIELD_NGRAM = "content_ngram";
	
	public static final float K1 = 1.2f;
	
	public static final float B = 0.75f;
	
	private IrAnalyzers() {
	}
	
	public static Analyzer simpleAnalyzer() {
		return new Analyzer() {
			
			@Override
			protected TokenStreamComponents createComponents(String fieldName) {
				Tokenizer source = new WhitespaceTokenizer();
				TokenStream result = new LowerCaseFilter(source);
				return new TokenStreamComponents(source, result);
			}
		};
	}
	
	public static Analyzer ngramAnalyzer(int minGram, int maxGram) {
		return new Analyzer() {
			
			@Override
			protected TokenStreamComponents createComponents(String fieldName) {
				Tokenizer source = new WhitespaceTokenizer();
				TokenStream result = new LowerCaseFilter(source);
				result = new NGramTokenFilter(result, minGram, maxGram, false);
				return new TokenStreamComponents(source, result);
			}
		};
	}
	
	/** Per-field analyzer: everything is simple except {@code content_ngram}. */
	public static Analyzer perFieldAnalyzer() {
		Map<String, Analyzer> byField = new HashMap<>();
		byField.put(FIELD_NGRAM, ngramAnalyzer(3, 5));
		return new PerFieldAnalyzerWrapper(simpleAnalyzer(), byField);
	}
	
	public static Analyzer ngramAnalyzer() {
		return ngramAnalyzer(3, 5);
	}
}
