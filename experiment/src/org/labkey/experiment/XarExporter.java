/*
 * Copyright (c) 2005-2009 LabKey Corporation
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

package org.labkey.experiment;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlOptions;
import org.fhcrc.cpas.exp.xml.*;
import org.fhcrc.cpas.exp.xml.ExperimentRunType;
import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.util.DateUtil;
import org.labkey.experiment.api.*;
import org.labkey.experiment.xar.AutoFileLSIDReplacer;
import org.labkey.experiment.xar.XarExportSelection;
import org.w3c.dom.Document;

import javax.xml.namespace.QName;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * User: jeckels
 * Date: Nov 21, 2005
 */
public class XarExporter
{
    private final URLRewriter _urlRewriter;
    private final ExperimentArchiveDocument _document;
    private final ExperimentArchiveType _archive;

    private String _xarXmlFileName = "experiment.xar.xml";

    /**
     * As we export objects to XML, we may transform the LSID so we need to remember the
     * original LSIDs
     */
    private Map<String, Set<String>> _experimentLSIDToRunLSIDs = new HashMap<String, Set<String>>();
    private Set<String> _experimentRunLSIDs = new HashSet<String>();
    private Set<String> _protocolLSIDs = new HashSet<String>();
    private Set<String> _inputDataLSIDs = new HashSet<String>();
    private Set<String> _inputMaterialLSIDs = new HashSet<String>();

    private Set<String> _sampleSetLSIDs = new HashSet<String>();
    private Set<String> _domainLSIDs = new HashSet<String>();

    private final LSIDRelativizer.RelativizedLSIDs _relativizedLSIDs;
    private Logger _log;

    public XarExporter(LSIDRelativizer lsidRelativizer, DataURLRelativizer urlRelativizer)
    {
        _relativizedLSIDs = new LSIDRelativizer.RelativizedLSIDs(lsidRelativizer);
        _urlRewriter = urlRelativizer.createURLRewriter();

        _document = ExperimentArchiveDocument.Factory.newInstance();
        _archive = _document.addNewExperimentArchive();
    }

    public XarExporter(LSIDRelativizer lsidRelativizer, DataURLRelativizer urlRelativizer, XarExportSelection selection, String xarXmlFileName, Logger log) throws SQLException, ExperimentException
    {
        this(lsidRelativizer, urlRelativizer);
        _log = log;

        selection.addContent(this);

        if (xarXmlFileName != null)
        {
            setXarXmlFileName(xarXmlFileName);
        }
    }

    private void logProgress(String message)
    {
        if (_log != null)
        {
            _log.info(message);
        }
    }

    public void setXarXmlFileName(String fileName) {
        this._xarXmlFileName = fileName;
    }

