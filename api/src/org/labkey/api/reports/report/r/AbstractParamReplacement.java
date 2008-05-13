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

package org.labkey.api.reports.report.r;

import org.labkey.api.view.HttpView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.FileUtil;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.StrutsAttachmentFile;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.Report;
import org.labkey.common.tools.TabLoader;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.struts.upload.FormFile;

import java.io.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: May 5, 2008
 */
public abstract class AbstractParamReplacement implements ParamReplacement
{
    protected String _id;
    protected String _name;
    protected File _file;
    protected Report _report;
    protected boolean _headerVisible = true;

    public AbstractParamReplacement(String id)
    {
        _id = id;
    }

    public String getId()
    {
        return _id;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public File getFile()
    {
        return _file;
    }

    public void setFile(File file)
    {
        _file = file;
    }

    public Report getReport()
    {
        return _report;
    }

    public void setReport(Report report)
    {
        _report = report;
    }

    public void setHeaderVisible(boolean headerVisible)
    {
        _headerVisible = headerVisible;
    }

    public boolean getHeaderVisible()
    {
        return _headerVisible;
    }

    public String toString()
    {
        return getName() + " (" + getId() + ")";
    }
}
