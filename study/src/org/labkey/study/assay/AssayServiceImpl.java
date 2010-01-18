/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.study.assay;

import com.google.gwt.user.client.rpc.SerializableException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringUtils;
import org.fhcrc.cpas.exp.xml.SimpleTypeNames;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.defaults.SetDefaultValuesAssayAction;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainEditorServiceBase;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.assay.AssayException;
import org.labkey.api.gwt.client.assay.AssayService;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.qc.TransformDataHandler;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.study.PlateService;
import org.labkey.api.study.PlateTemplate;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.PlateBasedAssayProvider;
import org.labkey.api.study.permissions.DesignAssayPermission;
import org.labkey.api.util.Pair;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.study.StudySchema;

import java.io.File;
import java.sql.SQLException;
import java.util.*;

/**
 * User: brittp
 * Date: Jun 22, 2007
 * Time: 10:01:10 AM
 */
public class AssayServiceImpl extends DomainEditorServiceBase implements AssayService
{
    public AssayServiceImpl(ViewContext context)
    {
        super(context);
    }

    public GWTProtocol getAssayDefinition(int rowId, boolean copy) throws SerializableException
    {
        ExpProtocol protocol = ExperimentService.get().getExpProtocol(rowId);
        if (protocol == null)
            return null;
        else
        {
            Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>> assayInfo;
            AssayProvider provider = org.labkey.api.study.assay.AssayService.get().getProvider(protocol);
            if (copy)
                assayInfo = provider.getAssayTemplate(getUser(), getContainer(), protocol);
            else
                assayInfo = new Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>>(protocol, provider.getDomains(protocol));
            return getAssayTemplate(provider, assayInfo, copy);
        }
    }

    public GWTProtocol getAssayTemplate(String providerName) throws SerializableException
    {
        AssayProvider provider = org.labkey.api.study.assay.AssayService.get().getProvider(providerName);
        if (provider == null)
        {
            throw new NotFoundException("Could not find assay provider " + providerName);
        }
        Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>> template = provider.getAssayTemplate(getUser(), getContainer());
        return getAssayTemplate(provider, template, false);
    }

    public GWTProtocol getAssayTemplate(AssayProvider provider, Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>> template, boolean copy) throws SerializableException
    {
        ExpProtocol protocol = template.getKey();
        List<GWTDomain> gwtDomains = new ArrayList<GWTDomain>();
        for (Pair<Domain, Map<DomainProperty, Object>> domainInfo : template.getValue())
        {
            Domain domain = domainInfo.getKey();
            GWTDomain<GWTPropertyDescriptor> gwtDomain = new GWTDomain<GWTPropertyDescriptor>();
            if (provider.allowDefaultValues(domain))
                gwtDomain.setDefaultValueOptions(provider.getDefaultValueOptions(domain), provider.getDefaultValueDefault(domain));
            Set<String> mandatoryPropertyDescriptors = new HashSet<String>();
            if (!copy)
            {
                gwtDomain.setDomainId(domain.getTypeId());
            }
            gwtDomain.setDomainURI(domain.getTypeURI());
            gwtDomain.setDescription(domain.getDescription());
            gwtDomain.setName(domain.getName());
            gwtDomain.setContainer(domain.getContainer().getId());
            gwtDomain.setAllowFileLinkProperties(provider.isFileLinkPropertyAllowed(template.getKey(), domain));
            ActionURL setDefaultValuesAction = new ActionURL(SetDefaultValuesAssayAction.class, getContainer());
            setDefaultValuesAction.addParameter("providerName", provider.getName());
            gwtDomain.setDefaultValuesURL(setDefaultValuesAction.getLocalURIString());
            gwtDomains.add(gwtDomain);
            List<GWTPropertyDescriptor> gwtProps = new ArrayList<GWTPropertyDescriptor>();

            DomainProperty[] properties = domain.getProperties();
            Map<DomainProperty, Object> defaultValues = domainInfo.getValue();

            for (DomainProperty prop : properties)
            {
                GWTPropertyDescriptor gwtProp = getPropertyDescriptor(prop, copy);
                if (gwtProp.getDefaultValueType() == null)
                {
                    // we want to explicitly set these "special" properties NOT to remember the user's last entered
                    // value if it hasn't been set before:
                    if (AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME.equals(prop.getName()) ||
                        AbstractAssayProvider.SPECIMENID_PROPERTY_NAME.equals(prop.getName()) ||
                        AbstractAssayProvider.VISITID_PROPERTY_NAME.equals(prop.getName()) ||
                        AbstractAssayProvider.DATE_PROPERTY_NAME.equals(prop.getName()))
                    {
                        prop.setDefaultValueTypeEnum(DefaultValueType.FIXED_EDITABLE);
                    }
                    else
                        gwtProp.setDefaultValueType(gwtDomain.getDefaultDefaultValueType());
                }
                gwtProps.add(gwtProp);
                Object defaultValue = defaultValues.get(prop);
                if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equals(gwtProp.getName()) && defaultValue instanceof String)
                {
                    Container studyContainer = ContainerManager.getForId((String) defaultValue);
                    if (studyContainer != null)
                        gwtProp.setDefaultDisplayValue(studyContainer.getPath());
                }
                else
                    gwtProp.setDefaultDisplayValue(DomainUtil.getFormattedDefaultValue(getUser(), prop, defaultValue));

                gwtProp.setDefaultValue(ConvertUtils.convert(defaultValue));
                if (provider.isMandatoryDomainProperty(domain, prop.getName()))
                    mandatoryPropertyDescriptors.add(prop.getName());
            }
            gwtDomain.setFields(gwtProps);
            gwtDomain.setMandatoryFieldNames(mandatoryPropertyDescriptors);
            gwtDomain.setReservedFieldNames(provider.getReservedPropertyNames(protocol, domain));
        }