    public void addExperimentRun(ExpRunImpl run) throws ExperimentException
    {
        if (_experimentRunLSIDs.contains(run.getLSID()))
        {
            return;
        }
        logProgress("Adding experiment run " + run.getLSID());
        _experimentRunLSIDs.add(run.getLSID());
        ExperimentArchiveType.ExperimentRuns runs = _archive.getExperimentRuns();
        if (runs == null)
        {
            runs = _archive.addNewExperimentRuns();
        }
        ExperimentRunType xRun = runs.addNewExperimentRun();
        xRun.setAbout(_relativizedLSIDs.relativize(run.getLSID()));

        // The XAR schema only supports one experiment (run group) association per run, so choose the first one that it belongs to
        // At the moment, the UI only lets you export one experiment (run group) at a time anyway
        for (Map.Entry<String,Set<String>> entry : _experimentLSIDToRunLSIDs.entrySet())
        {
            if (entry.getValue().contains(run.getLSID()))
            {
                xRun.setExperimentLSID(_relativizedLSIDs.relativize(entry.getKey()));
                break;
            }
        }

        if (run.getComments() != null)
        {
            xRun.setComments(run.getComments());
        }
        xRun.setName(run.getName());
        PropertyCollectionType properties = getProperties(run.getLSID(), run.getContainer());
        if (properties != null)
        {
            xRun.setProperties(properties);
        }
        ExpProtocolImpl protocol = run.getProtocol();
        xRun.setProtocolLSID(_relativizedLSIDs.relativize(protocol.getLSID()));

        addProtocol(protocol, true);

        Collection<ExpData> inputData = run.getDataInputs().keySet();
        ExperimentArchiveType.StartingInputDefinitions inputDefs = _archive.getStartingInputDefinitions();
        if (inputData.size() > 0 && inputDefs == null)
        {
            inputDefs = _archive.addNewStartingInputDefinitions();
        }
        for (ExpData data : inputData)
        {
            if (!_inputDataLSIDs.contains(data.getLSID()))
            {
                _inputDataLSIDs.add(data.getLSID());

                DataBaseType xData = inputDefs.addNewData();
                populateData(xData, data, run);
            }
        }

        List<Material> inputMaterials = ExperimentServiceImpl.get().getRunInputMaterial(run.getLSID());
        if (inputMaterials.size() > 0 && inputDefs == null)
        {
            inputDefs = _archive.addNewStartingInputDefinitions();
        }
        for (Material material: inputMaterials)
        {
            if (!_inputMaterialLSIDs.contains(material.getLSID()))
            {
                _inputMaterialLSIDs.add(material.getLSID());

                MaterialBaseType xMaterial = inputDefs.addNewMaterial();
                populateMaterial(xMaterial, new ExpMaterialImpl(material));
            }
        }

        ExpProtocolApplication[] applications = ExperimentService.get().getExpProtocolApplicationsForRun(run.getRowId());
        ExperimentRunType.ProtocolApplications xApplications = xRun.addNewProtocolApplications();

        for (ExpProtocolApplication application : applications)
        {
            addProtocolApplication(application, run, xApplications);
        }
    }

    private void addProtocolApplication(ExpProtocolApplication application, ExpRunImpl run, ExperimentRunType.ProtocolApplications xApplications)
        throws ExperimentException
    {
        ProtocolApplicationBaseType xApplication = xApplications.addNewProtocolApplication();
        xApplication.setAbout(_relativizedLSIDs.relativize(application.getLSID()));
        xApplication.setActionSequence(application.getActionSequence());
        Date activityDate = application.getActivityDate();
        if (activityDate != null)
        {
            Calendar cal = new GregorianCalendar();
            cal.setTime(application.getActivityDate());
            xApplication.setActivityDate(cal);
        }
        if (application.getComments() != null)
        {
            xApplication.setComments(application.getComments());
        }
        xApplication.setCpasType(application.getApplicationType().toString());

        InputOutputRefsType inputRefs = null;
        Data[] inputDataRefs = ExperimentServiceImpl.get().getDataInputReferencesForApplication(application.getRowId());
        DataInput[] dataInputs = ExperimentServiceImpl.get().getDataInputsForApplication(application.getRowId());
        for (Data data : inputDataRefs)
        {
            if (inputRefs == null)
            {
                inputRefs = xApplication.addNewInputRefs();
            }
            InputOutputRefsType.DataLSID dataLSID = inputRefs.addNewDataLSID();
            dataLSID.setStringValue(_relativizedLSIDs.relativize(data.getLSID()));
            if (AutoFileLSIDReplacer.AUTO_FILE_LSID_SUBSTITUTION.equals(dataLSID.getStringValue()))
            {
                ExpDataImpl expData = new ExpDataImpl(data);
                String url = _urlRewriter.rewriteURL(expData.getFile(), expData, run);
                if (url != null && !"".equals(url))
                {
                    dataLSID.setDataFileUrl(url);
                }
            }
            String roleName = null;
            for (DataInput dataInput : dataInputs)
            {
                if (dataInput.getDataId() == data.getRowId())
                {
                    roleName = dataInput.getRole();
                    break;
                }
            }
            if (roleName != null)
            {
                dataLSID.setRoleName(roleName);
            }
        }

        Material[] inputMaterial = ExperimentServiceImpl.get().getMaterialInputReferencesForApplication(application.getRowId());
        MaterialInput[] materialInputs = ExperimentServiceImpl.get().getMaterialInputsForApplication(application.getRowId());
        for (Material material : inputMaterial)
        {
            if (inputRefs == null)
            {
                inputRefs = xApplication.addNewInputRefs();
            }
            InputOutputRefsType.MaterialLSID materialLSID = inputRefs.addNewMaterialLSID();
            materialLSID.setStringValue(_relativizedLSIDs.relativize(material.getLSID()));

            String roleName = null;
            for (MaterialInput materialInput : materialInputs)
            {
                if (materialInput.getMaterialId() == material.getRowId())
                {
                    roleName = material.getName();
                    break;
                }
            }
            if (roleName != null)
            {
                materialLSID.setRoleName(roleName);
            }
        }

        xApplication.setName(application.getName());

        ProtocolApplicationBaseType.OutputDataObjects outputDataObjects = xApplication.addNewOutputDataObjects();
        List<ExpData> outputData = application.getOutputDatas();
        if (!outputData.isEmpty())
        {
            for (ExpData data : outputData)
            {
                DataBaseType xData = outputDataObjects.addNewData();
                populateData(xData, data, run);
            }
        }

        ProtocolApplicationBaseType.OutputMaterials outputMaterialObjects = xApplication.addNewOutputMaterials();
        for (ExpMaterial material : application.getOutputMaterials())
        {
            MaterialBaseType xMaterial = outputMaterialObjects.addNewMaterial();
            populateMaterial(xMaterial, material);
        }

        PropertyCollectionType appProperties = getProperties(application.getLSID(), run.getContainer());
        if (appProperties != null)
        {
            xApplication.setProperties(appProperties);
        }

        ProtocolApplicationParameter[] parameters = ExperimentService.get().getProtocolApplicationParameters(application.getRowId());
        if (parameters != null)
        {
            SimpleValueCollectionType xParameters = xApplication.addNewProtocolApplicationParameters();
            for (ProtocolApplicationParameter parameter : parameters)
            {
                SimpleValueType xValue = xParameters.addNewSimpleVal();
                populateXmlBeanValue(xValue, parameter);
            }
        }

        xApplication.setProtocolLSID(_relativizedLSIDs.relativize(application.getProtocol().getLSID()));
    }

