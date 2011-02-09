package org.labkey.api.visualization;

import org.labkey.api.reports.report.ReportDescriptor;

/**
 * Copyright (c) 2011 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 * <p/>
 * User: brittp
 * Date: Feb 3, 2011 11:15:10 AM
 */
public class VisualizationReportDescriptor extends ReportDescriptor
{
    public static final String JSON_PROPERTY = "json";

    public String getJSON()
    {
        return getProperty(JSON_PROPERTY);
    }

    public void setJSON(String json)
    {
        setProperty(JSON_PROPERTY, json);
    }
}
