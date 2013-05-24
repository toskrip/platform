/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
package org.labkey.api.module;

import org.apache.commons.collections15.Closure;
import org.apache.commons.collections15.CollectionUtils;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.WebPartFactory;
import org.labkey.data.xml.TablesDocument;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/*
* User: Dave
* Date: Dec 3, 2008
* Time: 4:08:20 PM
*/

/**
 * Used for simple, entirely file-based modules
 */
public class SimpleModule extends SpringModule
{
    private static final Logger _log = Logger.getLogger(ModuleUpgrader.class);

    public static String NAMESPACE_PREFIX = "ExtensibleTable";
    public static String DOMAIN_NAMESPACE_PREFIX_TEMPLATE = NAMESPACE_PREFIX + "-${SchemaName}";
    public static String DOMAIN_LSID_TEMPLATE = "${FolderLSIDBase}:${TableName}";

    public static String PROPERTY_NAMESPACE_PREFIX_TEMPLATE = NAMESPACE_PREFIX + "-${SchemaName}-${TableName}";
    public static String PROPERTY_LSID_TEMPLATE = "${FolderLSIDBase}:${GUID}";

    int _factorySetHash = 0;
    private Set<String> _schemaNames;

    public SimpleModule()
    {
    }

    @Deprecated
    public SimpleModule(String name)
    {
        setName(name);
    }

    protected void init()
    {
        if (getName() == null || getName().length() == 0)
            throw new ConfigurationException("Simple module must have a name");

        getSchemaNames(true);
        addController(getName().toLowerCase(), SimpleController.class);
    }

    @Override
    public Controller getController(@Nullable HttpServletRequest request, Class controllerClass)
    {
        return new SimpleController(getName().toLowerCase());
    }

    protected Collection<WebPartFactory> createWebPartFactories()
    {
        List<WebPartFactory> factories = new ArrayList<WebPartFactory>();
        for(File webPartFile : getWebPartFiles())
        {
            factories.add(new SimpleWebPartFactory(this, webPartFile));
        }
        _factorySetHash = calcFactorySetHash();
        return factories;
    }

    @NotNull
    protected File[] getWebPartFiles()
    {
        File viewsDir = new File(getExplodedPath(), SimpleController.VIEWS_DIRECTORY);
        return viewsDir.exists() && viewsDir.isDirectory() ? viewsDir.listFiles(SimpleWebPartFactory.webPartFileFilter) : new File[0];
    }

    public boolean isWebPartFactorySetStale()
    {
        return _factorySetHash != calcFactorySetHash();
    }

    protected int calcFactorySetHash()
    {
        return Arrays.hashCode(getWebPartFiles());
    }

    public boolean hasScripts()
    {
        return getSqlScripts(null, CoreSchema.getInstance().getSqlDialect()).size() > 0;
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return getSchemaNames(false);
    }

    private Set<String> getSchemaNames(final boolean throwOnError)
    {
        if (_schemaNames == null)
        {
            Resource schemasDir = getModuleResource(QueryService.MODULE_SCHEMAS_DIRECTORY);
            if (schemasDir != null && schemasDir.isCollection())
            {
                final Set<String> schemaNames = new LinkedHashSet<String>();
                CollectionUtils.forAllDo(schemasDir.list(), new Closure<Resource>() {
                    @Override
                    public void execute(Resource resource)
                    {
                        String name = resource.getName();
                        if (name.endsWith(".xml") && !name.endsWith(QueryService.SCHEMA_TEMPLATE_EXTENSION))
                        {
                            try
                            {
                                TablesDocument.Factory.parse(resource.getInputStream());
                                String schemaName = name.substring(0, name.length() - ".xml".length());
                                schemaNames.add(schemaName);
                            }
                            catch (XmlException | IOException e)
                            {
                                if (throwOnError)
                                    throw new ConfigurationException("Error in '" + name + "' schema file: " + e.getMessage());
                                else
                                    _log.error("Skipping '" + name + "' schema file: " + e.getMessage());
                            }
                        }
                    }
                });
                _schemaNames = Collections.unmodifiableSet(schemaNames);
            }
            else
            {
                _schemaNames = Collections.emptySet();
            }
        }
        return _schemaNames;
    }


    @NotNull
    @Override
    public Set<DbSchema> getSchemasToTest()
    {
        SupportedDatabase db = CoreSchema.getInstance().getSqlDialect().isSqlServer() ?
                SupportedDatabase.mssql : SupportedDatabase.pgsql;
        if (getSupportedDatabasesSet().contains(db))
            return super.getSchemasToTest();
        return Collections.emptySet();
    }


    @Override
    protected void startupAfterSpringConfig(ModuleContext moduleContext)
    {
        registerSchemas();
        registerContainerListeners();
    }

    protected void registerContainerListeners()
    {
        ContainerManager.addContainerListener(new SimpleModuleContainerListener(this));
    }

    protected void registerSchemas()
    {
        for (final String schemaName : getSchemaNames())
        {
            DefaultSchema.registerProvider(schemaName, new DefaultSchema.SchemaProvider()
            {
                public QuerySchema getSchema(final DefaultSchema schema)
                {
                    if (schema.getContainer().getActiveModules().contains(SimpleModule.this))
                    {
                        DbSchema dbschema = DbSchema.get(schemaName);
                        return QueryService.get().createSimpleUserSchema(schemaName, null, schema.getUser(), schema.getContainer(), dbschema);
                    }
                    return null;
                }
            });
        }
    }
    protected String getResourcePath()
    {
        return null;
    }

    public ActionURL getTabURL(Container c, User user)
    {
        return SimpleController.getBeginViewUrl(this, c);
    }

    @Override
    public Collection<String> getSummary(Container c)
    {
        Collection<String> summary = new LinkedList<>();

        User user = HttpView.currentContext().getUser();

        Filter containerFilter = SimpleFilter.createContainerFilter(c);
        Filter folderFilter = new SimpleFilter(new FieldKey(null, "Folder"), c);

        for (String schemaName : getSchemaNames())
        {
            UserSchema schema = QueryService.get().getUserSchema(user, c, schemaName);
            if (schema != null && !schema.isHidden())
            {
                for (String tableName : schema.getVisibleTableNames())
                {
                    TableInfo table = schema.getTable(tableName, false);
                    if (table != null)
                    {
                        Filter filter = null;
                        if (table.getColumn("Container") != null)
                            filter = containerFilter;
                        else if (table.getColumn("Folder") != null)
                            filter = folderFilter;

                        if (filter != null)
                        {
                            long count = new TableSelector(table, containerFilter, null).getRowCount();
                            if (count > 0)
                                summary.add(String.format("%d %s from %s.%s", count, (count == 1 ? "row" : "rows"), schema.getSchemaPath().toDisplayString(), table.getName()));
                        }
                    }
                }
            }
        }

        return summary;
    }
}