    private void populateXmlBeanValue(SimpleValueType xValue, AbstractParameter param)
    {
        xValue.setName(param.getName());
        xValue.setOntologyEntryURI(param.getOntologyEntryURI());
        xValue.setValueType(param.getXmlBeanValueType());
        String value = relativizeLSIDPropertyValue(param.getXmlBeanValue(), param.getXmlBeanValueType());
        if (value != null)
        {
            xValue.setStringValue(value);
        }
    }

    private String relativizeLSIDPropertyValue(String value, SimpleTypeNames.Enum type)
    {
        if (type == SimpleTypeNames.STRING &&
            value != null &&
            value.indexOf("urn:lsid:") == 0)
        {
            return _relativizedLSIDs.relativize(value);
        }
        else
        {
            return value;
        }
    }

    private void populateMaterial(MaterialBaseType xMaterial, ExpMaterial material) throws ExperimentException
    {
        logProgress("Adding material " + material.getLSID());
        addSampleSet(material.getCpasType());
        xMaterial.setAbout(_relativizedLSIDs.relativize(material.getLSID()));
        xMaterial.setCpasType(material.getCpasType() == null ? "Material" : _relativizedLSIDs.relativize(material.getCpasType()));
        xMaterial.setName(material.getName());
        PropertyCollectionType materialProperties = getProperties(material.getLSID(), material.getContainer());
        if (materialProperties != null)
        {
            xMaterial.setProperties(materialProperties);
        }
        ExpProtocol sourceProtocol = material.getSourceProtocol();
        if (sourceProtocol != null)
        {
            xMaterial.setSourceProtocolLSID(_relativizedLSIDs.relativize(sourceProtocol.getLSID()));
        }
    }

    private void addSampleSet(String cpasType)
    {
        if (_sampleSetLSIDs.contains(cpasType))
        {
            return;
        }
        _sampleSetLSIDs.add(cpasType);
        ExpSampleSet sampleSet = ExperimentService.get().getSampleSet(cpasType);
        if (sampleSet == null)
        {
            return;
        }
        if (_archive.getSampleSets() == null)
        {
            _archive.addNewSampleSets();
        }
        SampleSetType xSampleSet = _archive.getSampleSets().addNewSampleSet();
        xSampleSet.setAbout(_relativizedLSIDs.relativize(sampleSet.getLSID()));
        xSampleSet.setMaterialLSIDPrefix(_relativizedLSIDs.relativize(sampleSet.getMaterialLSIDPrefix()));
        xSampleSet.setName(sampleSet.getName());
        if (sampleSet.getDescription() != null)
        {
            xSampleSet.setDescription(sampleSet.getDescription());
        }
        
        Domain domain = sampleSet.getType();
        addDomain(domain);
    }

