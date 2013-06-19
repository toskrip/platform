<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.study.samples.SampleSearchBean" %>
<%@ page import="java.util.LinkedHashSet" %>
<%
/*
 * Copyright (c) 2011-2013 LabKey Corporation
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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!

  public LinkedHashSet<ClientDependency> getClientDependencies()
  {
      LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
      resources.add(ClientDependency.fromFilePath("clientapi"));
      resources.add(ClientDependency.fromFilePath("Ext4ClientApi"));
      resources.add(ClientDependency.fromFilePath("extWidgets/SearchPanel.js"));
      resources.add(ClientDependency.fromFilePath("study/redesignUtils.js"));
      resources.add(ClientDependency.fromFilePath("ux/CheckCombo/CheckCombo.js"));
      resources.add(ClientDependency.fromFilePath("ux/CheckCombo/CheckCombo.css"));
      resources.add(ClientDependency.fromFilePath("study/SpecimenSearchPanel.js"));
      resources.add(ClientDependency.fromModuleName("Study"));
      return resources;
  }
%>

<%
    JspView<SampleSearchBean> me = (JspView) HttpView.currentView();
    int webPartId = me.getModelBean().getWebPartId();
    String renderTarget = "labkey-specimen-search-"+ webPartId;
%>

<script type="text/javascript">

Ext4.onReady(function(){
    var multi = new LABKEY.MultiRequest();
    var requestFailed = false;
    var errorMessages = [];
    var studyMetadata = null;

    multi.add(LABKEY.Query.selectRows, {schemaName:"study",
        queryName:"StudyProperties",
        success:function (result) {
            if (result.rows.length > 0)
            {
                studyMetadata = result.rows[0];
            }
            else
                errorMessages.push("<i>No study found in this folder</i>");
        },
        failure: function(result) {
            errorMessages.push("<i>Could not retrieve study information for this folder: " + result.exception);
        },
    columns:"*"});

    // Test query to verify that there's specimen data in this study:
    multi.add(LABKEY.Query.selectRows,
        {
            schemaName: 'study',
            queryName: 'SimpleSpecimen',
            maxRows: 1,
            success : function(data)
            {
                if (data.rows.length == 0)
                     errorMessages.push('<i>No specimens found.</i>');
            },
            failure: function(result) {
                errorMessages.push("<i>Could not retrieve specimen information for this folder: </i>" + result.exception);
            }
    });

    multi.send(function() {
        if (errorMessages.length > 0)
            Ext4.get('<%=renderTarget%>').update(errorMessages.join("<br>"));
        else
            Ext4.create('LABKEY.ext.SampleSearchPanel', {}).render('<%=renderTarget%>');
    });
});
</script>
<div id="<%=renderTarget%>"></div>
