package org.labkey.visualization.sql;

import org.labkey.api.data.Aggregate;
import org.labkey.api.view.ViewContext;

import java.util.Map;

/**
* Copyright (c) 2011 LabKey Corporation
* <p/>
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* <p/>
* http://www.apache.org/licenses/LICENSE-2.0
* <p/>
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
* <p/>
* <p/>
* User: brittp
* Date: Jan 27, 2011 11:14:10 AM
*/
class VisualizationAggregateColumn extends VisualizationSourceColumn
{
    private Aggregate.Type _aggregate;

    VisualizationAggregateColumn(ViewContext context, Map<String, Object> properties)
    {
        super(context, properties);
        String aggregate = (String) properties.get("aggregate");
        if (aggregate == null)
            aggregate = "MAX";
        _aggregate = Aggregate.Type.valueOf(aggregate);
    }

    public Aggregate.Type getAggregate()
    {
        return _aggregate;
    }

    @Override
    public String getAlias()
    {
        return super.getAlias() + "_" + _aggregate.name();
    }
}
