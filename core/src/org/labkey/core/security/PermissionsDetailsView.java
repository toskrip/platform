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

package org.labkey.core.security;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.ACL;
import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityUrls;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * User: jeckels
* Date: May 2, 2008
*/
public class PermissionsDetailsView extends WebPartView
{
    String helpText = "<b>Admin:</b> Users have all permissions on a folder.<br><br>" +
            "<b>Editor:</b> Users can modify data, but cannot perform administrative actions.<br><br>" +
            "<b>Author:</b> Users can modify their own data, but can only read others' data.<br><br>" +
            "<b>Reader:</b> Users can read text and data, but cannot modify it.<br><br>" +
            "<b>Submitter:</b> Users can insert new records, but cannot view or change other records.<br><br>" +
            "<b>No Permissions:</b> Users cannot view or modify any information in a folder.<br><br>" +
            "See the LabKey Server <a target=\"_new\" href=\"" + (new HelpTopic("configuringPerms", HelpTopic.Area.SERVER)).getHelpTopicLink() + "\">security</a> help topics for more information.";

    Container _c;
    Container _project;


    PermissionsDetailsView(Container c, String view)
    {
        _c = c;

        // get project for this container
        String path = _c.getPath();
        int i = path.indexOf('/', 1);
        if (i == -1) i = path.length();
        String project = path.substring(0, i);
        _project = ContainerManager.getForPath(project);

        addObject("view", view);

        if(c.isRoot())
            setTitle("Default permissions for new projects");
        else
            setTitle("Permissions for " + _c.getPath());
    }

    private String getPermissionsOption(SecurityManager.PermissionSet permission, int selectedPerm)
    {
        return "<option value=\"" + permission.getPermissions() + "\"" +
                    (selectedPerm == permission.getPermissions() ? " SELECTED" : "") +
                    ">" + permission.getLabel() + "</option>\n";
    }

    private void renderGroupTableRow(Group group, ACL acl, PrintWriter out, String displayName)
    {
        int id = group.getUserId();
        int perm = acl.getPermissions(id);
        if (perm == -1) perm = ACL.PERM_ALLOWALL; // HACK
        String htmlId = "group." + Integer.toHexString(id);
        out.print("<tr><td class='labkey-form-label'>");
        out.print(PageFlowUtil.filter(displayName));
        out.print("</td><td><select onchange=\"if(document.updatePermissions.inheritPermissions) document.updatePermissions.inheritPermissions.checked=false;\" id=");
        out.print(htmlId);
        out.print(" name=");
        out.print(htmlId);
        out.print(">");
        if (!group.isGuests() || perm == ACL.PERM_ALLOWALL)
            out.print(getPermissionsOption(SecurityManager.PermissionSet.ADMIN, perm));

        out.print(getPermissionsOption(SecurityManager.PermissionSet.EDITOR, perm));
        out.print(getPermissionsOption(SecurityManager.PermissionSet.AUTHOR, perm));
        out.print(getPermissionsOption(SecurityManager.PermissionSet.READER, perm));
        out.print(getPermissionsOption(SecurityManager.PermissionSet.RESTRICTED_READER, perm));
        out.print(getPermissionsOption(SecurityManager.PermissionSet.SUBMITTER, perm));
        out.print(getPermissionsOption(SecurityManager.PermissionSet.NO_PERMISSIONS, perm));
        
        SecurityManager.PermissionSet permSet = SecurityManager.PermissionSet.findPermissionSet(perm);
        if (permSet == null)
            out.print("<option value=" + perm + ">" + perm + "</option>");
        out.print("</select>");
        out.print("</td>");
        out.print("<td>");
        out.print(PageFlowUtil.helpPopup("LabKey Server Security Roles", helpText, true));
        out.print("</td>");

        if (!_c.isRoot())
        {
            out.print("<td>");
            out.print("&nbsp;[<a href=\"" + PageFlowUtil.filter(PageFlowUtil.urlProvider(SecurityUrls.class).getGroupPermissionURL(_c, group.getUserId())) + "\">");
            out.print("permissions</a>]</td>");
        }
        out.print("</tr>");
    }


