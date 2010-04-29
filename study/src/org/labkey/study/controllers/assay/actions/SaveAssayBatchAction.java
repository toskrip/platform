/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
package org.labkey.study.controllers.assay.actions;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiVersion;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.qc.DataValidator;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.*;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.URIUtil;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.api.data.Container;
import org.labkey.study.assay.ModuleRunUploadContext;
import org.labkey.study.assay.TsvAssayProvider;
import org.labkey.study.assay.TsvDataHandler;
import org.springframework.validation.BindException;

import java.sql.SQLException;
import java.util.*;
import java.net.URI;
import java.io.File;

/**
 * User: jeckels
 * Date: Jan 14, 2009
 */
@RequiresPermissionClass(InsertPermission.class)
@ApiVersion(9.1)
public class SaveAssayBatchAction extends AbstractAssayAPIAction<SimpleApiJsonForm>
{
    private static final Logger LOG = Logger.getLogger(SaveAssayBatchAction.class);

    public ApiResponse executeAction(ExpProtocol protocol, AssayProvider provider, SimpleApiJsonForm form, BindException errors) throws Exception
    {
        JSONObject rootJsonObject = form.getJsonObject();

        JSONObject batchJsonObject = rootJsonObject.getJSONObject(BATCH);
        if (batchJsonObject == null)
        {
            throw new IllegalArgumentException("No batch object found");
        }

        if (!(provider instanceof TsvAssayProvider))
        {
            throw new IllegalArgumentException("SaveAssayBatch is not supported for assay provider: " + provider);
        }

        ExpExperiment batch;

        try
        {
            ExperimentService.get().beginTransaction();
            batch = handleBatch(batchJsonObject, protocol, provider);

            ExperimentService.get().commitTransaction();
        }
        finally
        {
            ExperimentService.get().closeTransaction();
        }

        return serializeResult(provider, protocol, batch);
    }

    private ExpRun handleRun(JSONObject runJsonObject, ExpProtocol protocol, AssayProvider provider, ExpExperiment batch) throws JSONException, ValidationException, ExperimentException, SQLException
    {
        String name = runJsonObject.has(ExperimentJSONConverter.NAME) ? runJsonObject.getString(ExperimentJSONConverter.NAME) : null;
        ExpRun run;
        if (runJsonObject.has(ExperimentJSONConverter.ID))
        {
            int runId = runJsonObject.getInt(ExperimentJSONConverter.ID);
            run = ExperimentService.get().getExpRun(runId);
            if (run == null)
            {
                throw new NotFoundException("Could not find assay run " + runId);
            }
            if (!batch.getContainer().equals(getViewContext().getContainer()))
            {
                throw new NotFoundException("Could not find assay run " + runId + " in folder " + getViewContext().getContainer());
            }
        }
        else
        {
            run = provider.createExperimentRun(name, getViewContext().getContainer(), protocol);
        }
        PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(getViewContext().getContainer());
        if (pipeRoot == null)
        {
            throw new NotFoundException("Pipeline root is not configured for folder " + getViewContext().getContainer());
        }
        run.setFilePathRoot(pipeRoot.getRootPath());
        if (runJsonObject.has(ExperimentJSONConverter.COMMENT))
        {
            run.setComments(runJsonObject.getString(ExperimentJSONConverter.COMMENT));
        }

        handleStandardProperties(runJsonObject, run, provider.getRunDomain(protocol).getProperties());

        if (runJsonObject.has(DATA_ROWS) ||
                runJsonObject.has(ExperimentJSONConverter.DATA_INPUTS) ||
                runJsonObject.has(ExperimentJSONConverter.MATERIAL_INPUTS))
        {
            JSONArray dataRows;
            JSONArray dataInputs;
            JSONArray materialInputs;
            JSONArray dataOutputs;
            if (!runJsonObject.has(DATA_ROWS))
            {
                // Client didn't post the rows so reuse the values that are currently attached to the run
                // Inefficient but easy
                dataRows = serializeRun(run, provider, protocol).getJSONArray(DATA_ROWS);
            }
            else
            {
                dataRows = runJsonObject.getJSONArray(DATA_ROWS);
            }

            if (!runJsonObject.has(ExperimentJSONConverter.DATA_INPUTS))
            {
                // Client didn't post the inputs so reuse the values that are currently attached to the run
                // Inefficient but easy
                dataInputs = serializeRun(run, provider, protocol).getJSONArray(ExperimentJSONConverter.DATA_INPUTS);
            }
            else
            {
                dataInputs = runJsonObject.getJSONArray(ExperimentJSONConverter.DATA_INPUTS);
            }

            if (!runJsonObject.has(ExperimentJSONConverter.MATERIAL_INPUTS))
            {
                materialInputs = serializeRun(run, provider, protocol).getJSONArray(ExperimentJSONConverter.MATERIAL_INPUTS);
            }
            else
            {
                materialInputs = runJsonObject.getJSONArray(ExperimentJSONConverter.MATERIAL_INPUTS);
            }

            if (!runJsonObject.has(ExperimentJSONConverter.DATA_OUTPUTS))
            {
                // Client didn't post the outputs so reuse the values that are currently attached to the run
                // Ineffecient but easy
                dataOutputs = serializeRun(run, provider, protocol).getJSONArray(ExperimentJSONConverter.DATA_OUTPUTS);
            }
            else
            {
                dataOutputs = runJsonObject.getJSONArray(ExperimentJSONConverter.DATA_OUTPUTS);
            }

            rewriteProtocolApplications(protocol, provider, run, dataInputs, dataRows, materialInputs, runJsonObject, pipeRoot, dataOutputs);
        }

        return run;
    }

