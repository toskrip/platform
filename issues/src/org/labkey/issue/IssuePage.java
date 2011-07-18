/*
 * Copyright (c) 2006-2011 LabKey Corporation
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

package org.labkey.issue;

import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.DbCache;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.HString;
import org.labkey.api.util.HStringBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewContext;
import org.labkey.issue.IssuesController.*;
import org.labkey.issue.model.Issue;
import org.labkey.issue.model.IssueManager;
import org.labkey.issue.model.IssueManager.Keyword;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.mvc.Controller;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.labkey.api.util.PageFlowUtil.filter;


/**
 * User: Karl Lum
 * Date: Aug 31, 2006
 * Time: 1:07:36 PM
 */
public class IssuePage implements DataRegionSelection.DataSelectionKeyForm
{
    private Issue _issue;
    private Issue _prevIssue;
    private List<Issue> _issueList = Collections.emptyList();
    private IssueManager.CustomColumnConfiguration _ccc;
    private Set<String> _editable = Collections.emptySet();
    private String _callbackURL;
    private BindException _errors;
    private Class<? extends Controller> _action;
    private String _body;
    private boolean _hasUpdatePermissions;
    private HString _requiredFields;
    private String _dataRegionSelectionKey;
    private boolean _print = false;

    public Issue getIssue()
    {
        return _issue;
    }

    public void setIssue(Issue issue)
    {
        _issue = issue;
    }

    public Issue getPrevIssue()
    {
        return _prevIssue;
    }

    public void setPrevIssue(Issue prevIssue)
    {
        _prevIssue = prevIssue;
    }

    public void setPrint(boolean print)
    {
        _print = print;
    }

    public boolean isPrint()
    {
        return _print;
    }
    
    public List<Issue> getIssueList()
    {
        return _issueList;
    }

    public void setIssueList(List<Issue> issueList)
    {
        _issueList = issueList;
    }

    public IssueManager.CustomColumnConfiguration getCustomColumnConfiguration()
    {
        return _ccc;
    }

    public void setCustomColumnConfiguration(IssueManager.CustomColumnConfiguration ccc)
    {
        _ccc = ccc;
    }

    public Set<String> getEditable()
    {
        return _editable;
    }

    public void setEditable(Set<String> editable)
    {
        _editable = editable;
    }

    public String getCallbackURL()
    {
        return _callbackURL;
    }

    public void setCallbackURL(String callbackURL)
    {
        _callbackURL = callbackURL;
    }

    public BindException getErrors()
    {
        return _errors;
    }

    public void setErrors(BindException errors)
    {
        _errors = errors;
    }

    public Class<? extends Controller> getAction()
    {
        return _action;
    }

    public void setAction(Class<? extends Controller> action)
    {
        _action = action;
    }

    public String getBody()
    {
        return _body;
    }

    public void setBody(String body)
    {
        _body = body;
    }

    public void setUserHasUpdatePermissions(boolean hasUpdatePermissions)
    {
        _hasUpdatePermissions = hasUpdatePermissions;
    }

    public boolean getHasUpdatePermissions()
    {
        return _hasUpdatePermissions;
    }

    public HString getRequiredFields()
    {
        return _requiredFields;
    }

    public void setRequiredFields(HString requiredFields)
    {
        _requiredFields = requiredFields;
    }

    public String getDataRegionSelectionKey()
    {
        return _dataRegionSelectionKey;
    }

    public void setDataRegionSelectionKey(String dataRegionSelectionKey)
    {
        _dataRegionSelectionKey = dataRegionSelectionKey;
    }

    public String getColumnName(String tableColumnName)
    {
        String caption = _ccc.getColumnCaptions().get(tableColumnName);
        if (caption != null)
        {
            return caption;
        }
        return tableColumnName;
    }

    public String writeCustomColumn(Container container, HString tableColumnName, HString value, int keywordType, int tabIndex) throws IOException
    {
        final String caption = _ccc.getColumnCaptions().get(tableColumnName.getSource());

        if (null != caption)
        {
            final StringBuilder sb = new StringBuilder();

            sb.append("<tr><td class=\"labkey-form-label\">");
            sb.append(getLabel(tableColumnName));
            sb.append("</td><td>");

            // If custom column has pick list, then show select with keywords, otherwise input box
            if (_ccc.getPickListColumns().contains(tableColumnName.getSource()))
                sb.append(writeSelect(tableColumnName, value, getKeywordOptions(container, keywordType, true), tabIndex));
            else if (tableColumnName.startsWith("int"))
                sb.append(writeIntegerInput(tableColumnName, value, tabIndex));
            else
                sb.append(writeInput(tableColumnName, value, tabIndex));

            sb.append("</td></tr>");

            return sb.toString();
        }

        return "";
    }


