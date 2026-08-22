/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module;

import java.io.File;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.context.Context;
import org.openmrs.module.api.IrBm25Service;
import org.openmrs.util.OpenmrsUtil;

/**
 * Builds the BM25 index on module startup when the {@code irbm25.corpusPath} global property is
 * configured, so the search UI is usable out of the box.
 */
public class IrBm25Activator extends BaseModuleActivator {
	
	private Log log = LogFactory.getLog(this.getClass());
	
	@Override
	public void started() {
		log.info("Started IR BM25");
		try {
			String corpusPath = Context.getAdministrationService().getGlobalProperty("irbm25.corpusPath");
			if (StringUtils.isBlank(corpusPath)) {
				log.info("irbm25.corpusPath not set; skipping index build");
				return;
			}
			String indexDir = Context.getAdministrationService().getGlobalProperty("irbm25.indexDir");
			if (StringUtils.isBlank(indexDir)) {
				indexDir = OpenmrsUtil.getApplicationDataDirectory() + File.separator + "irbm25" + File.separator + "index";
			}
			IrBm25Service service = Context.getService(IrBm25Service.class);
			int n = service.buildIndex(corpusPath, indexDir);
			log.info("IR BM25 index built: " + n + " documents -> " + indexDir);
		}
		catch (Exception e) {
			log.warn("IR BM25 index not built at startup: " + e.getMessage(), e);
		}
	}
	
	@Override
	public void stopped() {
		log.info("Shutdown IR BM25");
	}
	
}