    private void rewriteProtocolApplications(ExpProtocol protocol, AssayProvider provider, ExpRun run, JSONArray inputDataArray, JSONArray dataArray, JSONArray inputMaterialArray, JSONObject runJsonObject, PipeRoot pipelineRoot, JSONArray outputDataArray) throws ExperimentException, ValidationException
    {
        ViewContext context = getViewContext();

        // First, clear out any old data analysis results
        for (ExpData data : run.getOutputDatas(provider.getDataType()))
        {
            data.delete(context.getUser());
        }

        Map<ExpData, String> inputData = new HashMap<ExpData, String>();
        for (int i = 0; i < inputDataArray.length(); i++)
        {
            JSONObject dataObject = inputDataArray.getJSONObject(i);
            inputData.put(handleData(dataObject, pipelineRoot), dataObject.optString(ExperimentJSONConverter.ROLE, "Data"));
        }

        Map<ExpMaterial, String> inputMaterial = new HashMap<ExpMaterial, String>();
        for (int i=0; i < inputMaterialArray.length(); i++)
        {
            JSONObject materialObject = inputMaterialArray.getJSONObject(i);
            ExpMaterial material = handleMaterial(materialObject);
            if (material != null)
                inputMaterial.put(material, null);
        }

        // Delete the contents of the run
        run.deleteProtocolApplications(context.getUser());

        // Recreate the run
        Map<ExpData, String> outputData = new HashMap<ExpData, String>();
        ExpData newData = AbstractAssayProvider.createData(run.getContainer(), null, "Analysis Results", provider.getDataType());
        newData.save(getViewContext().getUser());
        outputData.put(newData, "Data");

        //other data outputs
        for (int i=0; i < outputDataArray.length(); i++)
        {
            JSONObject dataObject = outputDataArray.getJSONObject(i);
            outputData.put(handleData(dataObject, pipelineRoot), dataObject.optString(ExperimentJSONConverter.ROLE, "Data"));
        }

        List<Map<String, Object>> rawData = dataArray.toMapList();

        run = ExperimentService.get().insertSimpleExperimentRun(run,
            inputMaterial,
            inputData,
            Collections.<ExpMaterial, String>emptyMap(),
            outputData,
            Collections.<ExpData, String>emptyMap(),                
            new ViewBackgroundInfo(context.getContainer(),
                    context.getUser(), context.getActionURL()), LOG, false);

        // programmatic qc validation
        DataValidator dataValidator = provider.getDataValidator();
        if (dataValidator != null)
            dataValidator.validate(new ModuleRunUploadContext(getViewContext(), protocol.getRowId(), runJsonObject, rawData), run);

        TsvDataHandler dataHandler = new TsvDataHandler();
        dataHandler.setAllowEmptyData(true);
        dataHandler.importRows(newData, getViewContext().getUser(), run, protocol, provider, rawData);
    }

