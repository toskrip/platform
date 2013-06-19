/*
 * Copyright (c) 2008-2012 LabKey Corporation
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
package org.labkey.api.jsp;

import org.apache.log4j.Logger;
import org.labkey.api.module.ModuleLoader;

import javax.servlet.ServletContext;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * User: adam
 * Date: Oct 25, 2008
 * Time: 2:45:22 PM
 */

// Production JSP loader -- loads pre-compiled classes from JSP JAR files.  No reloading or auto-recompiling.
public class JspClassLoader
{
    private static final Logger _log = Logger.getLogger(JspClassLoader.class);
    protected static final String JSP_PACKAGE = "org.labkey.jsp.compiled";

    private transient ClassLoader _loader;

    JspClassLoader()
    {
        ServletContext context = ModuleLoader.getServletContext();
        Set<String> paths = context.getResourcePaths("/WEB-INF/jsp/");
        List<URL> urls = new ArrayList<>();
        for (String path : paths)
        {
            File file = new File(context.getRealPath(path));
            try
            {
                urls.add(file.toURL());
            }
            catch (MalformedURLException mURLe)
            {
                _log.error("initLoader exception", mURLe);
            }
        }
        _loader = new URLClassLoader(urls.toArray(new URL[urls.size()]), Thread.currentThread().getContextClassLoader());
    }

    public Class loadClass(ServletContext context, String packageName, String jspFile) throws ClassNotFoundException
    {
        String className = getJspClassName(packageName, jspFile);
        return _loader.loadClass(className);
    }

    protected String getJspClassName(String packageName, String jspFile)
    {
        return JSP_PACKAGE + getCompiledJspPath(packageName, jspFile).replaceAll("/", "\\.");
    }

    protected String getCompiledJspPath(String packageName, String jspFile)
    {
        //NOTE: jasper encodes underscores in the filepath, so we account for this here
        jspFile = jspFile.replaceAll("_", "_005f");
        return getSourceJspPath(packageName, jspFile.replaceAll("\\.", "_"));
    }

    protected String getSourceJspPath(String packageName, String jspFile)
    {
        StringBuilder ret = new StringBuilder();
        if (packageName != null)
        {
            ret.append("/").append(packageName.replaceAll("\\.", "/")).append("/");
        }
        else
        {
            if (!jspFile.startsWith("/"))
                throw new IllegalArgumentException("Path must start with '/' if no package defined.");
        }
        ret.append(jspFile);
        return ret.toString();
    }
}
