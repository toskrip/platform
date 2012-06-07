/*
 * Copyright (c) 2008-2012 LabKey Corporation
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
package org.labkey.pipeline.analysis;

import org.labkey.api.action.*;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.*;
import org.labkey.api.pipeline.browse.PipelinePathForm;
import org.labkey.api.pipeline.file.AbstractFileAnalysisJob;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProtocol;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProtocolFactory;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProvider;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.*;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <code>AnalysisController</code>
 */
public class AnalysisController extends SpringActionController
{
    private static DefaultActionResolver _resolver = new DefaultActionResolver(AnalysisController.class);

    public AnalysisController()
    {
        super();
        setActionResolver(_resolver);
    }

    public static ActionURL urlAnalyze(Container container, TaskId tid, String path)
    {
        return new ActionURL(AnalyzeAction.class, container)
                .addParameter(AnalyzeForm.Params.taskId, tid.toString())
                .addParameter(AnalyzeForm.Params.path, path);
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class AnalyzeAction extends SimpleViewAction<AnalyzeForm>
    {
        private TaskPipeline _taskPipeline;

        public ModelAndView getView(AnalyzeForm analyzeForm, BindException errors) throws Exception
        {
            try
            {
                _taskPipeline = PipelineJobService.get().getTaskPipeline(new TaskId(analyzeForm.getTaskId()));
                return new JspView<ActionURL>("/org/labkey/pipeline/analysis/analyze.jsp", PageFlowUtil.urlProvider(PipelineUrls.class).urlReferer(getContainer()));
            }
            catch (ClassNotFoundException e)
            {
                throw new NotFoundException("Could not find task pipeline: " + analyzeForm.getTaskId());
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(_taskPipeline.getDescription());
        }
    }

    private AbstractFileAnalysisProtocol getProtocol(PipeRoot root, File dirData, AbstractFileAnalysisProtocolFactory factory, String protocolName)
    {
        try
        {
            File protocolFile = factory.getParametersFile(dirData, protocolName, root);
            AbstractFileAnalysisProtocol result;
            if (NetworkDrive.exists(protocolFile))
            {
                result = factory.loadInstance(protocolFile);

                // Don't allow the instance file to override the protocol name.
                result.setName(protocolName);
            }
            else
            {
                result = factory.load(root, protocolName);
            }
            return result;
        }
        catch (IOException e)
        {
            return null;
        }
    }

    private TaskPipeline getTaskPipeline(String taskIdString)
    {
        try
        {
            TaskId taskId = new TaskId(taskIdString);
            TaskPipeline pipeline = PipelineJobService.get().getTaskPipeline(taskId);
            if (pipeline == null)
                throw new NotFoundException("The pipeline '" + taskId + "' was not found.");
            return pipeline;
        }
        catch (ClassNotFoundException e)
        {
            throw new NotFoundException("No pipeline found: " + e.getMessage());
        }
    }

    private AbstractFileAnalysisProtocolFactory getProtocolFactory(TaskPipeline taskPipeline)
    {
        AbstractFileAnalysisProvider provider = (AbstractFileAnalysisProvider)
                PipelineService.get().getPipelineProvider(FileAnalysisPipelineProvider.name);
        if (provider == null)
            throw new NotFoundException("No pipeline provider found for task pipeline: " + taskPipeline);

        return provider.getProtocolFactory(taskPipeline);
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class StartAnalysisAction extends AbstractAnalysisApiAction
    {
        protected ApiResponse execute(AnalyzeForm form, PipeRoot root, File dirData, AbstractFileAnalysisProtocolFactory factory) throws IOException, PipelineValidationException
        {
            try
            {
                TaskPipeline taskPipeline = getTaskPipeline(form.getTaskId());
                if (form.getProtocolName() == null)
                {
                    throw new IllegalArgumentException("Must specify a protocol name");
                }

                AbstractFileAnalysisProtocol protocol = getProtocol(root, dirData, factory, form.getProtocolName());
                if (protocol == null)
                {
                    String xml;
                    if (form.getConfigureXml() != null)
                    {
                        if (form.getConfigureJson() != null)
                        {
                            throw new IllegalArgumentException("The parameters should be defined as XML or JSON, not both");
                        }
                        xml = form.getConfigureXml();
                    }
                    else
                    {
                        if (form.getConfigureJson() == null)
                        {
                            throw new IllegalArgumentException("Parameters must be defined, either as XML or JSON");
                        }
                        ParamParser parser = PipelineJobService.get().createParamParser();
                        JSONObject o = new JSONObject(form.getConfigureJson());
                        Map<String, String> params = new HashMap<String, String>();
                        for (Map.Entry<String, Object> entry : o.entrySet())
                        {
                            params.put(entry.getKey(), entry.getValue() == null ? null : entry.getValue().toString());
                        }
                        xml = parser.getXMLFromMap(params);
                    }

                    protocol = getProtocolFactory(taskPipeline).createProtocolInstance(
                            form.getProtocolName(),
                            form.getProtocolDescription(),
                            xml);

                    protocol.setEmail(getUser().getEmail());
                    protocol.validateToSave(root);
                    if (form.isSaveProtocol())
                    {
                        protocol.saveDefinition(root);
                        PipelineService.get().rememberLastProtocolSetting(protocol.getFactory(),
                                getContainer(), getUser(), protocol.getName());
                    }
                }
                else
                {
                    if(form.getConfigureXml() != null || form.getConfigureJson() != null)
                    {
                        throw new IllegalArgumentException("Cannot redefine an existing protocol");
                    }
                    PipelineService.get().rememberLastProtocolSetting(protocol.getFactory(),
                            getContainer(), getUser(), protocol.getName());
                }

                protocol.getFactory().ensureDefaultParameters(root);

                File fileParameters = protocol.getParametersFile(dirData, root);
                // Make sure configure.xml file exists for the job when it runs.
                if (!fileParameters.exists())
                {
                    protocol.setEmail(getUser().getEmail());
                    protocol.saveInstance(fileParameters, getContainer());
                }

                List<File> filesInputList = form.getValidatedFiles(getContainer());

                if (form.isActiveJobs())
                {
                    throw new IllegalArgumentException("Active jobs already exist for this protocol.");
                }

                AbstractFileAnalysisJob job =
                        protocol.createPipelineJob(getViewBackgroundInfo(), root, filesInputList, fileParameters);

                PipelineService.get().queueJob(job);

                return new ApiSimpleResponse("status", "success");
            }
            catch (IOException e)
            {
                throw new ApiUsageException(e);
            }
            catch (PipelineValidationException e)
            {
                throw new ApiUsageException(e);
            }
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetFileStatusAction extends AbstractAnalysisApiAction
    {
        protected ApiResponse execute(AnalyzeForm form, PipeRoot root, File dirData, AbstractFileAnalysisProtocolFactory factory)
        {
            if (form.getProtocolName() == null || "".equals(form.getProtocolName()))
            {
                throw new NotFoundException("No protocol specified");
            }
            AbstractFileAnalysisProtocol protocol = getProtocol(root, dirData, factory, form.getProtocolName());
            File dirAnalysis = factory.getAnalysisDir(dirData, form.getProtocolName(), root);
            form.initStatus(protocol, dirData, dirAnalysis);

            boolean isRetry = false;

            JSONArray files = new JSONArray();
            for (int i = 0; i < form.getFile().length; i++)
            {
                JSONObject o = new JSONObject();
                o.put("name", form.getFile()[i]);
                o.put("status", form.getFileInputStatus()[i]);
                isRetry |= form.getFileInputStatus()[i] != null;
                files.put(o);
            }
            JSONObject result = new JSONObject();
            result.put("files", files);
            if (!form.isActiveJobs())
            {
                result.put("submitType", isRetry ? "Retry" : "Analyze");
            }
            return new ApiSimpleResponse(result);
        }
    }

    public abstract class AbstractAnalysisApiAction extends ApiAction<AnalyzeForm>
    {
        protected abstract ApiResponse execute(AnalyzeForm form, PipeRoot root, File dirData, AbstractFileAnalysisProtocolFactory factory) throws IOException, PipelineValidationException;

        public ApiResponse execute(AnalyzeForm form, BindException errors) throws Exception
        {
            PipeRoot pr = PipelineService.get().findPipelineRoot(getContainer());
            if (pr == null || !pr.isValid())
                throw new NotFoundException();

            File dirData = null;
            if (form.getPath() != null)
            {
                dirData = pr.resolvePath(form.getPath());
                if (dirData == null || !NetworkDrive.exists(dirData))
                    throw new NotFoundException("Could not resolve path: " + form.getPath());
            }

            TaskPipeline taskPipeline = getTaskPipeline(form.getTaskId());
            AbstractFileAnalysisProtocolFactory factory = getProtocolFactory(taskPipeline);
            return execute(form, pr, dirData, factory);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetSavedProtocolsAction extends AbstractAnalysisApiAction
    {
        protected ApiResponse execute(AnalyzeForm form, PipeRoot root, File dirData, AbstractFileAnalysisProtocolFactory factory)
        {
            JSONArray protocols = new JSONArray();
            for (String protocolName : factory.getProtocolNames(root, dirData))
            {
                JSONObject protocol = new JSONObject();
                protocol.put("name", protocolName);
                AbstractFileAnalysisProtocol pipelineProtocol = getProtocol(root, dirData, factory, protocolName);
                protocol.put("description", pipelineProtocol.getDescription());
                protocol.put("xmlParameters", pipelineProtocol.getXml());
                ParamParser parser = PipelineJobService.get().createParamParser();
                parser.parse(pipelineProtocol.getXml());
                if (parser.getErrors() == null || parser.getErrors().length == 0)
                {
                    protocol.put("jsonParameters", new JSONObject(parser.getInputParameters()));
                }
                protocols.put(protocol);
            }
            JSONObject result = new JSONObject();
            result.put("protocols", protocols);
            result.put("defaultProtocolName", PipelineService.get().getLastProtocolSetting(factory, getContainer(), getUser()));
            return new ApiSimpleResponse(result);
        }
    }

    public static class AnalyzeForm extends PipelinePathForm
    {
        public enum Params { path, taskId, file }

        private String taskId = "";
        private String protocolName = "";
        private String protocolDescription = "";
        private String[] fileInputStatus = null;
        private String configureXml;
        private String configureJson;
        private boolean saveProtocol = false;
        private boolean runAnalysis = false;
        private boolean activeJobs = false;

        private static final String UNKNOWN_STATUS = "UNKNOWN";
        
        public void initStatus(AbstractFileAnalysisProtocol protocol, File dirData, File dirAnalysis)
        {
            if (fileInputStatus != null)
                return;
            
            activeJobs = false;

            int len = getFile().length;
            fileInputStatus = new String[len + 1];
            for (int i = 0; i < len; i++)
                fileInputStatus[i] = initStatusFile(protocol, dirData, dirAnalysis, getFile()[i], true);
            fileInputStatus[len] = initStatusFile(protocol,  dirData, dirAnalysis, null, false);
        }

        private String initStatusFile(AbstractFileAnalysisProtocol protocol, File dirData, File dirAnalysis,
                                  String fileInputName, boolean statusSingle)
        {
            File fileStatus = null;

            if (!statusSingle)
            {
                fileStatus = PipelineJob.FT_LOG.newFile(dirAnalysis,
                        AbstractFileAnalysisProtocol.getDataSetBaseName(dirData));
            }
            else if (fileInputName != null)
            {
                if (protocol == null)
                {
                    return UNKNOWN_STATUS;
                }
                File fileInput = new File(dirData, fileInputName);
                FileType ft = protocol.findInputType(fileInput);
                if (ft != null)
                    fileStatus = PipelineJob.FT_LOG.newFile(dirAnalysis, ft.getBaseName(fileInput));
            }

            if (fileStatus != null)
            {
                String path = PipelineJobService.statusPathOf(fileStatus.getAbsolutePath());
                try
                {
                    PipelineStatusFile sf = PipelineService.get().getStatusFile(path);
                    if (sf == null)
                        return null;

                    activeJobs = activeJobs || sf.isActive();
                    return sf.getStatus();
                }
                catch (SQLException e)
                {
                }
            }

            // Failed to get status.  Assume job is active, and return unknown status.
            activeJobs = true;
            return UNKNOWN_STATUS;
        }

        public String getTaskId()
        {
            return taskId;
        }

        public void setTaskId(String taskId)
        {
            this.taskId = taskId;
        }

        public String getConfigureXml()
        {
            return configureXml;
        }

        public void setConfigureXml(String configureXml)
        {
            this.configureXml = (configureXml == null ? "" : configureXml);
        }

        public String getConfigureJson()
        {
            return configureJson;
        }

        public void setConfigureJson(String configureJson)
        {
            this.configureJson = configureJson;
        }

        public String getProtocolName()
        {
            return protocolName;
        }

        public void setProtocolName(String protocolName)
        {
            this.protocolName = (protocolName == null ? "" : protocolName);
        }

        public String getProtocolDescription()
        {
            return protocolDescription;
        }

        public void setProtocolDescription(String protocolDescription)
        {
            this.protocolDescription = (protocolDescription == null ? "" : protocolDescription);
        }

        public String[] getFileInputStatus()
        {
            return fileInputStatus;
        }

        public boolean isActiveJobs()
        {
            return activeJobs;
        }

        public boolean isSaveProtocol()
        {
            return saveProtocol;
        }

        public void setSaveProtocol(boolean saveProtocol)
        {
            this.saveProtocol = saveProtocol;
        }

        public boolean isRunAnalysis()
        {
            return runAnalysis;
        }

        public void setRunAnalysis(boolean runAnalysis)
        {
            this.runAnalysis = runAnalysis;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class FileNotificationAction extends ApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object form, BindException errors) throws Exception
        {
            PipelineStatusFile[] statusFiles = PipelineService.get().getJobsWaitingForFiles(getContainer());

            for (PipelineStatusFile statusFile : statusFiles)
            {
                if (PipelineJob.WAITING_FOR_FILES.equals(statusFile.getStatus()) && statusFile.getJobStore() != null)
                {
                    PipelineJob pipelineJob = PipelineJobService.get().getJobStore().fromXML(statusFile.getJobStore());
                    if (pipelineJob instanceof AbstractFileAnalysisJob)
                    {
                        List<File> inputFiles = ((AbstractFileAnalysisJob) pipelineJob).getInputFiles();
                        boolean allFilesAvailable = !inputFiles.isEmpty();
                        for (File inputFile : inputFiles)
                        {
                            if (!NetworkDrive.exists(inputFile))
                            {
                                allFilesAvailable = false;
                                break;
                            }
                        }
                        if (allFilesAvailable)
                        {
                            PipelineService.get().queueJob(pipelineJob);
                        }
                    }
                }
            }
            return new ApiSimpleResponse();
        }
    }
}
