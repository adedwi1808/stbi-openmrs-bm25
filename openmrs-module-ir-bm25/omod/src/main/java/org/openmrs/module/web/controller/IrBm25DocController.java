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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openmrs.module.api.IrBm25Service;
import org.openmrs.module.irbm25.IrDocument;
import org.openmrs.module.irbm25.SearchVariant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The document detail page: renders one corpus document in full, reached by clicking a row in the
 * ranked result list. When the originating query is carried along, the terms the variant actually
 * searched with are highlighted in the transcription, which turns the page into a way to inspect
 * <em>why</em> a document was retrieved rather than just <em>that</em> it was.
 */
@Controller("irbm25.IrBm25DocController")
@RequestMapping(value = "module/ir-bm25/ir-bm25-doc.form")
public class IrBm25DocController {
	
	/**
	 * Terms shorter than this are not highlighted: they are almost always normalization residue and
	 * marking them adds noise rather than signal.
	 */
	private static final int MIN_HIGHLIGHT_LENGTH = 3;
	
	private final String VIEW = "/module/ir-bm25/ir-bm25-doc";
	
	@Autowired
	IrBm25Service irBm25Service;
	
	@RequestMapping(method = RequestMethod.GET)
	public String onGet(@RequestParam(value = "id", required = false) String docId,
	        @RequestParam(value = "q", required = false) String query,
	        @RequestParam(value = "variant", defaultValue = "v2") String variant,
	        @RequestParam(value = "limit", required = false) Integer limit,
	        @RequestParam(value = "back", required = false) String back, ModelMap model) {
		
		IrDocument document = irBm25Service.isIndexReady() ? irBm25Service.getDocument(docId) : null;
		
		model.addAttribute("docId", docId);
		model.addAttribute("document", document);
		model.addAttribute("query", query);
		model.addAttribute("variant", variant);
		model.addAttribute("indexReady", irBm25Service.isIndexReady());
		model.addAttribute("backUrl", backUrl(query, back != null ? back : variant, limit));
		
		if (document != null) {
			List<String> terms = highlightTerms(query, variant);
			model.addAttribute("transcriptionHtml", highlight(document.getTranscription(), terms));
			model.addAttribute("matchedTerms", matchedTerms(document.getTranscription(), terms));
			SearchVariant sv = SearchVariant.fromId(variant);
			model.addAttribute("variantLabel", sv == null ? variant : sv.getLabel());
		}
		return VIEW;
	}
	
	/**
	 * The terms to mark in the transcription. Two adjustments are needed relative to the raw query
	 * terms: character n-grams (v3) are 3-5 character fragments that would match nearly everywhere,
	 * so the plain query words are used instead; and collapsed multi-word tokens carry an
	 * underscore ({@code chest_pain}) that never appears in the transcription, so they are split
	 * back into words.
	 */
	private List<String> highlightTerms(String query, String variant) {
		if (query == null || query.trim().isEmpty()) {
			return Collections.emptyList();
		}
		List<String> source;
		if (SearchVariant.NGRAM == SearchVariant.fromId(variant)) {
			source = Arrays.asList(query.trim().toLowerCase().split("\\s+"));
		} else {
			source = irBm25Service.getQueryTerms(query, variant);
		}
		Set<String> out = new LinkedHashSet<>();
		for (String term : source) {
			for (String word : term.split("_")) {
				String cleaned = stripNonAlnum(word);
				if (cleaned.length() >= MIN_HIGHLIGHT_LENGTH) {
					out.add(cleaned);
				}
			}
		}
		return new ArrayList<>(out);
	}
	
	/**
	 * HTML-escapes the transcription and wraps every whole-word occurrence of a query term in
	 * {@code <mark>}. Matching runs over the raw text so that escaping cannot introduce spurious
	 * matches (a term such as "amp" would otherwise hit inside {@code &amp;}).
	 */
	private String highlight(String text, List<String> terms) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		if (terms.isEmpty()) {
			return escapeHtml(text);
		}
		Matcher matcher = wholeWordPattern(terms).matcher(text);
		StringBuilder out = new StringBuilder(text.length());
		int last = 0;
		while (matcher.find()) {
			out.append(escapeHtml(text.substring(last, matcher.start())));
			out.append("<mark>").append(escapeHtml(matcher.group())).append("</mark>");
			last = matcher.end();
		}
		out.append(escapeHtml(text.substring(last)));
		return out.toString();
	}
	
	/** The subset of query terms that literally occur in this transcription. */
	private List<String> matchedTerms(String text, List<String> terms) {
		List<String> out = new ArrayList<>();
		if (text == null || terms.isEmpty()) {
			return out;
		}
		for (String term : terms) {
			if (wholeWordPattern(Collections.singletonList(term)).matcher(text).find()) {
				out.add(term);
			}
		}
		return out;
	}
	
	private Pattern wholeWordPattern(List<String> terms) {
		StringBuilder alternation = new StringBuilder();
		for (String term : terms) {
			if (alternation.length() > 0) {
				alternation.append('|');
			}
			alternation.append(Pattern.quote(term));
		}
		return Pattern.compile("\\b(?:" + alternation + ")\\b", Pattern.CASE_INSENSITIVE);
	}
	
	private String backUrl(String query, String variant, Integer limit) {
		StringBuilder url = new StringBuilder("ir-bm25.form");
		if (query != null && !query.trim().isEmpty()) {
			url.append("?q=").append(urlEncode(query));
			url.append("&variant=").append(urlEncode(variant));
			if (limit != null) {
				url.append("&limit=").append(limit);
			}
		}
		return url.toString();
	}
	
	private String urlEncode(String value) {
		try {
			return URLEncoder.encode(value == null ? "" : value, "UTF-8");
		}
		catch (UnsupportedEncodingException e) {
			return "";
		}
	}
	
	private String stripNonAlnum(String token) {
		StringBuilder sb = new StringBuilder(token.length());
		for (int i = 0; i < token.length(); i++) {
			char c = token.charAt(i);
			if (Character.isLetterOrDigit(c)) {
				sb.append(c);
			}
		}
		return sb.toString();
	}
	
	private String escapeHtml(String text) {
		StringBuilder sb = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			switch (c) {
				case '&':
					sb.append("&amp;");
					break;
				case '<':
					sb.append("&lt;");
					break;
				case '>':
					sb.append("&gt;");
					break;
				case '"':
					sb.append("&quot;");
					break;
				case '\'':
					sb.append("&#39;");
					break;
				default:
					sb.append(c);
			}
		}
		return sb.toString();
	}
}
