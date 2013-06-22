/*
 * Copyright (c) 2008-2013 LabKey Corporation
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

package org.labkey.list.controllers;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.GWTServiceAction;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.attachments.AttachmentForm;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.DownloadURL;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UrlColumn;
import org.labkey.api.defaults.ClearDefaultValuesAction;
import org.labkey.api.defaults.SetDefaultValuesListAction;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.lists.permissions.DesignListPermission;
import org.labkey.api.query.AbstractQueryImportAction;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DetailsView;
import org.labkey.api.view.GWTView;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.ZipFile;
import org.labkey.api.writer.ZipUtil;
import org.labkey.list.model.ListAuditViewFactory;
import org.labkey.list.model.ListEditorServiceImpl;
import org.labkey.list.model.ListImporter;
import org.labkey.list.model.ListManager;
import org.labkey.list.model.ListWriter;
import org.labkey.list.view.ListDefinitionForm;
import org.labkey.list.view.ListImportServiceImpl;
import org.labkey.list.view.ListItemAttachmentParent;
import org.labkey.list.view.ListQueryForm;
import org.labkey.list.view.ListQueryView;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * User: adam
 * Date: Dec 30, 2007
 * Time: 12:44:30 PM
 */
public class ListController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(ListController.class,
            SetDefaultValuesListAction.class,
            ClearDefaultValuesAction.class
            );

    public ListController()
    {
        setActionResolver(_actionResolver);
    }


    private NavTree appendRootNavTrail(NavTree root)
    {
        return appendRootNavTrail(root, getContainer(), getUser());
    }


    public static NavTree appendRootNavTrail(NavTree root, Container c, User user)
    {
        if (c.hasPermission(user, AdminPermission.class) || user.isDeveloper())
        {
            root.addChild("Lists", getBeginURL(c));
        }
        return root;
    }


    private NavTree appendListNavTrail(NavTree root, ListDefinition list, @Nullable String title)
    {
        appendRootNavTrail(root);
        root.addChild(list.getName(), list.urlShowData());

        if (null != title)
            root.addChild(title);

        return root;
    }


    public static ActionURL getBeginURL(Container c)
    {
        return new ActionURL(BeginAction.class, c);
    }

    @Override
    public PageConfig defaultPageConfig()
    {
        PageConfig config = super.defaultPageConfig();
        return config.setHelpTopic(new HelpTopic("lists"));
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object form, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/list/view/begin.jsp", null, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Available Lists");
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class DomainImportServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService()
        {
            return new ListImportServiceImpl(getViewContext());
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ShowListDefinitionAction extends SimpleRedirectAction<ListDefinitionForm>
    {
        @Override
        public ActionURL getRedirectURL(ListDefinitionForm listDefinitionForm) throws Exception
        {
            if (listDefinitionForm.getListId() == null)
            {
                throw new NotFoundException();
            }
            return new ActionURL(EditListDefinitionAction.class, getContainer()).addParameter("listId", listDefinitionForm.getListId().intValue());
        }
    }


    @RequiresPermissionClass(DesignListPermission.class)
    public class EditListDefinitionAction extends SimpleViewAction<ListDefinitionForm>
    {
        private ListDefinition _list;

        public ModelAndView getView(ListDefinitionForm form, BindException errors) throws Exception
        {
            _list = null;

            boolean createList = (null == form.getListId() || 0 == form.getListId()) && form.getName() == null;
            if (!createList)
                _list = form.getList();

            Map<String, String> props = new HashMap<>();

            URLHelper returnURL = form.getReturnURLHelper();

            props.put("listId", null == _list ? "0" : String.valueOf(_list.getListId()));
            props.put(ActionURL.Param.returnUrl.name(), returnURL.toString());
            props.put("allowFileLinkProperties", "0");
            props.put("allowAttachmentProperties", "1");
            props.put("showDefaultValueSettings", "1");
            props.put("hasDesignListPermission", getContainer().hasPermission(getUser(), DesignListPermission.class) ? "true":"false");
            props.put("hasInsertPermission", getContainer().hasPermission(getUser(), InsertPermission.class) ? "true":"false");
            // Why is this different than DesignListPermission???
            props.put("hasDeleteListPermission", getContainer().hasPermission(getUser(), AdminPermission.class) ? "true":"false");
            props.put("loading", "Loading...");

            return new GWTView("org.labkey.list.Designer", props);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (null == _list)
                root.addChild("Create new List");
            else
                appendListNavTrail(root, _list, null);
            return root;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class ListEditorServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService()
        {
            return new ListEditorServiceImpl(getViewContext());
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class DeleteListDefinitionAction extends ConfirmAction<ListDefinitionForm>
    {
        public void validateCommand(ListDefinitionForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getConfirmView(ListDefinitionForm form, BindException errors) throws Exception
        {
            return new HtmlView("Are you sure you want to delete the list '" + PageFlowUtil.filter(form.getList().getName()) + "'?");
        }

        public boolean handlePost(ListDefinitionForm form, BindException errors) throws Exception
        {
            try
            {
                form.getList().delete(getUser());
            }
            catch (Table.OptimisticConflictException e)
            {
                //bug 11729: if someone else already deleted the list, no need to throw exception
            }
            return true;
        }

        public URLHelper getSuccessURL(ListDefinitionForm form)
        {
            return getBeginURL(getContainer());     // Always go back to manage views page
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class GridAction extends SimpleViewAction<ListQueryForm>
    {
        private ListDefinition _list;
        public ModelAndView getView(ListQueryForm form, BindException errors) throws Exception
        {
            _list = form.getList();
            if (null == _list)
                throw new NotFoundException("List does not exist in this container");
            return new ListQueryView(form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendListNavTrail(root, _list, null);
        }
    }


    // Unfortunate query hackery that orders details columns based on default view
    // TODO: Fix this... build into InsertView (or QueryInsertView or something)
    private void setDisplayColumnsFromDefaultView(int listId, DataRegion rgn)
    {
        ListQueryView lqv = new ListQueryView(new ListQueryForm(listId, getViewContext()), null);
        List<DisplayColumn> defaultGridColumns = lqv.getDisplayColumns();
        List<DisplayColumn> displayColumns = new ArrayList<>(defaultGridColumns.size());

        // Save old grid column list
        List<String> currentColumns = rgn.getDisplayColumnNames();

        rgn.setTable(lqv.getTable());

        for (DisplayColumn dc : defaultGridColumns)
        {
            assert null != dc;

            // Occasionally in production this comes back null -- not sure why.  See #8088
            if (null == dc)
                continue;

            if (dc instanceof UrlColumn)
                continue;

            if (dc.getColumnInfo() != null && dc.getColumnInfo().isShownInDetailsView())
            {
                displayColumns.add(dc);
            }
        }

        rgn.setDisplayColumns(displayColumns);

        // Add all columns that aren't in the default grid view
        for (String columnName : currentColumns)
            if (null == rgn.getDisplayColumn(columnName))
                rgn.addColumn(rgn.getTable().getColumn(columnName));
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class DetailsAction extends SimpleViewAction<ListDefinitionForm>
    {
        private ListDefinition _list;

        public ModelAndView getView(ListDefinitionForm form, BindException errors) throws Exception
        {
            _list = form.getList();
            TableInfo table = _list.getTable(getUser());

            ListQueryUpdateForm tableForm = new ListQueryUpdateForm(table, getViewContext(), _list, errors);
            DetailsView details = new DetailsView(tableForm);

            ButtonBar bb = new ButtonBar();
            bb.setStyle(ButtonBar.Style.separateButtons);

            if (table.hasPermission(getUser(), UpdatePermission.class))
            {
                ActionURL updateUrl = _list.urlUpdate(getUser(), tableForm.getPkVal(), getViewContext().getActionURL());
                ActionButton editButton = new ActionButton("Edit", updateUrl);
                bb.add(editButton);
            }

            ActionButton gridButton;
            ActionURL gridUrl = _list.urlShowData();
            if (form.getReturnUrl() != null)
            {
                URLHelper url = form.getReturnURLHelper();
                String text = "Return";
                if(gridUrl.getPath().equalsIgnoreCase(url.getPath()))
                    text = "Show Grid";

                gridButton = new ActionButton(text, url);
            }
            else
                gridButton = new ActionButton("Show Grid", gridUrl);

            bb.add(gridButton);
            details.getDataRegion().setButtonBar(bb);
            setDisplayColumnsFromDefaultView(_list.getListId(), details.getDataRegion());

            VBox view = new VBox();
            ListItem item;
            item = _list.getListItem(tableForm.getPkVal(), getUser());

            if (null == item)
                throw new NotFoundException("List item '" + tableForm.getPkVal() + "' does not exist");

            view.addView(details);

            if (form.isShowHistory())
            {
                WebPartView linkView = new HtmlView(PageFlowUtil.textLink("hide item history", getViewContext().cloneActionURL().deleteParameter("showHistory")));
                linkView.setFrame(WebPartView.FrameType.NONE);
                view.addView(linkView);
                WebPartView history = ListAuditViewFactory.getInstance().createListItemDetailsView(getViewContext(), item.getEntityId());
                history.setFrame(WebPartView.FrameType.NONE);
                view.addView(history);
            }
            else
            {
                view.addView(new HtmlView(PageFlowUtil.textLink("show item history", getViewContext().cloneActionURL().addParameter("showHistory", "1"))));
            }

            if (_list.getDiscussionSetting().isLinked())
            {
                String entityId = item.getEntityId();

                DomainProperty titleProperty = _list.getDomain().getPropertyByName(_list.getTable(getUser()).getTitleColumn());
                Object title = (null != titleProperty ? item.getProperty(titleProperty) : null);
                String discussionTitle = (null != title ? title.toString() : "Item " + tableForm.getPkVal());

                ActionURL linkBackURL = _list.urlFor(ResolveAction.class).addParameter("entityId", entityId);
                DiscussionService.Service service = DiscussionService.get();
                boolean multiple = _list.getDiscussionSetting() == ListDefinition.DiscussionSetting.ManyPerItem;

                // Display discussion by default in single-discussion case, #4529
                DiscussionService.DiscussionView discussion = service.getDisussionArea(getViewContext(), entityId, linkBackURL, discussionTitle, multiple, !multiple);
                view.addView(discussion);

                getPageConfig().setFocusId(discussion.getFocusId());
            }

            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendListNavTrail(root, _list, "View List Item");
        }
    }


    // Override to ensure that pk value type matches column type.  This is critical for PostgreSQL 8.3.
    public static class ListQueryUpdateForm extends QueryUpdateForm
    {
        private ListDefinition _list;

        public ListQueryUpdateForm(TableInfo table, ViewContext ctx, ListDefinition list, BindException errors)
        {
            super(table, ctx, errors);
            _list = list;
        }

        public Object[] getPkVals()
        {
            Object[] pks = super.getPkVals();
            assert 1 == pks.length;
            pks[0] = _list.getKeyType().convertKey(pks[0]);
            return pks;
        }

        public Domain getDomain()
        {
            return _list != null ? _list.getDomain() : null;
        }
    }


    // Users can change the PK of a list item, so we don't want to store PK in discussion source URL (back link
    // from announcements to the object).  Instead, we tell discussion service to store a URL with ListId and
    // EntityId.  This action resolves to the current details URL for that item.
    @RequiresPermissionClass(ReadPermission.class)
    public class ResolveAction extends SimpleRedirectAction<ListDefinitionForm>
    {
        public ActionURL getRedirectURL(ListDefinitionForm form) throws Exception
        {
            ListDefinition list = form.getList();
            ListItem item = list.getListItemForEntityId(getViewContext().getActionURL().getParameter("entityId"), getUser()); // TODO: Use proper form, validate
            ActionURL url = getViewContext().cloneActionURL().setAction(DetailsAction.class);   // Clone to preserve discussion params
            url.deleteParameter("entityId");
            url.addParameter("pk", item.getKey().toString());

            return url;
        }
    }


    @RequiresPermissionClass(InsertPermission.class)
    public class UploadListItemsAction extends AbstractQueryImportAction<ListDefinitionForm>
    {
        private ListDefinition _list;

        public UploadListItemsAction()
        {
            super(ListDefinitionForm.class);
        }
        
        @Override
        protected void initRequest(ListDefinitionForm form) throws ServletException
        {
            _list = form.getList();
            setTarget(_list.getTable(getUser()));
        }

        public ModelAndView getView(ListDefinitionForm form, BindException errors) throws Exception
        {
            initRequest(form);
            return getDefaultImportView(form, errors);
        }

        @Override
        protected int importData(DataLoader dl, FileStream file, String originalName, BatchValidationException errors) throws IOException
        {
            int count = _list.insertListItems(getUser(), dl, errors, null, null, false);
            return count;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendListNavTrail(root, _list, "Import Data");
        }
    }

    
    @RequiresPermissionClass(ReadPermission.class)
    public class HistoryAction extends SimpleViewAction<ListQueryForm>
    {
        private ListDefinition _list;

        public ModelAndView getView(ListQueryForm form, BindException errors) throws Exception
        {
            _list = form.getList();
            if (_list != null)
                return ListAuditViewFactory.getInstance().createListHistoryView(getViewContext(), _list);
            else
                return new HtmlView("Unable to find the specified List");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (_list != null)
                return appendListNavTrail(root, _list, _list.getName() + ":History");
            else
                return root.addChild(":History");
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ListItemDetailsAction extends SimpleViewAction
    {
        private ListDefinition _list;

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            int id = NumberUtils.toInt((String)getViewContext().get("rowId"));
            int listId = NumberUtils.toInt((String)getViewContext().get("listId"));
            _list = ListService.get().getList(getContainer(), listId);
            if (_list == null)
            {
                throw new NotFoundException();
            }

            AuditLogEvent event = AuditLogService.get().getEvent(id);
            if (event != null && event.getLsid() != null)
            {
                Map<String, Object> dataMap = OntologyManager.getProperties(ContainerManager.getSharedContainer(), event.getLsid());
                if (dataMap != null)
                {
                    String oldRecord;
                    String newRecord;
                    boolean isEncoded = false;
                    if (dataMap.containsKey(AuditLogService.get().getPropertyURI(ListManager.LIST_AUDIT_EVENT, ListAuditViewFactory.OLD_RECORD_PROP_NAME)) ||
                            dataMap.containsKey(AuditLogService.get().getPropertyURI(ListManager.LIST_AUDIT_EVENT, ListAuditViewFactory.NEW_RECORD_PROP_NAME)))
                    {
                        isEncoded = true;
                        oldRecord = (String)dataMap.get(AuditLogService.get().getPropertyURI(ListManager.LIST_AUDIT_EVENT, ListAuditViewFactory.OLD_RECORD_PROP_NAME));
                        newRecord = (String)dataMap.get(AuditLogService.get().getPropertyURI(ListManager.LIST_AUDIT_EVENT, ListAuditViewFactory.NEW_RECORD_PROP_NAME));
                    }
                    else
                    {
                        oldRecord = (String)dataMap.get(AuditLogService.get().getPropertyURI(ListManager.LIST_AUDIT_EVENT, "oldRecord"));
                        newRecord = (String)dataMap.get(AuditLogService.get().getPropertyURI(ListManager.LIST_AUDIT_EVENT, "newRecord"));
                    }

                    if (!StringUtils.isEmpty(oldRecord) || !StringUtils.isEmpty(newRecord))
                    {
                        return new ItemDetails(event, oldRecord, newRecord, isEncoded, getViewContext().getActionURL().getParameter(ActionURL.Param.redirectUrl));
                    }
                    else
                        return new HtmlView("No details available for this event.");
                }
            }
            throw new NotFoundException("Unable to find the audit history detail for this event");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (_list != null)
                return appendListNavTrail(root, _list, "List Item Details");
            else
                return root.addChild("List Item Details"); 
        }
    }


    private static class ItemDetails extends WebPartView
    {
        AuditLogEvent _event;
        String _oldRecord;
        String _newRecord;
        boolean _isEncoded;
        String _returnUrl;

        public ItemDetails(AuditLogEvent event, String oldRecord, String newRecord, boolean isEncoded, String returnUrl)
        {
            _event = event;
            _oldRecord = oldRecord;
            _newRecord = newRecord;
            _isEncoded = isEncoded;
            _returnUrl = returnUrl;
        }

        protected void renderView(Object model, PrintWriter out) throws Exception
        {
            if (_isEncoded)
            {
                _renderViewEncoded(out);
            }
            else
            {
                out.write("<table>\n");
                out.write("<tr><td>");
                if (_returnUrl != null)
                    out.write(PageFlowUtil.generateButton("Done", _returnUrl));
                out.write("</tr></td>");
                out.write("<tr><td></td></tr>");

                out.write("<tr class=\"labkey-wp-header\"><th align=\"left\">Item Changes</th></tr>");
                out.write("<tr><td>Comment:&nbsp;<i>" + PageFlowUtil.filter(_event.getComment()) + "</i></td></tr>");
                out.write("<tr><td><table>\n");
                if (!StringUtils.isEmpty(_oldRecord))
                    _renderRecord("previous:", _oldRecord, out);

                if (!StringUtils.isEmpty(_newRecord))
                    _renderRecord("current:", _newRecord, out);
                out.write("</table></td></tr>\n");
                out.write("</table>\n");
            }
        }

        private void _renderRecord(String title, String record, PrintWriter out)
        {
            out.write("<tr><td><b>" + title + "</b></td>");
            for (Pair<String, String> param : PageFlowUtil.fromQueryString(record))
            {
                out.write("<td>" + param.getValue() + "</td>");
            }
        }

        private void _renderViewEncoded(PrintWriter out)
        {
            Map<String, String> prevProps = ListAuditViewFactory.decodeFromDataMap(_oldRecord);
            Map<String, String> newProps = ListAuditViewFactory.decodeFromDataMap(_newRecord);
            int modified = 0;

            out.write("<table>\n");
            out.write("<tr class=\"labkey-wp-header\"><th colspan=\"2\" align=\"left\">Item Changes</th></tr>");
            out.write("<tr><td colspan=\"2\">Comment:&nbsp;<i>" + PageFlowUtil.filter(_event.getComment()) + "</i></td></tr>");
            out.write("<tr><td/>\n");

            for (Map.Entry<String, String> entry : prevProps.entrySet())
            {
                String newValue = newProps.remove(entry.getKey());
                if (!Objects.equals(newValue, entry.getValue()))
                {
                    out.write("<tr><td class=\"labkey-form-label\">");
                    out.write(PageFlowUtil.filter(entry.getKey()));
                    out.write("</td><td>");

                    modified++;
                    out.write(PageFlowUtil.filter(entry.getValue()));
                    out.write("&nbsp;&raquo;&nbsp;");
                    out.write(PageFlowUtil.filter(Objects.toString(newValue, "")));
                    out.write("</td></tr>\n");
                }
                else
                {
                    out.write("<tr><td class=\"labkey-form-label\">");
                    out.write(PageFlowUtil.filter(entry.getKey()));
                    out.write("</td><td>");
                    out.write(PageFlowUtil.filter(entry.getValue()));
                    out.write("</td></tr>\n");
                }
            }

            for (Map.Entry<String, String> entry : newProps.entrySet())
            {
                modified++;
                out.write("<tr><td class=\"labkey-form-label\">");
                out.write(PageFlowUtil.filter(entry.getKey()));
                out.write("</td><td>");

                out.write("&nbsp;&raquo;&nbsp;");
                out.write(PageFlowUtil.filter(Objects.toString(entry.getValue(), "")));
                out.write("</td></tr>\n");
            }
            out.write("<tr><td/>\n");
            out.write("<tr><td colspan=\"2\">Summary:&nbsp;<i>");
            if (1 == modified)
                out.write(modified + " field was modified");
            else
                out.write(modified + " fields were modified");
            out.write("</i></td></tr>");


            out.write("<tr><td>&nbsp;</td></tr>");
            out.write("<tr><td>");
            if (_returnUrl != null)
                out.write(PageFlowUtil.generateButton("Done", _returnUrl));
            out.write("</tr></td>");

            out.write("</table>\n");
        }
    }


    public static ActionURL getDownloadURL(Container c, String entityId, String filename)
    {
        return new DownloadURL(DownloadAction.class, c, entityId, filename);
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class DownloadAction extends SimpleViewAction<AttachmentForm>
    {
        public ModelAndView getView(final AttachmentForm form, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.None);
            final AttachmentParent parent = new ListItemAttachmentParent(form.getEntityId(), getContainer());

            return new HttpView()
            {
                protected void renderInternal(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
                {
                    AttachmentService.get().download(response, parent, form.getName());
                }
            };
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermissionClass(DesignListPermission.class)
    public class ExportListArchiveAction extends ExportAction
    {
        public void export(Object o, HttpServletResponse response, BindException errors) throws Exception
        {
            Container c = getContainer();
            ListWriter writer = new ListWriter();
            ZipFile zip = new ZipFile(response, FileUtil.makeFileNameWithTimestamp(c.getName(), "lists.zip"));
            writer.write(c, getUser(), zip);
            zip.close();
        }
    }


    @RequiresPermissionClass(DesignListPermission.class)
    public class ImportListArchiveAction extends FormViewAction<Object>
    {
        public void validateCommand(Object target, Errors errors)
        {
        }

        public ModelAndView getView(Object o, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/list/view/importLists.jsp", null, errors);
        }

        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            Map<String, MultipartFile> map = getFileMap();

            if (map.isEmpty())
            {
                errors.reject("listImport", "You must select a .list.zip file to import.");
            }
            else if (map.size() > 1)
            {
                errors.reject("listImport", "Only one file is allowed.");
            }
            else
            {
                MultipartFile file = map.values().iterator().next();

                if (0 == file.getSize() || StringUtils.isBlank(file.getOriginalFilename()))
                {
                    errors.reject("listImport", "You must select a .list.zip file to import.");
                }
                else
                {
                    InputStream is = file.getInputStream();

                    File dir = FileUtil.createTempDirectory("list");
                    ZipUtil.unzipToDirectory(is, dir);

                    ListImporter li = new ListImporter();

                    List<String> errorList = new LinkedList<>();

                    try
                    {
                        li.process(new FileSystemFile(dir), getContainer(), getUser(), errorList, Logger.getLogger(ListController.class));

                        for (String error : errorList)
                            errors.reject(ERROR_MSG, error);
                    }
                    catch (InvalidFileException e)
                    {
                        errors.reject(ERROR_MSG, "Invalid list archive");
                    }
                }
            }

            return !errors.hasErrors();
        }

        public ActionURL getSuccessURL(Object o)
        {
            return getBeginURL(getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Import List Archive");
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class BrowseListsAction extends ApiAction<Object>
    {
        public ApiResponse execute(Object form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            response.put("lists", getJSONLists(ListService.get().getLists(getContainer())));
            response.put("success", true);

            return response;
        }

        private List<JSONObject> getJSONLists(Map<String, ListDefinition> lists){
            List<JSONObject> listsJSON = new ArrayList<>();
            for(ListDefinition def : new TreeSet<>(lists.values())){
                JSONObject listObj = new JSONObject();
                listObj.put("name", def.getName());
                listObj.put("id", def.getListId());
                listObj.put("description", def.getDescription());
                listsJSON.add(listObj);
            }
            return listsJSON;
        }
    }
}
