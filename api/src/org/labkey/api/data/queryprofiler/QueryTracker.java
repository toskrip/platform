package org.labkey.api.data.queryprofiler;

import org.apache.commons.collections15.map.ReferenceMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.ByteArrayHashKey;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.util.Compress;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.Formats;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.ActionURL;

import java.io.PrintWriter;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.DataFormatException;

/**
* User: jeckels
* Date: 2/13/14
*/
class QueryTracker
{
    private final @Nullable
    DbScope _scope;
    private final String _sql;
    private final boolean _validSql;
    private final long _firstInvocation;
    private final Map<ByteArrayHashKey, AtomicInteger> _stackTraces = new ReferenceMap<>(ReferenceMap.SOFT, ReferenceMap.HARD, true); // Not sure about purgeValues

    private @Nullable
    List<Object> _parameters = null;  // Keep parameters from the longest running query

    private long _count = 0;
    private long _max = 0;
    private long _cumulative = 0;
    private long _lastInvocation;

    QueryTracker(@Nullable DbScope scope, @NotNull String sql, long elapsed, String stackTrace, boolean validSql)
    {
        _scope = scope;
        _sql = sql;
        _validSql = validSql;
        _firstInvocation = System.currentTimeMillis();

        addInvocation(elapsed, stackTrace);
    }

    public void addInvocation(long elapsed, String stackTrace)
    {
        _count++;
        _cumulative += elapsed;
        _lastInvocation = System.currentTimeMillis();

        if (elapsed > _max)
            _max = elapsed;

        ByteArrayHashKey compressed = new ByteArrayHashKey(Compress.deflate(stackTrace));
        AtomicInteger frequency = _stackTraces.get(compressed);

        if (null == frequency)
            _stackTraces.put(compressed, new AtomicInteger(1));
        else
            frequency.incrementAndGet();
    }

    @Nullable
    public DbScope getScope()
    {
        return _scope;
    }

    public String getSql()
    {
        return _sql;
    }

    public SQLFragment getSQLFragment()
    {
        return null != _parameters ? new SQLFragment(getSql(), _parameters) : new SQLFragment(getSql());
    }

    public String getSqlAndParameters()
    {
        return getSQLFragment().toString();
    }

    public void setParameters(@Nullable List<Object> parameters)
    {
        _parameters = parameters;
    }

    @Nullable
    public List<Object> getParameters()
    {
        return _parameters;
    }

    public boolean canShowExecutionPlan()
    {
        return null != _scope && _scope.getSqlDialect().canShowExecutionPlan() && _validSql && Table.isSelect(_sql);
    }

    public long getCount()
    {
        return _count;
    }

    public long getMax()
    {
        return _max;
    }

    public long getCumulative()
    {
        return _cumulative;
    }

    public long getFirstInvocation()
    {
        return _firstInvocation;
    }

    public long getLastInvocation()
    {
        return _lastInvocation;
    }

    public long getAverage()
    {
        return _cumulative / _count;
    }

    public int getStackTraceCount()
    {
        return _stackTraces.size();
    }

    public void appendStackTraces(StringBuilder sb)
    {
        // Descending order by occurrences (the value)
        Set<Pair<String, AtomicInteger>> set = new TreeSet<>(new Comparator<Pair<String, AtomicInteger>>() {
            public int compare(Pair<String, AtomicInteger> e1, Pair<String, AtomicInteger> e2)
            {
                int compare = e2.getValue().intValue() - e1.getValue().intValue();

                if (0 == compare)
                    compare = e2.getKey().compareTo(e1.getKey());

                return compare;
            }
        });

        // Save the stacktraces separately to find common prefix
        List<String> stackTraces = new LinkedList<>();

        for (Map.Entry<ByteArrayHashKey, AtomicInteger> entry : _stackTraces.entrySet())
        {
            try
            {
                String decompressed = Compress.inflate(entry.getKey().getBytes());
                set.add(new Pair<>(decompressed, entry.getValue()));
                stackTraces.add(decompressed);
            }
            catch (DataFormatException e)
            {
                ExceptionUtil.logExceptionToMothership(null, e);
            }
        }

        int commonLength = 0;
        String formattedCommonPrefix = "";

        if (set.size() > 1)
        {
            String commonPrefix = StringUtilsLabKey.findCommonPrefix(stackTraces);
            int idx = commonPrefix.lastIndexOf('\n');

            if (-1 != idx)
            {
                commonLength = idx;
                formattedCommonPrefix = "<b>" + PageFlowUtil.filter(commonPrefix.substring(0, commonLength), true) + "</b>";
            }
        }

        sb.append("<tr><td>").append("<b>Count</b>").append("</td><td style=\"padding-left:10;\">").append("<b>Traces</b>").append("</td></tr>\n");

        int alt = 0;
        String[] classes = new String[]{"labkey-alternate-row", "labkey-row"};

        for (Map.Entry<String, AtomicInteger> entry : set)
        {
            String stackTrace = entry.getKey();
            String formattedStackTrace = formattedCommonPrefix + PageFlowUtil.filter(stackTrace.substring(commonLength), true);
            int count = entry.getValue().get();

            sb.append("<tr class=\"").append(classes[alt]).append("\"><td valign=top align=right>").append(count).append("</td><td style=\"padding-left:10;\">").append(formattedStackTrace).append("</td></tr>\n");
            alt = 1 - alt;
        }
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        QueryTracker that = (QueryTracker) o;

        return _sql.equals(that._sql);
    }

