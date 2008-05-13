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

import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.*;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ActionURL;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryView;
import org.apache.log4j.Logger;

import java.util.Map;
import java.util.List;
import java.util.Set;
import java.sql.SQLException;
import java.io.File;
import java.io.IOException;

public class ExperimentService
{
    static private Interface instance;

    public static final String MODULE_NAME = "Experiment";

    public static final String EXPERIMENT_RUN_CPAS_TYPE = "ExperimentRun";
    public static final String EXPERIMENT_RUN_OUTPUT_CPAS_TYPE = "ExperimentRunOutput";
    public static final String SCHEMA_LOCATION = "http://cpas.fhcrc.org/exp/xml http://www.labkey.org/download/XarSchema/V2.3/expTypes.xsd";

    static public Interface get()
    {
        return instance;
    }

    static public void setInstance(Interface impl)
    {
        instance = impl;
    }

    public interface Interface
    {
        public static final String SAMPLE_DERIVATION_PROTOCOL_LSID = "urn:lsid:labkey.org:Protocol:SampleDerivationProtocol";

        ExpObject findObjectFromLSID(String lsid) throws Exception;

        ExpRun getExpRun(int rowid);
        ExpRun getExpRun(String lsid);
        ExpRun[] getExpRuns(Container container, ExpProtocol parentProtocol, ExpProtocol childProtocol);
        ExpRun createExperimentRun(Container container, String name);

        ExpData getExpData(int rowid);
        ExpData getExpData(String lsid);
        ExpData[] getExpDatas(Container container, DataType type);
        /**
         * Create a data object.  The object will be unsaved, and will have a name which is a GUID.
         */
        ExpData createData(Container container, DataType type);
        ExpData createData(Container container, DataType type, String name);

        ExpMaterial createExpMaterial();
        ExpMaterial getExpMaterial(int rowid);
        ExpMaterial getExpMaterial(String lsid);

        ExpSampleSet getSampleSet(int rowid);
        ExpSampleSet getSampleSet(String lsid);

        /**
         * @param includeOtherContainers whether sample sets from the shared container or the container's project should be included
         */
        ExpSampleSet[] getSampleSets(Container container, boolean includeOtherContainers);
        ExpSampleSet getSampleSet(Container container, String name);
        ExpSampleSet lookupActiveSampleSet(Container container);
        void setActiveSampleSet(Container container, ExpSampleSet sampleSet);

        ExpExperiment createExpExperiment(Container container, String name);
        ExpExperiment getExpExperiment(int rowid);
        ExpExperiment getExpExperiment(String lsid);
        ExpExperiment[] getExperiments(Container container);
        ExpExperiment[] getExpExperimentsForRun(String lsid);

        ExpProtocol getExpProtocol(int rowid);
        ExpProtocol getExpProtocol(String lsid);
        ExpProtocol getExpProtocol(Container container, String name);
        ExpProtocol createExpProtocol(Container container, String name, ExpProtocol.ApplicationType type);

        Map<String, PropertyDescriptor> getDataInputRoles(Container container);
        Map<String, PropertyDescriptor> getMaterialInputRoles(Container container);
        String getDataInputRolePropertyURI(Container container, String roleName);
        PropertyDescriptor ensureDataInputRole(User user, Container container, String roleName, ExpData data) throws SQLException;
        PropertyDescriptor ensureMaterialInputRole(Container container, String roleName, ExpMaterial material) throws SQLException;


        /**
         * The following methods return TableInfo's suitable for using in queries.
         * These TableInfo's initially have no columns, but have methods to
         * add particular columns as needed by the client.
         */
        ExpRunTable createRunTable(String alias);
        ExpDataTable createDataTable(String alias);
        ExpSampleSetTable createSampleSetTable(String alias);
        ExpProtocolTable createProtocolTable(String alias);
        ExpExperimentTable createExperimentTable(String alias);
        ExpMaterialTable createMaterialTable(String alias, QuerySchema schema);
        ExpProtocolApplicationTable createProtocolApplicationTable(String alias);

        String generateLSID(Container container, Class<? extends ExpObject> clazz, String name);
        String generateGuidLSID(Container container, Class<? extends ExpObject> clazz);
        String generateLSID(Container container, DataType type, String name);
        String generateGuidLSID(Container container, DataType type);

        DataType getDataType(String namespacePrefix);

        boolean isTransactionActive();
        void beginTransaction() throws SQLException;
        void commitTransaction() throws SQLException;
        void rollbackTransaction();

        QueryView createExperimentRunWebPart(ViewContext context, ExperimentRunFilter filter, boolean moveButton);

        public DbSchema getSchema();

        /** @return the runs loaded from the XAR */
        public List<ExpRun> loadXar(XarSource source, PipelineJob pipelineJob, boolean reloadExistingRuns) throws ExperimentException;

        ExpProtocolApplication getExpProtocolApplication(String lsid);
        ExpProtocolApplication[] getExpProtocolApplicationsForProtocolLSID(String protocolLSID) throws SQLException;

        ExpData[] getExpData(Container c) throws SQLException;
        ExpData getExpDataByURL(String canonicalURL, Container container);
        ExpData getDataByURL(File f, Container c) throws IOException;
        
