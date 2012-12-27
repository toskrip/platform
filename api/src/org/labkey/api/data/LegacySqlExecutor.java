/*
 * Copyright (c) 2011-2012 LabKey Corporation
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

import java.sql.SQLException;

/**
 * User: adam
 * Date: 10/26/11
 * Time: 12:05 AM
 */
public class LegacySqlExecutor
{
    private final SqlExecutor _executor;

    public LegacySqlExecutor(DbSchema schema)
    {
        _executor = new SqlExecutor(schema);
        _executor.setExceptionFramework(ExceptionFramework.JDBC);
    }

    public int execute(SQLFragment sql) throws SQLException
    {
        try
        {
            return _executor.execute(sql);
        }
        catch (RuntimeSQLException e)
        {
            throw e.getSQLException();
        }
    }

    public int execute(String sql, Object... params) throws SQLException
    {
        try
        {
            return _executor.execute(sql, params);
        }
        catch (RuntimeSQLException e)
        {
            throw e.getSQLException();
        }
    }
}