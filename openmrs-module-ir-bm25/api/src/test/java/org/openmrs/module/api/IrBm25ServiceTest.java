package org.openmrs.module.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.module.api.impl.IrBm25ServiceImpl;
import org.openmrs.module.irbm25.IrSearchResult;
import org.openmrs.module.irbm25.SearchVariant;

public class IrBm25ServiceTest {
	
	private Path corpus;
	
	private Path indexDir;
	
	private IrBm25ServiceImpl service;
	
	@Before
	public void setUp() throws Exception {
		corpus = Files.createTempFile("corpus", ".jsonl");
		indexDir = Files.createTempDirectory("irbm25-index");
		StringBuilder sb = new StringBuilder();
		sb.append("{\"doc_id\":\"mts-00001\",\"sample_name\":\"CKD Followup\",\"description\":\"\","
		        + "\"medical_specialties\":[\"Nephrology\"],\"transcription\":\"The patient has chronic kidney disease and hypertension.\"}\n");
		sb.append("{\"doc_id\":\"mts-00002\",\"sample_name\":\"Allergic Rhinitis\",\"description\":\"\","
		        + "\"medical_specialties\":[\"Allergy\"],\"transcription\":\"No fever or cough. Shortness of breath noted.\"}\n");
		Files.write(corpus, sb.toString().getBytes(StandardCharsets.UTF_8));
		service = new IrBm25ServiceImpl();
	}
	
	@After
	public void tearDown() throws Exception {
		Files.deleteIfExists(corpus);
	}
	
	@Test
	public void buildIndexThenSearch_shouldReturnRankedResults() {
		int n = service.buildIndex(corpus.toString(), indexDir.toString());
		assertEquals(2, n);
		assertTrue(service.isIndexReady());
		
		List<IrSearchResult> results = service.search("CKD", "v2", 10);
		assertFalse(results.isEmpty());
		assertEquals("mts-00001", results.get(0).getDocId());
	}
	
	@Test
	public void getSearchVariants_shouldExposeAllVariants() {
		assertEquals(SearchVariant.values().length, service.getSearchVariants().size());
	}
	
	@Test
	public void isIndexReady_shouldBeFalseBeforeBuild() {
		assertFalse(service.isIndexReady());
	}
}
