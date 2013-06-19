/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

package org.labkey.study.importer;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.StudySchema;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitDataSetType;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.model.VisitMapKey;
import org.labkey.study.visitmanager.VisitManager;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Jan 6, 2006
 * Time: 10:19:36 AM
 */
public class VisitMapImporter
{
    private boolean _ensureDataSets = true;

    public enum Format
    {
        DataFax {
            public VisitMapReader getReader(String contents)
            {
                return new DataFaxVisitMapReader(contents);
            }

            public VisitMapReader getReader(VirtualFile file, String name) throws IOException
            {
                InputStream is = file.getInputStream(name);

                if (is != null)
                {
                    String contents = PageFlowUtil.getStreamContentsAsString(is);
                    return new DataFaxVisitMapReader(contents);
                }
                return null;
            }

            public String getExtension()
            {
                return ".txt";
            }},

        @SuppressWarnings({"UnusedDeclaration"})
        Xml {
            public VisitMapReader getReader(String contents) throws VisitMapParseException
            {
                return new XmlVisitMapReader(contents);
            }
            public VisitMapReader getReader(VirtualFile file, String name) throws VisitMapParseException, IOException
            {
                return new XmlVisitMapReader(file.getXmlBean(name));  //To change body of implemented methods use File | Settings | File Templates.
            }

            public String getExtension()
            {
                return ".xml";
            }};

        abstract public VisitMapReader getReader(String contents) throws VisitMapParseException;
        abstract public VisitMapReader getReader(VirtualFile file, String name) throws VisitMapParseException, IOException;
        abstract public String getExtension();

        static Format getFormat(String name)
        {
            for (Format format : Format.values())
                if (name.endsWith(format.getExtension()))
                    return format;

            throw new IllegalStateException("Unknown visit map extension for file " + name);
        }
    }

    public boolean isEnsureDataSets()
    {
        return _ensureDataSets;
    }

    public void setEnsureDataSets(boolean ensureDataSets)
    {
        _ensureDataSets = ensureDataSets;
    }

    public boolean process(User user, StudyImpl study, String content, Format format, List<String> errors, Logger logger) throws SQLException, IOException, ValidationException
    {
        if (content == null)
        {
            errors.add("Visit map is empty");
            return false;
        }

        try
        {
            VisitMapReader reader = format.getReader(content);
            return _process(user, study, reader, errors, logger);
        }
        catch (VisitMapParseException x)
        {
            errors.add("Unable to parse the visit map format: " + x.getMessage());
            return false;
        }
    }

    public boolean process(User user, StudyImpl study, VirtualFile file, String name, Format format, List<String> errors, Logger logger) throws SQLException, IOException, ValidationException
    {
        if (file == null)
        {
            errors.add("Visit map is empty");
            return false;
        }
        try
        {
            VisitMapReader reader = format.getReader(file, name);
            return _process(user, study, reader, errors, logger);
        }
        catch (VisitMapParseException x)
        {
            errors.add("Unable to parse the visit map format: " + x.getMessage());
            return false;
        }
    }

    private boolean _process(User user, StudyImpl study, VisitMapReader reader, List<String> errors, Logger logger) throws SQLException, IOException, ValidationException
    {
        if (study.getTimepointType() == TimepointType.CONTINUOUS)
        {
            logger.warn("Can't import visits for an continuous date based study.");
            return true;
        }

        List<VisitMapRecord> records;
        List<StudyManager.VisitAlias> aliases;

        try
        {
            records = reader.getVisitMapRecords();
            aliases = reader.getVisitImportAliases();
        }
        catch (VisitMapParseException x)
        {
            errors.add("Unable to parse the visit map format: " + x.getMessage());
            return false;
        }
        catch (IOException x)
        {
            errors.add("IOException while parsing visit map: " + x.getMessage());
            return false;
        }

        DbScope scope = StudySchema.getInstance().getSchema().getScope();

        try
        {
            scope.ensureTransaction();
            saveDataSets(user, study, records);
            saveVisits(user, study, records);
            saveVisitMap(user, study, records);
            saveImportAliases(user, study, aliases);
            scope.commitTransaction();
            return true;
        }
        catch (StudyManager.VisitCreationException e)
        {
            errors.add(e.getMessage());
            return false;
        }
        finally
        {
            scope.closeConnection();
        }
    }

    private void saveImportAliases(User user, Study study, List<StudyManager.VisitAlias> aliases) throws ValidationException, IOException, SQLException
    {
        StudyManager.getInstance().importVisitAliases(study, user, aliases);
    }

