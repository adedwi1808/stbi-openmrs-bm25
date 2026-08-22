package org.openmrs.module.irbm25.search;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.openmrs.module.irbm25.ClinicalNormalizer;

/**
 * Builds the multi-field Lucene index from {@code corpus.jsonl}. One document per corpus entry; the
 * transcription is indexed three ways (naive, clinical, character n-gram) plus stored for display
 * and snippet generation.
 */
public class IrIndexer {
	
	private final ClinicalNormalizer normalizer;
	
	public IrIndexer(ClinicalNormalizer normalizer) {
		this.normalizer = normalizer;
	}
	
	/**
	 * @return the number of documents indexed.
	 */
	public int buildIndex(Path corpusJsonl, Path indexDir) throws IOException {
		Analyzer analyzer = IrAnalyzers.perFieldAnalyzer();
		IndexWriterConfig config = new IndexWriterConfig(analyzer);
		config.setSimilarity(new BM25Similarity(IrAnalyzers.K1, IrAnalyzers.B));
		config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
		
		int count = 0;
		try (IndexWriter writer = new IndexWriter(FSDirectory.open(indexDir), config);
		        BufferedReader reader = Files.newBufferedReader(corpusJsonl, StandardCharsets.UTF_8)) {
			ObjectMapper mapper = new ObjectMapper();
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				JsonNode node = mapper.readTree(line);
				writer.addDocument(toDocument(node));
				count++;
			}
		}
		return count;
	}
	
	private Document toDocument(JsonNode node) {
		String docId = node.path("doc_id").getTextValue();
		String transcription = node.path("transcription").getTextValue();
		String sampleName = node.path("sample_name").getTextValue();
		JsonNode descNode = node.path("description");
		String description = descNode.isNull() ? "" : descNode.getTextValue();
		String specialties = joinArray(node.path("medical_specialties"));
		
		String naiveText = String.join(" ", normalizer.normalizeNaive(transcription));
		String clinicalText = String.join(" ", normalizer.normalizeClinical(transcription));
		
		Document doc = new Document();
		doc.add(new StringField(IrAnalyzers.FIELD_ID, docId, Field.Store.YES));
		doc.add(new StoredField(IrAnalyzers.FIELD_TEXT, transcription));
		doc.add(new StoredField(IrAnalyzers.FIELD_SAMPLE_NAME, sampleName));
		doc.add(new StoredField(IrAnalyzers.FIELD_DESCRIPTION, description));
		doc.add(new StoredField(IrAnalyzers.FIELD_SPECIALTIES, specialties));
		doc.add(new TextField(IrAnalyzers.FIELD_NAIVE, naiveText, Field.Store.NO));
		doc.add(new TextField(IrAnalyzers.FIELD_CLINICAL, clinicalText, Field.Store.NO));
		doc.add(new TextField(IrAnalyzers.FIELD_NGRAM, transcription, Field.Store.NO));
		return doc;
	}
	
	private String joinArray(JsonNode array) {
		if (array == null || !array.isArray()) {
			return "";
		}
		List<String> values = new ArrayList<>();
		Iterator<JsonNode> it = array.getElements();
		while (it.hasNext()) {
			JsonNode v = it.next();
			if (!v.isNull()) {
				values.add(v.getTextValue());
			}
		}
		return String.join(", ", values);
	}
}
