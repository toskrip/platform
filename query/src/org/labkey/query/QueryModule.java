/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

package org.labkey.query;

import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.RExportScriptFactory;
import org.labkey.api.reports.LabkeyScriptEngineManager;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.chart.ChartRendererFactory;
import org.labkey.api.reports.report.*;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.WebPartFactory;
import org.labkey.query.controllers.QueryControllerSpring;
import org.labkey.query.controllers.dbuserschema.DbUserSchemaController;
import org.labkey.query.persist.QueryManager;
import org.labkey.query.reports.ReportServiceImpl;
import org.labkey.query.reports.ReportsController;
import org.labkey.query.reports.ReportsPipelineProvider;
import org.labkey.query.reports.ReportsWebPartFactory;
import org.labkey.query.reports.chart.TimeSeriesRenderer;
import org.labkey.query.reports.chart.XYChartRenderer;
import org.labkey.query.reports.view.ReportUIProvider;
import org.labkey.query.view.QueryWebPartFactory;
import org.labkey.query.data.Query;
import org.labkey.query.sql.SqlParser;

import javax.script.ScriptEngineManager;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.HashSet;

import junit.framework.TestCase;


public class QueryModule extends DefaultModule
{
    public String getName()
    {
        return "Query";
    }

    public double getVersion()
    {
        return 8.31;
    }

    protected void init()
    {
        addController("query", QueryControllerSpring.class);
        addController("reports", ReportsController.class);
        addController("dbuserschema", DbUserSchemaController.class);

        QueryService.set(new QueryServiceImpl());
        ContainerManager.addContainerListener(QueryManager.CONTAINER_LISTENER);

        ReportService.registerProvider(new ReportServiceImpl());
        ReportService.get().addUIProvider(new ReportUIProvider());

        ChartRendererFactory.get().addChartRenderer(XYChartRenderer.getInstance());
        ChartRendererFactory.get().addChartRenderer(TimeSeriesRenderer.getInstance());
        ReportService.get().registerDescriptor(new ReportDescriptor());
        ReportService.get().registerDescriptor(new ChartReportDescriptor());
        ReportService.get().registerDescriptor(new QueryReportDescriptor());
        ReportService.get().registerDescriptor(new RReportDescriptor());

        ReportService.get().registerReport(new QueryReport());
        ReportService.get().registerReport(new ChartQueryReport());
        ReportService.get().registerReport(new RReport());
        ReportService.get().registerReport(new ExternalScriptEngineReport());
        ReportService.get().registerReport(new InternalScriptEngineReport());

        QueryView.register(new RExportScriptFactory());
    }

    protected Collection<? extends WebPartFactory> createWebPartFactories()
    {
        return Arrays.asList(new QueryWebPartFactory(), new ReportsWebPartFactory());
    }

    public boolean hasScripts()
    {
        return true;
    }

    @Override
    public UpgradeCode getUpgradeCode()
    {
        return new QueryUpgradeCode();
    }

    public void startup(ModuleContext moduleContext)
    {
        PipelineService.get().registerPipelineProvider(new ReportsPipelineProvider());
        ReportsController.registerAdminConsoleLinks();

        ServiceRegistry.get().registerService(ScriptEngineManager.class, new LabkeyScriptEngineManager());
    }

    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(QueryManager.get().getDbSchemaName());
    }

    public Set<Class<? extends TestCase>> getJUnitTests()
    {
        return new HashSet<Class<? extends TestCase>>(Arrays.asList(
                SqlParser.TestCase.class,
				Query.TestCase.class));
    }
}
