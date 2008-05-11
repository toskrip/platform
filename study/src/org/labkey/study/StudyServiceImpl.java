package org.labkey.study;

import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.UnexpectedException;
import org.labkey.study.dataset.DatasetAuditViewFactory;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.UploadLog;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * User: jgarms
 */
public class StudyServiceImpl implements StudyService.Service
{
    public int getDatasetId(Container c, String datasetName)
    {
        Study study = StudyManager.getInstance().getStudy(c);
        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetName);
        if (def == null)
            return -1;
        return def.getDataSetId();
    }

    public String updateDatasetRow(User u, Container c, int datasetId, String lsid, Map<String, Object> data, List<String> errors)
            throws SQLException
    {
        Study study = StudyManager.getInstance().getStudy(c);
        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);
        String tsv = createTSV(data);
        // Start a transaction, so that we can rollback if our insert fails
        beginTransaction();
        try
        {
            Map<String,Object> oldData = getDatasetRow(u, c, datasetId, lsid);
            StudyManager.getInstance().deleteDatasetRows(study, def, Collections.singletonList(lsid));
            // TODO: switch from using a TSV to a map, so that strange characters like quotes don't throw off the data
            String[] result = StudyManager.getInstance().importDatasetTSV(study,def, tsv, System.currentTimeMillis(),
                Collections.<String,String>emptyMap(), errors, true);

            if (errors.size() > 0)
            {
                // Update failed
                return null;
            }
            // Successfully updated
            if(isTransactionActive())
                commitTransaction();

            // lsid is not in the updated map by default since it is not editable,
            // however it can be changed by the update
            data.put("lsid", result[0]);

            addDatasetAuditEvent(u, c, def, oldData, data);

            return result[0];
        }
        catch (IOException ioe)
        {
            throw UnexpectedException.wrap(ioe);
        }
        catch (ServletException se)
        {
            throw UnexpectedException.wrap(se);
        }
        finally
        {
            if(isTransactionActive())
                rollbackTransaction();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getDatasetRow(User u, Container c, int datasetId, String lsid) throws SQLException
    {
        Study study = StudyManager.getInstance().getStudy(c);
        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);
        try
        {
            TableInfo tInfo = def.getTableInfo(u);
            Map<String,Object> data = Table.selectObject(tInfo, lsid, Map.class);

            // Need to remove extraneous columns
            data.remove("_row");
            List<ColumnInfo> columns = tInfo.getColumnsList();
            for (ColumnInfo col : columns)
            {
                // special handling for lsid -- it's not user-editable,
                // but we want to display it
                if (col.getName().equals("lsid"))
                    continue;
                if (!col.isUserEditable())
                    data.remove(col.getName());
            }
            return data;
        }
        catch (ServletException se)
        {
            throw UnexpectedException.wrap(se);
        }
    }

    public String insertDatasetRow(User u, Container c, int datasetId, Map<String, Object> data, List<String> errors) throws SQLException
    {
        Study study = StudyManager.getInstance().getStudy(c);
        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);
        String tsv = createTSV(data);
        try
        {
            // TODO: switch from using a TSV to a map, so that strange characters like quotes don't throw off the data
            String[] result = StudyManager.getInstance().importDatasetTSV(study,def, tsv, System.currentTimeMillis(),
                Collections.<String,String>emptyMap(), errors, true);

            if (result.length > 0)
            {
                // Log to the audit log
                Map<String,Object> auditDataMap = new HashMap<String,Object>();
                auditDataMap.putAll(data);
                auditDataMap.put("lsid", result[0]);
                addDatasetAuditEvent(u, c, def, null, auditDataMap);

                return result[0];
            }

            // Update failed
            return null;
        }
        catch (IOException ioe)
        {
            throw UnexpectedException.wrap(ioe);
        }
        catch (ServletException se)
        {
            throw UnexpectedException.wrap(se);
        }
    }

    public void deleteDatasetRow(User u, Container c, int datasetId, String lsid) throws SQLException
    {
        Study study = StudyManager.getInstance().getStudy(c);
        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);

        // Need to fetch the old item in order to log the deletion
        Map<String,Object> oldData = getDatasetRow(u, c, datasetId, lsid);

        StudyManager.getInstance().deleteDatasetRows(study, def, Collections.singletonList(lsid));

        addDatasetAuditEvent(u, c, def, oldData, null);
    }

    private String createTSV(Map<String,Object> data)
    {
        StringBuilder sb = new StringBuilder();

        // Need to hold the keys in an array list to preserve order
        List<String> keyList = new ArrayList<String>();
        for (Map.Entry<String,Object> entry : data.entrySet())
        {
            keyList.add(entry.getKey());
            sb.append(entry.getKey()).append('\t');
        }
        sb.append(System.getProperty("line.separator"));

        for (String key:keyList)
        {
            sb.append(data.get(key)).append('\t');
        }
        return sb.toString();
    }

    /**
     * if oldRecord is null, it's an insert, if newRecord is null, it's delete,
     * if both are set, it's an edit
     */
    private void addDatasetAuditEvent(User u, Container c, DataSetDefinition def, Map<String,Object>oldRecord, Map<String,Object> newRecord)
    {
        AuditLogEvent event = new AuditLogEvent();
        event.setCreatedBy(u.getUserId());

        event.setContainerId(c.getId());
        if (c.getProject() != null)
            event.setProjectId(c.getProject().getId());

        event.setIntKey1(def.getDataSetId());

        // IntKey2 is non-zero because we have details (a previous or new datamap)
        event.setIntKey2(1);

        event.setEventType(DatasetAuditViewFactory.DATASET_AUDIT_EVENT);
        DatasetAuditViewFactory.getInstance().ensureDomain(u);

        String comment;
        String oldRecordString = null;
        String newRecordString = null;
        if (oldRecord == null)
        {
            comment = "A new dataset record was inserted";
            newRecordString = encodeAuditMap(newRecord);
        }
        else if (newRecord == null)
        {
            comment = "A dataset record was deleted";
            oldRecordString = encodeAuditMap(oldRecord);
        }
        else
        {
            comment = "A dataset record was modified";
            oldRecordString = encodeAuditMap(oldRecord);
            newRecordString = encodeAuditMap(newRecord);
        }

        event.setComment(comment);

        Map<String,Object> dataMap = new HashMap<String,Object>();
        if (oldRecordString != null) dataMap.put("oldRecordMap", oldRecordString);
        if (newRecordString != null) dataMap.put("newRecordMap", newRecordString);

        AuditLogService.get().addEvent(event, dataMap, AuditLogService.get().getDomainURI(DatasetAuditViewFactory.DATASET_AUDIT_EVENT));
    }

    public static void addDatasetAuditEvent(User u, Container c, DataSetDefinition def, String comment, UploadLog ul /*optional*/)
    {
        AuditLogEvent event = new AuditLogEvent();
        event.setCreatedBy(u.getUserId());

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

}