        TableInfo getTinfoMaterial();
        TableInfo getTinfoMaterialSource();
        TableInfo getTinfoProtocol();
        TableInfo getTinfoExperiment();
        TableInfo getTinfoExperimentRun();
        TableInfo getTinfoData();
        TableInfo getTinfoPropertyDescriptor();
        ExpSampleSet ensureDefaultSampleSet() throws SQLException;
        ExpSampleSet ensureActiveSampleSet(Container container) throws SQLException;
        public String getDefaultSampleSetLsid();

        ExpRun[] getRunsUsingMaterials(int... materialIds) throws SQLException;
        List<ExpRun> getRunsUsingDatas(List<ExpData> datas) throws SQLException;

        ExpRun getCreatingRun(File file, Container c) throws IOException;
        List<ExpRun> getExpRunsForProtocolIds(boolean protocolIds, int... rowIds) throws SQLException;
        ExpRun[] getRunsUsingSampleSets(ExpSampleSet... sampleSets) throws SQLException;

        /**
         * @return the subset of these runs which are supposed to be deleted when one of their inputs is deleted.
         */
        List<ExpRun> runsDeletedWithInput(ExpRun[] runs) throws SQLException;

        Map<String, ProtocolParameter> getProtocolParameters(int rowId) throws SQLException;

        void deleteProtocolByRowIds(Container container, User user, int... rowIds) throws SQLException, ExperimentException;
        void deleteMaterialByRowIds(Container c, int... materialRowIds) throws SQLException;
        void deleteDataByRowIds(Container container, int... dataRowIds) throws SQLException;
        void deleteAllExpObjInContainer(Container container, User user) throws Exception;
        void deleteSampleSet(int rowId, Container c, User user) throws SQLException, ExperimentException;
        void deleteExperimentByRowIds(Container container, int... experimentRowIds) throws SQLException, ExperimentException;
        void deleteExperimentRunsByRowIds(Container container, User user, int... rowIds) throws SQLException, ExperimentException;

        ExpExperiment addRunsToExperiment(int expId, int... runIds) throws SQLException;
        
        void insertProtocolPredecessor(User user, int actionRowId, int predecessorRowId) throws SQLException;
        
        Lsid getSampleSetLsid(String name, Container container);

        void clearCaches();

        ProtocolApplicationParameter[] getProtocolApplicationParameters(int rowId) throws SQLException;

        void moveContainer(Container c, Container cOldParent, Container cNewParent) throws SQLException, ExperimentException;

        LsidType findType(Lsid lsid);

        Identifiable getObject(Lsid lsid);

        ExpData[] deleteExperimentRunForMove(int runId, Container container, User user) throws SQLException, ExperimentException;

        ExpData[] getAllDataUsedByRun(int runId) throws SQLException;

        /** Kicks off an asynchronous move - a PipelineJob is submitted to the queue to perform the move */
        void moveRuns(ViewBackgroundInfo targetInfo, Container sourceContainer, List<ExpRun> runs) throws SQLException, IOException;
        public ExpProtocol insertSimpleProtocol(ExpProtocol baseProtocol, User user) throws SQLException;

        /**
         * The run must be a instance of a protocol created with insertSimpleProtocol().
         * The run must have at least one input and one output.
         * @param run ExperimentRun, populated with protocol, name, etc
         * @param inputMaterials map from input role name to input material
         * @param inputDatas map from input role name to input data
         * @param outputMaterials map from output role name to output material
         * @param outputDatas map from output role name to output data
         * @param info context information, including the user
         * @param log output log target
         * @throws SQLException
         * @throws XarFormatException
         * @throws ExperimentException
         */
        public ExpRun insertSimpleExperimentRun(ExpRun run, Map<ExpMaterial, String> inputMaterials, Map<ExpData, String> inputDatas, Map<ExpMaterial, String> outputMaterials, Map<ExpData, String> outputDatas, ViewBackgroundInfo info, Logger log) throws SQLException, ExperimentException;
        public ExpRun deriveSamples(Map<ExpMaterial, String> inputMaterials, Map<ExpMaterial, String> outputMaterials, ViewBackgroundInfo info, Logger log) throws ExperimentException;

        public void registerExperimentDataHandler(ExperimentDataHandler handler);
        public void registerRunExpansionHandler(RunExpansionHandler handler);
        public void registerExperimentRunFilter(ExperimentRunFilter filter);
        public void registerDataType(DataType type);
        public void registerProtocolImplementation(ProtocolImplementation impl);

        public Set<ExperimentRunFilter> getExperimentRunFilters();
        public Set<ExperimentDataHandler> getExperimentDataHandlers();
        public Set<RunExpansionHandler> getRunExpansionHandlers();
        public ProtocolImplementation getProtocolImplementation(String name);

        ExpProtocolApplication getExpProtocolApplication(int rowId);
        ExpProtocolApplication[] getExpProtocolApplicationsForRun(int runId) throws SQLException;

        ExpSampleSet createSampleSet();

        ExpProtocol[] getExpProtocols(Container container);
    }
}
