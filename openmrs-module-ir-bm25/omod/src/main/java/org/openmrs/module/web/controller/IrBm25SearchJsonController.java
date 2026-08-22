/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.web.controller;

import java.io.IOException;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.codehaus.jackson.map.ObjectMapper;
import org.openmrs.module.api.IrBm25Service;
import org.openmrs.module.irbm25.IrSearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * JSON search endpoint used by the client-side "compare all" view and any programmatic client.
 * Mirrors the planned REST contract: {@code GET /module/ir-bm25/search.json?q=&variant=&limit=}.
 */
@Controller("irbm25.IrBm25SearchJsonController")
@RequestMapping(value = "module/ir-bm25/search.json")
public class IrBm25SearchJsonController {
	
	@Autowired
	IrBm25Service irBm25Service;
	
	@RequestMapping(method = RequestMethod.GET)
	public void search(@RequestParam("q") String query,
	        @RequestParam(value = "variant", defaultValue = "v2") String variant,
	        @RequestParam(value = "limit", defaultValue = "10") int limit, HttpServletResponse response) throws IOException {
		List<IrSearchResult> results = irBm25Service.search(query, variant, limit);
		response.setContentType("application/json; charset=UTF-8");
		response.setCharacterEncoding("UTF-8");
		new ObjectMapper().writeValue(response.getOutputStream(), results);
	}
}
