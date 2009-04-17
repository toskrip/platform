/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
package org.labkey.api.reports.report;

import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.common.util.Pair;
import org.w3c.dom.Document;
import org.apache.xml.serialize.XMLSerializer;
import org.apache.xml.serialize.OutputFormat;

import java.io.*;

/**
 * User: migra
 * Date: Mar 6, 2006
 * Time: 7:55:56 PM
 */
public abstract class AbstractReport implements Report
{
    private ReportDescriptor _descriptor;

    public String getDescriptorType()
    {
        return ReportDescriptor.TYPE;
    }

    public ReportIdentifier getReportId()
    {
        return getDescriptor().getReportId();
    }

    public void setReportId(ReportIdentifier reportId)
    {
        getDescriptor().setReportId(reportId);
    }

    public void beforeSave(ViewContext context){}
    public void beforeDelete(ViewContext context){}

    public ReportDescriptor getDescriptor()
    {
        if (_descriptor == null)
        {
            _descriptor = ReportService.get().createDescriptorInstance(getDescriptorType());
            _descriptor.setReportType(getType());
        }
        return _descriptor;
    }

    public void setDescriptor(ReportDescriptor descriptor)
    {
        _descriptor = descriptor;
    }

    public ActionURL getRunReportURL(ViewContext context)
    {
        return ReportUtil.getRunReportURL(context, this);
    }

    public ActionURL getEditReportURL(ViewContext context)
    {
        return null;
    }

    public HttpView renderDataView(ViewContext context) throws Exception
    {
        return new HtmlView("No Data view available for this report");
    }

    public HttpView getRunReportView(ViewContext context) throws Exception
    {
        return renderReport(context);
    }

    public ActionURL getDownloadDataURL(ViewContext context)
    {
        ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlDownloadData(context.getContainer());

        for (Pair<String, String> param : context.getActionURL().getParameters())
        {
            url.replaceParameter(param.getKey(), param.getValue());
        }
        url.replaceParameter(ReportDescriptor.Prop.reportType.toString(), getDescriptor().getReportType());
        url.replaceParameter(ReportDescriptor.Prop.schemaName, getDescriptor().getProperty(ReportDescriptor.Prop.schemaName));
        url.replaceParameter(ReportDescriptor.Prop.queryName, getDescriptor().getProperty(ReportDescriptor.Prop.queryName));
        url.replaceParameter(ReportDescriptor.Prop.viewName, getDescriptor().getProperty(ReportDescriptor.Prop.viewName));
        url.replaceParameter(ReportDescriptor.Prop.dataRegionName, getDescriptor().getProperty(ReportDescriptor.Prop.dataRegionName));

        return url;
    }

    public void clearCache()
    {
    }

    public void serialize(Writer writer) throws IOException
    {
        ReportDescriptor descriptor = getDescriptor();
        if (descriptor.getReportId() != null)
        {
            Document doc = descriptor.toXML();

            OutputFormat format = new OutputFormat(doc);
            format.setIndenting(true);
            XMLSerializer serializer = new XMLSerializer(writer, format);

            serializer.asDOMSerializer();
            serializer.serialize(doc);
        }
        else
            throw new IllegalArgumentException("Cannot serialize a report that hasn't been saved yet");
    }

    public void serializeToFolder(File directory) throws IOException
    {
        ReportDescriptor descriptor = getDescriptor();

        if (descriptor.getReportId() != null)
        {
            String fileName = String.format("%s.%s.report.xml", descriptor.getReportName(), descriptor.getReportId());
            fileName = FileUtil.makeLegalName(fileName);
            FileWriter writer = null;
            try {
                writer = new FileWriter(new File(directory, fileName));
                serialize(writer);
            }
            finally
            {
                if (writer != null)
                    try {writer.close();} catch(IOException ioe) {}
            }
        }
        else
            throw new IllegalArgumentException("Cannot serialize a report that hasn't been saved yet");
    }

    public void afterDeserializeFromFile(File reportFile) throws IOException
    {
    }
}