    @Override
    public void renderView(Object model, PrintWriter out) throws IOException, ServletException
    {
        ACL acl = _c.getAcl();

        if (SecurityManager.isAdminOnlyPermissions(_c))
        {
            out.println("<b>Note: </b> Only administrators currently have access to this " + (_c.isProject() ? "project" : "") + " folder. <br>");
            if (_c.hasChildren())
            {
                Container[] children = ContainerManager.getAllChildren(_c);
                boolean childrenAdminOnly = true;
                for (Container child : children)
                {
                    if (!SecurityManager.isAdminOnlyPermissions(child))
                    {
                        childrenAdminOnly = false;
                        break;
                    }
                }
                out.println((childrenAdminOnly ? "No" : "Some") + " child folders can be accessed by non-administrators.");
            }
        }
        Group[] projGroups = SecurityManager.getGroups(_project, false);

        // browse link
        //out.println("Go back to <a href=\"" + ActionURL.toPathString("Project", "begin", _c.getPath()) + "\">" + _c.getPath() + "</a>");

        out.println("<form name=\"updatePermissions\" action=\"updatePermissions.post\" method=\"POST\">");

        if(_c.isProject())
        {
            boolean subfoldersInherit = SecurityManager.shouldNewSubfoldersInheritPermissions(_c);
            out.println("<input type=\"checkbox\" name=\"newSubfoldersInheritPermissions\" " + (subfoldersInherit ? "checked=\"true\"" : "") + "> Newly created subfolders should inherit permissions");
        }
        else if(!_c.isRoot())
        {
            out.println("<input type=checkbox name=inheritPermissions " + (_c.isInheritedAcl() ? "checked" : "")
                    + "> Inherit permissions from " + (_c.isProject() ? "Global Groups" : _c.getParent().getPath()));
            out.println("<br/>");
        }

        out.println("<table><tr><td colspan='2' class='labkey-strong'>Project Groups</td></tr>");
        for (Group group : projGroups)
            renderGroupTableRow(group, acl, out, group.getName());

        out.println("<tr><td colspan='2' class='labkey-strong'>Site Groups</td></tr>");
        
        //render global groups
        Group[] globalGroups = SecurityManager.getGroups(ContainerManager.getRoot(), true);
        Group usersGroup = null;
        Group guestsGroup = null;
        for(Group group : globalGroups)
        {
            if(group.isGuests())
                guestsGroup = group;
            else if(group.isUsers())
                usersGroup = group;
            else if(group.isAdministrators())
            {
                // for groups that we don't want to display, we still have to output a hidden input
                // for the ACL value; otherwise, a submit with 'inherit' turned off will result in the
                // hidden groups having all permissions set to no-access.
                int id = group.getUserId();
                int perm = acl.getPermissions(id);
                if (perm == -1) perm = ACL.PERM_ALLOWALL; // HACK
                String htmlId = "group." + Integer.toHexString(id);
                out.println("<input type=\"hidden\" name=\"" + htmlId + "\" value=\"" + perm + "\">");
            }
            else
                renderGroupTableRow(group, acl, out, group.getName());
        }

        //always render all site users and groups last
        if (usersGroup != null)
            renderGroupTableRow(usersGroup, acl, out, "All site users");
        if (guestsGroup != null)
            renderGroupTableRow(guestsGroup, acl, out, "Guests");

        out.println("</table>");
        out.println(PageFlowUtil.generateSubmitButton("Update"));
        out.println("<input name=objectId type=hidden value=\"" + _c.getId() + "\">");
        out.println("<input name=view type=hidden value=\"" + getViewContext().get("view") + "\">");
        out.println("</form><br>");

        // Now render all the module-specific views registered for this page
        VBox vbox = new VBox();
        List<SecurityManager.ViewFactory> factories = SecurityManager.getViewFactories();

        for (SecurityManager.ViewFactory factory : factories)
            vbox.addView(factory.createView(getViewContext()));

        try
        {
            ViewContext ctx = getViewContext();
            vbox.render(ctx.getRequest(), ctx.getResponse());
        }
        catch(Exception e)
        {
            throw new ServletException(e);
        }
    }
}
