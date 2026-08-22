package org.openmrs.module.irbm25;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.en.EnglishAnalyzer;

/**
 * Produces the normalized token lists used to build the index and to process queries. The
 * linguistic work (multi-word collapse, abbreviation expansion, negation-preserving stopword
 * removal) happens here in plain Java rather than inside a Lucene {@code TokenFilter}, which keeps
 * the behavior easy to test and identical between indexing and querying.
 * <p>
 * The output tokens are already lowercased and whitespace-free, so the simple whitespace +
 * lowercase Lucene analyzer used for the naive/clinical fields is effectively idempotent.
 */
public class ClinicalNormalizer {
	
	private final ClinicalLexicon lexicon;
	
	private final Set<String> stopwords;
	
	private final Map<String, List<String[]>> multiwordIndex;
	
	public ClinicalNormalizer(ClinicalLexicon lexicon) {
		this.lexicon = lexicon;
		this.multiwordIndex = lexicon.indexMultiwordTerms();
		this.stopwords = buildStopwords();
	}
	
	/**
	 * Naive baseline: lowercase + punctuation stripping only. No stopword removal, no multi-word
	 * collapse, no abbreviation expansion.
	 */
	public List<String> normalizeNaive(String text) {
		List<String> out = new ArrayList<>();
		for (String raw : split(text)) {
			String stripped = stripPunctuation(raw);
			if (!stripped.isEmpty()) {
				out.add(stripped.toLowerCase());
			}
		}
		return out;
	}
	
	/**
	 * Clinical normalization: naive tokenization plus abbreviation expansion (matched
	 * case-sensitively on the surface form), multi-word collapse, and negation-preserving stopword
	 * removal.
	 */
	public List<String> normalizeClinical(String text) {
		List<String> tokens = new ArrayList<>();
		for (String raw : split(text)) {
			String surface = stripPunctuation(raw);
			if (surface.isEmpty()) {
				continue;
			}
			tokens.add(surface.toLowerCase());
			String expansion = lexicon.getAbbreviations().get(surface);
			if (expansion != null) {
				for (String word : split(expansion)) {
					String s = stripPunctuation(word);
					if (!s.isEmpty()) {
						tokens.add(s.toLowerCase());
					}
				}
			}
		}
		
		List<String> collapsed = collapseMultiword(tokens);
		List<String> out = new ArrayList<>();
		for (String token : collapsed) {
			if (isStopword(token) && !lexicon.getNegationTerms().contains(token)) {
				continue;
			}
			out.add(token);
		}
		return out;
	}
	
	/**
	 * Query-side synonym expansion: for each clinical-normalized token that is a synonym-group
	 * head, append its equivalent surface forms. The original token is kept.
	 */
	public List<String> expandSynonyms(List<String> tokens) {
		Set<String> seen = new HashSet<>(tokens);
		List<String> out = new ArrayList<>(tokens);
		for (String token : tokens) {
			List<String> syns = lexicon.getSynonyms().get(token);
			if (syns != null) {
				for (String syn : syns) {
					if (seen.add(syn)) {
						out.add(syn);
					}
				}
			}
		}
		return out;
	}
	
	private List<String> collapseMultiword(List<String> tokens) {
		List<String> out = new ArrayList<>();
		int i = 0;
		while (i < tokens.size()) {
			String first = tokens.get(i);
			List<String[]> candidates = multiwordIndex.get(first);
			boolean matched = false;
			if (candidates != null) {
				for (String[] phrase : candidates) {
					if (i + phrase.length <= tokens.size() && matchesAt(tokens, i, phrase)) {
						out.add(join(phrase));
						i += phrase.length;
						matched = true;
						break;
					}
				}
			}
			if (!matched) {
				out.add(first);
				i++;
			}
		}
		return out;
	}
	
	private boolean matchesAt(List<String> tokens, int start, String[] phrase) {
		for (int j = 0; j < phrase.length; j++) {
			if (!tokens.get(start + j).equals(phrase[j])) {
				return false;
			}
		}
		return true;
	}
	
	private String join(String[] phrase) {
		StringBuilder sb = new StringBuilder();
		for (int j = 0; j < phrase.length; j++) {
			if (j > 0) {
				sb.append('_');
			}
			sb.append(phrase[j]);
		}
		return sb.toString();
	}
	
	private boolean isStopword(String token) {
		return stopwords.contains(token);
	}
	
	private Set<String> buildStopwords() {
		Set<String> stop = new HashSet<>();
		for (Object o : EnglishAnalyzer.ENGLISH_STOP_WORDS_SET) {
			stop.add(new String((char[]) o));
		}
		// Keep negation markers: removing them would flip clinical meaning.
		stop.removeAll(lexicon.getNegationTerms());
		return stop;
	}
	
	private String[] split(String text) {
		return text.split("\\s+");
	}
	
	/** Remove leading/trailing characters that are not letters or digits. */
	private String stripPunctuation(String token) {
		int start = 0;
		int end = token.length();
		while (start < end && !isAlnum(token.charAt(start))) {
			start++;
		}
		while (end > start && !isAlnum(token.charAt(end - 1))) {
			end--;
		}
		return token.substring(start, end);
	}
	
	private boolean isAlnum(char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
	}
}