        GWTProtocol result = new GWTProtocol();
        result.setProtocolId(protocol.getRowId() > 0 ? protocol.getRowId() : null);
        result.setDomains(gwtDomains);
        result.setName(protocol.getName());
        result.setProviderName(provider.getName());
        result.setDescription(protocol.getDescription());
        Map<String, String> gwtProtocolParams = new HashMap<String, String>();
        for (ProtocolParameter property : protocol.getProtocolParameters().values())
        {
            if (property.getXmlBeanValueType() != SimpleTypeNames.STRING)
            {
                throw new IllegalStateException("Did not expect non-string protocol parameter " + property.getOntologyEntryURI() + " (" + property.getValueType() + ")");
            }
            gwtProtocolParams.put(property.getOntologyEntryURI(), property.getStringValue());
        }
        result.setProtocolParameters(gwtProtocolParams);
        if (provider instanceof PlateBasedAssayProvider)
        {
            PlateTemplate plateTemplate = ((PlateBasedAssayProvider)provider).getPlateTemplate(getContainer(), protocol);
            if (plateTemplate != null)
                result.setSelectedPlateTemplate(plateTemplate.getName());
            setPlateTemplateList(provider, result);
        }

        // validation scripts
        List<File> scripts = provider.getValidationAndAnalysisScripts(protocol, AssayProvider.Scope.ASSAY_DEF, AssayProvider.ScriptType.VALIDATION);
        if (scripts.size() > 1)
            throw new IllegalStateException("Only a single validation script per Assay Definition is available for this release");

        if (scripts.size() == 1)
            result.setProtocolValidationScript(scripts.get(0).getAbsolutePath());

        List<File> typeScripts = provider.getValidationAndAnalysisScripts(protocol, AssayProvider.Scope.ASSAY_TYPE, AssayProvider.ScriptType.VALIDATION);
        if (!typeScripts.isEmpty())
        {
            List<String> scriptNames = new ArrayList<String>();
            for (File script : typeScripts)
                scriptNames.add(script.getAbsolutePath());

            result.setValidationScripts(scriptNames);
        }

        // data transform scripts
        List<File> transformScripts = provider.getValidationAndAnalysisScripts(protocol, AssayProvider.Scope.ASSAY_DEF, AssayProvider.ScriptType.TRANSFORM);
        if (transformScripts.size() > 1)
            throw new IllegalStateException("Only a single data transform script per Assay Definition is available for this release");

        if (transformScripts.size() == 1)
            result.setProtocolTransformScript(transformScripts.get(0).getAbsolutePath());

