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
package org.labkey.study.reports;

import org.apache.commons.lang3.BooleanUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.AbstractReport;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.StudyService;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UniqueID;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.ParticipantCategoryImpl;
import org.labkey.study.model.ParticipantGroup;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.StudyManager;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jan 17, 2012
 */
public class ParticipantReport extends AbstractReport
{
    public static final String TYPE = "ReportService.ParticipantReport";
    public static final String MEASURES_PROP = "measures";
    public static final String GROUPS_PROP = "groups";

    @Override
    public String getType()
    {
        return TYPE;
    }

    @Override
    public String getTypeDescription()
    {
        return "Participant Report";
    }

    @Override
    public HttpView renderReport(ViewContext context) throws Exception
    {
        ReportsController.ParticipantReportForm form = new ReportsController.ParticipantReportForm();
        
        form.setReportId(getReportId());
        form.setComponentId("participant-report-panel-" + UniqueID.getRequestScopedUID(context.getRequest()));

        JspView<ReportsController.ParticipantReportForm> view = new JspView<ReportsController.ParticipantReportForm>("/org/labkey/study/view/participantReport.jsp", form);

        String rwp = (String)context.get("reportWebPart");
        form.setExpanded(!(rwp != null));

        form.setAllowOverflow(!BooleanUtils.toBoolean(rwp));

        view.setTitle(getDescriptor().getReportName());
        view.setFrame(WebPartView.FrameType.PORTAL);

        if (canEdit(context.getUser(), context.getContainer()))
        {
            String script = String.format("javascript:customizeParticipantReport('%s');", form.getComponentId());
            NavTree edit = new NavTree("Edit", script, AppProps.getInstance().getContextPath() + "/_images/partedit.png");
            view.addCustomMenu(edit);

            NavTree menu = new NavTree();
            menu.addChild("New " + StudyService.get().getSubjectNounSingular(context.getContainer()) + " Report", new ActionURL(ReportsController.ParticipantReportAction.class, context.getContainer()));
            menu.addChild("Manage Views", PageFlowUtil.urlProvider(ReportUrls.class).urlManageViews(context.getContainer()));
            view.setNavMenu(menu);
        }

        return view;
    }

    @Override
    public Thumbnail getStaticThumbnail()
    {
        InputStream is = ParticipantReport.class.getResourceAsStream("participantThumbnail.gif");
        return new Thumbnail(is, "image/gif");
    }

    @Override
    public String getStaticThumbnailCacheKey()
    {
        return "Reports:ParticipantReportStatic";
    }

    /**
     * Utility method to serialize the report to JSON, we might consider formalizing
     * this into the report interface as using JSON to serialize report state to clients
     * is becoming more common.
     */
    public static JSONObject toJSON(User user, Container container, Report report)
    {
        JSONObject json = ReportUtil.JsonReportForm.toJSON(user, container, report);
        ReportDescriptor descriptor = report.getDescriptor();

        String measuresConfig = descriptor.getProperty(ParticipantReport.MEASURES_PROP);
        if (measuresConfig != null)
        {
            json.put("measures", new JSONArray(measuresConfig));
        }
        String groupsConfig = descriptor.getProperty(ParticipantReport.GROUPS_PROP);
        if (groupsConfig != null)
        {
            json.put("groups", new JSONArray(groupsConfig));
        }
        return json;
    }

    @Override
    public void afterImport(Container container, User user)
    {
        // lookup the filtered cohort and participant groups (if they have been imported as well)
        String groupsConfig = getDescriptor().getProperty(GROUPS_PROP);
        if (groupsConfig != null)
        {
            JSONArray groups = new JSONArray(groupsConfig);
            Map<String, CohortImpl> cohortMap = new HashMap<String, CohortImpl>();
            Map<String, ParticipantGroup> groupMap = new HashMap<String, ParticipantGroup>();

            for (CohortImpl cohort : StudyManager.getInstance().getCohorts(container, user))
                cohortMap.put(cohort.getLabel(), cohort);

            for (ParticipantCategoryImpl category : ParticipantGroupManager.getInstance().getParticipantCategories(container, user))
            {
                for (ParticipantGroup group : category.getGroups())
                    groupMap.put(group.getLabel(), group);
            }

            JSONArray newGroups = new JSONArray();
            for (JSONObject group : groups.toJSONObjectArray())
            {
                String type = group.getString("type");
                String label = group.getString("label");
                int id = group.getInt("id");

                if (id == -1)
                {
                    newGroups.put(group);
                }
                else if ("cohort".equals(type))
                {
                    if (cohortMap.containsKey(label))
                    {
                        group.put("id", cohortMap.get(label).getRowId());
                        newGroups.put(group);
                    }
                }
                else if ("participantGroup".equals(type))
                {
                    if (groupMap.containsKey(label))
                    {
                        group.put("id", groupMap.get(label).getRowId());
                        newGroups.put(group);
                    }
                }
                else
                    newGroups.put(group);
            }

            getDescriptor().setProperty(GROUPS_PROP, newGroups.toString());
        }
    }
}
