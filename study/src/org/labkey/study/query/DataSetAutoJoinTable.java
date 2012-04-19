/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.study.TimepointType;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.study.StudyService;
import org.labkey.study.StudySchema;
import org.labkey.study.model.DataSetDefinition;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Magic table that joins a source DataSet to other DataSets based on primary key types.
 *
 * DataSets may have three types of primary keys:
 * (A) ParticipantID only.
 * (B) ParticipantID, SequenceNum (either Visit or Date)
 * (C) ParticipantID, SequenceNum, and an additional key.
 *
 * This virtual table has a column for each DataSet that the source DataSet can join to (without row duplication):
 *   A -> A
 *   B -> A or B
 *   C -> A, B, or C (if C key name and type matches)
 *
 * Other joins may make sense (A -> B or A -> C), but would produce row duplication.
 */
public class DataSetAutoJoinTable extends VirtualTable
{
    private StudyQuerySchema _schema;
    private DataSetTableImpl _source;
    private DataSetDefinition _dataset;
    private String _keyPropertyName;

    private ColumnInfo _participantIdColumn;

    public DataSetAutoJoinTable(StudyQuerySchema schema, DataSetTableImpl source,
                                @Nullable ColumnInfo participantIdColumn)
    {
        super(StudySchema.getInstance().getSchema());
        setName("DataSets");
        _schema = schema;
        _source = source;

        _dataset = source.getDatasetDefinition();
        _keyPropertyName = _dataset.getKeyPropertyName();

        // We only need to the SequenceNum and Key columns when traversing the dataset FKs.
        // The participantIdColumn should always be present in that case.
        _participantIdColumn = participantIdColumn;
        if (_participantIdColumn != null)
        {
            TableInfo parent = _participantIdColumn.getParentTable();

            // SequenceNum is always available
            ColumnInfo colSequenceNum = new AliasedColumn(parent, "SequenceNum", parent.getColumn("SequenceNum"));
            colSequenceNum.setHidden(true);
            addColumn(colSequenceNum);

            // The extra key property is not always available
            if (_keyPropertyName != null)
            {
                ColumnInfo colExtraKey = new AliasedColumn(parent, "_key", parent.getColumn("_key"));
                colExtraKey.setHidden(true);
                addColumn(colExtraKey);
            }
        }

        Set<FieldKey> defaultVisible = new LinkedHashSet<FieldKey>();
        for (DataSetDefinition dataset : _schema.getStudy().getDataSets())
        {
            // verify that the current user has permission to read this dataset (they may not if
            // advanced study security is enabled).
            if (!dataset.canRead(schema.getUser()))
                continue;

            String name = _schema.decideTableName(dataset);
            if (name == null)
                continue;

            // duplicate labels! see BUG 2206
            if (getColumn(name) != null)
                continue;

            ColumnInfo datasetColumn = createDataSetColumn(name, dataset);
            if (datasetColumn != null)
            {
                addColumn(datasetColumn);
                defaultVisible.add(FieldKey.fromParts(name));
            }
        }

        setDefaultVisibleColumns(defaultVisible);
    }


    protected ColumnInfo createDataSetColumn(String name, final DataSetDefinition dsd)
    {
        ColumnInfo ret;
        if (_participantIdColumn == null)
        {
            ret = new ColumnInfo(name, this);
            ret.setSqlTypeName("VARCHAR");
        }
        else
        {
            ret = new AliasedColumn(name, _participantIdColumn);
        }

        DataSetForeignKey fk = null;
        if (_dataset.isDemographicData())
        {
            if (dsd.isDemographicData())
                // A -> A
                fk = createParticipantFK(dsd);
        }
        else if (_keyPropertyName == null)
        {
            if (dsd.isDemographicData())
                // B -> A
                fk = createParticipantFK(dsd);
            else if (dsd.getKeyPropertyName() == null)
                // B -> B
                fk = createParticipantSequenceNumFK(dsd);
        }
        else
        {
            if (dsd.isDemographicData())
                // C -> A
                fk = createParticipantFK(dsd);
            else if (dsd.getKeyPropertyName() == null)
                // C -> B
                fk = createParticipantSequenceNumFK(dsd);
            else
                // C -> C
                fk = createParticipantSequenceNumKeyFK(dsd);
        }

        // The join type was not supported.
        if (fk == null)
            return null;

        ret.setFk(fk);
        ret.setLabel(dsd.getLabel());
        ret.setDescription("Lookup to the " + dsd.getLabel() + " DataSet, with one row for each " + fk.getJoinDescription() + ".");
        ret.setIsUnselectable(true);
        ret.setUserEditable(false);
        return ret;
    }

