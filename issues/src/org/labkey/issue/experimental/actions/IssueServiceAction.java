package org.labkey.issue.experimental.actions;

import org.labkey.api.action.GWTServiceAction;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Sort;
import org.labkey.api.exp.property.DomainEditorServiceBase;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.security.Group;
import org.labkey.api.security.GroupManager;
import org.labkey.api.security.MemberType;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewContext;
import org.labkey.issue.CustomColumnConfiguration;
import org.labkey.issue.model.CustomColumn;
import org.labkey.issue.model.IssueManager;
import org.labkey.issues.client.GWTIssueDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by klum on 5/11/2016.
 */
@RequiresPermission(ReadPermission.class)
public class IssueServiceAction extends GWTServiceAction
{
    protected BaseRemoteService createService()
    {
        return new IssueServiceImpl(getViewContext());
    }

    private class IssueServiceImpl extends DomainEditorServiceBase implements org.labkey.issues.client.IssueService
    {
        public IssueServiceImpl(ViewContext context)
        {
            super(context);
        }

        @Override
        public GWTDomain getDomainDescriptor(String typeURI)
        {
            GWTDomain domain = super.getDomainDescriptor(typeURI);
            domain.setDefaultValueOptions(new DefaultValueType[]
                    { DefaultValueType.FIXED_EDITABLE, DefaultValueType.LAST_ENTERED, DefaultValueType.FIXED_NON_EDITABLE }, DefaultValueType.FIXED_EDITABLE);
            return domain;
        }

        @Override
        public GWTIssueDefinition getIssueDefinition(String defName)
        {
            GWTIssueDefinition def = new GWTIssueDefinition();
            Container c = getContainer();

            IssueManager.EntryTypeNames typeNames = IssueManager.getEntryTypeNames(c);
            Group assignedToGroup = IssueManager.getAssignedToGroup(c);
            User defaultUser = IssueManager.getDefaultAssignedToUser(c);
            //bean.moveToContainers = IssueManager.getMoveDestinationContainers(c);

            def.setSingularItemName(typeNames.singularName);
            def.setPluralItemName(typeNames.pluralName);
            def.setCommentSortDirection(IssueManager.getCommentSortDirection(c).name());
            if (assignedToGroup != null)
                def.setAssignedToGroup(assignedToGroup.getUserId());
            if (defaultUser != null)
                def.setAssignedToUser(defaultUser.getUserId());

            return def;
        }

        @Override
        public List<String> updateIssueDefinition(GWTIssueDefinition def, GWTDomain orig, GWTDomain dd)
        {
            IssueManager.EntryTypeNames names = new IssueManager.EntryTypeNames();

            try (DbScope.Transaction transaction = IssuesSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                names.singularName = def.getSingularItemName();
                names.pluralName = def.getPluralItemName();

                IssueManager.saveEntryTypeNames(getContainer(), names);
                IssueManager.saveCommentSortDirection(getContainer(), Sort.SortDirection.fromString(def.getCommentSortDirection()));
                //IssueManager.saveMoveDestinationContainers(getContainer(), _moveToContainers);
                //IssueManager.saveRelatedIssuesList(getContainer(), form.getRelatedIssuesList());

                Group group = null;
                if (def.getAssignedToGroup() != null)
                    group = SecurityManager.getGroup(def.getAssignedToGroup());
                IssueManager.saveAssignedToGroup(getContainer(), group);

                User user = null;
                if (def.getAssignedToUser() != null)
                    user = UserManager.getUser(def.getAssignedToUser());
                IssueManager.saveDefaultAssignedToUser(getContainer(), user);

                List<String> errors = super.updateDomainDescriptor(orig, dd);
                transaction.commit();

                return errors;
            }
        }

        @Override
        public List<Map<String, String>> getProjectGroups()
        {
            List<Map<String, String>> groups = new ArrayList<>();

            SecurityManager.getGroups(getContainer().getProject(), true).stream().filter(group -> !group.isGuests() && (!group.isUsers() || getUser().isSiteAdmin())).forEach(group -> {
                String displayText = (group.isProjectGroup() ? "" : "Site:") + group.getName();
                groups.add(PageFlowUtil.map("name", displayText, "value", String.valueOf(group.getUserId())));
            });

            return groups;
        }

        @Override
        public List<Map<String, String>> getUsersForGroup(int groupId)
        {
            List<Map<String, String>> users = new ArrayList<>();

            Group group = SecurityManager.getGroup(groupId);
            if (group != null)
            {
                for (User user : SecurityManager.getAllGroupMembers(group, MemberType.ACTIVE_USERS, group.isUsers()))
                {
                    if (getContainer().hasPermission(user, UpdatePermission.class))
                    {
                        users.add(PageFlowUtil.map("name", user.getDisplayName(getUser()), "value", String.valueOf(user.getUserId())));
                    }
                }
            }
            return users;
        }

        @Override
        public List<Map<String, String>> getFolderMoveContainers()
        {
            List<Map<String, String>> containers = new ArrayList<>();
            Container root = ContainerManager.getRoot();
            List<Container> allContainers = ContainerManager.getAllChildren(root, getUser(), AdminPermission.class, false);

            // remove current container
            allContainers.remove(getContainer());
            allContainers.remove(root);

            for (Container container : allContainers)
            {
                // remove containers that start with underscore
                if (container.getName().startsWith("_"))
                    continue;

                containers.add(PageFlowUtil.map("name", container.getPath(), "value", container.getId()));
            }
            return containers;
        }
    }
}