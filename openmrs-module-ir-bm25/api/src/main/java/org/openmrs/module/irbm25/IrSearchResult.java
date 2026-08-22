package org.openmrs.module.irbm25;

/**
 * A single ranked search result returned by the IR engine.
 */
public class IrSearchResult {
	
	private final String docId;
	
	private final float score;
	
	private final String sampleName;
	
	private final String description;
	
	private final String specialties;
	
	private final String snippet;
	
	public IrSearchResult(String docId, float score, String sampleName, String description, String specialties,
	    String snippet) {
		this.docId = docId;
		this.score = score;
		this.sampleName = sampleName;
		this.description = description;
		this.specialties = specialties;
		this.snippet = snippet;
	}
	
	public String getDocId() {
		return docId;
	}
	
	public float getScore() {
		return score;
	}
	
	public String getSampleName() {
		return sampleName;
	}
	
	public String getDescription() {
		return description;
	}
	
	public String getSpecialties() {
		return specialties;
	}
	
	public String getSnippet() {
		return snippet;
	}
}
