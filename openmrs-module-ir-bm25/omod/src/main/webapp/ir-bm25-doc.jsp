<%@ include file="/WEB-INF/template/include.jsp"%>

<%@ include file="/WEB-INF/template/header.jsp"%>

<style type="text/css">
	.irbm25-meta th { text-align: left; vertical-align: top; padding: 4px 12px 4px 0; white-space: nowrap; }
	.irbm25-meta td { vertical-align: top; padding: 4px 0; }
	.irbm25-transcription {
		white-space: pre-wrap;
		word-wrap: break-word;
		line-height: 1.5;
		background: #fff;
		border: 1px solid #ccc;
		padding: 12px;
		max-height: 32em;
		overflow-y: auto;
	}
	.irbm25-transcription mark { background: #ffe066; padding: 0 1px; }
	.irbm25-term { display: inline-block; background: #ffe066; padding: 0 5px; margin: 0 3px 3px 0; border-radius: 3px; }
</style>

<h2><spring:message code="ir-bm25.docTitle"/></h2>

<c:if test="${!indexReady}">
	<div class="error"><spring:message code="ir-bm25.indexNotReady"/></div>
</c:if>

<c:if test="${indexReady and empty document}">
	<div class="error"><spring:message code="ir-bm25.docNotFound"/>: <c:out value="${docId}"/></div>
</c:if>

<p><a href="<c:out value="${backUrl}"/>">&laquo; <spring:message code="ir-bm25.backToResults"/></a></p>

<c:if test="${not empty document}">
	<div class="box">
		<h3><c:out value="${document.sampleName}"/> <small>(<c:out value="${document.docId}"/>)</small></h3>
		<table class="irbm25-meta">
			<tr>
				<th><spring:message code="ir-bm25.description"/></th>
				<td><c:out value="${document.description}"/></td>
			</tr>
			<tr>
				<th><spring:message code="ir-bm25.specialties"/></th>
				<td><c:out value="${document.specialties}"/></td>
			</tr>
			<c:if test="${not empty query}">
				<tr>
					<th><spring:message code="ir-bm25.retrievedBy"/></th>
					<td>&quot;<c:out value="${query}"/>&quot; &mdash; <c:out value="${variantLabel}"/></td>
				</tr>
				<tr>
					<th><spring:message code="ir-bm25.matchedTerms"/></th>
					<td>
						<c:choose>
							<c:when test="${not empty matchedTerms}">
								<c:forEach var="t" items="${matchedTerms}">
									<span class="irbm25-term"><c:out value="${t}"/></span>
								</c:forEach>
							</c:when>
							<c:otherwise>
								<em><spring:message code="ir-bm25.noDirectMatch"/></em>
							</c:otherwise>
						</c:choose>
					</td>
				</tr>
			</c:if>
		</table>
	</div>

	<h3><spring:message code="ir-bm25.transcription"/></h3>
	<div class="irbm25-transcription">${transcriptionHtml}</div>
</c:if>

<%@ include file="/WEB-INF/template/footer.jsp"%>
