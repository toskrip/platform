/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.data;

import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.query.LookupURLExpression;

import java.io.Writer;
import java.io.IOException;
import java.util.Set;

public class UrlColumn extends SimpleDisplayColumn
{
    public UrlColumn(StringExpressionFactory.StringExpression urlExpression, String text)
    {
        setDisplayHtml(text);
        setURL(urlExpression);
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Object value = getValue(ctx);
        String url = getURL(ctx);
        if (value != null && url != null)
        {
            out.write("[<a href=\"");
            out.write(PageFlowUtil.filter(url));
            out.write("\">");
            out.write(value.toString());
            out.write("</a>]");
        }
    }

    public void addQueryColumns(Set<ColumnInfo> set)
    {
        if (getURLExpression() instanceof LookupURLExpression)
        {
            set.addAll(((LookupURLExpression)getURLExpression()).getQueryColumns());
        }
    }
}
