<%
/*
 * Copyright (c) 2007-2012 LabKey Corporation
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
<%@ page import="org.labkey.api.exp.api.ExperimentUrls" %>
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.api.study.assay.AssayProvider" %>
<%@ page import="org.labkey.api.study.permissions.DesignAssayPermission" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.study.controllers.assay.AssayController" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.study.assay.AssayService" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    AssayController.ChooseAssayBean bean = (AssayController.ChooseAssayBean)getModelBean();
    ViewContext context = getViewContext();
    List<AssayProvider> providers = bean.getProviders();
    Map<String, String> locations = new LinkedHashMap<String, String>();
    String defaultLocation = null;

    for (Pair<Container, String> entry : AssayService.get().getLocationOptions(context.getContainer(), context.getUser()))
    {
        locations.put(entry.getKey().getId(), entry.getValue());
        if (defaultLocation == null)
            defaultLocation = entry.getKey().getId();
    }
%>
<p>
    Each assay is a customized version of a particular assay type. 
    The assay type defines things like how the data is parsed and what kinds of analysis are provided.
</p>
<p>If you have an existing assay design in the XAR file format (a .xar or .xar.xml file), you can
    <a href="<%= urlProvider(ExperimentUrls.class).getUploadXARURL(getViewContext().getContainer()) %>">upload the XAR file directly</a>
    or place the file in this folder's pipeline directory and import using the
    <a href="<%= urlProvider(PipelineUrls.class).urlBrowse(getViewContext().getContainer(), getViewContext().getActionURL().toString()) %>">Data Pipeline</a>.
</p>
<p>
    To create a new assay design, please choose which assay type you would like to customize with your own settings and input options.
</p>
<labkey:errors />
<form method="POST">
    <input type="hidden" name="returnURL" value="<%=h(bean.getReturnURL())%>">
    <table>
        <% for (AssayProvider provider : providers) { %>
        <tr>
            <td><input id="providerName_<%=h(provider.getName())%>" name="providerName" type="radio" value="<%= h(provider.getName()) %>"/></td>
            <td><label for="providerName_<%=h(provider.getName())%>"><strong><%= h(provider.getName())%></strong></label></td>
        </tr>
        <tr>
            <td />
            <td><label for="providerName_<%=h(provider.getName())%>"><%= provider.getDescription() %></label></td>
        </tr>
        <% } %>
        <tr><td>&nbsp;</td><td>&nbsp;</td></tr>
        <%
            if (!locations.isEmpty()) {
        %>
        <tr>
            <td></td>
            <td><span><strong>Assay Location</strong>&nbsp;Create assay in project or shared locations so it is visible in subfolders.</span></td>
        </tr>
        <tr>
            <td></td>
            <td><select name="assayContainer" id="assayContainer"><labkey:options value="<%=defaultLocation%>" map="<%=locations%>"></labkey:options></select></td>
        </tr>
        <tr><td>&nbsp;</td><td>&nbsp;</td></tr>
        <%
            }
        %>
        <tr>
            <td />
            <td><%= generateSubmitButton("Next" )%><%= generateButton("Cancel", new ActionURL(AssayController.BeginAction.class, getViewContext().getContainer())) %></td>
        </tr>
    </table>
</form>