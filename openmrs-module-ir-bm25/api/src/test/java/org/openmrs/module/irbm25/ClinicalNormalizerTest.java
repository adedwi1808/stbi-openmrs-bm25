package org.openmrs.module.irbm25;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class ClinicalNormalizerTest {
	
	private ClinicalNormalizer normalizer;
	
	@Before
	public void setUp() throws Exception {
		normalizer = new ClinicalNormalizer(new ClinicalLexicon());
	}
	
	@Test
	public void naiveLowercasesAndStripsPunctuation() {
		List<String> tokens = normalizer.normalizeNaive("The Patient, has FEVER.");
		assertEquals("the", tokens.get(0));
		assertEquals("patient", tokens.get(1));
		assertEquals("fever", tokens.get(3));
	}
	
	@Test
	public void clinicalPreservesNegationMarkers() {
		// "no" and "without" are negation markers and must survive stopword removal.
		List<String> tokens = normalizer.normalizeClinical("no fever without cough");
		assertTrue(tokens.contains("no"));
		assertTrue(tokens.contains("without"));
		assertTrue(tokens.contains("fever"));
		assertTrue(tokens.contains("cough"));
	}
	
	@Test
	public void clinicalRemovesOrdinaryStopwords() {
		List<String> tokens = normalizer.normalizeClinical("the patient and the doctor");
		assertFalse(tokens.contains("the"));
		assertFalse(tokens.contains("and"));
	}
	
	@Test
	public void clinicalCollapsesMultiwordPhrase() {
		List<String> tokens = normalizer.normalizeClinical("shortness of breath");
		assertTrue(tokens.contains("shortness_of_breath"));
		assertFalse(tokens.contains("shortness"));
	}
	
	@Test
	public void clinicalExpandsAbbreviation() {
		// "CKD" (upper case) expands to its full form, both directions.
		List<String> tokens = normalizer.normalizeClinical("patient has CKD");
		assertTrue(tokens.contains("ckd"));
		assertTrue(tokens.contains("chronic"));
		assertTrue(tokens.contains("kidney"));
		assertTrue(tokens.contains("disease"));
	}
	
	@Test
	public void expandSynonymsAppendsSurfaceForms() {
		List<String> tokens = normalizer.expandSynonyms(normalizer.normalizeClinical("hypertension"));
		assertTrue(tokens.contains("hypertension"));
		assertTrue(tokens.contains("high blood pressure"));
		assertTrue(tokens.contains("htn"));
	}
}