    private void addDomain(Domain domain)
    {
        if (_domainLSIDs.contains(domain.getTypeURI()))
        {
            return;
        }
        _domainLSIDs.add(domain.getTypeURI());
        
        ExperimentArchiveType.DomainDefinitions domainDefs = _archive.getDomainDefinitions();
        if (domainDefs == null)
        {
            domainDefs = _archive.addNewDomainDefinitions();
        }
        DomainDescriptorType xDomain = domainDefs.addNewDomain();
        xDomain.setName(domain.getName());
        if (domain.getDescription() != null)
        {
            xDomain.setDescription(domain.getDescription());
        }
        xDomain.setDomainURI(_relativizedLSIDs.relativize(domain.getTypeURI()));
        for (DomainProperty domainProp : domain.getProperties())
        {
            PropertyDescriptor prop = domainProp.getPropertyDescriptor();

            addPropertyDescriptor(xDomain, domainProp, prop);
        }
    }

    private void addPropertyDescriptor(DomainDescriptorType xDomain, DomainProperty domainProp, PropertyDescriptor prop)
    {
        PropertyDescriptorType xProp = xDomain.addNewPropertyDescriptor();
        if (domainProp.getDescription() != null)
        {
            xProp.setDescription(domainProp.getDescription());
        }
        xProp.setName(domainProp.getName());
        xProp.setPropertyURI(_relativizedLSIDs.relativize(domainProp.getPropertyURI()));
        if (prop.getConceptURI() != null)
        {
            xProp.setConceptURI(prop.getConceptURI());
        }
        xProp.setRequired(domainProp.isRequired());
        if (domainProp.getDescription() != null)
        {
            xProp.setDescription(domainProp.getDescription());
        }
        if (domainProp.getFormatString() != null)
        {
            xProp.setFormat(domainProp.getFormatString());
        }
        if (domainProp.getLabel() != null)
        {
            xProp.setLabel(domainProp.getLabel());
        }
        if (prop.getOntologyURI() != null)
        {
            xProp.setOntologyURI(prop.getOntologyURI());
        }
        if (prop.getRangeURI() != null)
        {
            xProp.setRangeURI(prop.getRangeURI());
        }
        if (prop.getSearchTerms() != null)
        {
            xProp.setSearchTerms(prop.getSearchTerms());
        }
        if(prop.getSemanticType() != null)
        {
            xProp.setSemanticType(prop.getSemanticType());
        }
        xProp.setQcEnabled(domainProp.isQcEnabled());

        for (IPropertyValidator validator : domainProp.getValidators())
        {
            addPropertyValidator(xProp, validator);
        }
    }

    private PropertyValidatorType addPropertyValidator(PropertyDescriptorType xProp, IPropertyValidator validator)
    {
        PropertyValidatorType xValidator = xProp.addNewPropertyValidator();
        xValidator.setName(validator.getName());
        xValidator.setTypeURI(validator.getTypeURI());
        if (validator.getDescription() != null)
        {
            xValidator.setDescription(validator.getDescription());
        }
        if (validator.getErrorMessage() != null)
        {
            xValidator.setErrorMessage(validator.getErrorMessage());
        }
        if (validator.getExpressionValue() != null)
        {
            xValidator.setExpression(validator.getExpressionValue());
        }
        for (Map.Entry<String, String> property : validator.getProperties().entrySet())
        {
            PropertyValidatorPropertyType xProperty = xValidator.addNewProperty();
            xProperty.setName(property.getKey());
            xProperty.setValue(property.getValue());
        }
        return xValidator;
    }

