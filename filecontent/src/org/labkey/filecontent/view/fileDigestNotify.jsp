<%
/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
<%@ page import="org.labkey.api.audit.AuditLogEvent"%>
<%@ page import="org.labkey.api.files.FileContentEmailPref" %>
<%@ page import="org.labkey.api.files.FileUrls" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.Path" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.webdav.WebdavResource" %>
<%@ page import="org.labkey.api.webdav.WebdavService" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    org.labkey.filecontent.message.FileContentDigestProvider.FileDigestForm form = ((JspView<org.labkey.filecontent.message.FileContentDigestProvider.FileDigestForm>)HttpView.currentView()).getModelBean();
    int pref = FileContentEmailPref.FOLDER_DEFAULT;//NumberUtils.stringToInt(EmailService.get().getEmailPref(user, c, new FileContentEmailPref()), -1);

    ActionURL emailPrefs = PageFlowUtil.urlProvider(FileUrls.class).urlFileEmailPreference(form.getContainer());
    ActionURL fileBrowser = PageFlowUtil.urlProvider(FileUrls.class).urlBegin(form.getContainer());
%>
<html>
    <head>
        <base href="<%=h(AppProps.getInstance().getBaseServerUrl() + AppProps.getInstance().getContextPath())%>"/>
        <%=PageFlowUtil.getStylesheetIncludes(form.getContainer(), form.getUser())%>
    </head>
    <body>
        <table width="100%">
            <tr><td>Summary of notifications of files at <a href="<%=fileBrowser.getURIString()%>"><%=form.getContainer().getPath()%></a>.</td></tr>
        </table>
        <hr size="1"/>
        <br>

        <table width="100%">
            <tr><th>Time</th><th>User</th><th>Comment</th></tr>
        <%
            for (Map.Entry<Path, List<AuditLogEvent>> record : form.getRecords().entrySet())
            {
                Path path = record.getKey();
                WebdavResource resource = WebdavService.get().getResolver().lookup(path);
                
        %>
            <%  if (resource.exists()) { %>
                <tr><td class="labkey-alternate-row" colspan="3"><%=resource.isCollection() ? "Folder: " : " File: "%><a href="<%=resource.getLocalHref(getViewContext())%>"><%=h(resource.getName())%></a></td></tr>
            <%  } else { %>
                <tr><td class="labkey-alternate-row" colspan="3"><%=resource.isCollection() ? "Folder: " : " File: "%><span class="labkey-strong"><%=h(resource.getName())%></span></td></tr>
            <%  }

                int i=0;
                for (AuditLogEvent event : record.getValue())
                {
                    String rowCls = (i % 2 == 0) ? "labkey-row" : "labkey-row";
                    User user = event.getCreatedBy();
            %>
                    <tr class="<%=rowCls%>"><td><%=DateUtil.formatDateTime(event.getCreated())%></td><td><%=user.getDisplayName(user)%></td><td><%=event.getComment()%></td></tr>
            <%
                }
            %>
                <tr><td colspan="3"><hr size="1"/></td></tr>
        <%
            }
        %>
        </table>
        <br>

        <table width="100%">
            <tr><td>You have received this email because <%
                switch(pref)
                {
                    case FileContentEmailPref.FOLDER_DEFAULT:
                    case FileContentEmailPref.INDIVIDUAL: %>
                    you are signed up to receive notifications about updates to files at <a href="<%=fileBrowser.getURIString()%>"><%= PageFlowUtil.filter(form.getContainer().getPath()) %></a>.
                    If you no longer wish to receive these notifications you can <a href="<%=emailPrefs.getURIString()%>">change your email preferences</a>. <%
                    break;
                } %>
            </td></tr>
        </table>
    </body>
</html>
