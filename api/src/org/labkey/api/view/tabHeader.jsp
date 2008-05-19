<%
/*
 * Copyright (c) 2008 LabKey Corporation
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
<%@ page import="org.labkey.api.view.TabStripView"%>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.view.*" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    ViewContext context = HttpView.currentContext();
    JspView<List<TabStripView.TabInfo>> me = (JspView<List<TabStripView.TabInfo>>) HttpView.currentView();
    List<TabStripView.TabInfo> tabs = me.getModelBean();
    ActionURL url = context.getActionURL();

    String currentTab = url.getParameter(TabStripView.TAB_PARAM);
    if (currentTab == null && !tabs.isEmpty())
        currentTab = tabs.get(0).getId();
%>
<table cellspacing=0 cellpadding=0 width="100%">
    <tr>
        <td class="navtab" style="border-top:none;border-left:none;border-right:none;">
            <img src="<%=request.getContextPath()%>/_.gif" height=1 width=5>
        </td>
    <%
        for (TabStripView.TabInfo tab : tabs)
        {
            out.println("<td class=\"" + (tab.getId().equals(currentTab) ? "navtab-selected" : "navtab-inactive") + "\">" + tab.render(HttpView.currentContext()) + "</td>");
        }
    %>
        <td class="navtab" style="border-top:none;border-left:none;border-right:none;text-align:right;" width=100%>
            <img src="<%=request.getContextPath()%>/_.gif" height=1 width=5>
        </td>
    </tr>
    <tr>
        <td colspan="<%=tabs.size() + 2%>" class="navtab" style="border-top:none;text-align:left;" width=100%>
            <img src="<%=request.getContextPath()%>/_.gif" height=1 width=5>

