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

public class SqlSelector extends BaseSelector<SqlSelector.SimpleSqlFactory, SqlSelector>
{
    private final SQLFragment _sql;

    // Execute select SQL against a scope
    public SqlSelector(DbScope scope, SQLFragment sql)
    {
        super(scope);
        _sql = sql;
    }

    // Execute select SQL against a scope
    public SqlSelector(DbScope scope, String sql)
    {
        this(scope, new SQLFragment(sql));
    }

    // Execute select SQL against a schema
    public SqlSelector(DbSchema schema, SQLFragment sql)
    {
        this(schema.getScope(), sql);
    }

    // Execute select SQL against a schema; simple query with no parameters
    public SqlSelector(DbSchema schema, String sql)
    {
        this(schema.getScope(), sql);
    }

    // Execute select SQL against a schema; simple query with no parameters
    public SqlSelector(DbSchema schema, String sql, Object... params)
    {
        this(schema.getScope(), new SQLFragment(sql, params));
    }

    @Override
    protected SqlSelector getThis()
    {
        return this;
    }

    @Override
    protected SimpleSqlFactory getSqlFactory()
    {
        return new SimpleSqlFactory();
    }


    public class SimpleSqlFactory extends BaseSqlFactory
    {
        @Override
        public SQLFragment getSql()
        {
            return _sql;
        }
    }
}
