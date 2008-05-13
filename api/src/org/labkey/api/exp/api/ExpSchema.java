/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.api.exp.api;

import org.labkey.api.query.*;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.security.User;

import java.util.*;

public class ExpSchema extends UserSchema
{
    private boolean _restrictContainer = true;

    public static final String EXPERIMENTS_NARROW_WEB_PART_TABLE_NAME = ExpSchema.TableType.Experiments + "NarrowWebPart";
    public static final String EXPERIMENTS_MEMBERSHIP_FOR_RUN_TABLE_NAME = ExpSchema.TableType.Experiments + "MembershipForRun";

    public enum TableType
    {
        Runs
        {
            public TableInfo createTable(String alias, ExpSchema expSchema)
            {
                return expSchema.createRunsTable(alias);
            }
        },
        Datas
        {
            public TableInfo createTable(String alias, ExpSchema expSchema)
            {
                return expSchema.createDatasTable(alias);
            }
        },
        Materials
        {
            public TableInfo createTable(String alias, ExpSchema expSchema)
            {
                return expSchema.createMaterialsTable(alias);
            }
        },
        Protocols
        {
            public TableInfo createTable(String alias, ExpSchema expSchema)
            {
                return expSchema.createProtocolsTable(alias);
            }
        },
        SampleSets
        {
            public TableInfo createTable(String alias, ExpSchema expSchema)
            {
                return expSchema.createSampleSetTable(alias);
            }
        },
        Experiments
        {
            public TableInfo createTable(String alias, ExpSchema expSchema)
            {
                return expSchema.createExperimentsTable(alias);
            }
        };

        public abstract TableInfo createTable(String alias, ExpSchema expSchema);
    }

    public ExpExperimentTable createExperimentsTable(String alias)
    {
        ExpExperimentTable ret = ExperimentService.get().createExperimentTable(alias);
        ret.setContainer(getContainer());
        ret.populate(this);
        return ret;
    }
    
    public ExpExperimentTable createExperimentsTableWithRunMemberships(String alias, ExpRun run)
    {
        ExpExperimentTable ret = createExperimentsTable(alias);
        ret.getColumn(ExpExperimentTable.Column.RunCount).setIsHidden(true);

        ret.addExperimentMembershipColumn(run);
        List<FieldKey> defaultCols = new ArrayList<FieldKey>(ret.getDefaultVisibleColumns());
        defaultCols.add(0, FieldKey.fromParts("RunMembership"));
        defaultCols.remove(FieldKey.fromParts(ExpExperimentTable.Column.RunCount.name()));
        ret.setDefaultVisibleColumns(defaultCols);

        return ret;
    }

    static private Set<String> tableNames = new LinkedHashSet<String>();
    static
    {
        for (TableType type : TableType.values())
        {
            tableNames.add(type.toString());
        }
        tableNames = Collections.unmodifiableSet(tableNames);
    }


    public static final String SCHEMA_NAME = "exp";

    static public void register()
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider() {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                return new ExpSchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public SamplesSchema getSamplesSchema()
    {
        return new SamplesSchema(getUser(), getContainer());
    }

    public ExpSchema(User user, Container container)
    {
        super(SCHEMA_NAME, user, container, ExperimentService.get().getSchema());
    }

    public Set<String> getTableNames()
    {
        return tableNames;
    }

    public TableInfo getTable(String name, String alias)
    {
        try
        {
            return TableType.valueOf(name).createTable(alias, this);
        }
        catch (IllegalArgumentException e)
        {
            // ignore
        }

        // TODO - find a better way to do this. We want to have different sets of views for the experiments table,
        // so this is a hacky way to make sure that customizing one set of views doesn't affect the other.
        if (EXPERIMENTS_NARROW_WEB_PART_TABLE_NAME.equalsIgnoreCase(name))
        {
            return createExperimentsTable(alias);
        }
        if (EXPERIMENTS_MEMBERSHIP_FOR_RUN_TABLE_NAME.equalsIgnoreCase(name))
        {
            return createExperimentsTableWithRunMemberships(alias, null);
        }

        return super.getTable(name, alias);
    }

    public ExpDataTable createDatasTable(String alias)
    {
        ExpDataTable ret = ExperimentService.get().createDataTable(alias);
        ret.populate(this);
        return ret;
    }

    public ExpSampleSetTable createSampleSetTable(String alias)
    {
        ExpSampleSetTable ret = ExperimentService.get().createSampleSetTable(alias);
        ret.populate(this);
        return ret;
    }

    public ExpMaterialTable createMaterialsTable(String alias)
    {
        return getSamplesSchema().getSampleTable(alias, null);
    }

    public ExpRunTable createRunsTable(String alias)
    {
        ExpRunTable ret = ExperimentService.get().createRunTable(alias);
        ret.populate(this);
        return ret;
    }

    public ExpProtocolTable createProtocolsTable(String alias)
    {
        ExpProtocolTable ret = ExperimentService.get().createProtocolTable(alias);
        ret.populate(this);
        return ret;
    }

    public ForeignKey getProtocolLSIDForeignKey()
    {
        return new LookupForeignKey("LSID")
        {
            public TableInfo getLookupTableInfo()
            {
                boolean restrictContainer = isRestrictContainer();
                setRestrictContainer(false);
                ExpProtocolTable protocolTable = createProtocolsTable("lookup");
                setRestrictContainer(restrictContainer);
                return protocolTable;
            }
        };
    }

    public ForeignKey getRunIdForeignKey()
    {
        return new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return createRunsTable("lookup");
            }
        };
    }

    public ForeignKey getDataIdForeignKey()
    {
        return new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return createDatasTable("lookup");
            }
        };
    }

    public ForeignKey getMaterialIdForeignKey()
    {
        return new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return createMaterialsTable("lookup");
            }
        };
    }

    public ForeignKey getRunLSIDForeignKey()
    {
        return new LookupForeignKey("LSID")
        {
            public TableInfo getLookupTableInfo()
            {
                return createRunsTable("lookup");
            }
        };
    }


    public boolean isRestrictContainer()
    {
        return _restrictContainer;
    }

    public void setRestrictContainer(boolean restrictContainer)
    {
        _restrictContainer = restrictContainer;
    }
}
