<%@ include file="/WEB-INF/template/include.jsp"%>

<%@ include file="/WEB-INF/template/header.jsp"%>

<h2><spring:message code="ir-bm25.title"/></h2>

<c:if test="${!indexReady}">
	<div class="error">
		<spring:message code="ir-bm25.indexNotReady"/>
		<c:if test="${not empty indexDirPath}"> (indexDirPath: <c:out value="${indexDirPath}"/>)</c:if>
	</div>
</c:if>

<form method="get" action="ir-bm25.form" class="box">
	<label for="q"><spring:message code="ir-bm25.query"/></label>
	<input id="q" type="text" name="q" value="<c:out value="${query}"/>" size="48"
	       placeholder="<spring:message code="ir-bm25.placeholder"/>"/>

	<label for="variant"><spring:message code="ir-bm25.variant"/></label>
	<select id="variant" name="variant">
		<c:forEach var="v" items="${variants}">
			<option value="${v.id}" ${v.id == variant ? 'selected="selected"' : ''}>${v.label}</option>
		</c:forEach>
		<option value="all" ${'all' == variant ? 'selected="selected"' : ''}>
			<spring:message code="ir-bm25.compareAll"/>
		</option>
	</select>

	<label for="limit"><spring:message code="ir-bm25.limit"/></label>
	<input id="limit" type="number" name="limit" value="${empty limit ? 10 : limit}" min="1" max="50"/>

	<input type="submit" value="<spring:message code="ir-bm25.search"/>"/>
</form>

<c:if test="${not empty results}">
	<h3><spring:message code="ir-bm25.results"/> (<c:out value="${fn:length(results)}"/>)</h3>
	<table class="irbm25-results">
		<thead>
			<tr><th>#</th><th><spring:message code="ir-bm25.doc"/></th>
			    <th><spring:message code="ir-bm25.score"/></th>
			    <th><spring:message code="ir-bm25.specialties"/></th>
			    <th><spring:message code="ir-bm25.snippet"/></th></tr>
		</thead>
		<tbody>
			<c:forEach var="r" items="${results}" varStatus="st">
				<tr>
					<td>${st.index + 1}</td>
					<td><strong><c:out value="${r.sampleName}"/></strong> <small>(<c:out value="${r.docId}"/>)</small></td>
					<td>${r.score}</td>
					<td><c:out value="${r.specialties}"/></td>
					<td><c:out value="${r.snippet}"/></td>
				</tr>
			</c:forEach>
		</tbody>
	</table>
</c:if>

<c:if test="${not empty allResults}">
	<h3><spring:message code="ir-bm25.compareTitle"/></h3>
	<c:forEach var="entry" items="${allResults}">
		<h4><c:out value="${entry.key.label}"/></h4>
		<table class="irbm25-results">
			<thead>
				<tr><th>#</th><th><spring:message code="ir-bm25.doc"/></th>
				    <th><spring:message code="ir-bm25.score"/></th>
				    <th><spring:message code="ir-bm25.specialties"/></th></tr>
			</thead>
			<tbody>
				<c:forEach var="r" items="${entry.value}" varStatus="st">
					<tr>
						<td>${st.index + 1}</td>
						<td><strong><c:out value="${r.sampleName}"/></strong> <small>(<c:out value="${r.docId}"/>)</small></td>
						<td>${r.score}</td>
						<td><c:out value="${r.specialties}"/></td>
					</tr>
				</c:forEach>
			</tbody>
		</table>
	</c:forEach>
</c:if>

<%@ include file="/WEB-INF/template/footer.jsp"%>
