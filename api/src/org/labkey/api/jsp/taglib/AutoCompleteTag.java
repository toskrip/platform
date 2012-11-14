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
public abstract class AutoCompleteTag extends SimpleTagBase
{
    private String _name;
    private String _id;
    private String _url;
    private String _value;

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getId()
    {
        return _id;
    }

    public void setId(String id)
    {
        _id = id;
    }

    public String getUrl()
    {
        return _url;
    }

    public void setUrl(String url)
    {
        _url = url;
    }

    public String getValue()
    {
        return _value;
    }

    public void setValue(String value)
    {
        _value = value;
    }

    @Override
    public void doTag() throws JspException, IOException
    {
        String renderId = "auto-complete-div-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
        StringBuilder sb = new StringBuilder();

        sb.append("<script type=\"text/javascript\">");
        sb.append("LABKEY.requiresExt4Sandbox(true);\n");
        sb.append("LABKEY.requiresScript('completion.js');\n");

        sb.append("Ext4.onReady(function(){\n" +
            "        Ext4.create('LABKEY.element.AutoCompletionField', {\n" +
            "            renderTo        : " + PageFlowUtil.jsString(renderId) + ",\n" +
            "            completionUrl   : " + PageFlowUtil.jsString(getUrl()) + ",\n");
        sb.append(getTagConfig());
        sb.append("  });\n" +
            "    });\n");

        sb.append("</script>\n");
        sb.append("<div id='").append(renderId).append("'></div>");

        JspWriter out = getOut();
        out.write(sb.toString());
    }

    protected abstract String getTagConfig();

    protected void addOptionalAttrs(StringBuilder sb)
    {
        // optional attribute
        if (getId() != null)
            sb.append("                id : ").append(PageFlowUtil.jsString(getId())).append(",\n");
    }
}