    private ExpData handleData(JSONObject dataObject, PipeRoot pipelineRoot) throws ValidationException
    {
        ExperimentService.Interface expSvc = ExperimentService.get();
        Container container = getViewContext().getContainer();
        ExpData data;
        if (dataObject.has(ExperimentJSONConverter.ID))
        {
            int dataId = dataObject.getInt(ExperimentJSONConverter.ID);
            data = expSvc.getExpData(dataId);

            if (data == null)
            {
                throw new NotFoundException("Could not find data with id " + dataId);
            }
            if (!data.getContainer().equals(getViewContext().getContainer()))
            {
                throw new NotFoundException("Data with row id " + dataId + " is not in folder " + getViewContext().getContainer());
            }
        }
        else if (dataObject.has(ExperimentJSONConverter.PIPELINE_PATH) && dataObject.getString(ExperimentJSONConverter.PIPELINE_PATH) != null)
        {
            String pipelinePath = dataObject.getString(ExperimentJSONConverter.PIPELINE_PATH);

            //check to see if this is already an ExpData
            File file = new File(pipelineRoot.getRootPath(), pipelinePath);
            URI uri = file.toURI();
            data = expSvc.getExpDataByURL(uri.toString(), container);

            if (null == data)
            {
                //create a new one
                String name = dataObject.optString(ExperimentJSONConverter.NAME, pipelinePath);
                DataType type = AbstractAssayProvider.RELATED_FILE_DATA_TYPE;
                String lsid = expSvc.generateLSID(container, type, name);
                data = expSvc.createData(container, name, lsid);
                data.setDataFileURI(uri);
                data.save(getViewContext().getUser());
            }
        }
        else if (dataObject.has(ExperimentJSONConverter.DATA_FILE_URL) && dataObject.getString(ExperimentJSONConverter.DATA_FILE_URL) != null)
        {
            String dataFileURL = dataObject.getString(ExperimentJSONConverter.DATA_FILE_URL);
            //check to see if this is already an ExpData
            data = expSvc.getExpDataByURL(dataFileURL, container);

            if (null == data)
            {
                throw new IllegalArgumentException("Could not find a file for dataFileURL " + dataFileURL);
            }
        }
        else if (dataObject.has(ExperimentJSONConverter.ABSOLUTE_PATH) && dataObject.get(ExperimentJSONConverter.ABSOLUTE_PATH) != null)
        {
            String absolutePath = dataObject.getString(ExperimentJSONConverter.ABSOLUTE_PATH);
            File f = new File(absolutePath);
            URI uri = f.toURI();
            if (!URIUtil.isDescendant(pipelineRoot.getUri(), uri))
            {
                throw new IllegalArgumentException("File with path " + absolutePath + " is not under the pipeline root for this folder");
            }
            //check to see if this is already an ExpData
            data = expSvc.getExpDataByURL(f, container);

            if (null == data)
            {
                String name = dataObject.optString(ExperimentJSONConverter.NAME, f.getName());
                DataType type = AbstractAssayProvider.RELATED_FILE_DATA_TYPE;
                String lsid = expSvc.generateLSID(container, type, name);
                data = expSvc.createData(container, name, lsid);
                data.setDataFileURI(uri);
                data.save(getViewContext().getUser());
            }
        }
        else
            throw new IllegalArgumentException("Data input must have an id, pipelinePath, dataFileURL, or absolutePath property.");

        saveProperties(data, new DomainProperty[0], dataObject);
        return data;
    }

