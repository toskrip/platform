/*
 * Copyright (c) 2008-2011 LabKey Corporation
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

package org.labkey.api.reports.report.view;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.RReportJob;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.VBox;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * User: Karl Lum
 * Date: Feb 5, 2008
 */
public class RunRReportView extends RunScriptReportView
{
    public static final String TAB_SYNTAX = "Help";

    public RunRReportView(Report report)
    {
        super(report);
    }


    public List<NavTree> getTabList()
    {
        URLHelper url = getBaseUrl().replaceParameter(CACHE_PARAM, String.valueOf(_reportId));

        List<NavTree> tabs = new ArrayList<NavTree>();

        boolean saveChanges = TAB_SOURCE.equals(url.getParameter(TAB_PARAM));

        tabs.add(new ScriptTabInfo(TAB_VIEW, TAB_VIEW, url, saveChanges));
        tabs.add(new ScriptTabInfo(TAB_SOURCE, TAB_SOURCE, url, saveChanges));
        tabs.add(new ScriptTabInfo(TAB_DATA, TAB_DATA, url, saveChanges));
        tabs.add(new ScriptTabInfo(TAB_SYNTAX, TAB_SYNTAX, url, saveChanges));

        return tabs;
    }


    private RReportBean getReportForm() throws Exception
    {
        return populateReportForm(new RReportBean());
    }


    public HttpView getTabView(String tabId) throws Exception
    {
        VBox view = new VBox();
        RReportBean form = getReportForm();

        if (TAB_SOURCE.equals(tabId))
        {
            JspView designer = new JspView<RReportBean>("/org/labkey/api/reports/report/view/scriptReportDesigner.jsp", form, form.getErrors());
            form.setRenderURL(getRenderAction());

            if (_report != null)
            {
                boolean isReadOnly = !_report.getDescriptor().canEdit(getViewContext().getUser(), getViewContext().getContainer());
                form.setReadOnly(isReadOnly);

                view.addView(designer);

                if (!isReadOnly)
                {
                    for (ReportService.ViewFactory vf : ReportService.get().getViewFactories())
                    {
                        view.addView(vf.createView(getViewContext(), form));
                    }
                }

                view.addView(new HttpView() {
                    protected void renderInternal(Object model, PrintWriter out) throws Exception
                    {
                        out.write("</form>");
                    }
                });
            }
            else
            {
                view.addView(new HtmlView("Unable to find the specified view"));
            }
        }
        else if (TAB_SYNTAX.equals(tabId))
        {
            view.addView(new JspView("/org/labkey/api/reports/report/view/rReportDesignerSyntaxRef.jsp"));
        }
        else if (TAB_VIEW.equals(tabId))
        {
            // for now limit pipeline view to saved reports
            if (null != form.getReportId() && form.isRunInBackground())
            {
                Report report = form.getReport();

                if (report instanceof RReport)
                {
                    view.addView(new JspView<RReport>("/org/labkey/api/reports/report/view/rReportRenderBackground.jsp", (RReport)report));

                    File logFile = new File(((RReport)report).getReportDir(), RReportJob.LOG_FILE_NAME);
                    PipelineStatusFile statusFile = PipelineService.get().getStatusFile(logFile.getAbsolutePath());

                    if (statusFile != null &&
                            !(statusFile.getStatus().equals(PipelineJob.WAITING_STATUS) ||
                              statusFile.getStatus().equals(RReportJob.PROCESSING_STATUS)))
                        view.addView(new RenderBackgroundRReportView((RReport)report));
                }
            }
            else
            {
                Report report = form.getReport();

                if (report != null)
                {
                    if (form.getIsDirty())
                        report.clearCache();
                    view.addView(report.renderReport(getViewContext()));
                }
            }
        }
        else if (TAB_DATA.equals(tabId))
        {
            Report report = form.getReport();
            if (report != null)
                view.addView(report.renderDataView(getViewContext()));
        }

        // add the view to manage tab and view dirty state
        JspView tabHandler = new JspView<RReportBean>("/org/labkey/api/reports/report/view/rReportTabHandler.jsp", form);
        view.addView(tabHandler);

        return view;
    }
}
