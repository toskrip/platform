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

import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.IdentifiableBase;

import java.util.Date;

/**
 * User: migra
 * Date: Jun 14, 2005
 * Time: 2:49:46 PM
 */
public class ProtocolApplication extends IdentifiableBase
{
    private int rowId;
    private String name;
    private String cpasType;
    private String protocolLSID;
    private Date activityDate;
    private Integer runId;
    private int actionSequence;
    private String comments;

    public int getRowId()
    {
        return rowId;
    }

    public void setRowId(int rowId)
    {
        this.rowId = rowId;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getCpasType()
    {
        return cpasType;
    }

    public void setCpasType(String cpasType)
    {
        // Make sure that we have a valid CpasType
        ExpProtocol.ApplicationType.valueOf(cpasType);
        this.cpasType = cpasType;
    }

    public String getProtocolLSID()
    {
        return protocolLSID;
    }

    public void setProtocolLSID(String protocolLSID)
    {
        this.protocolLSID = protocolLSID;
    }

    public Date getActivityDate()
    {
        return activityDate;
    }

    public void setActivityDate(Date activityDate)
    {
        this.activityDate = activityDate;
    }

    public Integer getRunId()
    {
        return runId;
    }

    public void setRunId(Integer runId)
    {
        this.runId = runId;
    }

    public int getActionSequence()
    {
        return actionSequence;
    }

    public void setActionSequence(int actionSequence)
    {
        this.actionSequence = actionSequence;
    }

    public String getComments()
    {
        return comments;
    }

    public void setComments(String comments)
    {
        this.comments = comments;
    }
}