    private ExpMaterial handleMaterial(JSONObject materialObject) throws ValidationException
    {
        if (materialObject.has(ExperimentJSONConverter.ROW_ID))
        {
            // Unlike with runs and batches, we require that the materials are already created
            int materialRowId = materialObject.getInt(ExperimentJSONConverter.ROW_ID);
            ExpMaterial material = ExperimentService.get().getExpMaterial(materialRowId);
            if (material == null)
            {
                throw new NotFoundException("Could not find material with row id: " + materialRowId);
            }
            if (!material.getContainer().equals(getViewContext().getContainer()))
            {
                throw new NotFoundException("Material with row id " + materialRowId + " is not in folder " + getViewContext().getContainer());
            }
            saveProperties(material, new DomainProperty[0], materialObject);
            return material;
        }
        return null;
    }

    private ExpExperiment handleBatch(JSONObject batchJsonObject, ExpProtocol protocol, AssayProvider provider) throws Exception
    {
        ExpExperiment batch;
        if (batchJsonObject.has(ExperimentJSONConverter.ID))
        {
            batch = lookupBatch(batchJsonObject.getInt(ExperimentJSONConverter.ID));
        }
        else
        {
            batch = AssayService.get().createStandardBatch(getViewContext().getContainer(),
                    batchJsonObject.has(ExperimentJSONConverter.NAME) ? batchJsonObject.getString(ExperimentJSONConverter.NAME) : null, protocol);
        }

        if (batchJsonObject.has(ExperimentJSONConverter.COMMENT))
        {
            batch.setComments(batchJsonObject.getString(ExperimentJSONConverter.COMMENT));
        }
        handleStandardProperties(batchJsonObject, batch, provider.getBatchDomain(protocol).getProperties());

        List<ExpRun> runs = new ArrayList<ExpRun>();
        if (batchJsonObject.has(RUNS))
        {
            JSONArray runsArray = batchJsonObject.getJSONArray(RUNS);
            for (int i = 0; i < runsArray.length(); i++)
            {
                JSONObject runJsonObject = runsArray.getJSONObject(i);
                ExpRun run = handleRun(runJsonObject, protocol, provider, batch);
                runs.add(run);
            }
        }
        List<ExpRun> existingRuns = Arrays.asList(batch.getRuns());

        // Make sure that all the runs are considered part of the batch
        List<ExpRun> runsToAdd = new ArrayList<ExpRun>(runs);
        runsToAdd.removeAll(existingRuns);
        batch.addRuns(getViewContext().getUser(), runsToAdd.toArray(new ExpRun[runsToAdd.size()]));

        // Remove any runs that are no longer part of the batch
        List<ExpRun> runsToRemove = new ArrayList<ExpRun>(existingRuns);
        runsToRemove.removeAll(runs);
        for (ExpRun runToRemove : runsToRemove)
        {
            batch.removeRun(getViewContext().getUser(), runToRemove);
        }

        return batch;
    }

    private void handleStandardProperties(JSONObject jsonObject, ExpObject object, DomainProperty[] dps) throws ValidationException, SQLException
    {
        if (jsonObject.has(ExperimentJSONConverter.NAME))
        {
            object.setName(jsonObject.getString(ExperimentJSONConverter.NAME));
        }

        object.save(getViewContext().getUser());
        OntologyManager.ensureObject(object.getContainer(), object.getLSID());

        if (jsonObject.has(ExperimentJSONConverter.PROPERTIES))
        {
            saveProperties(object, dps, jsonObject.getJSONObject(ExperimentJSONConverter.PROPERTIES));
        }
    }

    private void saveProperties(ExpObject object, DomainProperty[] dps, JSONObject propertiesJsonObject) throws ValidationException, JSONException
    {
        for (Map.Entry<DomainProperty, Object> entry : ExperimentJSONConverter.convertProperties(propertiesJsonObject, dps).entrySet())
        {
            object.setProperty(getViewContext().getUser(), entry.getKey().getPropertyDescriptor(), entry.getValue());
        }
    }
}

