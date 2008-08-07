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

package org.labkey.api.reports.report.view;

import org.labkey.api.reports.Report;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.HtmlView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Feb 5, 2008
 */
public class RunChartReportView extends RunReportView
{
    private Report _report;
    protected int _reportId;

    public RunChartReportView(Report report)
    {
        _report = report;
        if (_report != null)
            _reportId = _report.getDescriptor().getReportId();
    }
    
    protected Report getReport() throws Exception
    {
        return _report;
    }

    protected List<TabInfo> getTabList()
    {
        ActionURL url = getViewContext().cloneActionURL();

        List<TabInfo> tabs = new ArrayList<TabInfo>();
        tabs.add(new TabInfo(TAB_VIEW, TAB_VIEW, url));
        tabs.add(new TabInfo(TAB_DATA, TAB_DATA, url));

        return tabs;
    }

    protected HttpView getTabView(String tabId) throws Exception
    {
        if (TAB_VIEW.equals(tabId))
        {
            ActionURL url = ReportUtil.getPlotChartURL(getViewContext(), getReport());
            return new HtmlView("<img src='" + url.getLocalURIString() + "'>");
        }
        else if (TAB_DATA.equals(tabId))
        {
            return getReport().renderDataView(getViewContext());
        }
        return null; 
    }
}
