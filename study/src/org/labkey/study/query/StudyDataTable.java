/*
 * Copyright (c) 2010 LabKey Corporation
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

import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.query.*;
import org.labkey.api.study.StudyService;
import org.labkey.api.view.ActionURL;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.StudyImpl;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * User: kevink
 * Date: Jan 11, 2010 4:31:18 PM
 *
 * Provides a Query-ized TableInfo that contains every row from every dataset in the study.
 * Eventually merge with StudyUnionTableInfo.
 */
public class StudyDataTable extends FilteredTable
{
    StudyQuerySchema _schema;

    public StudyDataTable(StudyQuerySchema schema)
    {
        super(StudySchema.getInstance().getTableInfoStudyData(schema.getStudy(), schema.getUser()), schema.getContainer());
        _schema = schema;

        List<FieldKey> defaultColumns = new LinkedList<FieldKey>();

        ColumnInfo datasetColumn = new AliasedColumn(this, "DataSet", _rootTable.getColumn("DataSet"));
        datasetColumn.setFk(new LookupForeignKey(getDataSetURL(), "entityId", "entityId", "Name") {
            public TableInfo getLookupTableInfo()
            {
                return new DataSetsTable(_schema);
            }
        });
        addColumn(datasetColumn);
        defaultColumns.add(FieldKey.fromParts("DataSet"));

        String subjectColName = StudyService.get().getSubjectColumnName(getContainer());
        ColumnInfo participantIdColumn = new AliasedColumn(this, subjectColName, _rootTable.getColumn("participantid"));
        participantIdColumn.setFk(new QueryForeignKey(_schema, StudyService.get().getSubjectTableName(getContainer()), subjectColName, null));
        addColumn(participantIdColumn);
        defaultColumns.add(FieldKey.fromParts(subjectColName));

        if (null != _rootTable.getColumn("Date"))
        {
            ColumnInfo dateColumn = new AliasedColumn(this, "Date", _rootTable.getColumn("_visitdate"));
            addColumn(dateColumn);
            defaultColumns.add(FieldKey.fromParts("Date"));
        }

//        ColumnInfo createdColumn = new AliasedColumn(this, "Created", _rootTable.getColumn("Created"));
//        addColumn(createdColumn);
//
//        ColumnInfo modifiedColumn = new AliasedColumn(this, "Modified", _rootTable.getColumn("Modified"));
//        addColumn(modifiedColumn);
//
        ColumnInfo lsidColumn = new AliasedColumn(this, "LSID", _rootTable.getColumn("lsid"));
        lsidColumn.setHidden(true);
        addColumn(lsidColumn);

        ColumnInfo sourceLsidColumn = new AliasedColumn(this, "Source LSID", _rootTable.getColumn("sourceLsid"));
        sourceLsidColumn.setHidden(true);
        addColumn(sourceLsidColumn);

        ColumnInfo sequenceNumColumn = new AliasedColumn(this, "SequenceNum", _rootTable.getColumn("SequenceNum"));
        sequenceNumColumn.setHidden(true);
        sequenceNumColumn.setMeasure(false);
        addColumn(sequenceNumColumn);

        StudyImpl study = _schema.getStudy();
        if (study != null)
        {
            PropertyDescriptor[] pds = study.getSharedProperties();

            for (PropertyDescriptor pd : pds)
            {
                addWrapColumn(_rootTable.getColumn(pd.getName()));
                defaultColumns.add(FieldKey.fromParts(pd.getName()));
            }
        }

        setDefaultVisibleColumns(defaultColumns);

        setDescription("Contains one row for every row in every dataset in this folder with the columns that are common " +
                "across all the datasets");

        Map<String, String> params = new HashMap<String, String>();
        params.put("lsid", "LSID");
        params.put("datasetId", "Dataset");
        setDetailsURL(new DetailsURL(new ActionURL(StudyController.DatasetDetailRedirectAction.class, getContainer()), params));
    }

    public ActionURL getDataSetURL()
    {
        return new ActionURL(StudyController.DatasetAction.class, _schema.getContainer());
    }

}
