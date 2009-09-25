/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.query;

import org.labkey.api.query.QueryService;
import org.labkey.api.study.StudyImportException;
import org.labkey.api.study.ExternalStudyImporter;
import org.labkey.api.study.StudyContext;
import org.labkey.api.study.ExternalStudyImporterFactory;
import org.labkey.study.xml.StudyDocument;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 2:33:52 PM
 */
public class CustomViewImporter implements ExternalStudyImporter
{
    public String getDescription()
    {
        return "custom views";
    }

    public void process(StudyContext ctx, File root) throws IOException, SQLException, StudyImportException
    {
        StudyDocument.Study.Views viewsXml = ctx.getStudyXml().getViews();

        if (null != viewsXml)
        {
            File viewDir = ctx.getStudyDir(root, viewsXml.getDir());

            int count = QueryService.get().importCustomViews(ctx.getUser(), ctx.getContainer(), viewDir);

            ctx.getLogger().info(count + " custom view" + (1 == count ? "" : "s") + " imported");
        }
    }

    public void postProcess(StudyContext ctx, File root) throws Exception
    {
        //nothing for now
    }

    public static class Factory implements ExternalStudyImporterFactory
    {
        public ExternalStudyImporter create()
        {
            return new CustomViewImporter();
        }
    }
}