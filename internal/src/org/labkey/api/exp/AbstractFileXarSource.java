/*
 * Copyright (c) 2005-2008 LabKey Corporation
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

package org.labkey.api.exp;

import org.fhcrc.cpas.exp.xml.ExperimentArchiveDocument;
import org.labkey.api.util.NetworkDrive;
import org.apache.xmlbeans.XmlException;

import java.io.IOException;
import java.io.FileInputStream;
import java.io.File;

/**
 * User: jeckels
 * Date: Dec 2, 2005
 */
public abstract class AbstractFileXarSource extends XarSource
{
    protected File _xmlFile;

    public ExperimentArchiveDocument getDocument() throws XmlException, IOException
    {
        FileInputStream fIn = null;

        try
        {
            NetworkDrive.exists(_xmlFile);
            fIn = new FileInputStream(_xmlFile);
            return ExperimentArchiveDocument.Factory.parse(fIn);
        }
        finally
        {
            if (fIn != null)
            {
                try
                {
                    fIn.close();
                }
                catch (IOException e)
                {
                }
            }
        }
    }

    public File getRoot()
    {
        return _xmlFile.getParentFile();
    }

    public boolean shouldIgnoreDataFiles()
    {
        return false;
    }

    public String canonicalizeDataFileURL(String dataFileURL) throws XarFormatException
    {
        if (dataFileURL.startsWith("/") || dataFileURL.startsWith("\\"))
        {
            dataFileURL = dataFileURL.substring(1);
        }

        File xarDirectory = getRoot();
        File dataFile = new File(xarDirectory, dataFileURL);
        try
        {
            return dataFile.getCanonicalFile().toURI().toString();
        }
        catch (IOException e)
        {
            throw new XarFormatException(e);
        }
    }

    public static File getLogFileFor(File f) throws IOException
    {
        File xarDirectory = f.getParentFile();
        if (!xarDirectory.exists())
        {
            throw new IOException("Xar file parent directory does not exist");
        }

        String xarShortName = f.getName();
        int index = xarShortName.toLowerCase().lastIndexOf(".xml");
        if (index == -1)
        {
            index = xarShortName.toLowerCase().lastIndexOf(".xar");
        }
        if (index != -1)
        {
            xarShortName = xarShortName.substring(0, index);
        }
        return new File(xarDirectory, xarShortName + LOG_FILE_NAME_SUFFIX);

    }
}
