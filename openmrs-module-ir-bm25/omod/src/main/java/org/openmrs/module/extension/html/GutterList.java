/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.extension.html;

import org.openmrs.module.web.extension.LinkExt;

/**
 * Adds the IR BM25 search page to the left-hand navigation gutter (sidebar) of the legacy UI, via
 * the org.openmrs.gutter.tools extension point.
 */
public class GutterList extends LinkExt {
	
	@Override
	public String getLabel() {
		return "ir-bm25.title";
	}
	
	@Override
	public String getUrl() {
		return "module/ir-bm25/ir-bm25.form";
	}
	
	@Override
	public String getRequiredPrivilege() {
		return null;
	}
}
