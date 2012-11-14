package org.labkey.api.jsp.taglib;

import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UniqueID;
import org.labkey.api.view.HttpView;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 11/12/12
 */
public class AutoCompleteTextTag extends AutoCompleteTag
{
    private String _type;
    private int _size = 30;

    public String getType()
    {
        return _type;
    }

    public void setType(String type)
    {
        _type = type;
    }

    public int getSize()
    {
        return _size;
    }

    public void setSize(int size)
    {
        _size = size;
    }

    @Override
    protected String getTagConfig()
    {
        StringBuilder sb = new StringBuilder();

        sb.append("      tagConfig   : {\n" +
            "                tag     : 'input',\n" +
            "                type    : 'text',\n");
        addOptionalAttrs(sb);
        sb.append(
            "                name    : " + PageFlowUtil.jsString(getName()) + ",\n" +
            "                size    : " + getSize() + ",\n" +
            "                autocomplete : 'off'\n" +
            "            }\n");

        return sb.toString();
    }

    protected void addOptionalAttrs(StringBuilder sb)
    {
        super.addOptionalAttrs(sb);

        if (getValue() != null)
            sb.append("                value : ").append(PageFlowUtil.jsString(getValue())).append(",\n");
    }
}