    private DataSetForeignKey createParticipantFK(DataSetDefinition dsd)
    {
        assert dsd.isDemographicData();
        DataSetForeignKey fk = new DataSetForeignKey(dsd);
        fk.setJoinDescription(StudyService.get().getSubjectColumnName(dsd.getContainer()));
        return fk;
    }

    private DataSetForeignKey createParticipantSequenceNumFK(DataSetDefinition dsd)
    {
        assert !dsd.isDemographicData() && dsd.getKeyPropertyName() == null;
        assert !_dataset.isDemographicData();

        DataSetForeignKey fk = new DataSetForeignKey(dsd);
        if (_participantIdColumn != null)
        {
            // NOTE: We are using the underlying dataset column name 'SequenceNum' and '_Key' so the database indices are used.
            TableInfo parentTable = _participantIdColumn.getParentTable();
            ColumnInfo fkColumn = parentTable.getColumn("SequenceNum");
            fk.addJoin(fkColumn, "SequenceNum");
        }

        fk.setJoinDescription(StudyService.get().getSubjectNounSingular(dsd.getContainer()) +
                              (dsd.getStudy().getTimepointType() == TimepointType.VISIT ? "/Visit" : "/Date"));
        return fk;
    }

    private DataSetForeignKey createParticipantSequenceNumKeyFK(DataSetDefinition dsd)
    {
        assert !dsd.isDemographicData() && dsd.getKeyPropertyName() != null;
        assert !_dataset.isDemographicData() && _keyPropertyName != null;

        // Key property name must match
        if (!_keyPropertyName.equalsIgnoreCase(dsd.getKeyPropertyName()))
            return null;

        DomainProperty fkDomainProperty = _dataset.getDomain().getPropertyByName(_keyPropertyName);
        DomainProperty pkDomainProperty = dsd.getDomain().getPropertyByName(_keyPropertyName);
        if (fkDomainProperty == null || pkDomainProperty == null)
            return null;

        // Key property types must match
        PropertyType fkPropertyType = fkDomainProperty.getPropertyDescriptor().getPropertyType();
        PropertyType pkPropertyType = pkDomainProperty.getPropertyDescriptor().getPropertyType();
        if (!LOOKUP_KEY_TYPES.contains(fkPropertyType) || fkPropertyType != pkPropertyType)
            return null;

        // NOTE: Also consider comparing ConceptURI of the properties

        DataSetForeignKey fk = new DataSetForeignKey(dsd);
        if (_participantIdColumn != null)
        {
            // NOTE: We are using the underlying dataset column name 'SequenceNum' and '_Key' so the database indices are used.
            TableInfo parentTable = _participantIdColumn.getParentTable();
            ColumnInfo seqNumFkColumn = parentTable.getColumn("SequenceNum");
            fk.addJoin(seqNumFkColumn, "SequenceNum");

            ColumnInfo keyFkColumn = parentTable.getColumn("_key");
            fk.addJoin(keyFkColumn, "_key");
        }

        fk.setJoinDescription(StudyService.get().getSubjectNounSingular(dsd.getContainer()) +
                (dsd.getStudy().getTimepointType() == TimepointType.VISIT ? "/Visit" : "/Date") +
                "/" + _keyPropertyName);
        return fk;
    }

    // The set of allowed extra key lookup types that we can join across.
    private static final EnumSet<PropertyType> LOOKUP_KEY_TYPES = EnumSet.of(
            PropertyType.DATE_TIME,
            PropertyType.DOUBLE, // Attempting to allow this
            PropertyType.STRING,
            PropertyType.INTEGER);

    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        ColumnInfo col = super.resolveColumn(name);
        if (col != null)
            return col;

        return null;
    }

    private class DataSetForeignKey extends LookupForeignKey
    {
        private final DataSetDefinition dsd;
        private String _joinDescription;

        public DataSetForeignKey(DataSetDefinition dsd)
        {
            super(StudyService.get().getSubjectColumnName(dsd.getContainer()));
            this.dsd = dsd;
        }

        public DataSetTableImpl getLookupTableInfo()
        {
            try
            {
                DataSetTableImpl ret = new DataSetTableImpl(_schema, dsd);
                ret.hideParticipantLookups();
                return ret;
            }
            catch (UnauthorizedException e)
            {
                return null;
            }
        }

        public void setJoinDescription(String description)
        {
            _joinDescription = description;
        }

        public String getJoinDescription()
        {
            return _joinDescription;
        }
    }

    @Override
    public boolean supportsContainerFilter()
    {
        return false;
    }
}

