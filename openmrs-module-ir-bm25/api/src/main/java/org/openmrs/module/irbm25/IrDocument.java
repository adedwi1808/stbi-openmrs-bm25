package org.openmrs.module.irbm25;

/**
 * A full corpus document as read back from the index. Unlike {@link IrSearchResult}, which carries
 * only a short snippet for the ranked list, this carries the complete transcription and is used by
 * the document detail view.
 */
public class IrDocument {
	
	private final String docId;
	
	private final String sampleName;
	
	private final String description;
	
	private final String specialties;
	
	private final String transcription;
	
	public IrDocument(String docId, String sampleName, String description, String specialties, String transcription) {
		this.docId = docId;
		this.sampleName = sampleName;
		this.description = description;
		this.specialties = specialties;
		this.transcription = transcription;
	}
	
	public String getDocId() {
		return docId;
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
	
	public String getTranscription() {
		return transcription;
	}
}