    private PropertyCollectionType addOriginalURLProperty(PropertyCollectionType properties, String originalURL)
    {
        if (properties == null)
        {
            properties = PropertyCollectionType.Factory.newInstance();
        }

        for (SimpleValueType prop : properties.getSimpleValArray())
        {
            if (XarReader.ORIGINAL_URL_PROPERTY.equals(prop.getOntologyEntryURI()) &&
                XarReader.ORIGINAL_URL_PROPERTY_NAME.equals(prop.getName()))
            {
                return properties;
            }
        }

        SimpleValueType newProperty = properties.addNewSimpleVal();
        newProperty.setValueType(SimpleTypeNames.STRING);
        newProperty.setOntologyEntryURI(XarReader.ORIGINAL_URL_PROPERTY);
        newProperty.setName(XarReader.ORIGINAL_URL_PROPERTY_NAME);
        newProperty.setStringValue(originalURL);

        return properties;
    }

    private void populateData(DataBaseType xData, ExpData data, ExpRun run) throws ExperimentException
    {
        logProgress("Adding data " + data.getLSID());
        xData.setAbout(_relativizedLSIDs.relativize(data));
        xData.setCpasType(data.getCpasType() == null ? "Data" : _relativizedLSIDs.relativize(data.getCpasType()));

        File f = data.getFile();
        String url = null;
        if (f != null)
        {
            url = _urlRewriter.rewriteURL(f, data, run);
        }
        xData.setName(data.getName());
        PropertyCollectionType dataProperties = getProperties(data.getLSID(), data.getContainer());
        if (url != null)
        {
            xData.setDataFileUrl(url);
            if (!url.equals(data.getDataFileUrl()))
            {
                // Add the original URL as a property on the data object
                // so that it's easier to figure out links between files later, since
                // the URLs have all been rewritten
                dataProperties = addOriginalURLProperty(dataProperties, data.getDataFileUrl());
            }
        }
        if (dataProperties != null)
        {
            xData.setProperties(dataProperties);
        }
        ExpProtocol protocol = data.getSourceProtocol();
        if (protocol != null)
        {
            String sourceProtocolLSID = _relativizedLSIDs.relativize(protocol.getLSID());
            if (sourceProtocolLSID != null)
            {
                xData.setSourceProtocolLSID(sourceProtocolLSID);
            }
        }
    }

    public void addProtocol(ExpProtocol protocol, boolean includeChildren) throws ExperimentException
    {
        if (_protocolLSIDs.contains(protocol.getLSID()))
        {
            return;
        }
        logProgress("Adding protocol " + protocol.getLSID());
        _protocolLSIDs.add(protocol.getLSID());

        ExperimentArchiveType.ProtocolDefinitions protocolDefs = _archive.getProtocolDefinitions();
        if (protocolDefs == null)
        {
            protocolDefs = _archive.addNewProtocolDefinitions();
        }
        ProtocolBaseType xProtocol = protocolDefs.addNewProtocol();

        xProtocol.setAbout(_relativizedLSIDs.relativize(protocol.getLSID()));
        xProtocol.setApplicationType(protocol.getApplicationType().toString());
        ContactType contactType = getContactType(protocol.getLSID(), protocol.getContainer());
        if (contactType != null)
        {
            xProtocol.setContact(contactType);
        }
        if (protocol.getInstrument() != null)
        {
            xProtocol.setInstrument(protocol.getInstrument());
        }
        xProtocol.setName(protocol.getName());
        PropertyCollectionType properties = getProperties(protocol.getLSID(), protocol.getContainer(), XarReader.CONTACT_PROPERTY);
        if (properties != null)
        {
            xProtocol.setProperties(properties);
        }

        if (protocol.getMaxInputDataPerInstance() != null)
        {
            xProtocol.setMaxInputDataPerInstance(protocol.getMaxInputDataPerInstance().intValue());
        }

        if (protocol.getMaxInputMaterialPerInstance() != null)
        {
            xProtocol.setMaxInputMaterialPerInstance(protocol.getMaxInputMaterialPerInstance().intValue());
        }

        if (protocol.getOutputDataPerInstance() != null)
        {
            xProtocol.setOutputDataPerInstance(protocol.getOutputDataPerInstance().intValue());
        }

        if (protocol.getOutputMaterialPerInstance() != null)
        {
            xProtocol.setOutputMaterialPerInstance(protocol.getOutputMaterialPerInstance().intValue());
        }

        xProtocol.setOutputDataType(protocol.getOutputDataType());
        xProtocol.setOutputMaterialType(protocol.getOutputMaterialType());
        if (protocol.getProtocolDescription() != null)
        {
            xProtocol.setProtocolDescription(protocol.getProtocolDescription());
        }
        if (protocol.getSoftware() != null)
        {
            xProtocol.setSoftware(protocol.getSoftware());
        }

        Map<String, ProtocolParameter> params = protocol.getProtocolParameters();
        SimpleValueCollectionType valueCollection = null;
        for (ProtocolParameter param : params.values())
        {
            if (valueCollection == null)
            {
                valueCollection = xProtocol.addNewParameterDeclarations();
            }
            SimpleValueType xValue = valueCollection.addNewSimpleVal();
            populateXmlBeanValue(xValue, param);
        }

        if (includeChildren)
        {
            List<ExpProtocolAction> protocolActions = protocol.getSteps();

            if (protocolActions.size() > 0)
            {
                ExperimentArchiveType.ProtocolActionDefinitions actionDefs = _archive.getProtocolActionDefinitions();
                if (actionDefs == null)
                {
                    actionDefs = _archive.addNewProtocolActionDefinitions();
                }

                ProtocolActionSetType actionSet = actionDefs.addNewProtocolActionSet();
                actionSet.setParentProtocolLSID(_relativizedLSIDs.relativize(protocol.getLSID()));
                for (ExpProtocolAction action : protocolActions)
                {
                    addProtocolAction(action, actionSet);
                }
            }
        }
    }