    public HString writeInput(HString field, HString value, HString extra)
    {
        if (!isEditable(field.getSource()))
            return new HString(filter(value.getSource(), false, true));
        final HStringBuilder sb = new HStringBuilder();

        sb.append("<input name=\"");
        sb.append(field);
        sb.append("\" value=\"");
        sb.append(filter(value));
        sb.append("\" onchange=\"LABKEY.setDirty(true);return true;");
        if (null == extra)
            sb.append("\">");
        else
        {
            sb.append("\" ");
            sb.append(extra);
            sb.append(">");
        }
        return sb.toHString();
    }

    // Limit number of characters in an integer field
    public HString writeIntegerInput(HString field, HString value, int tabIndex)
    {
        return writeInput(field, value, new HString("maxlength=\"10\" tabIndex=\"" + tabIndex + "\" size=\"8\"",false));
    }

    public HString writeInput(HString field, HString value, int tabIndex)
    {
        return writeInput(field, value, new HString("tabIndex=\"" + tabIndex + "\"",false));
    }

    public HString writeSelect(HString field, HString value, HString display, HString options, int tabIndex)
    {
        if (!isEditable(field.getSource()))
        {
            return filter(display);
        }
        final HStringBuilder sb = new HStringBuilder();
        sb.append("<select id=\"");
        sb.append(PageFlowUtil.filter(field));
        sb.append("\" name=\"");
        sb.append(PageFlowUtil.filter(field));
        sb.append("\" tabindex=\"");
        sb.append(tabIndex);
        sb.append("\" onchange=\"LABKEY.setDirty(true);return true;\" >");

        if (null != display && 0 != display.length())
        {
            sb.append("<option value=\"");
            sb.append(filter(value));
            sb.append("\" selected>");
            sb.append(filter(display));
            sb.append("</option>");
        }
        sb.append(options);
        sb.append("</select>");
        return sb.toHString();
    }

    public HString writeSelect(HString field, HString value, HString options, int tabIndex) throws IOException
    {
        return writeSelect(field, value, value, options, tabIndex);
    }

    public boolean isEditable(String field)
    {
        return _editable.contains(field);
    }

    protected HString getKeywordOptions(Container container, int type, boolean allowBlank)
    {
        assert type != IssuesController.ISSUE_NONE;

        synchronized (IssueManager.KEYWORD_LOCK)
        {
            String cacheKey = container + "/" + type;
            String s = (String) DbCache.get(IssuesSchema.getInstance().getTableInfoIssueKeywords(), cacheKey);

            if (null != s)
                return new HString(s);

            Keyword[] keywords = IssueManager.getKeywords(container.getId(), type);
            StringBuilder sb = new StringBuilder(keywords.length * 30);
            if (allowBlank)
                sb.append("<option></option>\n");
            for (Keyword keyword : keywords)
            {
                sb.append("<option>");
                sb.append(PageFlowUtil.filter(keyword.getKeyword()));
                sb.append("</option>\n");
            }
            s = sb.toString();
            DbCache.put(IssuesSchema.getInstance().getTableInfoIssueKeywords(), cacheKey, s, 10 * CacheManager.MINUTE);
            return new HString(s);
        }
    }

    protected HString getKeywordOptionsWithDefault(Container c, int type, HString[] standardValues, HString def) throws SQLException
    {
        synchronized (IssueManager.KEYWORD_LOCK)
        {
            HString options = getKeywordOptions(c, type, false);

            if (0 == options.length())
            {
                // First reference in this container... save away standard values
                IssueManager.addKeyword(c, type, standardValues);
                IssueManager.setKeywordDefault(c, type, def);

                options = getKeywordOptions(c, type, false);
            }

            return options;
        }
    }

    public HString getTypeOptions(Container container)
    {
        return getKeywordOptions(container, IssuesController.ISSUE_TYPE, true);
    }

    public HString getAreaOptions(Container container)
    {
        return getKeywordOptions(container, IssuesController.ISSUE_AREA, true);
    }

    public HString getMilestoneOptions(Container container)
    {
        return getKeywordOptions(container, IssuesController.ISSUE_MILESTONE, true);
    }

    public HString getResolutionOptions(Container c) throws SQLException
    {
        return getKeywordOptionsWithDefault(c, IssuesController.ISSUE_RESOLUTION, HString.array(false, "Fixed", "Duplicate", "Won't Fix", "Not Repro", "By Design"), new HString("Fixed",false));
    }

    public HString getPriorityOptions(Container c) throws SQLException
    {
        return getKeywordOptionsWithDefault(c, IssuesController.ISSUE_PRIORITY, HString.array(false,"0", "1", "2", "3", "4"), new HString("3",false));
    }

