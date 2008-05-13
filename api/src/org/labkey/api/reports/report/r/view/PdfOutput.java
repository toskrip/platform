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

package org.labkey.api.reports.report.r.view;

import org.labkey.api.reports.report.r.AbstractParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.Report;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.HtmlView;
import org.labkey.api.attachments.AttachmentParent;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: May 7, 2008
 */
public class PdfOutput extends AbstractParamReplacement
{
    public static final String ID = "pdfout:";

    public PdfOutput()
    {
        super(ID);
    }
    
    public File convertSubstitution(File directory) throws Exception
    {
        if (directory != null)
            _file = new File(directory, getName().concat(".pdf"));

        return _file;
    }

    public HttpView render(ViewContext context)
    {
        if (getReport() instanceof AttachmentParent)
            return new PdfReportView(this, (AttachmentParent)getReport());
        else
            return new HtmlView("Unable to render this output, no report associated with this replacement param");
    }

    public static class PdfReportView extends DownloadOutputView
    {
        PdfReportView(ParamReplacement param, AttachmentParent parent)
        {
            super(param, parent, "PDF");
        }
    }
}
