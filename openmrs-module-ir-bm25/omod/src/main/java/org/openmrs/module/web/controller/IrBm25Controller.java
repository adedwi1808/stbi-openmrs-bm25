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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.api.IrBm25Service;
import org.openmrs.module.irbm25.IrSearchResult;
import org.openmrs.module.irbm25.SearchVariant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The search page controller: renders the query form and, when a query is supplied (GET), the
 * ranked results. Passing {@code variant=all} runs every variant side-by-side so the tokenization
 * strategies can be compared directly. Search is performed via GET so it is not subject to the CSRF
 * guard.
 */
@Controller("irbm25.IrBm25Controller")
@RequestMapping(value = "module/ir-bm25/ir-bm25.form")
public class IrBm25Controller {
	
	protected final Log log = LogFactory.getLog(getClass());
	
	private final String VIEW = "/module/ir-bm25/ir-bm25";
	
	@Autowired
	IrBm25Service irBm25Service;
	
	@RequestMapping(method = RequestMethod.GET)
	public String onGet(@RequestParam(value = "q", required = false) String query,
	        @RequestParam(value = "variant", defaultValue = "v2") String variant,
	        @RequestParam(value = "limit", defaultValue = "10") int limit, ModelMap model) {
		if (query != null && !query.trim().isEmpty()) {
			model.addAttribute("query", query);
			model.addAttribute("variant", variant);
			model.addAttribute("limit", limit);
			
			if ("all".equalsIgnoreCase(variant)) {
				Map<SearchVariant, List<IrSearchResult>> all = new LinkedHashMap<>();
				for (SearchVariant v : irBm25Service.getSearchVariants()) {
					all.put(v, irBm25Service.search(query, v.getId(), limit));
				}
				model.addAttribute("allResults", all);
			} else {
				model.addAttribute("results", irBm25Service.search(query, variant, limit));
			}
		}
		
		prepare(model);
		return VIEW;
	}
	
	private void prepare(ModelMap model) {
		model.addAttribute("variants", irBm25Service.getSearchVariants());
		model.addAttribute("indexReady", irBm25Service.isIndexReady());
		model.addAttribute("indexDirPath", irBm25Service.getIndexDirPath());
	}
}
