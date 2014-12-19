<%
/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.query.controllers.QueryController" %>
<%@ page import="org.springframework.validation.Errors" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("Ext4"));
        resources.add(ClientDependency.fromPath("query/ExportTablePanel.js"));
        return resources;
    }
%>
<%
    JspView<QueryController.ExportTablesForm> me = (JspView<QueryController.ExportTablesForm>) HttpView.currentView();
    Errors errors = me.getErrors();

    if(errors.hasErrors())
    {
%>
        <div id="errors">
            <ul>
                <%
                    for (ObjectError error : (List<ObjectError>) errors.getAllErrors())
                    {
                %>
                <li>
                    <p style="color:red;"><%=h(getViewContext().getMessage(error))%></p>
                </li>
                <%
                    }
                %>
            </ul>
        </div>
<%
    }
%>

<div id='merge-tables-ui'></div>
<script type="text/javascript">
    Ext4.onReady(function(){
        var exportPanel = Ext4.create('HIPC.tree.ExportTablePanel', {
            renderTo: 'merge-tables-ui'
        });
    });
</script>