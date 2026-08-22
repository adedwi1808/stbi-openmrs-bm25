package org.openmrs.module.irbm25.tool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.openmrs.module.irbm25.ClinicalLexicon;
import org.openmrs.module.irbm25.ClinicalNormalizer;
import org.openmrs.module.irbm25.IrSearchResult;
import org.openmrs.module.irbm25.SearchVariant;
import org.openmrs.module.irbm25.eval.EvalRunner;
import org.openmrs.module.irbm25.search.IrIndexer;
import org.openmrs.module.irbm25.search.IrSearcher;

/**
 * Command-line smoke test / inspection tool for the IR engine.
 * 
 * <pre>
 *   IrCli index   &lt;corpus.jsonl&gt; &lt;indexDir&gt;
 *   IrCli search  &lt;indexDir&gt; &lt;variant&gt; [limit] &lt;query...&gt;
 *   IrCli eval    &lt;corpus.jsonl&gt; &lt;indexDir&gt; &lt;queries.tsv&gt; &lt;qrels.txt&gt; [k]
 * </pre>
 */
public class IrCli {
	
	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			usage();
			return;
		}
		String cmd = args[0];
		if ("index".equals(cmd)) {
			index(args[1], args[2]);
		} else if ("search".equals(cmd)) {
			int limit = args.length > 3 ? Integer.parseInt(args[3]) : 10;
			search(args[1], args[2], limit, join(args, 4));
		} else if ("eval".equals(cmd)) {
			eval(args[1], args[2], args[3], args[4], args.length > 5 ? Integer.parseInt(args[5]) : 10);
		} else {
			usage();
		}
	}
	
	private static String join(String[] args, int from) {
		StringBuilder sb = new StringBuilder();
		for (int i = from; i < args.length; i++) {
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(args[i]);
		}
		return sb.toString();
	}
	
	private static void index(String corpus, String indexDir) throws Exception {
		ClinicalLexicon lexicon = new ClinicalLexicon();
		ClinicalNormalizer normalizer = new ClinicalNormalizer(lexicon);
		IrIndexer indexer = new IrIndexer(normalizer);
		int n = indexer.buildIndex(Paths.get(corpus), Paths.get(indexDir));
		System.out.println("indexed " + n + " documents -> " + indexDir);
	}
	
	private static void search(String indexDir, String variantId, int limit, String query) throws Exception {
		SearchVariant variant = SearchVariant.fromId(variantId);
		if (variant == null) {
			System.err.println("unknown variant: " + variantId + " (use v0..v4)");
			return;
		}
		if (query.isEmpty()) {
			System.err.println("empty query");
			return;
		}
		ClinicalLexicon lexicon = new ClinicalLexicon();
		ClinicalNormalizer normalizer = new ClinicalNormalizer(lexicon);
		try (IrSearcher searcher = new IrSearcher(Paths.get(indexDir), normalizer)) {
			List<IrSearchResult> results = searcher.search(query, variant, limit);
			System.out.println("query=" + query + " variant=" + variant.getId() + " (" + variant.getLabel()
			        + ") numDocs=" + searcher.numDocs() + " hits=" + results.size());
			for (IrSearchResult r : results) {
				System.out.printf("  %.4f  %s  %s%n", r.getScore(), r.getDocId(), r.getSampleName());
				System.out.println("       " + r.getSnippet());
			}
		}
	}
	
	private static void eval(String corpus, String indexDir, String queries, String qrels, int k) throws Exception {
		new EvalRunner(k).run(Paths.get(corpus), Paths.get(indexDir), Paths.get(queries), Paths.get(qrels));
	}
	
	private static void usage() {
		System.out.println("usage:\n" + "  IrCli index <corpus.jsonl> <indexDir>\n"
		        + "  IrCli search <indexDir> <v0..v4> [limit] <query...>\n"
		        + "  IrCli eval <corpus.jsonl> <indexDir> <queries.tsv> <qrels.txt> [k]");
	}
}
