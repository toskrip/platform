<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.data.ColumnInfo" %>
<%@ page import="org.labkey.api.data.DisplayColumn" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.query.snapshot.QuerySnapshotForm" %>
<%@ page import="org.labkey.api.query.snapshot.QuerySnapshotService" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    JspView<QuerySnapshotForm> me = (JspView<QuerySnapshotForm>) HttpView.currentView();
    QuerySnapshotForm bean = me.getModelBean();
    ViewContext context = HttpView.currentContext();

    Map<String, String> columnMap = new HashMap<String, String>();
    for (String name : bean.getSnapshotColumns())
        columnMap.put(name, name);

    boolean isAutoUpdateable = QuerySnapshotService.get(bean.getSchemaName()) instanceof QuerySnapshotService.AutoUpdateable;
    boolean isEdit = QueryService.get().getSnapshotDef(context.getContainer(), bean.getSchemaName(), bean.getSnapshotName()) != null;

    Map<String, String> updateDelay = new LinkedHashMap<String, String>();
    updateDelay.put("30", "30 seconds");
    updateDelay.put("60", "1 minute");
    updateDelay.put("300", "5 minutes");
    updateDelay.put("600", "10 minutes");
    updateDelay.put("1800", "30 minutes");
    updateDelay.put("3600", "1 hour");
    updateDelay.put("7200", "2 hours");
%>

<labkey:errors/>

<script type="text/javascript">

    function onAutoUpdate()
    {
        var manualUpdate = YAHOO.util.Dom.get('manualUpdate');
        var updateDelay = YAHOO.util.Dom.get('updateDelay');

        if (manualUpdate.checked)
            updateDelay.style.display = "none";
        else
            updateDelay.style.display = "";
    }
</script>

<form action="" method="post">
    <table cellpadding="0" class="normal">
        <tr><td colspan="10" style="padding-top:14; padding-bottom:2"><span class="ms-announcementtitle">Snapshot Name and Type</span></td></tr>
        <tr><td colspan="10" width="100%" class="ms-titlearealine"><img height="1" width="1" src="<%=AppProps.getInstance().getContextPath() + "/_.gif"%>"></td></tr>

        <tr><td>&nbsp;</td></tr>
        <tr><td>Snapshot Name:</td><td><input type="text" name="snapshotName" <%=isEdit ? "readonly" : ""%> value="<%=StringUtils.trimToEmpty(bean.getSnapshotName())%>"></td></tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td>Manual Refresh</td><td><input <%=isAutoUpdateable ? "" : "disabled"%> checked type="radio" name="manualRefresh" id="manualUpdate" onclick="onAutoUpdate();"></td></tr>
        <tr><td>Automatic Refresh</td><td><input disabled="<%=isAutoUpdateable ? "" : "disabled"%>" onclick="onAutoUpdate();" type="radio" name="automaticRefresh"></td>
            <td><select id="updateDelay" style="display:none"><labkey:options value="<%=bean.getUpdateDelay()%>" map="<%=updateDelay%>"></labkey:options></select></td>
        </tr>
        <tr><td colspan="10" style="padding-top:14; padding-bottom:2"><span class="ms-announcementtitle">Snapshot Column Selection</span></td></tr>
        <tr><td colspan="10" width="100%" class="ms-titlearealine"><img height="1" width="1" src="<%=AppProps.getInstance().getContextPath() + "/_.gif"%>"></td></tr>

        <tr><td></td><td><table class="normal">
            <tr><td></td><th>Name</th><th>Label</th><th>Type</th><th>Description</th></tr>
    <%
        for (DisplayColumn col : QuerySnapshotService.get(bean.getSchemaName()).getDisplayColumns(bean))
        {
            ColumnInfo info = col.getColumnInfo();
    %>
            <tr>
                <td><input type="checkbox" name="snapshotColumns" <%=columnMap.containsKey(col.getName()) ? "checked" : ""%> value="<%=col.getName()%>"></td>
                <td><%=h(col.getName())%></td>
                <td><%=h(info.getCaption())%></td>
                <td><%=h(info.getFriendlyTypeName())%></td>
                <td><%=h(info.getDescription())%></td>
            </tr>
    <%
        }
    %>
        </table></td></tr>
        <tr><td><input type="image" src="<%=PageFlowUtil.submitSrc()%>"></td></tr>
    </table>
</form>