    @Override
    public int hashCode()
    {
        return _sql.hashCode();
    }

    public static void appendRowHeader(StringBuilder sb, QueryTrackerSet currentSet, QueryProfiler.ActionURLFactory factory)
    {
        sb.append("  <tr>");

        for (QueryTrackerSet set : QueryProfiler.getInstance()._trackerSets)
            if (set.shouldDisplay())
                appendColumnHeader(set.getCaption(), set == currentSet, sb, factory);

        sb.append("<td>");
        sb.append("Traces");
        sb.append("</td><td style=\"padding-left:10;\">");
        sb.append("SQL");
        sb.append("</td>");
        sb.append("<td>");
        sb.append("SQL&nbsp;With&nbsp;Parameters");
        sb.append("</td>");
        sb.append("</tr>\n");
    }

    private static void appendColumnHeader(String name, boolean highlight, StringBuilder sb, QueryProfiler.ActionURLFactory factory)
    {
        sb.append("<td><a href=\"");
        sb.append(PageFlowUtil.filter(factory.getActionURL(name)));
        sb.append("\">");

        if (highlight)
            sb.append("<b>");

        sb.append(name);

        if (highlight)
            sb.append("</b>");

        sb.append("</a></td>");
    }

    public static void exportRowHeader(PrintWriter pw)
    {
        String tab = "";

        for (QueryTrackerSet set : QueryProfiler.getInstance()._trackerSets)
        {
            if (set.shouldDisplay())
            {
                pw.print(tab);
                pw.print(set.getCaption());
                tab = "\t";
            }
        }

        pw.print(tab);
        pw.println("SQL");
        pw.print(tab);
        pw.println("SQL With Parameters");
    }

    public void insertRow(StringBuilder sb, String className, QueryProfiler.ActionURLFactory factory)
    {
        StringBuilder row = new StringBuilder();
        row.append("  <tr class=\"").append(className).append("\">");

        for (QueryTrackerSet set : QueryProfiler.getInstance()._trackerSets)
            if (set.shouldDisplay())
                row.append("<td valign=top align=right>").append(((QueryTrackerComparator) set.comparator()).getFormattedPrimaryStatistic(this)).append("</td>");

        ActionURL url = factory.getActionURL(getSql());
        row.append("<td valign=top align=right><a href=\"").append(PageFlowUtil.filter(url.getLocalURIString())).append("\">").append(Formats.commaf0.format(getStackTraceCount())).append("</a></td>");
        row.append("<td style=\"padding-left:10;\">").append(PageFlowUtil.filter(getSql(), true)).append("</td>");
        row.append("<td style=\"padding-left:10;\">").append(PageFlowUtil.filter(getSqlAndParameters(), true)).append("</td>");
        row.append("</tr>\n");
        sb.insert(0, row);
    }

    public void exportRow(StringBuilder sb)
    {
        StringBuilder row = new StringBuilder();
        String tab = "";

        for (QueryTrackerSet set : QueryProfiler.getInstance()._trackerSets)
        {
            if (set.shouldDisplay())
            {
                row.append(tab).append((((QueryTrackerComparator)set.comparator()).getFormattedPrimaryStatistic(this)));
                tab = "\t";
            }
        }

        row.append(tab).append(getSql().trim().replaceAll("(\\s)+", " "));
        row.append(tab).append(getSqlAndParameters().trim().replaceAll("(\\s)+", " ")).append("\n");
        sb.insert(0, row);
    }
}
