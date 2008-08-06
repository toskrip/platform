<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.model.Study"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.study.model.SampleRequestStatus"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="java.util.Set" %>
<%@ page import="org.labkey.study.SampleManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Study> me = (JspView<Study>) HttpView.currentView();
    Study study = me.getModelBean();
    SampleRequestStatus[] statuses = study.getSampleRequestStatuses(me.getViewContext().getUser());
    Set<Integer> inUseStatuses = study.getSampleRequestStatusesInUse();
    SampleManager.StatusSettings settings = SampleManager.getInstance().getStatusSettings(study.getContainer());
    boolean showSystemStatuses = settings.isUseShoppingCart();
%>
<%=PageFlowUtil.getStrutsError(request, "main")%>
<script type="text/javascript">
function showSystemRows(value)
{
    document.getElementById("systemStatusRow").style.display = value ? "" : "none";
    return true;
}

</script>
<form action="manageStatuses.post" name="manageStatuses" method="POST">
<table width="600px">
    <tr>
        <td class="labkey-form-label" style="padding-top:4px;padding-bottom:4px;">Allowing users to build up specimen requests over multiple
            searches is generally more convenient, but requires the administrator to watch for abandoned unsubmitted requests.</td>
    </tr>
    <tr><td><input type="checkbox" name="useShoppingCart" <%= settings.isUseShoppingCart() ? "CHECKED" : "" %> onclick='showSystemRows(this.checked)'>
        Allow requests to be built over multiple searches before submission</td></tr>
    <tr>
        <td class="labkey-form-label" style="padding-top:4px;padding-bottom:4px;">The specimen request administrator moves requests through states for
            organization and to communicate request progress to end-users.  All submitted requests will
            start in step number 1.</td>
    </tr>
    <tr>
        <td>
        <table>
            <tr>
                <th>&nbsp;</th>
                <th><%= statuses != null && statuses.length > 0 ? "Step Number" : "&nbsp;" %></th>
                <th>Status Name</th>
                <th>Final
                    State<%= helpPopup("Final States", "States are final if they indicate no further processing will occur for a request.  For example, 'Completed', or 'Rejected' could be final states.")%></th>
                <th>Lock
                    Specimens<%= helpPopup("Locked Specimen States", "Specifies whether specimens should be available for additional requests while in each status.")%></th>
            </tr>
            <%
            if (statuses != null && statuses.length > 0)
            {
                for(SampleRequestStatus status : statuses)
                {
            %>
                    <tr <%= status.isSystemStatus() ? "id=\"systemStatusRow\"" : "" %> <%= !showSystemStatuses && status.isSystemStatus() ? "style=\"display:none\"" : "" %>>
                <%
                    if (status.isSystemStatus() || inUseStatuses.contains(status.getRowId()))
                    {
                %>
                        <td>In-use<%= helpPopup("In-use Status", status.isSystemStatus() ? "This is a system status that cannot be deleted." :
                                "This status cannot be deleted because one or more requests are currently in this status.") %></td>
                <%
                    }
                    else
                    {
                %>
                        <td><%= textLink("delete", "deleteStatus.view?id=" + status.getRowId())%></td>
                <%
                    }
                %>
                        <td align="center"><%= status.isSystemStatus() ? "&nbsp;" : status.getSortOrder() + 1 %></td>
                        <td>
                            <%
                                if (!status.isSystemStatus())
                                {
                            %>
                            <input type="hidden" name="ids" value="<%= status.getRowId() %>">
                            <%
                                }
                            %>
                            <input type="text" name="labels" size="40"
                                   value="<%= status.getLabel() != null ? h(status.getLabel()) : "" %>"
                                    <%= status.isSystemStatus() ? "DISABLED" : "" %>>
                        </td>
                        <td align="center"><input type="checkbox" name="finalStateIds"
                                      value="<%= status.getRowId() %>" <%= status.isFinalState() ? "CHECKED" : ""
                                      %> <%= status.isSystemStatus() ? "DISABLED" : "" %>>
                        </td>
                        <td align="center"><input type="checkbox" name="specimensLockedIds"
                                      value="<%= status.getRowId() %>" <%= status.isSpecimensLocked() ? "CHECKED" : ""%>
                                <%= status.isSystemStatus() ? "DISABLED" : "" %>>
                        </td>
                    </tr>
                    <%
                }
            }
            %>
            <tr>
                <th>&nbsp;</th>
                <th>New Status:</th>
                <td><input type="text" name="newLabel" size="40"></td>
                <td align="center"><input type="checkbox" name="newFinalState"></td>
                <td align="center"><input type="checkbox" name="newSpecimensLocked" CHECKED></td>
            </tr>
            <tr>
                <th>&nbsp;</th>
                <td>&nbsp;</td>
                <td colspan="3">
                    <%= buttonImg("Save")%>&nbsp;
                    <%= buttonImg("Done", "document.manageStatuses.nextPage.value=''; return true;")%>
                    <%= buttonLink("Cancel", ActionURL.toPathString("Study", "manageStudy.view", study.getContainer()))%>&nbsp;
                    <%= buttonImg("Change Order", "document.manageStatuses.nextPage.value='manageStatusOrder'; return true;")%>
                    <input type="hidden" name="nextPage" value="manageStatuses">
                </td>
            </tr>
        </table>
        </td>
    </tr>
</table>
</form>