package org.labkey.api.laboratory.assay;

import org.apache.log4j.Level;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.view.ViewContext;

import java.io.File;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 1/9/13
 * Time: 5:12 PM
 */
public class ImportContext
{
    private JSONObject _json;
    private File _file;
    private String _fileName;
    private ViewContext _ctx;
    private ParserErrors _errors;

    public ImportContext(JSONObject json, File file, String fileName, ViewContext ctx)
    {
        _json = json;
        _file = file;
        _fileName = fileName;
        _ctx = ctx;

        Level level = Level.ALL;
        if (json != null && json.containsKey("errorLevel"))
        {
            level = Level.toLevel((String)json.get("errorLevel"));
        }
        _errors = new ParserErrors(level);
    }

    public JSONObject getJson()
    {
        return _json;
    }

    public File getFile()
    {
        return _file;
    }

    public String getFileName()
    {
        return _fileName;
    }

    public ViewContext getViewContext()
    {
        return _ctx;
    }

    public JSONArray getResultRowsFromJson()
    {
        if (_json.has("ResultRows"))
        {
            return _json.getJSONArray("ResultRows");
        }

        return null;
    }

    public JSONObject getPromotedResultsFromJson()
    {
        if (_json.has("Results"))
        {
            return _json.getJSONObject("Results");
        }

        return null;
    }

    public Integer getTemplateIdFromJson()
    {
        return _json.getInt("TemplateId");
    }

    public ParserErrors getErrors()
    {
        return _errors;
    }

    public JSONObject getRunProperties()
    {
        return _json.getJSONObject("Run");
    }
}
