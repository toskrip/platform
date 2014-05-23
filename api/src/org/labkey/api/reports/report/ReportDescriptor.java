/*
 * Copyright (c) 2006-2014 LabKey Corporation
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

package org.labkey.api.reports.report;

import org.apache.commons.lang3.StringUtils;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Entity;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.reportProps.PropertyList;
import org.labkey.query.xml.ReportDescriptorDocument;
import org.labkey.query.xml.ReportDescriptorType;
import org.labkey.query.xml.ReportPropertyList;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: Karl Lum
 * Date: Oct 4, 2006
 */
public class ReportDescriptor extends Entity implements SecurableResource
{
    public static final String TYPE = "reportDescriptor";
    public static final int FLAG_INHERITABLE = 0x01;
    final static public int FLAG_HIDDEN = 0x02;

    private String _reportKey;
    private Integer _owner;
    private int _flags;
    private ViewCategory _category;
    private int _displayOrder;

    protected Map<String, Object> _props = new LinkedHashMap<>();

    // For clients of the descriptor and reports,
    // hide the fact that refreshDate, status, and author are stored via the
    // report property manager.  Do not include as _props above or you'll
    // serialize these twice
    protected Map<String, Object> _mapReportProps = new HashMap<>();

public enum Prop implements ReportProperty
    {
        descriptorType,
        reportId,
        reportType,
        reportName,
        reportDescription,
        filterParam,
        schemaName,
        queryName,
        viewName,
        dataRegionName,
        redirectUrl,
        returnUrl,
        cached,
        version,
        author,         // author is treated as metadata instead of a first-class field
        refreshDate,    // same
        status,         // same
        json,
        modified,
    }

    public ReportDescriptor()
    {
        setDescriptorType(TYPE);

        // set the report version to the one that is stored in the query module
        Module queryModule = ModuleLoader.getInstance().getModule("Query");
        if (queryModule != null)
            setProperty(Prop.version, String.valueOf(queryModule.getVersion()));
    }

    public interface ReportProperty
    {
        // marker
    }

    public void setReportId(ReportIdentifier reportId)
    {
        setProperty(Prop.reportId, reportId.toString());
    }

    public ReportIdentifier getReportId()
    {
        return ReportService.get().getReportIdentifier(getProperty(Prop.reportId));
    }

    public void setReportKey(String key){_reportKey = key;}
    public String getReportKey(){return _reportKey;}

    /**
     * Specify the type of report associated with this descriptor, valid types must
     * be registered/obtained through ReportService.registerReport(String type)
     */
    public void setReportType(String reportType)
    {
        setProperty(Prop.reportType, reportType);
    }

    public String getReportType()
    {
        return getProperty(Prop.reportType);
    }

    public void setReportName(String label)
    {
        setProperty(Prop.reportName, label);
    }

    public String getReportName()
    {
        return getProperty(Prop.reportName);
    }

    public void setReportDescription(String desc)
    {
        setProperty(Prop.reportDescription, desc);
    }

    public String getReportDescription()
    {
        return getProperty(Prop.reportDescription);
    }

    public void setProperty(String key, String value)
    {
        _props.put(key, value);
    }

    public void setProperties(List<Pair<String,String>> props)
    {
        init(props);
    }

    public void setProperties(Map<String, Object> props)
    {
        _props.putAll(props);
    }

    public String getProperty(String key){return (String)_props.get(key);}

    public Map<String, Object> getProperties() {return Collections.unmodifiableMap(_props);}

    public void setProperty(ReportProperty prop, String value)
    {
        _props.put(prop.toString(), value);
    }

    public void setProperty(ReportProperty prop, int value)
    {
        _props.put(prop.toString(), String.valueOf(value));
    }

    public void setProperty(ReportProperty prop, boolean value)
    {
        _props.put(prop.toString(), String.valueOf(value));
    }

    public String getProperty(ReportProperty prop)
    {
        Object o = _props.get(prop.toString());
        if (o != null)
        {
            if (o instanceof String)
                return (String)o;
            throw new IllegalStateException("Property value for: " + prop.toString() + " is not a String");
        }
        return null;
    }

