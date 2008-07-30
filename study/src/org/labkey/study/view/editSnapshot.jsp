<%@ page import="org.apache.commons.lang.BooleanUtils" %>
<%@ page import="org.labkey.api.query.QueryAction" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.query.QueryUrls" %>
<%@ page import="org.labkey.api.query.snapshot.QuerySnapshotDefinition" %>
<%@ page import="org.labkey.api.query.snapshot.QuerySnapshotForm" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.common.util.Pair" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    JspView<QuerySnapshotForm> me = (JspView<QuerySnapshotForm>) HttpView.currentView();
    QuerySnapshotForm bean = me.getModelBean();
    ViewContext context = HttpView.currentContext();

    QuerySnapshotDefinition def = QueryService.get().getSnapshotDef(context.getContainer(), bean.getSchemaName(), bean.getSnapshotName());
    Pair<String, String>[] params = context.getActionURL().getParameters();

    Map<String, String> updateDelay = new LinkedHashMap<String, String>();
    updateDelay.put("30", "30 seconds");
    updateDelay.put("60", "1 minute");
    updateDelay.put("300", "5 minutes");
    updateDelay.put("600", "10 minutes");
    updateDelay.put("1800", "30 minutes");
    updateDelay.put("3600", "1 hour");
    updateDelay.put("7200", "2 hours");

    boolean showHistory = BooleanUtils.toBoolean(context.getActionURL().getParameter("showHistory"));
    String historyLabel = showHistory ? "Hide History" : "Show History";

    boolean showDataset = BooleanUtils.toBoolean(context.getActionURL().getParameter("showDataset"));
    String datasetLabel = showDataset ? "Hide Dataset Definition" : "Edit Dataset Definition";
%>

<labkey:errors/>

<table>
<%  if (def != null) { %>
    <tr><td class="labkey-form-label">Name</td><td><%=h(def.getName())%></td>
<%  } %>
    <tr><td class="labkey-form-label">Description</td><td></td>
    <tr><td class="labkey-form-label">Created By</td><td><%=h(def.getCreatedBy())%></td>
    <tr><td class="labkey-form-label">Modified By</td><td><%=h(def.getModifiedBy())%></td>
    <tr><td class="labkey-form-label">Created</td><td></td>
    <tr><td class="labkey-form-label">Last Updated</td><td><%=StringUtils.trimToEmpty(DateUtil.formatDateTime(def.getLastUpdated()))%></td>
</table>

<table>
    <tr><td>&nbsp;</td></tr>
    <tr>
        <td><%=PageFlowUtil.buttonLink("Update Snapshot", PageFlowUtil.urlProvider(QueryUrls.class).urlUpdateSnapshot(context.getContainer()).addParameters(params), "return confirm('Updating will replace all current data with a fresh snapshot');")%></td>
<%  if (def != null) { %>
        <td><%=PageFlowUtil.buttonLink("Source Query", bean.getSchema().urlFor(QueryAction.sourceQuery, def.getQueryDefinition()))%></td>
        <td><%=PageFlowUtil.buttonLink(historyLabel, context.cloneActionURL().replaceParameter("showHistory", String.valueOf(!showHistory)))%></td>
        <td><%=PageFlowUtil.buttonLink(datasetLabel, context.cloneActionURL().replaceParameter("showDataset", String.valueOf(!showDataset)))%></td>
<%  } %>
    </tr>
</table>
