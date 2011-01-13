/*
 * Copyright (c) 2009-2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.qc;

import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.property.DomainProperty;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: May 7, 2009
 */
public class DefaultTransformResult implements TransformResult
{
    private Map<ExpData, List<Map<String, Object>>> _dataMap = Collections.emptyMap();
    private Map<DomainProperty, String> _batchProperties = Collections.emptyMap();
    private Map<DomainProperty, String> _runProperties = Collections.emptyMap();
    private File _uploadedFile;

    public DefaultTransformResult(){}

    public DefaultTransformResult(Map<ExpData, List<Map<String, Object>>> dataMap)
    {
        _dataMap = dataMap;
    }

    public Map<ExpData, List<Map<String, Object>>> getTransformedData()
    {
        return _dataMap;
    }

    public Map<DomainProperty, String> getBatchProperties()
    {
        return _batchProperties;
    }

    public void setBatchProperties(Map<DomainProperty, String> batchProperties)
    {
        _batchProperties = batchProperties;
    }

    public Map<DomainProperty, String> getRunProperties()
    {
        return _runProperties;
    }

    public void setRunProperties(Map<DomainProperty, String> runProperties)
    {
        _runProperties = runProperties;
    }

    public File getUploadedFile()
    {
        return _uploadedFile;
    }

    public void setUploadedFile(File uploadedFile)
    {
        _uploadedFile = uploadedFile;
    }

    public static TransformResult createEmptyResult()
    {
        return new DefaultTransformResult();
/*
        return new TransformResult()
        {
            public Map<DataType, File> getTransformedData()
            {
                return Collections.emptyMap();
            }
            public boolean isEmpty() {return true;}
        };
*/
    }
}