    public void setDescriptorType(String type)
    {
        setProperty(Prop.descriptorType, type);
    }

    public String getDescriptorType()
    {
        return getProperty(Prop.descriptorType);
    }

    public String getVersionString()
    {
        return StringUtils.defaultIfEmpty(getProperty(Prop.version), "9.10");    
    }

    @Deprecated  // Use isShared() instead... "owner" column and getter/setter should go away. "createdby" is the real owner.
    public Integer getOwner(){return _owner;}

    public void setOwner(Integer owner){_owner = owner;}

    // TODO: Replace "owner" column with boolean "shared" column
    public boolean isShared()
    {
        return null == _owner;
    }

    @Nullable
    public Integer getAuthor()
    {
        Object authorId  =  _mapReportProps.get(Prop.author.name());
        // TODO: Why are author IDs returned as doubles?
        if (null != authorId)
        {
            return ((Number)authorId).intValue();
        }

        return null;
    }

    public Object getAuthorAsObject()
    {
        return _mapReportProps.get(Prop.author.name());
    }

    //ReportViewProvider will set these as objects
    public void setAuthor(Object author)
    {
        _mapReportProps.put(Prop.author.name(), author);
    }

    public Object getRefreshDateAsObject()
    {
        return _mapReportProps.get(Prop.refreshDate.name());
    }

    public void setRefeshDate(Object refreshDate)
    {
        _mapReportProps.put(Prop.refreshDate.name(), refreshDate);
    }

    public void setAuthor(Integer author)
    {
        _mapReportProps.put(Prop.author.name(), author);
    }

    @Nullable
    public Date getRefreshDate()
    {
        return (Date) _mapReportProps.get(Prop.refreshDate.name());
    }

    public void setRefreshDate(Date refreshDate)
    {
        _mapReportProps.put(Prop.refreshDate.name(), refreshDate);
    }

    public String getStatus()
    {
        return (String) _mapReportProps.get(Prop.status.name());
    }

    public void setStatus(String value)
    {
         _mapReportProps.put(Prop.status.name(), value);
    }

    public void initFromQueryString(String queryString)
    {
        init(PageFlowUtil.fromQueryString(queryString));
    }

    public int getFlags()
    {
        return _flags;
    }

    public void setFlags(int flags)
    {
        _flags = flags;
    }

/*
    protected void init(Map<String,String> props)
    {
        _props.putAll(props);
    }
*/

    protected void init(List<Pair<String, String>> params)
    {
        _props.remove(Prop.version.name());
        Map<String, Object> m = mapFromQueryString(params);

        for (Map.Entry<String,Object> entry : m.entrySet())
        {
            _props.put(entry.getKey(), entry.getValue());
        }
    }

