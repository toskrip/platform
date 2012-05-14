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
package org.labkey.api.visualization;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.reports.report.AbstractReport;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: May 9, 2012
 */

/**
 * Generic javascript report which uses the client side api's developed over d3/raphael to
 * create box, scatter, bar charts.
 */
public class GenericChartReport extends AbstractReport
{
    public static final String TYPE = "ReportService.GenericChartReport";

    public static enum RenderType
    {
        BOX_PLOT()
        {
            @Override
            public String getId()
            {
                return "box_plot";
            }
            @Override
            public String getName()
            {
                return "Box Plot";
            }
            @Override
            public String getDescription()
            {
                return "Box and Whisker Plot";
            }
        },
        SCATTER_PLOT()
        {
            @Override
            public String getId()
            {
                return "scatter_plot";
            }
            @Override
            public String getName()
            {
                return "Scatter Plot";
            }
            @Override
            public String getDescription()
            {
                return "XY Scatter Plot";
            }
        };
        public abstract String getId();
        public abstract String getName();
        public abstract String getDescription();
    }

    public static RenderType getRenderType(String typeId)
    {
        for (RenderType type : RenderType.values())
        {
            if (type.getId().equals(typeId))
                return type;
        }
        return null;
    }

    @Override
    public String getType()
    {
        return TYPE;
    }

    @Override
    public String getTypeDescription()
    {
        return "Generic Chart Report";
    }

    @Override
    public String getDescriptorType()
    {
        return GenericChartReportDescriptor.TYPE;
    }

    @Override
    public HttpView renderReport(ViewContext context) throws Exception
    {
        return null;
    }
}