    private void addProtocolAction(ExpProtocolAction protocolAction, ProtocolActionSetType actionSet) throws ExperimentException
    {
        ExpProtocol protocol = protocolAction.getChildProtocol();
        ExpProtocol parentProtocol = protocolAction.getParentProtocol();
        String lsid = protocol.getLSID();

        ProtocolActionType xProtocolAction = actionSet.addNewProtocolAction();
        xProtocolAction.setActionSequence(protocolAction.getActionSequence());
        xProtocolAction.setChildProtocolLSID(_relativizedLSIDs.relativize(lsid));

        ProtocolActionPredecessor[] predecessors = ExperimentServiceImpl.get().getProtocolActionPredecessors(parentProtocol.getLSID(), lsid);
        for (ProtocolActionPredecessor predecessor : predecessors)
        {
            ProtocolActionType.PredecessorAction xPredecessor = xProtocolAction.addNewPredecessorAction();
            xPredecessor.setActionSequenceRef(predecessor.getPredecessorSequence());
        }

        addProtocol(protocol, true);
    }

    public void addExperiment(ExpExperimentImpl exp) throws ExperimentException, SQLException
    {
        Experiment experiment = exp.getDataObject();
        if (_experimentLSIDToRunLSIDs.containsKey(experiment.getLSID()))
        {
            return;
        }
        logProgress("Adding experiment " + experiment.getLSID());
        Set<String> runLsids = new HashSet<String>();
        for (ExpRun expRun : exp.getRuns())
        {
            runLsids.add(expRun.getLSID());
        }
        _experimentLSIDToRunLSIDs.put(experiment.getLSID(), runLsids);

        ExperimentType xExperiment = _archive.addNewExperiment();
        xExperiment.setAbout(_relativizedLSIDs.relativize(experiment.getLSID()));
        if (experiment.getComments() != null)
        {
            xExperiment.setComments(experiment.getComments());
        }
        ContactType contactType = getContactType(experiment.getLSID(), experiment.getContainer());
        if (contactType != null)
        {
            xExperiment.setContact(contactType);
        }
        if (experiment.getExperimentDescriptionURL() != null)
        {
            xExperiment.setExperimentDescriptionURL(experiment.getExperimentDescriptionURL());
        }
        if (experiment.getHypothesis() != null)
        {
            xExperiment.setHypothesis(experiment.getHypothesis());
        }
        xExperiment.setName(experiment.getName());

        PropertyCollectionType xProperties = getProperties(experiment.getLSID(), experiment.getContainer(), XarReader.CONTACT_PROPERTY);
        if (xProperties != null)
        {
            xExperiment.setProperties(xProperties);
        }
    }

