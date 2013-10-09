/*
 * Copyright (c) 2013 LabKey Corporation
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

import org.labkey.api.data.dialect.SqlDialect;

/**
* User: adam
* Date: 10/8/13
* Time: 10:57 PM
*/
public class PropertySchema
{
    private static final PropertySchema _instance = new PropertySchema();
    private static final String SCHEMA_NAME = "prop";

    public static PropertySchema getInstance()
    {
        return _instance;
    }

    PropertySchema()
    {
    }

    public String getSchemaName()
    {
        return SCHEMA_NAME;
    }

    public DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME);
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

    public TableInfo getTableInfoProperties()
    {
        return getSchema().getTable("Properties");
    }

    public TableInfo getTableInfoPropertyEntries()
    {
        return getSchema().getTable("PropertyEntries");
    }

    public TableInfo getTableInfoPropertySets()
    {
        return getSchema().getTable("PropertySets");
    }
}
