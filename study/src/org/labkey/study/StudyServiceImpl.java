/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

package org.labkey.study;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.data.*;
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.Study;
import org.labkey.api.study.DataSet;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.dataset.DatasetAuditViewFactory;
import org.labkey.study.model.*;
import org.labkey.study.query.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * User: jgarms
 */
public class StudyServiceImpl implements StudyService.Service
{
    public static final StudyServiceImpl INSTANCE = new StudyServiceImpl();

    private StudyServiceImpl() {}

    public Study getStudy(Container container)
    {
        return StudyManager.getInstance().getStudy(container);
    }

    public String getStudyName(Container container)
    {
        Study study = getStudy(container);
        return study == null ? null : study.getLabel();
    }

    public DataSet getDataSet(Container c, int datasetId)
    {
        Study study = StudyManager.getInstance().getStudy(c);
        if (study != null)
            return StudyManager.getInstance().getDataSetDefinition(study, datasetId);
        return null;
    }

    public int getDatasetId(Container c, String datasetLabel)
    {
        Study study = StudyManager.getInstance().getStudy(c);
        DataSet def = StudyManager.getInstance().getDataSetDefinition(study, datasetLabel);
        if (def == null)
            return -1;
        return def.getDataSetId();
    }

    public String updateDatasetRow(User u, Container c, int datasetId, String lsid, Map<String, Object> data, List<String> errors)
            throws SQLException
    {
        return updateDatasetRow(u, c, datasetId, lsid, data, errors, null, true);
    }

    public String updateDatasetRow(User u, Container c, int datasetId, String lsid, Map<String, Object> data, List<String> errors, String auditComment, boolean assignDefaultQCState)
            throws SQLException
    {
        StudyImpl study = StudyManager.getInstance().getStudy(c);
        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);

        QCState defaultQCState = null;
        if (assignDefaultQCState)
        {
            Integer defaultQcStateId = study.getDefaultDirectEntryQCState();
            if (defaultQcStateId != null)
                 defaultQCState = StudyManager.getInstance().getQCStateForRowId(c, defaultQcStateId.intValue());
        }

