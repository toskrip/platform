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

package org.labkey.study.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.OORDisplayColumnFactory;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.query.*;
import org.labkey.api.util.CaseInsensitiveHashSet;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.controllers.StudyController;

import javax.servlet.ServletException;
import java.util.*;

public class DataSetTable extends FilteredTable
{
    StudyQuerySchema _schema;
    DataSetDefinition _dsd;
    TableInfo _fromTable;

    public DataSetTable(StudyQuerySchema schema, DataSetDefinition dsd) throws ServletException
    {
        super(dsd.getTableInfo(schema.getUser(), schema.getMustCheckPermissions(), false));
        _schema = schema;
        _dsd = dsd;
        ColumnInfo pvColumn = new ParticipantVisitColumn(
                "ParticipantVisit",
                new AliasedColumn(this, "PVParticipant", getRealTable().getColumn("ParticipantId")),
                new AliasedColumn(this, "PVVisit", getRealTable().getColumn("SequenceNum")));
        addColumn(pvColumn);
        pvColumn.setFk(new LookupForeignKey("ParticipantVisit")
        {
            public TableInfo getLookupTableInfo()
            {
                return new ParticipantVisitTable(_schema, null);
            }
        });

        List<FieldKey> defaultVisibleCols = new ArrayList<FieldKey>();

        HashSet<String> standardURIs = new HashSet<String>();
        for (PropertyDescriptor pd :  DataSetDefinition.getStandardPropertiesSet())
            standardURIs.add(pd.getPropertyURI());

        for (ColumnInfo baseColumn : getRealTable().getColumns())
        {
            String name = baseColumn.getName();
            if ("ParticipantId".equalsIgnoreCase(name))
            {
                ColumnInfo column = new AliasedColumn(this, "ParticipantId", baseColumn);
                //column.setFk(new QueryForeignKey(_schema, "Participant", "RowId", "RowId"));

                column.setFk(new QueryForeignKey(_schema, "Participant", "ParticipantId", "ParticipantId")
                {
                    public StringExpressionFactory.StringExpression getURL(ColumnInfo parent) {
                        ActionURL base = new ActionURL(StudyController.ParticipantAction.class, _schema.getContainer());
                        base.addParameter(DataSetDefinition.DATASETKEY, Integer.toString(_dsd.getDataSetId()));

                        Map params = new HashMap();
                        params.put("participantId", parent);

                        return new LookupURLExpression(base, params);
                    }
                });
                addColumn(column);
                if (isVisibleByDefault(column))
                    defaultVisibleCols.add(FieldKey.fromParts(column.getName()));
            }
            else if (getRealTable().getColumn(baseColumn.getName() + OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX) != null)
            {
                // If this is the value column that goes with an OORIndicator, add the special OOR options
                OORDisplayColumnFactory.addOORColumns(this, baseColumn, getRealTable().getColumn(baseColumn.getName() +
                        OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX));
                if (isVisibleByDefault(baseColumn))
                    defaultVisibleCols.add(FieldKey.fromParts(baseColumn.getName()));
            }
            else if (baseColumn.getName().toLowerCase().endsWith(OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX.toLowerCase()) &&
                    getRealTable().getColumn(baseColumn.getName().substring(0, baseColumn.getName().length() - OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX.length())) != null)
            {
                // If this is an OORIndicator and there's a matching value column in the same table, don't add this column
            }
            else if (baseColumn.getName().equalsIgnoreCase("SequenceNum") && _schema.getStudy().isDateBased())
            {
                addWrapColumn(baseColumn);
                //Don't add to visible cols...
            }
            else
            {
                ColumnInfo col = addWrapColumn(baseColumn);
                String propertyURI = col.getPropertyURI();
                if (null != propertyURI && !standardURIs.contains(propertyURI))
                {
                    PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(propertyURI, schema.getContainer());
                    if (null != pd && pd.getLookupQuery() != null)
                        col.setFk(new PdLookupForeignKey(schema.getUser(), pd));
                }
                if (isVisibleByDefault(col))
                    defaultVisibleCols.add(FieldKey.fromParts(col.getName()));
            }
        }
        ColumnInfo lsidColumn = getColumn("LSID");
        lsidColumn.setIsHidden(true);
        lsidColumn.setKeyField(true);
        getColumn("SourceLSID").setIsHidden(true);
        setDefaultVisibleColumns(defaultVisibleCols);
    }

    private static final Set<String> defaultHiddenCols = new CaseInsensitiveHashSet("VisitRowId", "Created", "Modified", "lsid");
    private boolean isVisibleByDefault(ColumnInfo col)
    {
        return (!col.isHidden() && !col.isUnselectable() && !defaultHiddenCols.contains(col.getName()));
    }


    protected TableInfo getFromTable()
    {
        try
        {
            if (_fromTable == null)
            {
                _fromTable = _dsd.getTableInfo(_schema.getUser(), _schema.getMustCheckPermissions(), true);
            }
            return _fromTable;
        }
        catch (ServletException se)
        {
            throw UnexpectedException.wrap(se);
        }
    }

    public DataSetDefinition getDatasetDefinition()
    {
        return _dsd;
    }

    /**
     * In order to discourage the user from selecting data from deeply nested datasets, we hide
     * the "ParticipantID" and "ParticipantVisit" columns when the user could just as easily find
     * the same data further up the tree.
     */
    public void hideParticipantLookups()
    {
        getColumn("ParticipantID").setIsHidden(true);
        getColumn("ParticipantVisit").setIsHidden(true);
    }

}
