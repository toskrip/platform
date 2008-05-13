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
package org.labkey.api.exp.pipeline;

import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.TaskId;

/**
 * <code>XarGeneratorFactorySettings</code>
 */
public class XarGeneratorFactorySettings extends AbstractTaskFactorySettings
{
    // CONSIDER: All necessary inputs?
    private String _inputExt;
    private String _outputExt;

    public XarGeneratorFactorySettings(String name)
    {
        super(new TaskId(XarGeneratorId.class, name));
    }

    public TaskId getCloneId()
    {
        return new TaskId(XarGeneratorId.class);
    }

    public String getInputExt()
    {
        return _inputExt;
    }

    public void setInputExt(String inputExt)
    {
        _inputExt = inputExt;
    }

    public String getOutputExt()
    {
        return _outputExt;
    }

    public void setOutputExt(String outputExt)
    {
        _outputExt = outputExt;
    }
}