    private PropertyCollectionType getProperties(String lsid, Container parentContainer, String... ignoreProperties) throws ExperimentException
    {
        Map<String, ObjectProperty> properties = getObjectProperties(parentContainer, lsid);

        Set<String> ignoreSet = new HashSet<String>();
        ignoreSet.addAll(Arrays.asList(ignoreProperties));

        PropertyCollectionType result = PropertyCollectionType.Factory.newInstance();
        boolean addedProperty = false;
        for (ObjectProperty value : properties.values())
        {
            if (ignoreSet.contains(value.getPropertyURI()))
            {
                continue;
            }
            addedProperty = true;

            if (value.getPropertyType() == PropertyType.RESOURCE)
            {
                PropertyObjectType subProperty = result.addNewPropertyObject();
                PropertyObjectDeclarationType propDec = subProperty.addNewPropertyObjectDeclaration();
                propDec.setName(value.getName());
                propDec.setOntologyEntryURI(_relativizedLSIDs.relativize(value.getPropertyURI()));
                propDec.setValueType(SimpleTypeNames.PROPERTY_URI);

                PropertyCollectionType childProperties = getProperties(value.getStringValue(), parentContainer);
                if (childProperties != null)
                {
                    subProperty.setChildProperties(childProperties);
                }
            }
            else
            {
                SimpleValueType simpleValue = result.addNewSimpleVal();
                simpleValue.setName(value.getName());
                simpleValue.setOntologyEntryURI(_relativizedLSIDs.relativize(value.getPropertyURI()));

                switch(value.getPropertyType())
                {
                    case DATE_TIME:
                        simpleValue.setValueType(SimpleTypeNames.DATE_TIME);
                        simpleValue.setStringValue(DateUtil.formatDateTime(value.getDateTimeValue(), AbstractParameter.SIMPLE_FORMAT_PATTERN));
                        break;
                    case DOUBLE:
                        simpleValue.setValueType(SimpleTypeNames.DOUBLE);
                        simpleValue.setStringValue(value.getFloatValue().toString());
                        break;
                    case FILE_LINK:
                        simpleValue.setValueType(SimpleTypeNames.FILE_LINK);
                        simpleValue.setStringValue(value.getStringValue());
                        break;
                    case INTEGER:
                        simpleValue.setValueType(SimpleTypeNames.INTEGER);
                        simpleValue.setStringValue(Long.toString(value.getFloatValue().longValue()));
                        break;
                    case STRING:
                    case MULTI_LINE:
                    case XML_TEXT:
                        simpleValue.setValueType(SimpleTypeNames.STRING);
                        if (ExternalDocsURLCustomPropertyRenderer.URI.equals(value.getPropertyURI()))
                        {
                            String link = value.getStringValue();
                            try
                            {
                                URI uri = new URI(link);
                                if (uri.getScheme().equals("file"))
                                {
                                    File f = new File(uri);
                                    if (f.exists())
                                    {
                                        link = _urlRewriter.rewriteURL(f, null, null);
                                    }
                                }
                            }
                            catch (URISyntaxException e) {}
                            simpleValue.setStringValue(link);
                        }
                        else
                        {
                            simpleValue.setStringValue(relativizeLSIDPropertyValue(value.getStringValue(), SimpleTypeNames.STRING));
                            Domain domain = PropertyService.get().getDomain(parentContainer, value.getStringValue());
                            if (domain != null)
                            {
                                addDomain(domain);
                            }
                        }
                        break;
                    default:
                        logProgress("Warning: skipping export of " + value.getName() + " -- unknown type " + value.getPropertyType());
                }
            }
        }

        if (!addedProperty)
        {
            return null;
        }
        return result;
    }

