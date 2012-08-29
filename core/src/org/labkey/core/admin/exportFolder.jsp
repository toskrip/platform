<%
/*
 * Copyright (c) 2012 LabKey Corporation
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
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.LinkedList" %>
<%@ page import="org.labkey.core.admin.writer.FolderSerializationRegistryImpl" %>
<%@ page import="org.labkey.api.admin.FolderWriter" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.writer.Writer" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.core.admin.FolderManagementAction" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
ViewContext context = HttpView.currentContext();
Container c = context.getContainer();
FolderManagementAction.FolderManagementForm form = (FolderManagementAction.FolderManagementForm) HttpView.currentModel();
%>

<labkey:errors/>
<div id="exportForm"></div>

<script type="text/javascript">

var formItems = [];

formItems.push({xtype: "label", text: "Folder objects to export:"});
<%
    Collection<FolderWriter> writers = new LinkedList<FolderWriter>(FolderSerializationRegistryImpl.get().getRegisteredFolderWriters());
    boolean showStudyOptions = false;
    for (FolderWriter writer : writers)
    {
        String parent = writer.getSelectionText();
        if (null != parent && writer.show(c))
        {
            boolean checked = writer.includeInType(form.getExportType());
            %>formItems.push({xtype: "checkbox", hideLabel: true, boxLabel: "<%=parent%>", name: "types", itemId: "<%=parent%>", inputValue: "<%=parent%>", checked: <%=checked%>, objectType: "parent"});<%

            Collection<Writer> children = writer.getChildren();
            if (null != children && children.size() > 0)
            {
                for (Writer child : children)
                {
                    if (null != child.getSelectionText())
                    {
                        String text = child.getSelectionText();
                        %>
                        formItems.push({xtype: "checkbox", style: {marginLeft: "20px"}, hideLabel: true, boxLabel: "<%=text%>", name: "types", itemId: "<%=text%>",
                            inputValue: "<%=text%>", checked: <%=checked%>, objectType: "child", parentId: "<%=parent%>"});
                        <%
                    }
                }
            }
        }

        // if there is a study writer shown, set a boolean variable so we know whether or not the show the study related options
        if ("Study".equals(parent) && writer.show(c))
            showStudyOptions = true;
    }
%>

formItems.push({xtype: "spacer", height: 20, hidden: <%=!showStudyOptions%>});
formItems.push({xtype: "label", text: "Export file formats:", hidden: <%=!showStudyOptions%>});
var formatRadios = new Ext.form.RadioGroup({
    hideLabel: true,
    columns: 1,
    hidden: <%=!showStudyOptions%>,
    items: [
        {boxLabel: 'New XML file formats<%=PageFlowUtil.helpPopup("New XML file formats","Selecting this option will export study meta data using XML file formats (e.g., visit_map.xml and datasets_metadata.xml). This is the recommended setting.")%>', name: "format", inputValue: "new", checked: true},
        {boxLabel: 'Legacy file formats<%=PageFlowUtil.helpPopup("Legacy File Formats", "Selecting this option will export some meta data using older, non-XML file formats (e.g, visit_map.txt and schema.tsv). This setting is not recommended since the non-XML formats contain less information than the XML formats. This option is provided to support older studies that haven't switched to XML file formats yet.")%>', name: "format", inputValue: "old"}
    ]
});
formItems.push(formatRadios);

formItems.push({xtype: "spacer", height: 20});
formItems.push({xtype: "label", text: "Options:"});
formItems.push({xtype: 'checkbox', hideLabel: true, boxLabel: 'Remove All Columns Tagged as Protected<%=PageFlowUtil.helpPopup("Remove Protected Columns", "Selecting this option will exclude all dataset and list columns that have been tagged as protected columns.")%>', name: 'removeProtected', objectType: 'otherOptions'});
formItems.push({xtype: 'checkbox', hideLabel: true, hidden: <%=!showStudyOptions%>, boxLabel: 'Shift All Participant Dates in Datasets<%=PageFlowUtil.helpPopup("Shift Date Columns", "Selecting this option will shift all date values associated with a participant by a random, participant specific, offset (from 1 to 365 days).")%>', name: 'shiftDates', objectType: 'otherOptions'});
formItems.push({xtype: 'checkbox', hideLabel: true, hidden: <%=!showStudyOptions%>, boxLabel: 'Export Alternate Participant IDs in Datasets<%=PageFlowUtil.helpPopup("Export Alternate Participant IDs", "Selecting this option will replace each participant id by an alternate randomly generated id.")%>', name: 'alternateIds', objectType: 'otherOptions'});

formItems.push({xtype: "spacer", height: 20});
formItems.push({xtype: "label", text: "Export to:"});
var locationRadios = new Ext.form.RadioGroup({
    hideLabel: true, 
    columns: 1,
    items: [
        {boxLabel: "Pipeline root <b>export</b> directory, as individual files", name: "location", inputValue: 0},
        {boxLabel: "Pipeline root <b>export</b> directory, as zip file", name: "location", inputValue: 1},
        {boxLabel: "Browser as zip file", name: "location", inputValue: 2, checked: true}
    ]
});
formItems.push(locationRadios);

var exportForm = new LABKEY.ext.FormPanel({
    border: false,
    standardSubmit: true,
    items:formItems,
    buttons:[{text:'Export', type:'submit', handler:submit}],
    buttonAlign:'left'
});

function submit()
{
    // issue 15792: warn if specimen export is selected with any of the PHI related options also selected
    var specimenItems = exportForm.find("itemId", "Specimens");
    if (hasSelectedOption("itemId", "Specimens") && hasSelectedOption("objectType", "otherOptions"))
    {
        Ext.Msg.confirm('Confirm export options', 'Specimen export does not support removing protected columns, shifting participant dates, or using alternate participant IDs. Would you like to proceed anyway?',
            function(btn)
            {
                if (btn == 'yes')
                    exportForm.getForm().submit();
            }
        );
    }
    else
    {
        exportForm.getForm().submit();
    }
}

function hasSelectedOption(attribute, value)
{
    var items = exportForm.find(attribute, value);
    for (var i = 0; i < items.length; i++)
    {
        if (items[i].checked)
            return true;
    }
    return false;
}

Ext.onReady(function() {
    exportForm.render('exportForm');

    // add listeners to each of the parent checkboxes
    var parentCbs = exportForm.find("objectType", "parent");
    Ext.each(parentCbs, function(cb) {
        cb.on("check", function(cmp, checked) {
            var children = exportForm.find("parentId", cb.getItemId());
            Ext.each(children, function(child) {
                child.setValue(checked);
                child.setDisabled(!checked);
            });
        });
    });
});

</script>

