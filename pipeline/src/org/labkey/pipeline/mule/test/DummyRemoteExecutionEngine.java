package org.labkey.pipeline.mule.test;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.AbstractRemoteExecutionEngineConfig;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.RemoteExecutionEngine;
import org.labkey.pipeline.api.PipelineStatusFileImpl;
import org.labkey.pipeline.api.PipelineStatusManager;

/**
 * Created by: jeckels
 * Date: 11/18/15
 */
public class DummyRemoteExecutionEngine implements RemoteExecutionEngine
{
    public static final String TYPE = "Dummy!!!";
    @NotNull
    @Override
    public String getType()
    {
        return TYPE;
    }

    private int _submitCount = 0;
    private int _statusCount = 0;
    private int _cancelCount = 0;

    @Override
    public void submitJob(@NotNull PipelineJob job) throws PipelineJobException
    {
        _submitCount++;
    }

    @Override
    public String getStatus(@NotNull String jobId) throws PipelineJobException
    {
        _statusCount++;
        return "UNKNOWN";
    }

    @Override
    public void cancelJob(@NotNull String jobId) throws PipelineJobException
    {
        _cancelCount++;
        PipelineStatusFileImpl file = PipelineStatusManager.getJobStatusFile(jobId);
        if (file != null)
        {
            file.setStatus(PipelineJob.TaskStatus.cancelled.toString());
            PipelineStatusManager.updateStatusFile(file);
        }
    }

    public int getSubmitCount()
    {
        return _submitCount;
    }

    public int getStatusCount()
    {
        return _statusCount;
    }

    public int getCancelCount()
    {
        return _cancelCount;
    }

    @Override
    public PipelineJobService.RemoteExecutionEngineConfig getConfig()
    {
        return new DummyConfig();
    }

    public static class DummyConfig extends AbstractRemoteExecutionEngineConfig
    {
        public static final String LOCATION = "dummylocation";

        public DummyConfig()
        {
            super(TYPE, LOCATION);
        }
    }

}
