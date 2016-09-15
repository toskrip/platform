package org.labkey.visualization.report;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.analytics.ColumnAnalyticsProvider;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.settings.FolderSettingsCache;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.template.ClientDependency;

import java.util.Set;

public class DimensionPieChartAnalyticsProvider extends ColumnAnalyticsProvider
{
    @Override
    public String getName()
    {
        return "VIS_PIE";
    }

    @Override
    public String getLabel()
    {
        return "Pie Chart";
    }

    @Override
    public String getDescription()
    {
        return "View a pie chart of the selected dimension column's data values.";
    }

    @Override
    public boolean isApplicable(@NotNull ColumnInfo col)
    {
        return !col.getSqlTypeName().equalsIgnoreCase("entityid");
    }

    @Override
    public boolean isVisible(RenderContext ctx, QuerySettings settings, ColumnInfo col)
    {
        if (FolderSettingsCache.areRestrictedColumnsEnabled(ctx.getContainer())) {
            return col.isDimension();
        }
        else
        {
            return true;
        }
    }

    @Nullable
    @Override
    public String getIconCls(RenderContext ctx, QuerySettings settings, ColumnInfo col)
    {
        return "fa fa-pie-chart";
    }

    @Nullable
    @Override
    public ActionURL getActionURL(RenderContext ctx, QuerySettings settings, ColumnInfo col)
    {
        return null;
    }

    @Override
    public String getScript(RenderContext ctx, QuerySettings settings, ColumnInfo col)
    {
        return "LABKEY.ColumnVisualizationAnalytics.showDimensionFromDataRegion(" +
                PageFlowUtil.jsString(ctx.getCurrentRegion().getName()) + "," +
                PageFlowUtil.jsString(col.getName()) + "," +
                PageFlowUtil.jsString(col.getFieldKey().toString()) + "," +
                PageFlowUtil.jsString(getName()) +
            ");";
    }

    @Override
    public void addClientDependencies(Set<ClientDependency> dependencies)
    {
        dependencies.add(ClientDependency.fromPath("vis/vis"));
        dependencies.add(ClientDependency.fromPath("vis/ColumnVisualizationAnalytics.js"));
        dependencies.add(ClientDependency.fromPath("vis/ColumnVisualizationAnalytics.css"));
    }

    @Override
    public Integer getSortOrder()
    {
        return 300;
    }
}