    private void saveVisits(User user, StudyImpl study, List<VisitMapRecord> records) throws SQLException
    {
        StudyManager studyManager = StudyManager.getInstance();
        VisitManager visitManager = studyManager.getVisitManager(study);

        for (VisitMapRecord record : records)
        {
            VisitImpl visit = visitManager.findVisitBySequence(record.getSequenceNumMin());

            // we're using sequenceNumMin as the key in this instance
            if (visit != null && visit.getSequenceNumMin() != record.getSequenceNumMin())
                visit = null;
            
            if (visit == null)
            {
                visit = new VisitImpl(study.getContainer(), record.getSequenceNumMin(), record.getSequenceNumMax(), record.getVisitLabel(), record.getVisitType());
                visit.setVisitDateDatasetId(record.getVisitDatePlate());
                visit.setShowByDefault(record.isShowByDefault());
                visit.setChronologicalOrder(record.getChronologicalOrder());
                visit.setDisplayOrder(record.getDisplayOrder());
                visit.setSequenceNumHandling(record.getSequenceNumHandling());
                int rowId = studyManager.createVisit(study, user, visit).getRowId();
                record.setVisitRowId(rowId);
                assert record.getVisitRowId() > 0;
            }
            else
            {
                if (visit.getVisitDateDatasetId() <= 0 && record.getVisitDatePlate() > 0)
                {
                    visit = _ensureMutable(visit);
                    visit.setVisitDateDatasetId(record.getVisitDatePlate());
                }
                if (visit.getSequenceNumMax() != record.getSequenceNumMax())
                {
                    visit = _ensureMutable(visit);
                    visit.setSequenceNumMax(record.getSequenceNumMax());
                }
                if (visit.isShowByDefault() != record.isShowByDefault())
                {
                    visit = _ensureMutable(visit);
                    visit.setShowByDefault(record.isShowByDefault());
                }
                if (visit.isMutable())
                {
                    StudyManager.getInstance().updateVisit(user, visit);
                }
                record.setVisitRowId(visit.getRowId());
                assert record.getVisitRowId() > 0;
            }
        }
    }

    
    private VisitImpl _ensureMutable(VisitImpl v)
    {
        if (!v.isMutable())
            v = v.createMutable();
        return v;
    }


    private void saveVisitMap(User user, Study study, List<VisitMapRecord> records) throws SQLException
    {
        // NOTE: the only visit map setting for now is REQUIRED/OPTIONAL so...
        Container container = study.getContainer();
        Map<VisitMapKey, Boolean> requiredMapCurr = StudyManager.getInstance().getRequiredMap(study);
        Map<VisitMapKey, Boolean> requiredMapNew = new HashMap<>();

        for (VisitMapRecord record : records)
        {
            int visitId = record.getVisitRowId();
            assert visitId > 0;

            for (int dataSetId : record.getOptionalPlates())
                requiredMapNew.put(new VisitMapKey(dataSetId,visitId), Boolean.FALSE);
            for (int dataSetId : record.getRequiredPlates())
                requiredMapNew.put(new VisitMapKey(dataSetId,visitId), Boolean.TRUE);
        }
            
        for (Map.Entry<VisitMapKey,Boolean> e : requiredMapNew.entrySet())
        {
            VisitMapKey key = e.getKey();
            Boolean isRequiredNew = e.getValue();
            Boolean isRequiredCurrent = requiredMapCurr.get(key);

            // CREATE
            if (null == isRequiredCurrent)
            {
                StudyManager.getInstance().createVisitDataSetMapping(user, container, key.visitRowId, key.datasetId, isRequiredNew);
            }
            // UPDATE
            else
            {
                requiredMapCurr.remove(key);
                if (isRequiredCurrent != isRequiredNew)
                {
                    // this does a bit too much work...
                    StudyManager.getInstance().updateVisitDataSetMapping(user, container, key.visitRowId, key.datasetId, isRequiredNew ? VisitDataSetType.REQUIRED : VisitDataSetType.OPTIONAL);
                }
            }
        }

        // NOTE: extra mappings don't hurt, just make sure they are not required
        for (Map.Entry<VisitMapKey, Boolean> e : requiredMapCurr.entrySet())
        {
            VisitMapKey key = e.getKey();
            Boolean isRequiredCurrent = e.getValue();
            if (isRequiredCurrent)
                StudyManager.getInstance().updateVisitDataSetMapping(user, container, key.visitRowId, key.datasetId, VisitDataSetType.OPTIONAL);
        }
    }


    private void saveDataSets(User user, Study study, List<VisitMapRecord> records) throws SQLException
    {
        DataSet[] defs = StudyManager.getInstance().getDataSetDefinitions(study);
        Set<Integer> existingSet = new HashSet<>();
        for (DataSet def : defs)
            existingSet.add(def.getDataSetId());

        Set<Integer> addDatasetIds = new HashSet<>();
        for (VisitMapRecord record : records)
        {
            for (int id : record.getRequiredPlates())
                addDatasetIds.add(id);
            for (int id : record.getOptionalPlates())
                addDatasetIds.add(id);
        }

        for (Integer dataSetId : addDatasetIds)
        {
            if (dataSetId > 0 && _ensureDataSets && !existingSet.contains(dataSetId))
                StudyManager.getInstance().createDataSetDefinition(user, study.getContainer(), dataSetId);
        }
    }
}
