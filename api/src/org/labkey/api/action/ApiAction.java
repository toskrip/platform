/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.action;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.BufferedReader;
import java.util.Iterator;

/**
 * Base class for API actions.
 *
 * Created by IntelliJ IDEA.
 * User: Dave
 * Date: Feb 8, 2008
 * Time: 1:14:43 PM
 */
public abstract class ApiAction<FORM> extends BaseViewAction<FORM>
{
    private ApiResponseWriter.Format _reqFormat = null;
    private ApiResponseWriter.Format _respFormat = ApiResponseWriter.Format.JSON;
    private String _contentTypeOverride = null;

    public ApiAction()
    {
    }

    public ApiAction(Class<? extends FORM> formClass)
    {
        super(formClass);
    }

    protected String getCommandClassMethodName()
    {
        return "execute";
    }

    public ModelAndView handleRequest() throws Exception
    {
        FORM form = null;
        BindException errors = null;

        try
        {
            String contentType = getViewContext().getRequest().getContentType();
            if(null != contentType && contentType.contains(ApiJsonWriter.CONTENT_TYPE_JSON))
            {
                _reqFormat = ApiResponseWriter.Format.JSON;
                JSONObject jsonObj = getJsonObject();
                form = getCommand();
                populateForm(jsonObj, form);
                errors = new BindException(form, "form");
            }
            else
            {
                if (null != getCommandClass())
                {
                    errors = defaultBindParameters(getCommand(), getPropertyValues());
                    form = (FORM)errors.getTarget();
                }
            }

            //validate the form
            validate(form, errors);

            //if we had binding or validation errors,
            //return them without calling execute.
            if(null != errors && errors.hasErrors())
                createResponseWriter().write((Errors)errors);
            else
            {
                ApiResponse response = execute(form, errors);
                if(null != response)
                    createResponseWriter().write(response);
            }
        }
        catch (Exception e)
        {
            Logger.getLogger(ApiAction.class).warn("ApiAction exception: ", e);
            createResponseWriter().write(e);
        }

        return null;
    } //handleRequest()

    protected JSONObject getJsonObject() throws Exception
    {
        //read the JSON into a buffer
        //unfortunately the json.org classes can't read directly from a stream!
        char[] buf = new char[2048];
        int chars;
        StringBuffer json = new StringBuffer();
        BufferedReader reader = getViewContext().getRequest().getReader();

        while((chars = reader.read(buf)) > 0)
            json.append(buf, 0, chars);

        String jsonString = json.toString();
        if(null == jsonString || jsonString.length() <= 0)
            return null;

        //deserialize the JSON
        return new JSONObject(jsonString);
    }

    protected void populateForm(JSONObject jsonObj, FORM form) throws Exception
    {
        if(null == jsonObj)
            return;

        if(form instanceof ApiJsonForm)
            ((ApiJsonForm)form).setJsonObject(jsonObj);
        else
            populateForm(jsonObj, form, null);
    }

    protected void populateForm(JSONObject jsonObj, FORM form, String parentProperty) throws Exception
    {
        Iterator keys = jsonObj.keys();
        while(keys.hasNext())
        {
            String key = (String)keys.next();
            String beanKey = null == parentProperty ? key : parentProperty + "." + key;
            Object value = jsonObj.get(key);

            if(value instanceof JSONArray)
            {
                JSONArray array = (JSONArray)value;
                for(int idx = 0; idx < array.length(); ++idx)
                {
                    String entryKey = beanKey + "[" + String.valueOf(idx) + "]";
                    Object entry = array.get(idx);
                    if(entry instanceof JSONObject)
                        populateForm((JSONObject)entry, form, entryKey);
                    else
                        BeanUtils.setProperty(form, entryKey, entry);
                }
            }
            else if(value instanceof JSONObject)
            {
                if(!value.equals(JSONObject.NULL))
                    populateForm((JSONObject)value, form, beanKey);
            }
            else if(value.equals(JSONObject.NULL))
            {
                BeanUtils.setProperty(form, beanKey, null);
            }
            else
                BeanUtils.setProperty(form, beanKey, value);

        }
    }

    public void validate(Object form, Errors errors)
    {
        validateForm((FORM)form, errors);
    }

    /**
     * Override to validate the form bean and populate the Errors collection as necessary.
     * The default implementation does nothing, so override this method to perform validation.
     *
     * @param form The form bean
     * @param errors The errors collection
     */
    public void validateForm(FORM form, Errors errors)
    {
    }

    public ApiResponseWriter createResponseWriter()
    {
        //for now, always return a JSON writer.
        //in the future, look at the posted content-type, or a query string param
        //to determine which format to create
        return new ApiJsonWriter(getViewContext().getResponse(), getContentTypeOverride());
    }

    public ApiResponseWriter.Format getResponseFormat()
    {
        return _respFormat;
    }

    public ApiResponseWriter.Format getRequestFormat()
    {
        return _reqFormat;
    }

    public String getContentTypeOverride()
    {
        return _contentTypeOverride;
    }

    public void setContentTypeOverride(String contentTypeOverride)
    {
        _contentTypeOverride = contentTypeOverride;
    }

    public abstract ApiResponse execute(FORM form, BindException errors) throws Exception;
}