        // Start a transaction, so that we can rollback if our insert fails
        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        boolean transactionOwner = !scope.isTransactionActive();
        if (transactionOwner)
            beginTransaction();
        try
        {
            Map<String,Object> oldData = getDatasetRow(u, c, datasetId, lsid);

            if (oldData == null)
            {
                // No old record found, so we can't update
                errors.add("Record not found with lsid: " + lsid);
                return null;
            }

            Map<String,Object> newData = new CaseInsensitiveHashMap<Object>(data);
            // If any fields aren't included, use the old values
            for (Map.Entry<String,Object> oldField : oldData.entrySet())
            {
                if (oldField.getKey().equals("lsid"))
                    continue;
                if (!newData.containsKey(oldField.getKey()))
                {
                    // if the new incoming data doesn't explicitly set a QC state, and 'assignDefaultQCState' is true,
                    // then we don't want to use the old QC state- we want to use the default instead.  This will be
                    // handled at a lower level by leaving QC state null here:
                    if (assignDefaultQCState && oldField.getKey().equals("QCState"))
                        continue;

                    newData.put(oldField.getKey(), oldField.getValue());
                }
            }

            StudyManager.getInstance().deleteDatasetRows(study, def, Collections.singletonList(lsid));

            List<Map<String,Object>> dataMap = convertMapToPropertyMapArray(u, newData, def);

            String[] result = StudyManager.getInstance().importDatasetData(
                study, u, def, dataMap, System.currentTimeMillis(), errors, true, defaultQCState);

            if (errors.size() > 0)
            {
                // Update failed
                return null;
            }
            // Successfully updated
            if(transactionOwner)
                commitTransaction();

            // lsid is not in the updated map by default since it is not editable,
            // however it can be changed by the update
            newData.put("lsid", result[0]);

            addDatasetAuditEvent(u, c, def, oldData, newData, auditComment);

            return result[0];
        }
        finally
        {
            if(transactionOwner)
                rollbackTransaction();
        }
    }

    // change a map's keys to have proper casing just like the list of columns
    private static Map<String,Object> canonicalizeDatasetRow(Map<String,Object> source, List<ColumnInfo> columns)
    {
        CaseInsensitiveHashMap<String> keyNames = new CaseInsensitiveHashMap<String>();
        for (ColumnInfo col : columns)
        {
            keyNames.put(col.getName(), col.getName());
        }

        Map<String,Object> result = new HashMap<String,Object>();

        for (Map.Entry<String,Object> entry : source.entrySet())
        {
            String key = entry.getKey();
            String newKey = keyNames.get(key);
            if (newKey != null)
                key = newKey;

            result.put(key, entry.getValue());
        }

        return result;
    }

    public Map<String,Object> getDatasetRow(User u, Container c, int datasetId, String lsid) throws SQLException
    {
        Map<String, Object>[] rows = getDatasetRows(u, c, datasetId, Collections.singleton(lsid));
        return rows != null && rows.length > 0 ? rows[0] : null;
    }

    @SuppressWarnings("unchecked")
    public Map<String,Object>[] getDatasetRows(User u, Container c, int datasetId, Collection<String> lsids) throws SQLException
    {
        StudyImpl study = StudyManager.getInstance().getStudy(c);
        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);

        if (def == null)
        {
            throw new RuntimeException("Dataset for id " + datasetId + " not found");
        }

        // Unfortunately we need to use two tableinfos: one to get the column names with correct casing,
        // and one to get the data.  We should eventually be able to convert to using Query completely.
        StudyQuerySchema querySchema = new StudyQuerySchema(study, u, true);
        TableInfo queryTableInfo = querySchema.getDataSetTable(def);
        SimpleFilter filter = new SimpleFilter();
        filter.addInClause("lsid", lsids);

        TableInfo tInfo = def.getTableInfo(u, true, false);
        Map<String,Object>[] datas = Table.select(tInfo, Table.ALL_COLUMNS, filter, null, Map.class);

        if (datas.length == 0)
            return null;

        Map<String,Object>[] canonicalDatas = new HashMap[datas.length];
        List<ColumnInfo> columns = tInfo.getColumns();
        for (int i = 0; i < datas.length; i++)
        {
            Map<String, Object> data = datas[i];
            // Need to remove extraneous columns
            data.remove("_row");
            for (ColumnInfo col : columns)
            {
                // special handling for lsids and keys -- they're not user-editable,
                // but we want to display them
                if (col.getName().equals("lsid") ||
                        col.getName().equals("sourcelsid") ||
                        col.getName().equals("QCState") ||
                        col.isKeyField())
                {
                    continue;
                }
                if (!col.isUserEditable())
                    data.remove(col.getName());
            }
            canonicalDatas[i] = canonicalizeDatasetRow(data, queryTableInfo.getColumns());
        }
        return canonicalDatas;
    }

    public String insertDatasetRow(User u, Container c, int datasetId, Map<String, Object> data, List<String> errors) throws SQLException
    {
        StudyImpl study = StudyManager.getInstance().getStudy(c);
        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);

        QCState defaultQCState = StudyManager.getInstance().getDefaultQCState(study);

        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        boolean transactionOwner = !scope.isTransactionActive();
        try
        {
            if (transactionOwner)
                beginTransaction();

            List<Map<String,Object>> dataMap = convertMapToPropertyMapArray(u, data, def);

            String[] result = StudyManager.getInstance().importDatasetData(
                study, u, def, dataMap, System.currentTimeMillis(), errors, true, defaultQCState);

            if (result.length > 0)
            {
                // Log to the audit log
                Map<String,Object> auditDataMap = new HashMap<String,Object>();
                auditDataMap.putAll(data);
                auditDataMap.put("lsid", result[0]);
                addDatasetAuditEvent(u, c, def, null, auditDataMap);

                if (transactionOwner)
                    commitTransaction();

                return result[0];
            }

            // Update failed
            return null;
        }
        finally
        {
            if (transactionOwner)
                rollbackTransaction();
        }
    }

    public void deleteDatasetRow(User u, Container c, int datasetId, String lsid) throws SQLException
    {
        Study study = StudyManager.getInstance().getStudy(c);
        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);

        // Need to fetch the old item in order to log the deletion
        Map<String,Object> oldData = getDatasetRow(u, c, datasetId, lsid);

        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        boolean transactionOwner = !scope.isTransactionActive();
        try
        {
            if (transactionOwner)
                beginTransaction();

            StudyManager.getInstance().deleteDatasetRows(study, def, Collections.singletonList(lsid));

            addDatasetAuditEvent(u, c, def, oldData, null);

            if (transactionOwner)
                commitTransaction();
        }
        finally
        {
            if (transactionOwner)
                rollbackTransaction();
        }
    }

    /**
     * Requests arrive as maps of name->value. The StudyManager expects arrays of maps
     * of property URI -> value. This is a convenience method to do that conversion.
     */
    private List<Map<String,Object>> convertMapToPropertyMapArray(User user, Map<String,Object> origData, DataSetDefinition def)
        throws SQLException
    {
        Map<String,Object> map = new HashMap<String,Object>();

        TableInfo tInfo = def.getTableInfo(user, false, false);

        Set<String> mvColumnNames = new HashSet<String>();
        for (ColumnInfo col : tInfo.getColumns())
        {
            String name = col.getName();
            if (mvColumnNames.contains(name))
                continue; // We've have already processed this field
            Object value = origData.get(name);

            if (col.isMvEnabled())
            {
                String mvColumnName = col.getMvColumnName();
                mvColumnNames.add(mvColumnName);
                String mvIndicator = (String)origData.get(mvColumnName);
                if (mvIndicator != null)
                {
                    MvFieldWrapper mvWrapper = new MvFieldWrapper(value, mvIndicator);
                    value = mvWrapper;
                }
            }

            if (value == null) // value isn't in the map. Ignore.
                continue;

            map.put(col.getPropertyURI(), value);
        }

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        result.add(map);
        return result;
    }

    /**
     * if oldRecord is null, it's an insert, if newRecord is null, it's delete,
     * if both are set, it's an edit
     */
    private void addDatasetAuditEvent(User u, Container c, DataSet def, Map<String,Object>oldRecord, Map<String,Object> newRecord)
    {
        String comment;
        if (oldRecord == null)
            comment = "A new dataset record was inserted";
        else if (newRecord == null)
            comment = "A dataset record was deleted";
        else
            comment = "A dataset record was modified";
        addDatasetAuditEvent(u, c, def, oldRecord, newRecord, comment);
    }

    /**
     * if oldRecord is null, it's an insert, if newRecord is null, it's delete,
     * if both are set, it's an edit
     */
    private void addDatasetAuditEvent(User u, Container c, DataSet def, Map<String,Object> oldRecord, Map<String,Object> newRecord, String auditComment)
    {
        AuditLogEvent event = new AuditLogEvent();
        event.setCreatedBy(u);

        event.setContainerId(c.getId());
        if (c.getProject() != null)
            event.setProjectId(c.getProject().getId());

        event.setIntKey1(def.getDataSetId());

        // IntKey2 is non-zero because we have details (a previous or new datamap)
        event.setIntKey2(1);

        event.setEventType(DatasetAuditViewFactory.DATASET_AUDIT_EVENT);
        DatasetAuditViewFactory.getInstance().ensureDomain(u);

        String oldRecordString = null;
        String newRecordString = null;
        if (oldRecord == null)
        {
            newRecordString = encodeAuditMap(newRecord);
        }
        else if (newRecord == null)
        {
            oldRecordString = encodeAuditMap(oldRecord);
        }
        else
        {
            oldRecordString = encodeAuditMap(oldRecord);
            newRecordString = encodeAuditMap(newRecord);
        }

        event.setComment(auditComment);

        Map<String,Object> dataMap = new HashMap<String,Object>();
        if (oldRecordString != null) dataMap.put("oldRecordMap", oldRecordString);
        if (newRecordString != null) dataMap.put("newRecordMap", newRecordString);

        AuditLogService.get().addEvent(event, dataMap, AuditLogService.get().getDomainURI(DatasetAuditViewFactory.DATASET_AUDIT_EVENT));
    }

    public static void addDatasetAuditEvent(User u, Container c, DataSet def, String comment, UploadLog ul /*optional*/)
    {
        AuditLogEvent event = new AuditLogEvent();
        event.setCreatedBy(u);

        event.setContainerId(c.getId());
        if (c.getProject() != null)
            event.setProjectId(c.getProject().getId());

        event.setIntKey1(def.getDataSetId());

        event.setEventType(DatasetAuditViewFactory.DATASET_AUDIT_EVENT);
        DatasetAuditViewFactory.getInstance().ensureDomain(u);

        event.setComment(comment);

        if (ul != null)
        {
            event.setKey1(ul.getFilePath());
        }

        AuditLogService.get().addEvent(event,
                Collections.<String,Object>emptyMap(),
                AuditLogService.get().getDomainURI(DatasetAuditViewFactory.DATASET_AUDIT_EVENT));
    }

    private String encodeAuditMap(Map<String,Object> data)
    {
        // encoding requires all strings, so convert our map
        Map<String,String> stringMap = new HashMap<String,String>();
        for (Map.Entry<String,Object> entry :  data.entrySet())
        {
            Object value = entry.getValue();
            stringMap.put(entry.getKey(), value == null ? null : value.toString());
        }
        return DatasetAuditViewFactory.encodeForDataMap(stringMap, true);
    }

    public void beginTransaction() throws SQLException
    {
        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        if(!scope.isTransactionActive())
            scope.beginTransaction();
    }

    public void commitTransaction() throws SQLException
    {
        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        if(scope.isTransactionActive())
            scope.commitTransaction();
    }

    public void rollbackTransaction()
    {
        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        if(scope.isTransactionActive())
            scope.rollbackTransaction();
    }

    public boolean isTransactionActive()
    {
        return StudySchema.getInstance().getSchema().getScope().isTransactionActive();
    }

    public void applyDefaultQCStateFilter(DataView view)
    {
        if (StudyManager.getInstance().showQCStates(view.getRenderContext().getContainer()))
        {
            QCStateSet stateSet = QCStateSet.getDefaultStates(view.getRenderContext().getContainer());
            if (null != stateSet)
            {
                SimpleFilter filter = (SimpleFilter) view.getRenderContext().getBaseFilter();
                if (null == filter)
                {
                    filter = new SimpleFilter();
                    view.getRenderContext().setBaseFilter(filter);
                }
                FieldKey qcStateKey = FieldKey.fromParts(DataSetTable.QCSTATE_ID_COLNAME, "rowid");
                Map<FieldKey, ColumnInfo> qcStateColumnMap = QueryService.get().getColumns(view.getDataRegion().getTable(), Collections.singleton(qcStateKey));
                ColumnInfo qcStateColumn = qcStateColumnMap.get(qcStateKey);
                if (qcStateColumn != null)
                    filter.addClause(new SimpleFilter.SQLClause(stateSet.getStateInClause(qcStateColumn.getAlias()), null, qcStateColumn.getName()));
            }
        }
    }

    public String getSchemaName()
    {
        return StudyQuerySchema.SCHEMA_NAME;
    }

    @Nullable
    public QueryUpdateService getQueryUpdateService(String queryName, Container container, User user)
    {
        if ("Cohort".equals(queryName))
        {
            if (container.getPolicy().hasPermission(user, AdminPermission.class))
                return new CohortUpdateService();
            else
                throw new RuntimeException("User is not allowed to update cohorts");
        }
        else if ("StudyProperties".equals(queryName))
        {
            if (container.getPolicy().hasPermission(user, AdminPermission.class))
                return new StudyPropertiesUpdateService();
            else
                throw new RuntimeException("User is not allowed to update study properties");
        }

        int datasetId = getDatasetId(container, queryName);
        if (datasetId >= 0)
        {
            // Check permission
            Study study = StudyManager.getInstance().getStudy(container);
            DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);
            if (def == null)
                throw new RuntimeException("Could not find dataset with id of " + datasetId);

            if (def.canWrite(user))
                return new DatasetUpdateService(datasetId);
            else
                throw new RuntimeException("User is not allowed to update dataset: " + queryName);
        }

        return null;
    }

    @Nullable
    public String getDomainURI(String queryName, Container container, User user)
    {
        if (CohortImpl.DOMAIN_INFO.getDomainName().equals(queryName))
            return CohortImpl.DOMAIN_INFO.getDomainURI(container);
        if (StudyPropertiesQueryView.QUERY_NAME.equals(queryName))
            return StudyImpl.DOMAIN_INFO.getDomainURI(container);

        Study study = StudyManager.getInstance().getStudy(container);
        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, queryName);
        if (def != null)
        {
            if (def.canRead(user))
                return def.getTypeURI();
            else
                throw new RuntimeException("User does not have permission to read that dataset");
        }

        return null;
    }

    public ActionURL getDatasetURL(Container container, int datasetId)
    {
        return new ActionURL(StudyController.DatasetAction.class, container).addParameter("datasetId", datasetId);
    }

    public Set<Container> getStudyContainersForAssayProtocol(int protocolId)
    {
        TableInfo datasetTable = StudySchema.getInstance().getTableInfoDataSet();
        SimpleFilter filter = new SimpleFilter("protocolid", new Integer(protocolId));
        Set<Container> containers = new HashSet<Container>();
        ResultSet rs = null;
        try
        {
            rs = Table.select(datasetTable, new CsvSet("container,protocolid"), filter, null);
            while (rs.next())
            {
                String containerId = rs.getString("container");
                containers.add(ContainerManager.getForId(containerId));
            }
        }
        catch (SQLException e)
        {
            throw UnexpectedException.wrap(e);
        }
        finally
        {
            if (rs != null) try {rs.close();} catch (SQLException se) {}
        }
        return containers;
    }

    public List<SecurableResource> getSecurableResources(Container container, User user)
    {
        Study study = StudyManager.getInstance().getStudy(container);
        SecurityPolicy policy = (null != study) ? SecurityManager.getPolicy(study) : null;

        if(null == study || !policy.hasPermission(user, AdminPermission.class))
            return Collections.emptyList();
        else
            return Collections.singletonList((SecurableResource)study);
    }
}
