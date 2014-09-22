/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.api.data;


import org.labkey.api.audit.AuditTypeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class SelectQueryAuditEvent extends AuditTypeEvent
{
    private String _loggedColumns;
    private String _identifiedData;
    private Integer _queryId;

    public SelectQueryAuditEvent()
    {
        super();
        setEventType(SelectQueryAuditProvider.EVENT_NAME);
    }

    public SelectQueryAuditEvent(Container container, String comment)
    {
        super(SelectQueryAuditProvider.EVENT_NAME, container.getId(), comment);
    }

    public SelectQueryAuditEvent(QueryLogging queryLogging, Set<String> dataLoggingValues)
    {
        this(queryLogging.getContainer(), queryLogging.getComment());

        List<ColumnLogging> sortedLoggings = new ArrayList<>(queryLogging.getColumnLoggings());
        Collections.sort(sortedLoggings);

        StringBuilder loggedColumns = new StringBuilder();
        String sep = "";
        for (ColumnLogging logging : sortedLoggings)
        {
            loggedColumns.append(sep).append(logging.getOriginalTableName())
                    .append(".").append(logging.getOriginalColumnFieldKey());
            sep = ", ";
        }

        List<String> sortedDataLoggingValues = new ArrayList<>(dataLoggingValues);
        Collections.sort(sortedDataLoggingValues);

        StringBuilder dataColumns = new StringBuilder();
        sep = "";
        for (String dataLoggingValue : sortedDataLoggingValues)
        {
            dataColumns.append(sep).append(dataLoggingValue);
            sep = ", ";
        }
        _loggedColumns = loggedColumns.toString();
        _identifiedData = dataColumns.toString();
        _queryId = queryLogging.getQueryId();
    }

    public String getLoggedColumns()
    {
        return _loggedColumns;
    }

    public void setLoggedColumns(String loggedColumns)
    {
        _loggedColumns = loggedColumns;
    }

    public String getIdentifiedData()
    {
        return _identifiedData;
    }

    public void setIdentifiedData(String identifiedData)
    {
        _identifiedData = identifiedData;
    }

    public Integer getQueryId()
    {
        return _queryId;
    }

    public void setQueryId(Integer queryId)
    {
        _queryId = queryId;
    }
}
