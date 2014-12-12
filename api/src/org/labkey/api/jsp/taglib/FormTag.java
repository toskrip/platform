/*
 * Copyright (c) 2014 LabKey Corporation
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
import org.labkey.api.util.CSRFUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.BodyTagSupport;
import java.io.IOException;

public class FormTag extends BodyTagSupport
{
    private String name=null;
    private String method="GET";
    private Object action=null;
    private String enctype = null;
    private String target=null;
    private String onsubmit=null;
    private String style=null;
    private String _class=null;

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getMethod()
    {
        return method;
    }

    public void setMethod(String method)
    {
        this.method = method.toUpperCase();
    }

    public Object getAction()
    {
        return action;
    }

    public void setAction(Object action)
    {
        this.action = action;
    }

    public String getEnctype()
    {
        return enctype;
    }

    public void setEnctype(String enctype)
    {
        this.enctype = enctype;
    }

    public String getTarget()
    {
        return target;
    }

    public void setTarget(String target)
    {
        this.target = target;
    }

    public String getOnsubmit()
    {
        return onsubmit;
    }

    public void setOnsubmit(String onsubmit)
    {
        this.onsubmit = onsubmit;
    }

    public String getStyle()
    {
        return style;
    }

    public void setStyle(String style)
    {
        this.style = style;
    }

    public void setClass(String aClass)
    {
        _class = aClass;
    }

    public void setClassName(String aClass)
    {
        _class = aClass;
    }

    public int doStartTag() throws JspException
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<form");
        if (StringUtils.isNotEmpty(getId()))
            sb.append(" id=\"").append(getId()).append("\"");
        if (StringUtils.isNotEmpty(name))
            sb.append(" name=\"").append(name).append("\"");
        if (StringUtils.isNotEmpty(method))
            sb.append(" method=\"").append(method).append("\"");
        if (null != action)
        {
            String s;
            if (action instanceof URLHelper)
                s = PageFlowUtil.filter(((URLHelper)action));
            else
                s = String.valueOf(action);
            sb.append(" action=\"").append(s).append("\"");
        }
        if (StringUtils.isNotEmpty(enctype))
            sb.append(" enctype=\"").append(enctype).append("\"");
        if (StringUtils.isNotEmpty(target))
            sb.append(" target=\"").append(target).append("\"");
        if (StringUtils.isNotEmpty(onsubmit))
            sb.append(" onsubmit=\"").append(onsubmit).append("\"");
        if (StringUtils.isNotEmpty(style))
            sb.append(" style=\"").append(style).append("\"");
        if (StringUtils.isNotEmpty(_class))
            sb.append(" class=\"").append(_class).append("\"");
        sb.append(">");
        try
        {
            pageContext.getOut().write(sb.toString());
        }
        catch (IOException e)
        {
            throw new JspException(e);
        }
        return BodyTagSupport.EVAL_BODY_INCLUDE;
    }


    public int doEndTag() throws JspException
    {
        try
        {
            String csrf = CSRFUtil.getExpectedToken(pageContext);
            if (StringUtils.equals("POST", method))
            {
                pageContext.getOut().write("<input type=hidden name='" + CSRFUtil.csrfName + "' value='" + PageFlowUtil.filter(csrf) + "'>");
            }
            pageContext.getOut().write("</form>");
        }
        catch (IOException e)
        {
            throw new JspException(e);
        }
        return BodyTagSupport.EVAL_PAGE;
    }
}
