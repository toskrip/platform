/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.api.jsp.taglib;

import org.apache.commons.lang3.StringUtils;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.BodyTagSupport;
import java.io.IOException;

public class PanelTag extends BodyTagSupport
{
    private String className = null;
    private String id = null;
    private String type = "default";

    public String getClassName()
    {
        return className;
    }

    public void setClassName(String className)
    {
        this.className = className;
    }

    @Override
    public String getId()
    {
        return id;
    }

    @Override
    public void setId(String id)
    {
        this.id = id;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public int doStartTag() throws JspException
    {
        StringBuilder sb = new StringBuilder();

        sb.append("<div class=\"panel panel-" + getType());
        if (StringUtils.isNotEmpty(getClassName()))
            sb.append(" " + getClassName().trim());
        sb.append("\"");

        if (StringUtils.isNoneEmpty(getId()))
            sb.append(" id=\"" + getId() + "\"");
        sb.append(">");

        sb.append("<div class=\"panel-body\">");

        write(sb);
        return BodyTagSupport.EVAL_BODY_INCLUDE;
    }

    public int doEndTag() throws JspException
    {
        StringBuilder sb = new StringBuilder();

        sb.append("</div></div>");

        write(sb);
        return BodyTagSupport.EVAL_PAGE;
    }

    private void write(StringBuilder sb) throws JspException
    {
        try
        {
            pageContext.getOut().write(sb.toString());
        }
        catch (IOException e)
        {
            throw new JspException(e);
        }
    }
}
