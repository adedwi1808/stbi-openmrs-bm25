/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.api;

import java.util.List;

import org.openmrs.api.APIException;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.irbm25.IrDocument;
import org.openmrs.module.irbm25.IrSearchResult;
import org.openmrs.module.irbm25.SearchVariant;

/**
 * The main service of this module: builds the BM25 index from a clinical-text corpus and runs
 * ranked retrieval against it using one of the supported {@link SearchVariant} strategies.
 */
public interface IrBm25Service extends OpenmrsService {
	
	/**
	 * Builds the multi-field Lucene index from a corpus JSONL file.
	 * 
	 * @param corpusJsonlPath path to a {@code corpus.jsonl} file (one JSON object per line)
	 * @param indexDirPath directory to write the Lucene index into
	 * @return the number of documents indexed
	 */
	int buildIndex(String corpusJsonlPath, String indexDirPath) throws APIException;
	
	/**
	 * Runs ranked BM25 retrieval for a query using the given variant.
	 * 
	 * @param query the free-text query
	 * @param variantId one of "v0".."v4" (see {@link SearchVariant#fromId(String)})
	 * @param limit maximum number of results
	 * @return the ranked results
	 */
	List<IrSearchResult> search(String query, String variantId, int limit) throws APIException;
	
	/**
	 * Loads a single indexed document by its corpus id, including the full transcription. Backs the
	 * click-through detail view from the ranked result list.
	 * 
	 * @param docId the corpus document id (e.g. "mts-01873")
	 * @return the document, or null if no document carries that id
	 */
	IrDocument getDocument(String docId) throws APIException;
	
	/**
	 * The normalized query terms a variant would search with. Used by the detail view to highlight
	 * why a document was retrieved.
	 * 
	 * @param query the free-text query
	 * @param variantId one of "v0".."v4"
	 */
	List<String> getQueryTerms(String query, String variantId) throws APIException;
	
	/** All search variants available in the UI dropdown. */
	List<SearchVariant> getSearchVariants();
	
	/** The directory holding the built index (or null if not yet built). */
	String getIndexDirPath();
	
	void setIndexDirPath(String indexDirPath);
	
	/** True if the index directory exists and can be opened. */
	boolean isIndexReady();
}
