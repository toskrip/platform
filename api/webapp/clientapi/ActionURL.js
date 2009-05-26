/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 8.1
 * @license Copyright (c) 2008 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 */

/**
 * @namespace ActionURL static class to supply the current context path, container and action.
 *            Additionally, builds a URL from a controller and an action.
 */
LABKEY.ActionURL = new function()
{
    // private member variables

    // private functions
    function buildParameterMap()
    {
        var paramString = window.location.search;
        if (paramString.charAt(0) == '?')
            paramString = paramString.substring(1, paramString.length);
        var paramArray = paramString.split('&');
        var parameters = {};
        for (var i = 0; i < paramArray.length; i++)
        {
            var nameValue = paramArray[i].split('=');
            if (nameValue.length == 2)
                parameters[decodeURIComponent(nameValue[0])] = decodeURIComponent(nameValue[1]);
        }
        return parameters;
    }

    /** @scope LABKEY.ActionURL.prototype */
    return {
        // public functions

        /**
        * Gets the current context path.  The default context path for LabKey Server is '/labkey'.
		* @return {String} Current context path.
		*/
        getContextPath : function()
        {
            return LABKEY.contextPath;
        },

		/**
		* Gets the current action
		* @return {String} Current action.
		*/
        getAction : function()
        {
            var path = window.location.pathname;
            i = path.lastIndexOf("/");
            path = path.substring(i + 1);
            i = path.indexOf('.');
            return path.substring(0, i);
        },

		/**
		* Gets the current container
		* @return {String} Current container.
		*/
        getContainer : function()
        {
            var path = window.location.pathname;
            var start = path.indexOf("/", this.getContextPath().length + 1);
            var end = path.lastIndexOf("/");
            return path.substring(start, end);
        },

        /**
        * Gets a URL parameteter by name
        * @return {String} The value of the named parameter, or undefined of the parameter is not present.
        */
        getParameter : function(parameterName)
        {
            return buildParameterMap()[parameterName];
        },

        /**
        * Returns an object mapping URL parameter names to parameter values.
        * @return {Object} Map of parameter names to values.
        */
        getParameters : function()
        {
            return buildParameterMap();
        },

        /**
		* Builds a URL from a controller and an action.  Uses the current container and context path.
		* @param {String} controller The controller to use in building the URL
		* @param {String} action The action to use in building the URL
		* @param {String} [containerPath] The container path to use (defaults to the current container)
		* @param {Object} [parameters] An object with properties corresponding to GET parameters to append to the URL.
		* Parameters will be encoded automatically. (Defaults to no parameters)
		* @example Examples:

1. Build the URL for the 'plotChartAPI' action in the 'reports' controller within 
the current container:
	var url = LABKEY.ActionURL.buildURL("reports", "plotChartApi");

2.  Build the URL for the 'getWebPart' action in the 'reports' controller within 
the current container:
	var url = LABKEY.ActionURL.buildURL("project", "getWebPart");

3.  Build the URL for the 'updateRows' action in the 'query' controller within
the container "My Project/My Folder":
	var url = LABKEY.ActionURL.buildURL("query", "updateRows",
	                         "My Project/My Folder");
		* @return {String} URL constructed from the current container and context path,
					plus the specified controller and action.
		*/
        buildURL : function(controller, action, containerPath, parameters)
        {
            if(!containerPath)
                containerPath = this.getContainer();

            //ensure that container path begins and ends with a /
            if(containerPath.charAt(0) != "/")
                containerPath = "/" + containerPath;
            if(containerPath.charAt(containerPath.length - 1) != "/")
                containerPath = containerPath + "/";

            var newUrl = this.getContextPath() + "/" + controller + containerPath + action;
            if (-1 == action.indexOf('.'))
                newUrl += '.view';
            if (parameters)
            {
                newUrl += '?';
                for (var parameter in parameters)
                    newUrl += encodeURI(parameter) + '=' + encodeURI(parameters[parameter]) + '&';
                newUrl = newUrl.substring(0, newUrl.length - 1);
            }
            return newUrl;
        }
    };
};