    public HString getUserOptions(Container c, Issue issue, User currentUser)
    {
        Collection<User> members = IssueManager.getAssignedToList(c, issue);
        HStringBuilder select = new HStringBuilder();
        select.append("<option value=\"\"></option>");

        for (User member : members)
        {
            select.append("<option value=").append(member.getUserId()).append(">");
            select.append(member.getDisplayName(currentUser));
            select.append("</option>\n");
        }

        return select.toHString();
    }

    public HString getNotifyListString()
    {
        final HString notify = _issue.getNotifyList();
        if (notify != null)
            return notify.replace(';', '\n');
        return HString.EMPTY;
    }

    public HString getNotifyList(Container c, Issue issue)
    {
        if (!isEditable("notifyList"))
        {
            return filter(getNotifyListString());
        }
        final HStringBuilder sb = new HStringBuilder();

        sb.append("<script type=\"text/javascript\">LABKEY.requiresScript('completion.js');</script>");
        sb.append("<textarea name=\"notifyList\" id=\"notifyList\" cols=\"30\" rows=\"4\" tabindex=\"10\"");
        sb.append(" onKeyDown=\"return ctrlKeyCheck(event);\"");
        sb.append(" onBlur=\"hideCompletionDiv();\"");
        sb.append(" onchange=\"LABKEY.setDirty(true);return true;\"");
        sb.append(" autocomplete=\"off\"");
        sb.append(" onKeyUp=\"return handleChange(this, event, 'completeUser.view?issueId=");
        sb.append(getIssue().getIssueId());
        sb.append("&amp;prefix=');\"");
        sb.append(">");
        sb.append(filter(getNotifyListString()));
        sb.append("</textarea>");

        return sb.toHString();
    }

    public HString getLabel(String columnName)
    {
        return getLabel(new HString(columnName));
    }

    public HString getLabel(HString columnName)
    {
        ColumnInfo col = IssuesSchema.getInstance().getTableInfoIssues().getColumn(columnName.getSource());
        String name = null;
        if (_ccc.getColumnCaptions().containsKey(columnName.getSource()))
            name = _ccc.getColumnCaptions().get(columnName.getSource());
        else if (col != null)
            name = col.getLabel();
        if (name != null && name.length() > 0)
        {
            String label = PageFlowUtil.filter(name).replaceAll(" ", "&nbsp;");
            if (_requiredFields != null && _requiredFields.indexOf(columnName.toLowerCase().getSource()) != -1)
                return new HString(label + "<span class=\"labkey-error\">*</span>", false);
            return new HString(label,false);
        }
        return columnName;
    }

    public String _toString(Object a)
    {
        return null == a ? "" : a.toString();
    }

    public String writeDate(Date d)
    {
        if (null == d) return "";
        return DateUtil.formatDate(d);
    }

    public String renderAttachments(ViewContext context, AttachmentParent parent)
    {
        List<Attachment> attachments = new ArrayList(AttachmentService.get().getAttachments(parent));

        StringBuilder sb = new StringBuilder();
        boolean canEdit = isEditable("attachments");

        Collections.sort(attachments, new Comparator<Attachment>()
        {
            @Override
            public int compare(Attachment o1, Attachment o2)
            {
                return o1.getName().compareTo(o2.getName());
            }
        });

        if (attachments.size() > 0)
        {
            sb.append("<table>");
            sb.append("<tr><td>&nbsp;</td></tr>");

            for (Attachment a : attachments)
            {
                sb.append("<tr><td>");

                if (!canEdit)
                {
                    sb.append("<a href=\"");
                    sb.append(PageFlowUtil.filter(a.getDownloadUrl(DownloadAction.class)));
                    sb.append("\"><img src=\"");
                    sb.append(context.getRequest().getContextPath());
                    sb.append(PageFlowUtil.filter(a.getFileIcon()));
                    sb.append("\">&nbsp;");
                    sb.append(PageFlowUtil.filter(a.getName()));
                    sb.append("</a>");
                }
                else
                {
                    sb.append("<img src=\"");
                    sb.append(context.getRequest().getContextPath());
                    sb.append(PageFlowUtil.filter(a.getFileIcon()));
                    sb.append("\">&nbsp;");
                    sb.append(PageFlowUtil.filter(a.getName()));
                }
                sb.append("</td></tr>");
            }
            sb.append("</table>");
        }
        return sb.toString();
    }

    public String renderDuplicates(ViewContext context, List<Integer> dups)
    {
        StringBuilder sb = new StringBuilder();
        for (Integer dup : dups)
            sb.append("<a href='").append(IssuesController.getDetailsURL(context.getContainer(), dup, false)).append("'>").append(dup).append("</a>, ");
        if (dups.size() > 0)
            sb.setLength(sb.length() - 2);
        return sb.toString();
    }
}
