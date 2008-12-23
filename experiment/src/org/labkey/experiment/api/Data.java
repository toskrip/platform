/*
 * Copyright (c) 2005-2008 Fred Hutchinson Cancer Research Center
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
package org.labkey.experiment.api;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URI;

/**
 * User: migra
 * Date: Jun 14, 2005
 * Time: 3:12:17 PM
 */
public class Data extends ProtocolOutput
{
    private String dataFileUrl;

    public Data()
    {
        setCpasType("Data");
    }

    public String getDataFileUrl()
    {
        return dataFileUrl;
    }

    public void setDataFileUrl(String dataFileUrl)
    {
        this.dataFileUrl = dataFileUrl;
    }

    public File getFile()
    {
        if (getDataFileUrl() == null)
        {
            return null;
        }
        
        try
        {
            return new File(new URI(getDataFileUrl()));
        }
        catch (URISyntaxException e)
        {
            throw new IllegalArgumentException(e);
        }
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Data data = (Data) o;

        return !(getRowId() == 0 || getRowId() != data.getRowId());
    }
}
