/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.api.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.openmrs.api.APIException;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.api.IrBm25Service;
import org.openmrs.module.irbm25.ClinicalLexicon;
import org.openmrs.module.irbm25.ClinicalNormalizer;
import org.openmrs.module.irbm25.IrDocument;
import org.openmrs.module.irbm25.IrSearchResult;
import org.openmrs.module.irbm25.SearchVariant;
import org.openmrs.module.irbm25.search.IrIndexer;
import org.openmrs.module.irbm25.search.IrSearcher;

public class IrBm25ServiceImpl extends BaseOpenmrsService implements IrBm25Service {
	
	private volatile ClinicalNormalizer normalizer;
	
	private volatile String indexDirPath;
	
	private ClinicalNormalizer normalizer() {
		if (normalizer == null) {
			synchronized (this) {
				if (normalizer == null) {
					try {
						normalizer = new ClinicalNormalizer(new ClinicalLexicon());
					}
					catch (IOException e) {
						throw new APIException("ir-bm25: failed to load clinical lexicon", e);
					}
				}
			}
		}
		return normalizer;
	}
	
	@Override
	public int buildIndex(String corpusJsonlPath, String indexDirPath) throws APIException {
		try {
			int n = new IrIndexer(normalizer()).buildIndex(Paths.get(corpusJsonlPath), Paths.get(indexDirPath));
			this.indexDirPath = indexDirPath;
			return n;
		}
		catch (IOException e) {
			throw new APIException("ir-bm25: failed to build index from " + corpusJsonlPath, e);
		}
	}
	
	@Override
	public List<IrSearchResult> search(String query, String variantId, int limit) throws APIException {
		if (!isIndexReady()) {
			throw new APIException("ir-bm25: index not ready (indexDirPath=" + indexDirPath
			        + "). Call buildIndex first.");
		}
		SearchVariant variant = SearchVariant.fromId(variantId);
		if (variant == null) {
			variant = SearchVariant.CLINICAL_EXPANDED;
		}
		try (IrSearcher searcher = new IrSearcher(Paths.get(indexDirPath), normalizer())) {
			return searcher.search(query, variant, limit);
		}
		catch (IOException e) {
			throw new APIException("ir-bm25: search failed for query '" + query + "'", e);
		}
	}
	
	@Override
	public IrDocument getDocument(String docId) throws APIException {
		if (!isIndexReady()) {
			throw new APIException("ir-bm25: index not ready (indexDirPath=" + indexDirPath
			        + "). Call buildIndex first.");
		}
		try (IrSearcher searcher = new IrSearcher(Paths.get(indexDirPath), normalizer())) {
			return searcher.getDocument(docId);
		}
		catch (IOException e) {
			throw new APIException("ir-bm25: failed to load document '" + docId + "'", e);
		}
	}
	
	@Override
	public List<String> getQueryTerms(String query, String variantId) throws APIException {
		if (query == null || query.trim().isEmpty()) {
			return Collections.emptyList();
		}
		try {
			return IrSearcher.queryTermsFor(normalizer(), query, SearchVariant.fromId(variantId));
		}
		catch (IOException e) {
			throw new APIException("ir-bm25: failed to analyze query '" + query + "'", e);
		}
	}
	
	@Override
	public List<SearchVariant> getSearchVariants() {
		return Arrays.asList(SearchVariant.values());
	}
	
	@Override
	public String getIndexDirPath() {
		return indexDirPath;
	}
	
	@Override
	public void setIndexDirPath(String indexDirPath) {
		this.indexDirPath = indexDirPath;
	}
	
	@Override
	public boolean isIndexReady() {
		return indexDirPath != null && Files.isDirectory(Paths.get(indexDirPath));
	}
}