    public void initProperties()
    {
        if (getReportId() != null )
        {
            try
            {
                // now initialize the property manager props
                for (Pair<DomainProperty, Object> pair : ReportPropsManager.get().getProperties(getEntityId(), lookupContainer()))
                    _mapReportProps.put(pair.getKey().getName(), pair.getValue());
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    public String toQueryString()
    {
        final StringBuffer sb = new StringBuffer();
        String strAnd = "";
        for (Map.Entry entry : _props.entrySet())
        {
            sb.append(strAnd);
            if (null == entry.getKey())
                continue;

            Object v = entry.getValue();
            if (v instanceof List)
            {
                String delim = "";
                for (String value : ((List<String>)entry.getValue()))
                {
                    sb.append(delim);
                    encode(sb, (String)entry.getKey(), value);
                    delim = "&";
                }
            }
            else
                encode(sb, (String)entry.getKey(), String.valueOf(v));

            strAnd = "&";
        }
        return sb.toString();
    }

    private void encode(final StringBuffer sb, String key, Object value)
    {
        sb.append(PageFlowUtil.encode(key));
        sb.append('=');
        sb.append(PageFlowUtil.encode(String.valueOf(value)));
    }

    protected static ReportDescriptor create(List<Pair<String,String>> props)
    {
        String type = null;
        for (Pair<String, String> param : props)
        {
            if (Prop.descriptorType.toString().equals(param.getKey()))
            {
                type = param.getValue();
                break;
            }
        }
        ReportDescriptor descriptor = ReportService.get().createDescriptorInstance(type);
        if (descriptor != null)
            descriptor.init(props);

        return descriptor;
    }

    private Map<String, Object> mapFromQueryString(List<Pair<String, String>> pairs)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Pair<String, String> p : pairs)
        {
            if (isArrayType(p.getKey()))
            {
                final Object o = m.get(p.getKey());
                if (o instanceof List)
                {
                    final List<String> l = (List<String>)o;
                    if (!l.contains(p.getValue()))
                        l.add(p.getValue());
                }
                else
                {
                    final List<String> list = new ArrayList<>();
                    list.add(p.getValue());
                    m.put(p.getKey(), list);
                }
            }
            else if (!StringUtils.isEmpty(p.getValue()))
                m.put(p.getKey(), p.getValue());
        }
        return m;
    }

    public boolean isArrayType(String prop)
    {
        return false;
    }

    private ReportDescriptorDocument getDescriptorDocument(Container c)
    {
        return getDescriptorDocument(c, null);
    }

    private ReportDescriptorDocument getDescriptorDocument(ImportContext context)
    {
        return getDescriptorDocument(context.getContainer(), context);
    }

    /**
     * Builds an XML representation of this descriptor
     * @return
     */
    private ReportDescriptorDocument getDescriptorDocument(Container c, @Nullable ImportContext context)
    {
        ReportDescriptorDocument doc = ReportDescriptorDocument.Factory.newInstance();
        ReportDescriptorType descriptor = doc.addNewReportDescriptor();

        descriptor.setDescriptorType(getDescriptorType());
        descriptor.setReportName(getReportName());
        descriptor.setReportKey(getReportKey());
        descriptor.setHidden(isHidden());

        if (getCategory() != null)
            descriptor.setCategory(ViewCategoryManager.getInstance().encode(getCategory()));

        ReportPropertyList props = descriptor.addNewProperties();
        for (Map.Entry<String, Object> entry : _props.entrySet())
        {
            if (!shouldSerialize(entry.getKey()))
                continue;

            final Object value = entry.getValue();
            if (value instanceof List)
            {
                for (Object item : ((List)value))
                {
                    addProperty(context, props, entry.getKey(), item);
                }
            }
            else if (value != null)
            {
                addProperty(context, props, entry.getKey(), value);
            }
        }

        // serialize any tags (reportPropsManager)
        PropertyList propList = descriptor.addNewTags();
        ReportPropsManager.get().exportProperties(getEntityId(), c, propList);

        return doc;
    }

    protected boolean shouldSerialize(String propName)
    {
        if (Prop.returnUrl.name().equals(propName))
            return false;

        return true;
    }

    private void addProperty(@Nullable ImportContext context, ReportPropertyList props, String key, Object value)
    {
        ReportPropertyList.Prop prop = props.addNewProp();
        prop.setName(key);
        prop.setStringValue(adjustPropertyValue(context, key, value));
    }

    // Let subclasses transform the property value based on the current context. For example, time charts
    // and participant reports need to map participant IDs to alternate IDs, if that's been requested.
    protected String adjustPropertyValue(@Nullable ImportContext context, String key, Object value)
    {
        return String.valueOf(value);
    }

    // Let subclasses decide how they need to handle query name changes, return true if changes have been made to the
    // descriptor. It is up to the caller to save the report changes
    public boolean updateQueryNameReferences(Collection<QueryChangeListener.QueryPropertyChange> changes)
    {
        return false;
    }

    public void serialize(ImportContext context, VirtualFile dir, String filename) throws IOException
    {
        ReportDescriptorDocument doc = getDescriptorDocument(context);
        dir.saveXmlBean(filename, doc);
    }

    public String serialize(Container c) throws IOException
    {
        ReportDescriptorDocument doc = getDescriptorDocument(c);
        StringWriter writer = new StringWriter();

        try
        {
            XmlBeansUtil.validateXmlDocument(doc);
            doc.save(writer, XmlBeansUtil.getDefaultSaveOptions());
            return writer.toString();
        }
        catch (XmlValidationException e)
        {
            // This is likely a code problem -- propagate it up so we log to mothership
            throw new RuntimeException(e);
        }
        finally
        {
            writer.close();
        }
    }

    public static ReportDescriptor createFromXML(String xmlString) throws IOException
    {
        List<Pair<String,String>> props = createPropsFromXML(xmlString);
        return create(props);
    }

    public static ReportDescriptor createFromFile(Container container, User user, File file) throws IOException, XmlValidationException
    {
        ReportDescriptorDocument doc;
        try
        {
            XmlOptions options = XmlBeansUtil.getDefaultParseOptions();
            options.setLoadSubstituteNamespaces(Collections.singletonMap("", "http://labkey.org/query/xml"));

            doc = ReportDescriptorDocument.Factory.parse(file, options);
            XmlBeansUtil.validateXmlDocument(doc);
        }
        catch (XmlException e)
        {
            throw new IOException(e.getMessage());
        }

        return createFromXML(container, user, doc);
    }

    public static ReportDescriptor createFromXmlObject(Container container, User user, XmlObject reportXml) throws IOException, XmlValidationException
    {
        ReportDescriptorDocument doc;
        try
        {
            if (reportXml instanceof ReportDescriptorDocument)
            {
                doc = (ReportDescriptorDocument)reportXml;
                XmlBeansUtil.validateXmlDocument(doc);
            }
            else
                throw new XmlException("Unable to get an instance of ReportDescriptorDocument");
        }
        catch (XmlException e)
        {
            throw new IOException(e.getMessage());
        }

        return createFromXML(container, user, doc);
    }

    private static ReportDescriptor createFromXML(Container container, User user, ReportDescriptorDocument doc) throws IOException, XmlValidationException
    {
        ReportDescriptorType d = doc.getReportDescriptor();

        ReportDescriptor descriptor = ReportService.get().createDescriptorInstance(d.getDescriptorType());
        if (descriptor != null)
        {
            descriptor.setReportName(d.getReportName());
            descriptor.setReportKey(d.getReportKey());
            descriptor.setHidden(d.getHidden());
            List<Pair<String, String>> props = new ArrayList<>();

            for (ReportPropertyList.Prop prop : d.getProperties().getPropArray())
            {
                props.add(new Pair<>(prop.getName(), prop.getStringValue()));
            }

            descriptor.init(props);

            if (d.getCategory() != null)
            {
                String[] parts = ViewCategoryManager.getInstance().decode(d.getCategory());
                ViewCategory category = ViewCategoryManager.getInstance().ensureViewCategory(container, user, parts);
                descriptor.setCategory(category);
            }

            PropertyList tags = d.getTags();
            if (tags != null && tags.sizeOfPropertyArray() > 0)
            {
                String entityId = descriptor.getEntityId();
                if (entityId == null)
                    descriptor.setEntityId(GUID.makeGUID());

                ReportPropsManager.get().importProperties(descriptor.getEntityId(), container, user, tags);
            }
            return descriptor;
        }
        return null;
    }

    public ReportDescriptorType setDescriptorFromXML(Container container, User user, String xmlString) throws IOException, XmlException
    {
        ReportDescriptorType d;

        XmlOptions options = XmlBeansUtil.getDefaultParseOptions();
        options.setLoadSubstituteNamespaces(Collections.singletonMap("", "http://labkey.org/query/xml"));

        ReportDescriptorDocument doc = ReportDescriptorDocument.Factory.parse(xmlString, options);
        d = doc.getReportDescriptor();

        List<Pair<String, String>> props = new ArrayList<>();
        if (d.getProperties() != null)
        {
            for (ReportPropertyList.Prop prop : d.getProperties().getPropArray())
                props.add(new Pair<>(prop.getName(), prop.getStringValue()));
        }

        setProperties(props);

        if (d.getCategory() != null && container != null && user != null)
        {
            String[] parts = ViewCategoryManager.getInstance().decode(d.getCategory());
            ViewCategory category = ViewCategoryManager.getInstance().ensureViewCategory(container, user, parts);
            setCategory(category);
        }

        if (d.getLabel() != null)
            setReportName(d.getLabel());

        if (d.getDescription() != null)
            setReportDescription(d.getDescription());

        setHidden(d.getHidden()); // not sure

        return d;
    }

    public static List<Pair<String, String>> createPropsFromXML(String xmlString) throws IOException
    {
        try
        {
            XmlOptions options = XmlBeansUtil.getDefaultParseOptions();
            options.setLoadSubstituteNamespaces(Collections.singletonMap("", "http://labkey.org/query/xml"));

            ReportDescriptorDocument doc = ReportDescriptorDocument.Factory.parse(xmlString, options);
            ReportDescriptorType d = doc.getReportDescriptor();

            List<Pair<String, String>> props = new ArrayList<>();
            if (d.getProperties() != null)
            {
                for (ReportPropertyList.Prop prop : d.getProperties().getPropArray())
                    props.add(new Pair<>(prop.getName(), prop.getStringValue()));
            }

            return props;
        }
        catch (XmlException e)
        {
            throw new IOException(e.getMessage());
        }
    }

    public void updatePolicy(ViewContext context, MutableSecurityPolicy policy)
    {
        assert policy.getResourceId().equalsIgnoreCase(this.getResourceId());
        SecurityPolicyManager.savePolicy(policy);
    }

    public boolean isNew()
    {
        return getReportId() == null;
    }

    public boolean isInherited(Container c)
    {
        if (null != getReportId())
        {
            Container srcContainer = ContainerManager.getForId(getContainerId());

            // if the report has been configured to be shared to child folders or is in the shared folder then
            // flag it as inherited.
            //
            if (((getFlags() & ReportDescriptor.FLAG_INHERITABLE) != 0) || (ContainerManager.getSharedContainer().equals(srcContainer)))
            {
                return !c.equals(srcContainer);
            }
        }
        return false;
    }

    public void setHidden(boolean hidden)
    {
        if (hidden)
            _flags = _flags | ReportDescriptor.FLAG_HIDDEN;
        else
            _flags = _flags  & ~ReportDescriptor.FLAG_HIDDEN;
    }

    public boolean isHidden()
    {
        return (getFlags() & ReportDescriptor.FLAG_HIDDEN) != 0;
    }

    @NotNull
    public String getResourceId()
    {
        return getEntityId();
    }

    @NotNull
    public String getResourceName()
    {
        return getReportName();
    }

    @NotNull
    public String getResourceDescription()
    {
        return getReportDescription();
    }

    @NotNull
    public Set<Class<? extends Permission>> getRelevantPermissions()
    {
        return RoleManager.BasicPermissions;
    }

    @NotNull
    public Module getSourceModule()
    {
        return ModuleLoader.getInstance().getCoreModule();
    }

    public boolean isModuleBased()
    {
        return false;
    }

    public SecurableResource getParentResource()
    {
        return getResourceContainer();
    }

    @NotNull
    public List<SecurableResource> getChildResources(User user)
    {
        return Collections.emptyList();
    }

    @NotNull
    public Container getResourceContainer()
    {
        return lookupContainer();
    }

    public boolean mayInheritPolicy()
    {
        return true;
    }

    public ViewCategory getCategory()
    {
        return _category;
    }

    public void setCategory(ViewCategory category)
    {
        _category = category;
    }

    public int getDisplayOrder()
    {
        return _displayOrder;
    }

    public void setDisplayOrder(int displayOrder)
    {
        _displayOrder = displayOrder;
    }

    public void setModified(Date modified)
    {
        setProperty(Prop.modified, DateUtil.formatDate(modified));
        if (null != modified)
        {
            super.setModified(modified);
        }
    }

    public boolean isStale()
    {
        return true;
    }

    public Resource getMetaDataFile()
    {
        return null;
    }

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        return new LinkedHashSet<>();
    }

    public String getViewClass()
    {
        return null;
    }
}
