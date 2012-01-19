/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

package org.labkey.list.model;

import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.study.ExternalStudyWriter;
import org.labkey.api.study.ExternalStudyWriterFactory;
import org.labkey.api.study.Study;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.xml.StudyDocument;

/*
* User: adam
* Date: Aug 25, 2009
* Time: 10:11:16 AM
*/
public class StudyListWriter implements ExternalStudyWriter
{
    private static final String DEFAULT_DIRECTORY = "lists";

    public String getSelectionText()
    {
        return "Lists";
    }

    public void write(Study study, ImportContext<StudyDocument.Study> ctx, VirtualFile root) throws Exception
    {
        Container c = ctx.getContainer();

        VirtualFile listsDir = root.getDir(DEFAULT_DIRECTORY);

        ListWriter listWriter = new ListWriter();

        if (listWriter.write(c, ctx.getUser(), listsDir))
            ctx.getXml().addNewLists().setDir(DEFAULT_DIRECTORY);
    }

    public static class Factory implements ExternalStudyWriterFactory
    {
        public ExternalStudyWriter create()
        {
            return new StudyListWriter();
        }
    }
}