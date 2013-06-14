package org.labkey.api.data;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;

/**
 * User: adam
 * Date: 6/13/13
 * Time: 2:18 PM
 */
public class JavaScriptDisplayColumn extends DataColumn
{
    private final Collection<String> _dependencies;  // TODO: DisplayColumn implements getClientDependencies()?
    private final String _javaScriptEvents;

    public JavaScriptDisplayColumn(ColumnInfo col, Collection<String> dependencies, String javaScriptEvents)
    {
        super(col);
        _dependencies = dependencies;
        _javaScriptEvents = javaScriptEvents;
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Object o = getValue(ctx);

        if (null != o)
        {
            out.write("<a href=\"#\" tabindex=\"-1\" ");
            out.write(_javaScriptEvents);
            out.write("\">");
            out.write(getFormattedValue(ctx));
            out.write("</a>");
        }
        else
            out.write("&nbsp;");
    }
}
