/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

package org.labkey.api.study.assay;

import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.data.Container;

import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.*;

/**
 * User: jeckels
 * Date: Jan 2, 2008
 */
public class PipelineDataCollector<ContextType extends AssayRunUploadContext> extends AbstractAssayDataCollector<ContextType>
{
    public static final String FILE_COLLECTION_ID_PARAMETER_NAME = ".fileCollectionId";

    public PipelineDataCollector()
    {
    }

    public String getHTML(ContextType context) throws ExperimentException
    {
        Map<String, File> files = getCurrentFilesForDisplay(context);
        if (files.isEmpty())
        {
            return "<div class=\"labkey-error\">No files have been selected.</div>";
        }

        StringBuilder sb = new StringBuilder();
        for (File file : files.values())
        {
            sb.append("<li>");
            sb.append(PageFlowUtil.filter(file.getName()));
            sb.append("</li>");
        }
        sb.append("</ul>");
        int additionalSets = getAdditionalFileSetCount(context);
        if (additionalSets > 0)
        {
            sb.append(" (");
            sb.append(additionalSets);
            sb.append(" more file set");
            sb.append(additionalSets > 1 ? "s" : "");
            sb.append(" available after this run is complete.)");
        }
        return sb.toString();
    }

    /**
     * @return the number of additional files available for uploading
     */
    protected int getAdditionalFileSetCount(ContextType context)
    {
        return getFileCollection(context).size() - 1;
    }

    /** @return the files to be processed for the current upload attempt */
    protected Map<String, File> getCurrentFilesForDisplay(ContextType context)
    {
        List<Map<String, File>> files = getFileCollection(context);
        if (files.isEmpty())
        {
            return Collections.emptyMap();
        }
        return files.get(0);
    }

    public String getShortName()
    {
        return "Pipeline";
    }

    public String getDescription(ContextType context)
    {
        List<Map<String, File>> allFiles = getFileCollection(context);
        if (allFiles.isEmpty())
        {
            return "";
        }
        Map<String, File> files = allFiles.get(0);
        return (files.size() > 1 ? files.size() + " files" : "File ") + " from the Data Pipeline in " + files.values().iterator().next().getParent();
    }

    public static synchronized void setFileCollection(HttpSession session, Container c, ExpProtocol protocol, List<Map<String, File>> files)
    {
        List<Map<String, File>> existingFiles = getFileCollection(session, c, protocol);
        existingFiles.clear();
        existingFiles.addAll(files);
    }

    public List<Map<String, File>> getFileCollection(ContextType context)
    {
        return getFileCollection(context.getRequest().getSession(true), context.getContainer(), context.getProtocol());
    }

    private static List<Map<String, File>> getFileCollection(HttpSession session, Container c, ExpProtocol protocol)
    {
        Map<Pair<Container, Integer>, List<Map<String, File>>> collections = (Map<Pair<Container, Integer>, List<Map<String, File>>>) session.getAttribute(PipelineDataCollector.class.getName());
        if (collections == null)
        {
            collections = new HashMap<Pair<Container, Integer>, List<Map<String, File>>>();
            session.setAttribute(PipelineDataCollector.class.getName(), collections);
        }
        Pair<Container, Integer> key = new Pair<Container, Integer>(c, protocol.getRowId());
        List<Map<String, File>> result = collections.get(key);
        if (result == null)
        {
            result = new ArrayList<Map<String, File>>();
            collections.put(key, result);
        }
        return result;
    }

    public Map<String, File> createData(ContextType context) throws IOException
    {
        List<Map<String, File>> files = getFileCollection(context);
        if (files.isEmpty())
        {
            throw new FileNotFoundException("No files from the pipeline directory have been selected");
        }
        return files.get(0);
    }

    public boolean isVisible()
    {
        return true;
    }

    public void uploadComplete(ContextType context)
    {
        List<Map<String, File>> files = getFileCollection(context);
        if (!files.isEmpty())
        {
            files.remove(0);
        }
    }

    public boolean allowAdditionalUpload(ContextType context)
    {
        return getFileCollection(context).size() > 1; 
    }
}
