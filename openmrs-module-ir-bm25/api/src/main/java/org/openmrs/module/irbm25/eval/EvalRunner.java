package org.openmrs.module.irbm25.eval;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openmrs.module.irbm25.ClinicalLexicon;
import org.openmrs.module.irbm25.ClinicalNormalizer;
import org.openmrs.module.irbm25.IrSearchResult;
import org.openmrs.module.irbm25.SearchVariant;
import org.openmrs.module.irbm25.search.IrIndexer;
import org.openmrs.module.irbm25.search.IrSearcher;

/**
 * Runs the ablation evaluation: builds the index once, then for every query in the query set and
 * every retrieval variant computes Precision@K, Recall@K, nDCG@K and mean response time against the
 * supplied qrels.
 */
public class EvalRunner {
	
	private final int k;
	
	public EvalRunner(int k) {
		this.k = k;
	}
	
	public void run(Path corpus, Path indexDir, Path queriesPath, Path qrelsPath) throws IOException {
		ClinicalLexicon lexicon = new ClinicalLexicon();
		ClinicalNormalizer normalizer = new ClinicalNormalizer(lexicon);
		new IrIndexer(normalizer).buildIndex(corpus, indexDir);
		
		List<String[]> queries = loadQueries(queriesPath);
		Map<String, Set<String>> qrels = loadQrels(qrelsPath);
		
		System.out.println("queries=" + queries.size() + " k=" + k);
		System.out.println();
		System.out.printf("%-8s %-30s %10s %10s %10s %12s%n", "variant", "label", "P@" + k, "Recall@" + k, "nDCG@" + k,
		    "resp(ms)");
		System.out.println("------------------------------------------------------------------------");
		
		for (SearchVariant variant : SearchVariant.values()) {
			Metrics agg = new Metrics();
			int evaluated = 0;
			try (IrSearcher searcher = new IrSearcher(indexDir, normalizer)) {
				for (String[] q : queries) {
					String qid = q[0];
					String text = q[1];
					Set<String> rel = qrels.get(qid);
					if (rel == null || rel.isEmpty()) {
						continue;
					}
					long t0 = System.nanoTime();
					List<IrSearchResult> results = searcher.search(text, variant, k);
					long t1 = System.nanoTime();
					agg.add(metrics(results, rel));
					agg.addResponseMs((t1 - t0) / 1_000_000.0);
					evaluated++;
				}
			}
			System.out.printf("%-8s %-30s %10.4f %10.4f %10.4f %12.2f%n", variant.getId(), variant.getLabel(),
			    agg.precision(), agg.recall(), agg.ndcg(), agg.responseMs());
			System.out.println("  (evaluated " + evaluated + " queries)");
		}
	}
	
	private Metrics metrics(List<IrSearchResult> results, Set<String> relevant) {
		Metrics m = new Metrics();
		int hits = 0;
		double dcg = 0.0;
		for (int i = 0; i < results.size(); i++) {
			boolean rel = relevant.contains(results.get(i).getDocId());
			if (rel) {
				hits++;
				dcg += 1.0 / (Math.log(i + 2) / Math.log(2));
			}
		}
		m.precision = hits / (double) k;
		m.recall = hits / (double) relevant.size();
		int ideal = Math.min(k, relevant.size());
		double idcg = 0.0;
		for (int i = 0; i < ideal; i++) {
			idcg += 1.0 / (Math.log(i + 2) / Math.log(2));
		}
		m.ndcg = idcg > 0 ? dcg / idcg : 0.0;
		return m;
	}
	
	private List<String[]> loadQueries(Path path) throws IOException {
		List<String[]> out = new ArrayList<>();
		for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
			line = line.trim();
			if (line.isEmpty()) {
				continue;
			}
			int tab = line.indexOf('\t');
			if (tab < 0) {
				continue;
			}
			out.add(new String[] { line.substring(0, tab).trim(), line.substring(tab + 1).trim() });
		}
		return out;
	}
	
	private Map<String, Set<String>> loadQrels(Path path) throws IOException {
		Map<String, Set<String>> out = new LinkedHashMap<>();
		for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
			String[] parts = line.trim().split("\\s+");
			if (parts.length < 4) {
				continue;
			}
			out.computeIfAbsent(parts[0], x -> new HashSet<>()).add(parts[2]);
		}
		return out;
	}
	
	private static final class Metrics {
		
		double precision;
		
		double recall;
		
		double ndcg;
		
		double responseMs;
		
		int n;
		
		void add(Metrics o) {
			precision += o.precision;
			recall += o.recall;
			ndcg += o.ndcg;
			n++;
		}
		
		void addResponseMs(double ms) {
			responseMs += ms;
		}
		
		double precision() {
			return n > 0 ? precision / n : 0;
		}
		
		double recall() {
			return n > 0 ? recall / n : 0;
		}
		
		double ndcg() {
			return n > 0 ? ndcg / n : 0;
		}
		
		double responseMs() {
			return n > 0 ? responseMs / n : 0;
		}
	}
}
