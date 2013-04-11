<%
/*
 * Copyright (c) 2009-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page import="org.labkey.api.util.FolderDisplayMode" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.api.view.template.MenuBarView" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.api.view.WebPartFactory" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<ClientDependency>();
        resources.add(ClientDependency.fromFilePath("ext3"));
        return resources;
    }
%>
<%
    List<Portal.WebPart> menus = ((MenuBarView) HttpView.currentView()).getModelBean();
    ViewContext currentContext = HttpView.currentContext();
    Container c = currentContext.getContainer();
    LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);
    NavTree homeLink;

    FolderDisplayMode folderMode = LookAndFeelProperties.getInstance(c).getFolderDisplayMode();
    boolean folderMenu = folderMode.isShowInMenu();
    boolean customMenusEnabled = laf.isMenuUIEnabled();
    folderMode.isShowInMenu();

    if (null == c || null == c.getProject() || c.getProject().equals(ContainerManager.getHomeContainer()))
        homeLink = new NavTree(laf.getShortName() + " Home", AppProps.getInstance().getHomePageActionURL());
    else
        homeLink = new NavTree(c.getProject().getName(), c.getProject().getFolderType().getStartURL(c.getProject(), currentContext.getUser()));
%>
<div id="menubar" class="labkey-main-menu">
    <ul>
        <li id="projectBar" class="menu-projects"> </li>
        <li id="folderBar" class="menu-folders"><%=h(c.getName())%></li>
        <%
            if(menus.size() > 0)
            {
                for (Portal.WebPart part : menus)
                {
                    String menuCaption = part.getName();
                    String menuName = part.getName() + part.getIndex();
                    try
                    {
                        WebPartFactory factory = Portal.getPortalPart(part.getName());
                        if (null == factory)
                            continue;
                        WebPartView view = factory.getWebPartView(currentContext, part);
                        if (view.isEmpty())
                            continue;       // Don't show folder/query if nothing to show
                        if (null != view.getTitle())
                            menuCaption = view.getTitle();
                    }
                    catch(Exception e)
                    {
                        //Use the part name...
                    }
        %>
        <li id="<%=h(menuName)%>$Header" class="labkey-main-menu-item">
            <a class="labkey-main-menu-link" href="#">
                <%=h(menuCaption)%>
            </a>
        </li>
        <%
                }
            }
        %>
    </ul>
</div>
<script type="text/javascript">
    Ext.onReady(function(){

        new LABKEY.HoverPopup({hoverElem:"projectBar", webPartName:"projectnav"});
        new LABKEY.HoverPopup({hoverElem:"folderBar", webPartName:"foldernav"});

        <%
            for (Portal.WebPart part : menus)
            {
                if (null == Portal.getPortalPartCaseInsensitive(part.getName()))
                    continue;

                String menuName = part.getName() + part.getIndex();
        %>
        new LABKEY.HoverPopup({hoverElem:"<%=menuName%>$Header", webPartName: "<%=part.getName()%>",
            partConfig: { <%
                    String sep = "";
                    for (Map.Entry<String,String> entry : part.getPropertyMap().entrySet())
                    { %>
                        <%=sep%><%=PageFlowUtil.jsString(entry.getKey())%>:<%=PageFlowUtil.jsString(entry.getValue())%><%
                        sep = ",";
                    }%>
            }});
        <%
            }
        %>
    });
</script>