    private Map<String, ObjectProperty> getObjectProperties(Container container, String lsid)
    {
        try
        {
            return OntologyManager.getPropertyObjects(container, lsid);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void dumpXML(OutputStream out) throws IOException, ExperimentException
    {
        XmlOptions validateOptions = new XmlOptions();
        ArrayList<XmlError> errorList = new ArrayList<XmlError>();
        validateOptions.setErrorListener(errorList);
        if (!_document.validate(validateOptions))
        {
            StringBuilder sb = new StringBuilder();
            for (XmlError error : errorList)
            {
                sb.append("Schema validation error: ");
                sb.append(error.getMessage());
                sb.append("\n");
                sb.append("Location of invalid XML: ");
                sb.append(error.getCursorLocation().xmlText());
                sb.append("\n");
            }
            throw new ExperimentException("Failed to create a valid XML file\n" + sb.toString());
        }

        XmlOptions options = new XmlOptions();
        options.setSaveAggressiveNamespaces();
        options.setSavePrettyPrint();

        XmlCursor cursor = _document.newCursor();
        if (cursor.toFirstChild())
        {
          cursor.setAttributeText(new QName("http://www.w3.org/2001/XMLSchema-instance","schemaLocation"), ExperimentService.SCHEMA_LOCATION);
        }

        _document.save(out, options);
    }

    public void write(OutputStream out) throws IOException, ExperimentException
    {
        ZipOutputStream zOut = null;
        try
        {
            zOut = new ZipOutputStream(out);
            ZipEntry xmlEntry = new ZipEntry(_xarXmlFileName);
            zOut.putNextEntry(xmlEntry);
            logProgress("Adding XAR XML to archive");
            dumpXML(zOut);

            zOut.closeEntry();

            for (URLRewriter.FileInfo fileInfo : _urlRewriter.getFileInfos())
            {
                if (fileInfo.hasContentToExport())
                {
                    logProgress("Adding data file to archive: " + fileInfo.getName());
                    ZipEntry fileEntry = new ZipEntry(fileInfo.getName());
                    zOut.putNextEntry(fileEntry);

                    fileInfo.writeFile(zOut);
                    zOut.closeEntry();
                }
            }
        }
        catch (Exception e)
        {
            // insert the stack trace into the zip file
            ZipEntry errorEntry = new ZipEntry("error.log");
            zOut.putNextEntry(errorEntry);

            final PrintStream ps = new PrintStream(zOut, true);
            ps.println("Failed to complete export of the XAR file: ");
            e.printStackTrace(ps);
            zOut.closeEntry();
        }
        finally
        {
            if (zOut != null) { try { zOut.close(); } catch (IOException e) {} }
        }
    }

    private ContactType getContactType(String parentLSID, Container parentContainer) throws ExperimentException
    {
        Map<String, Object> parentProperties = getProperties(parentContainer, parentLSID);
        Object contactLSIDObject = parentProperties.get(XarReader.CONTACT_PROPERTY);
        if (!(contactLSIDObject instanceof String))
        {
            return null;
        }
        String contactLSID = (String)contactLSIDObject;
        Map<String, Object> contactProperties = getProperties(parentContainer, contactLSID);

        Object contactIdObject = contactProperties.get(XarReader.CONTACT_ID_PROPERTY);
        Object emailObject = contactProperties.get(XarReader.CONTACT_EMAIL_PROPERTY);
        Object firstNameObject = contactProperties.get(XarReader.CONTACT_FIRST_NAME_PROPERTY);
        Object lastNameObject = contactProperties.get(XarReader.CONTACT_LAST_NAME_PROPERTY);

        ContactType contactType = ContactType.Factory.newInstance();
        if (contactIdObject instanceof String)
        {
            contactType.setContactId((String)contactIdObject);
        }
        if (emailObject instanceof String)
        {
            contactType.setEmail((String)emailObject);
        }
        if (firstNameObject instanceof String)
        {
            contactType.setFirstName((String)firstNameObject);
        }
        if (lastNameObject instanceof String)
        {
            contactType.setLastName((String)lastNameObject);
        }
        PropertyCollectionType properties = getProperties(contactLSID, parentContainer, XarReader.CONTACT_ID_PROPERTY, XarReader.CONTACT_EMAIL_PROPERTY, XarReader.CONTACT_FIRST_NAME_PROPERTY, XarReader.CONTACT_LAST_NAME_PROPERTY);
        if (properties != null)
        {
            contactType.setProperties(properties);
        }
        return contactType;
    }

    private Map<String, Object> getProperties(Container container, String contactLSID)
    {
        try
        {
            return OntologyManager.getProperties(container, contactLSID);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public Document getDOMDocument()
    {
        return (Document)_document.getDomNode();
    }

    public ExperimentArchiveDocument getXMLBean()
    {
        return _document;
    }
}
