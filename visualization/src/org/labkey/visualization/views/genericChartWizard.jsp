<%
/*
 * Copyright (c) 2012-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
%>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.PropertyManager" %>
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page import="org.labkey.api.reports.permissions.ShareReportPermission" %>
<%@ page import="org.labkey.api.reports.report.view.ReportUtil" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%@ page import="org.labkey.api.settings.FolderSettingsCache" %>
<%@ page import="org.labkey.api.util.ExtUtil" %>
<%@ page import="org.labkey.api.util.Formats" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.visualization.VisualizationController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("vischart");
    }
%>
<%
    JspView<VisualizationController.GenericReportForm> me = (JspView<VisualizationController.GenericReportForm>) HttpView.currentView();
    ViewContext ctx = getViewContext();
    Container c = getContainer();
    User user = getUser();
    VisualizationController.GenericReportForm form = me.getModelBean();

    String numberFormat = PropertyManager.getProperties(c, "DefaultStudyFormatStrings").get("NumberFormatString");
    if (numberFormat == null)
        numberFormat = Formats.f1.toPattern();
    String numberFormatFn = ExtUtil.toExtNumberFormatFn(numberFormat);

    boolean canEdit = false;
    ActionURL editUrl = null;
    if (form.getReportId() != null)
    {

        Report report = form.getReportId().getReport(ctx);
        if(report != null)
        {
            canEdit = report.canEdit(user, c);
            editUrl = report.getEditReportURL(ctx);
        }
    }
    else
    {
        canEdit = ctx.hasPermission(ReadPermission.class) && !user.isGuest();
    }

    String renderId = "generic-report-div-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
%>

<labkey:scriptDependency/>
<div id="<%=h(renderId)%>" style="width:100%;"></div>

<script type="text/javascript">
    var init = function()
    {
        var reportId = <%=q(form.getReportId() != null ? form.getReportId().toString() : null) %>;
        if (reportId != null)
        {
            LABKEY.Query.Visualization.get({
                reportId: reportId,
                success: function(result)
                {
                    if (result.type == LABKEY.Query.Visualization.Type.GenericChart)
                        initializeChartPanel(result);
                    else
                        displayErrorMsg("The saved chart does not match one of the expected chart types.");
                },
                failure: function (response)
                {
                    displayErrorMsg("No time chart report found for reportId:" + reportId + ".");
                },
                scope: this
            });
        }
        else
        {
            initializeChartPanel();
        }
    };

    var initializeChartPanel = function(savedReportInfo)
    {
        var panel = Ext4.create('LABKEY.ext4.GenericChartPanel', {
            renderTo: <%=q(renderId)%>,
            height: 650,
            canEdit: <%=canEdit%>,
            canShare: <%=c.hasPermission(user, ShareReportPermission.class)%>,
            isDeveloper: <%=user.isDeveloper()%>,
            defaultNumberFormat: eval("<%=text(numberFormatFn)%>"),
            allowEditMode: <%=form.allowToggleMode()%>,
            editModeURL: <%=q(editUrl != null ? editUrl.toString() : null) %>,
            hideSave        : <%=user.isGuest()%>,
            restrictColumnsEnabled: <%=FolderSettingsCache.areRestrictedColumnsEnabled(c)%>,

            savedReportInfo: savedReportInfo,
            schemaName      : <%=q(form.getSchemaName() != null ? form.getSchemaName() : null) %>,
            queryName       : <%=q(form.getQueryName() != null ? form.getQueryName() : null) %>,
            queryLabel      : <%=q(ReportUtil.getQueryLabelByName(user, c, form.getSchemaName(), form.getQueryName()))%>,
            viewName        : <%=q(form.getViewName() != null ? form.getViewName() : null) %>,
            dataRegionName  : <%=q(form.getDataRegionName())%>,
            renderType      : <%=q(form.getRenderType())%>,
            id              : <%=q(form.getComponentId()) %>,
            baseUrl         : <%=q(getActionURL().toString())%>,

            autoColumnName  : <%=q(form.getAutoColumnName() != null ? form.getAutoColumnName() : null) %>,
            autoColumnYName  : <%=q(form.getAutoColumnYName() != null ? form.getAutoColumnYName() : null) %>,
            autoColumnXName  : <%=q(form.getAutoColumnXName() != null ? form.getAutoColumnXName() : null) %>
        });

        var resize = function() {
            if (panel && panel.doLayout) { panel.doLayout(); }
        };

        Ext4.EventManager.onWindowResize(resize);
    };

    var displayErrorMsg = function(msg)
    {
        Ext4.get(<%=q(renderId)%>).update("<span class='labkey-error'>" + msg + "</span>");
    };

    Ext4.onReady(function(){ init(); });
</script>

