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
package org.labkey.api.jsp;

import org.apache.jasper.JspC;
import org.apache.log4j.Logger;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ResourceFinder;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileUtil;

import javax.servlet.ServletContext;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * User: adam
 * Date: Oct 25, 2008
 * Time: 4:12:44 PM
 */
public class RecompilingJspClassLoader extends JspClassLoader
{
    private static Logger _log = Logger.getLogger(JspClassLoader.class);
    private static final String JSP_PATH = "/jspTempDir/classes";
    private static final String JSP_PACKAGE_PATH = JSP_PACKAGE.replaceAll("\\.", "/");
    private static final Map<ResourceFinder, ClassLoader> _classLoaders = new HashMap<ResourceFinder, ClassLoader>();
    private static final boolean TEST = false;          // Set to true to force a re-compile of each JSP the first time it's encountered
    private static final Set<String> _compiledJsps = new HashSet<String>();    // Used during test mode

    @Override
    public Class loadClass(ServletContext context, String packageName, String jspFileName) throws ClassNotFoundException
    {
        String compiledJspPath = getCompiledJspPath(packageName, jspFileName);
        Collection<ResourceFinder> finders = ModuleLoader.getInstance().getResourceFindersForPath(compiledJspPath);

        if (null == finders)
        {
            _log.info("No ResourceFinders for " + compiledJspPath);
        }
        else
        {
            for (ResourceFinder finder : finders)
            {
                File jspTempBuildDirectory = new File(finder.getBuildPath() + JSP_PATH);
                File classFile = new File(jspTempBuildDirectory, JSP_PACKAGE_PATH + compiledJspPath + ".class");

                if (classFile.exists())
                    return getCompiledClassFile(classFile, jspTempBuildDirectory, finder, packageName, jspFileName);
            }
        }

        _log.warn("Can't load " + compiledJspPath);
        return super.loadClass(context, packageName, jspFileName);
    }


    private Class getCompiledClassFile(File classFile, File jspTempBuildDirectory, ResourceFinder finder, String packageName, String jspFileName)
    {
        String relativePath = getSourceJspPath(packageName, jspFileName);
        // Create File object for JSP source
        String sourcePath = finder.getSourcePath() + "/src" + relativePath;
        File sourceFile = new File(sourcePath);

        try
        {
            String className = getJspClassName(packageName, jspFileName);

            synchronized(_classLoaders)
            {
                // Is source more recent than compiled class?
                boolean requiresRecompile = sourceFile.exists() && sourceFile.lastModified() > classFile.lastModified();
                boolean requiresTestRecompile = TEST && !_compiledJsps.contains(sourcePath);

                if (requiresRecompile || requiresTestRecompile)
                {
                    _log.info("Recompiling " + relativePath);

                    // Copy .jsp file from source to build staging directory
                    File stagingJsp = new File(jspTempBuildDirectory.getParent() + "/webapp", relativePath);
                    FileUtil.copyFile(sourceFile, stagingJsp);

                    ClassPath cp = new ClassPath();
                    cp.addDirectory(new File(finder.getBuildPath(), "/explodedModule/lib"));
                    cp.addDirectory(getTomcatCommonLib());
                    cp.addDirectory(getWebInfLib());
                    cp.addDirectory(getWebInfClasses());

                    // Compile the .jsp file
                    JspC jasper = new JspC();
                    jasper.setValidateXml(false);
                    jasper.setUriroot(jspTempBuildDirectory.getParent() + "/webapp");
                    jasper.setOutputDir(jspTempBuildDirectory.getAbsolutePath());
                    jasper.setPackage("org.labkey.jsp.compiled");
                    jasper.setCompilerTargetVM("1.5");
                    jasper.setCompilerSourceVM("1.5");
                    jasper.setTrimSpaces(false);
                    jasper.setCompile(false);
                    jasper.setListErrors(true);

                    if (relativePath.startsWith("/"))
                        relativePath = relativePath.substring(1);
                    jasper.setJspFiles(relativePath);
                    jasper.setClassPath(cp.getPath());
                    jasper.execute();

                    // Compile the _jsp.java file
                    String stagingJava = classFile.getAbsolutePath().replaceFirst("\\.class", ".java");
                    compileJavaFile(stagingJava, cp.getPath());

                    _classLoaders.remove(finder);

                    if (TEST)
                        _compiledJsps.add(sourcePath);
                }

                ClassLoader loader = _classLoaders.get(finder);

                if (null == loader)
                {
                    // Convert directory to a URL
                    URL url = jspTempBuildDirectory.toURL();
                    loader = new URLClassLoader(new URL[]{url}, Thread.currentThread().getContextClassLoader());
                    _classLoaders.put(finder, loader);
                }

                return loader.loadClass(className);
            }
       }
       catch (Exception e)
       {
           throw new RuntimeException(e);
       }
    }


    private void compileJavaFile(String filePath, String classPath) throws Exception
    {
        /*
            This is the code I want to execute here:

                JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
                compiler.run(null, null, null, stagingJava, "-cp", cp.getPath());

            ...but these are Java 1.6 specific classes and we still need to compile on Java 1.5, so use reflection instead.
        */

        Class<?> clazz = Class.forName("javax.tools.ToolProvider");
        Method getSystemJavaCompilerMethod = clazz.getMethod("getSystemJavaCompiler");
        Object javaCompiler = getSystemJavaCompilerMethod.invoke(null);
        Method compileJavaMethod = javaCompiler.getClass().getMethod("run", InputStream.class, OutputStream.class, OutputStream.class, String[].class);
        compileJavaMethod.invoke(javaCompiler, null, null, null, new String[]{filePath, "-cp", classPath});
    }


    private static class ClassPath
    {
        private final String SEP = System.getProperty("path.separator");
        private StringBuilder _path = new StringBuilder();

        private void addFile(String filePath)
        {
            _path.append(filePath).append(SEP);
        }

        private void addFile(File file)
        {
            addFile(file.getAbsolutePath());
        }

        private void addDirectory(String dirPath)
        {
            addDirectory(new File(dirPath));
        }

        private void addDirectory(File dir)
        {
            if (dir.exists())
            {
                assert dir.isDirectory();

                for (File file : dir.listFiles())
                    addFile(file);
            }
        }

        private String getPath()
        {
            return _path.toString();
        }
    }

    private String getTomcatCommonLib()
    {
        String tomcat = System.getenv("CATALINA_HOME");

        if (null == tomcat)
            tomcat = System.getenv("TOMCAT_HOME");

        if (tomcat == null)
        {
            _log.warn("Could not find CATALINA_HOME environment variable, unlikely to be successful recompiling JSPs");
        }

        return tomcat + "/common/lib";
    }

    private String getWebInfLib()
    {
        return AppProps.getInstance().getProjectRoot() + "/build/deploy/labkeyWebapp/WEB-INF/lib";
    }

    private String getWebInfClasses()
    {
        return AppProps.getInstance().getProjectRoot() + "/build/deploy/labkeyWebapp/WEB-INF/classes";
    }
}
