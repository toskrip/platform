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
package org.labkey.experiment.api;

import org.labkey.api.exp.IdentifiableBase;
import org.labkey.api.data.Container;

import java.util.Date;

/**
 * Output of a protocol, like a data file or a material
 * User: jeckels
 * Date: Oct 17, 2005
 */
public abstract class ProtocolOutput extends IdentifiableBase
{
    private Container _container;
    private Integer _runId;
    private int _rowId;
    private Date _created;
    private String _cpasType;
    private Integer _sourceApplicationId;

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        this._rowId = rowId;
    }

    public void setContainer(Container parent)
    {
        this._container = parent;
    }

    public Container getContainer()
    {
        return _container;
    }

    public Integer getRunId()
    {
        return _runId;
    }

    public void setRunId(Integer runId)
    {
        this._runId = runId;
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        this._created = created;
    }

    public String getCpasType()
    {
        return _cpasType;
    }

    public void setCpasType(String cpasType)
    {
        _cpasType = cpasType;
    }

    public Integer getSourceApplicationId()
    {
        return _sourceApplicationId;
    }

    public void setSourceApplicationId(Integer sourceApplicationId)
    {
        this._sourceApplicationId = sourceApplicationId;
    }

    public int hashCode()
    {
        return _rowId + getClass().hashCode();
    }
}
