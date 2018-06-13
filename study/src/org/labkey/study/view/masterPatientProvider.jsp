<%
    /*
     * Copyright (c) 2013-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.PropertyManager" %>
<%@ page import="org.labkey.api.study.MasterPatientIndexService" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.element.Input" %>
<%@ page import="org.labkey.api.util.element.Option" %>
<%@ page import="org.labkey.api.util.element.Select" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="java.util.Collection" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    PropertyManager.PropertyMap map = PropertyManager.getNormalStore().getProperties(StudyController.MasterPatientProviderSettings.CATEGORY);
    String type = map.get(StudyController.MasterPatientProviderSettings.TYPE);
    MasterPatientIndexService.ServerSettings settings = null;
    if (type != null)
    {
        MasterPatientIndexService svc = MasterPatientIndexService.getProvider(type);
        if (svc != null )
            settings = MasterPatientIndexService.getProvider(type).getServerSettings();
    }

    Collection<MasterPatientIndexService> services = MasterPatientIndexService.getProviders();
    Select.SelectBuilder options = new Select.SelectBuilder().name("type").label("Type")
            .layout(Input.Layout.HORIZONTAL)
            .formGroup(true)
            .addOption(new Option.OptionBuilder().build());

    for (MasterPatientIndexService svc : services)
    {
        options.addOption(new Option.OptionBuilder().value(svc.getName())
                .label(svc.getName())
                .selected(svc.getName().equals(type))
                .build());
    }
%>


<labkey:errors/>
<%  if (services.isEmpty()) {
%>
    <p>
        A Master Patient Index Provider is not available in this LabKey installation.
    </p>
<%  }
    else
    {
%>
    <labkey:form method="post" layout="horizontal">
        <%= options %>

        <labkey:input type="text" label="Server URL *" name="url" value="<%= h(settings != null ? settings.getUrl() : null) %>" size="50" isRequired="true"/>
        <labkey:input type="text" label="User *" name="username" value="<%= h(settings != null ? settings.getUsername() : null) %>"
                      isRequired="true" contextContent="Provide a valid user name for logging onto the Master Patient Index server" forceSmallContext="true"/>
        <labkey:input type="password" label="Password *" name="password"
                      isRequired="true" contextContent="Provide the password for the user name" forceSmallContext="true"/>

        <labkey:button text="save" submit="true"/>
        <labkey:button text="cancel" href="<%=PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL()%>"/>
    </labkey:form>
<%  }
%>