        ExpData data = ExperimentService.get().createData(getContainer(), provider.getDataType());
        ExperimentDataHandler handler = data.findDataHandler();
        result.setAllowValidationScript(provider.getDataExchangeHandler() != null);
        result.setAllowTransformationScript(handler instanceof TransformDataHandler);

        return result;
    }

    private GWTPropertyDescriptor getPropertyDescriptor(DomainProperty prop, boolean copy)
    {
        GWTPropertyDescriptor gwtProp = DomainUtil.getPropertyDescriptor(prop);
        if (copy)
            gwtProp.setPropertyId(0);

        return gwtProp;
    }

    private void setPlateTemplateList(AssayProvider provider, GWTProtocol protocol)
    {
        if (provider instanceof PlateBasedAssayProvider)
        {
            List<String> plateTemplates = new ArrayList<String>();
            try
            {
                for (PlateTemplate template : PlateService.get().getPlateTemplates(getContainer()))
                    plateTemplates.add(template.getName());
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
            protocol.setAvailablePlateTemplates(plateTemplates);
        }
    }

    private void setPropertyDomainURIs(ExpProtocol protocol, Set<String> uris)
    {
        if (getContainer() == null)
        {
            throw new IllegalStateException("Must set container before setting domain URIs");
        }
        if (protocol.getLSID() == null)
        {
            throw new IllegalStateException("Must set LSID before setting domain URIs");
        }
        Map<String, ObjectProperty> props = new HashMap<String, ObjectProperty>(protocol.getObjectProperties());
        // First prune out any domains of the same type that aren't in the new set
        for (String uri : new HashSet<String>(props.keySet()))
        {
            Lsid lsid = new Lsid(uri);
            if (lsid.getNamespacePrefix() != null && lsid.getNamespacePrefix().startsWith(ExpProtocol.ASSAY_DOMAIN_PREFIX) && !uris.contains(uri))
            {
                props.remove(uri);
            }
        }

        for (String uri : uris)
        {
            if (!props.containsKey(uri))
            {
                ObjectProperty prop = new ObjectProperty(protocol.getLSID(), protocol.getContainer(), uri, uri);
                props.put(prop.getPropertyURI(), prop);
            }
        }
        protocol.setObjectProperties(props);
    }

    public GWTProtocol saveChanges(GWTProtocol assay, boolean replaceIfExisting) throws AssayException
    {
        if (replaceIfExisting)
        {
            DbSchema schema = StudySchema.getInstance().getSchema();
            boolean transactionOwner = !schema.getScope().isTransactionActive();
            try
            {
                if (transactionOwner)
                    schema.getScope().beginTransaction();

                ExpProtocol protocol;
                if (assay.getProtocolId() == null)
                {
                    protocol = AssayManager.get().createAssayDefinition(getUser(), getContainer(), assay);
                    assay.setProtocolId(protocol.getRowId());

                    XarContext context = new XarContext("Domains", getContainer(), getUser());
                    context.addSubstitution("AssayName", assay.getName());
                    Set<String> domainURIs = new HashSet<String>();
                    for (GWTDomain domain : assay.getDomains())
                    {
                        domain.setDomainURI(LsidUtils.resolveLsidFromTemplate(domain.getDomainURI(), context));
                        domain.setName(assay.getName() + " " + domain.getName());
                        DomainDescriptor dd = OntologyManager.ensureDomainDescriptor(domain.getDomainURI(), domain.getName(), getContainer());
                        dd.setDescription(domain.getDescription());
                        OntologyManager.updateDomainDescriptor(dd);
                        domainURIs.add(domain.getDomainURI());
                    }
                    setPropertyDomainURIs(protocol, domainURIs);
                }
                else
                {
                    protocol = ExperimentService.get().getExpProtocol(assay.getProtocolId().intValue());

                    //ensure that the user has edit perms in this container
                    if(!canUpdateProtocol(protocol))
                        throw new AssayException("You do not have sufficient permissions to update this Assay");

                    if (!protocol.getContainer().equals(getContainer()))
                        throw new AssayException("Assays can only be edited in the folder where they were created.  " +
                                "This assay was created in folder " + protocol.getContainer().getPath());
                    protocol.setName(assay.getName());
                    protocol.setProtocolDescription(assay.getDescription());
                }

                Map<String, ProtocolParameter> newParams = new HashMap<String, ProtocolParameter>(protocol.getProtocolParameters());
                for (Map.Entry<String, String> entry : assay.getProtocolParameters().entrySet())
                {
                    ProtocolParameter param = new ProtocolParameter();
                    String uri = entry.getKey();
                    param.setOntologyEntryURI(uri);
                    param.setValue(SimpleTypeNames.STRING, entry.getValue());
                    param.setName(uri.indexOf("#") != -1 ? uri.substring(uri.indexOf("#") + 1) : uri);
                    newParams.put(uri, param);
                }
                protocol.setProtocolParameters(newParams.values());

                AssayProvider provider = org.labkey.api.study.assay.AssayService.get().getProvider(protocol);
                if (provider instanceof PlateBasedAssayProvider && assay.getSelectedPlateTemplate() != null)
                {
                    PlateBasedAssayProvider plateProvider = (PlateBasedAssayProvider)provider;
                    PlateTemplate template = PlateService.get().getPlateTemplate(getContainer(), assay.getSelectedPlateTemplate());
                    if (template != null)
                        plateProvider.setPlateTemplate(getContainer(), protocol, template);
                    else
                        throw new AssayException("The selected plate template could not be found.  Perhaps it was deleted by another user?");
                }

                // qc and data transform scripts
                List<File> validationScripts = Collections.emptyList();
                List<File> transformScripts = Collections.emptyList();

                if (!StringUtils.isBlank(assay.getProtocolValidationScript()))
                    validationScripts = Collections.singletonList(new File(assay.getProtocolValidationScript()));

                provider.setValidationAndAnalysisScripts(protocol, validationScripts, AssayProvider.ScriptType.VALIDATION);

                if (!StringUtils.isBlank(assay.getProtocolTransformScript()))
                    transformScripts = Collections.singletonList(new File(assay.getProtocolTransformScript()));

                provider.setValidationAndAnalysisScripts(protocol, transformScripts, AssayProvider.ScriptType.TRANSFORM);
                protocol.save(getUser());

                StringBuilder errors = new StringBuilder();
                for (GWTDomain<GWTPropertyDescriptor> domain : assay.getDomains())
                {
                    List<String> domainErrors = updateDomainDescriptor(domain, protocol.getName(), getContainer());
                    if (domainErrors != null)
                    {
                        for (String error : domainErrors)
                        {
                            errors.append(error).append("\n");
                        }
                    }
                }
                if (errors.length() > 0)
                    throw new AssayException(errors.toString());

                if (transactionOwner)
                    schema.getScope().commitTransaction();

                return getAssayDefinition(assay.getProtocolId(), false);
            }
            catch (UnexpectedException e)
            {
                Throwable cause = e.getCause();
                throw new AssayException(cause.getMessage());
            }
            catch (ExperimentException e)
            {
                throw new AssayException(e.getMessage());
            }
            catch (SerializableException e)
            {
                throw new AssayException(e.getMessage());
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
            finally
            {
                if (transactionOwner && schema.getScope().isTransactionActive())
                    schema.getScope().rollbackTransaction();
            }
        }
        else
            throw new AssayException("Only replaceIfExisting == true is supported.");
    }

    private List<String> updateDomainDescriptor(GWTDomain<GWTPropertyDescriptor> domain, String protocolName, Container protocolContainer)
    {
        GWTDomain previous = getDomainDescriptor(domain.getDomainURI(), protocolContainer);
        for (GWTPropertyDescriptor prop : domain.getFields())
        {
            if (prop.getLookupQuery() != null)
            {
                prop.setLookupQuery(prop.getLookupQuery().replace(AbstractAssayProvider.ASSAY_NAME_SUBSTITUTION, protocolName));
            }
        }
        return updateDomainDescriptor(previous, domain);
    }

    public List<String> updateDomainDescriptor(GWTDomain orig, GWTDomain update)
    {
        try
        {
            return super.updateDomainDescriptor(orig, update);
        }
        catch (ChangePropertyDescriptorException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    public boolean canUpdateProtocol(ExpProtocol protocol)
    {
        Container c = getContainer();
        User u = getUser();
        SecurityPolicy policy = c.getPolicy();
        return policy.hasPermission(u, DesignAssayPermission.class);
    }
}
