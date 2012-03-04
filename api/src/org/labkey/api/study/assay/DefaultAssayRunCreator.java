/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
package org.labkey.api.study.assay;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.LabkeyError;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.qc.DataTransformer;
import org.labkey.api.qc.DataValidator;
import org.labkey.api.qc.DefaultTransformResult;
import org.labkey.api.qc.TransformDataHandler;
import org.labkey.api.qc.TransformResult;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: jeckels
 * Date: Oct 12, 2011
 */
public class DefaultAssayRunCreator<ProviderType extends AbstractAssayProvider> implements AssayRunCreator<ProviderType>
{
    private static final Logger LOG = Logger.getLogger(DefaultAssayRunCreator.class);

    private final ProviderType _provider;

    public DefaultAssayRunCreator(ProviderType provider)
    {
        _provider = provider;
    }

    public TransformResult transform(AssayRunUploadContext context, ExpRun run) throws ValidationException
    {
        DataTransformer transformer = getDataTransformer();
        if (transformer != null)
            return transformer.transform(context, run);

        return DefaultTransformResult.createEmptyResult();
    }

    /**
     * @param batch if not null, the run group that's already created for this batch. If null, a new one needs to be created
     * @param forceSaveBatchProps
     * @return the run and batch that were inserted
     */
    public ExpExperiment saveExperimentRun(AssayRunUploadContext context, @Nullable ExpExperiment batch, ExpRun run, boolean forceSaveBatchProps) throws ExperimentException, ValidationException
    {
        Map<ExpMaterial, String> inputMaterials = new HashMap<ExpMaterial, String>();
        Map<ExpData, String> inputDatas = new HashMap<ExpData, String>();
        Map<ExpMaterial, String> outputMaterials = new HashMap<ExpMaterial, String>();
        Map<ExpData, String> outputDatas = new HashMap<ExpData, String>();
        Map<ExpData, String> transformedDatas = new HashMap<ExpData, String>();

        Map<DomainProperty, String> runProperties = context.getRunProperties();
        Map<DomainProperty, String> batchProperties = context.getBatchProperties();

        Map<DomainProperty, String> allProperties = new HashMap<DomainProperty, String>();
        allProperties.putAll(runProperties);
        allProperties.putAll(batchProperties);

        ParticipantVisitResolverType resolverType = null;
        for (Map.Entry<DomainProperty, String> entry : allProperties.entrySet())
        {
            if (entry.getKey().getName().equals(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME))
            {
                resolverType = AbstractAssayProvider.findType(entry.getValue(), getProvider().getParticipantVisitResolverTypes());
                if (resolverType != null)
                {
                    resolverType.configureRun(context, run, inputDatas);
                }
                break;
            }
        }

        addInputMaterials(context, inputMaterials, resolverType);
        addInputDatas(context, inputDatas, resolverType);
        addOutputMaterials(context, outputMaterials, resolverType);
        addOutputDatas(context, outputDatas, resolverType);

        try
        {
            ParticipantVisitResolver resolver = null;
            if (resolverType != null)
            {
                String targetStudyId = null;
                for (Map.Entry<DomainProperty, String> property : allProperties.entrySet())
                {
                    if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equals(property.getKey().getName()))
                    {
                        targetStudyId = property.getValue();
                        break;
                    }
                }
                Container targetStudy = null;
                if (targetStudyId != null && targetStudyId.length() > 0)
                    targetStudy = ContainerManager.getForId(targetStudyId);

                resolver = resolverType.createResolver(Collections.unmodifiableCollection(inputMaterials.keySet()),
                        Collections.unmodifiableCollection(inputDatas.keySet()),
                        Collections.unmodifiableCollection(outputMaterials.keySet()),
                        Collections.unmodifiableCollection(outputDatas.keySet()),
                        context.getContainer(),
                        targetStudy, context.getUser());
            }
            resolveExtraRunData(resolver, context, inputMaterials, inputDatas, outputMaterials, outputDatas);
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }

        DbScope scope = ExperimentService.get().getSchema().getScope();
        try
        {
            boolean saveBatchProps = forceSaveBatchProps;
            scope.ensureTransaction();

            // Save the batch first
            if (batch == null)
            {
                // Make sure that we have a batch to associate with this run
                batch = AssayService.get().createStandardBatch(run.getContainer(), null, context.getProtocol());
                batch.save(context.getUser());
                saveBatchProps = true;
            }
            run.save(context.getUser());
            // Add the run to the batch so that we can find it when we're loading the data files
            batch.addRuns(context.getUser(), run);

            ViewBackgroundInfo info = new ViewBackgroundInfo(context.getContainer(), context.getUser(), context.getActionURL());
            XarContext xarContext = new AssayUploadXarContext("Simple Run Creation", context);

            run = ExperimentService.get().saveSimpleExperimentRun(run,
                    inputMaterials,
                    inputDatas,
                    outputMaterials,
                    outputDatas,
                    transformedDatas,
                    info,
                    LOG,
                    false);

            // handle data transformation
            TransformResult transformResult = transform(context, run);
            List<ExpData> insertedDatas = new ArrayList<ExpData>();
            if (context instanceof AssayRunUploadForm)
                ((AssayRunUploadForm)context).setTransformResult(transformResult);

            if (saveBatchProps)
            {
                if (!transformResult.getBatchProperties().isEmpty())
                {
                    Map<DomainProperty, String> props = transformResult.getBatchProperties();
                    List<ValidationError> errors = validateProperties(props);
                    if (!errors.isEmpty())
                        throw new ValidationException(errors);
                    savePropertyObject(batch, props, context.getUser());
                }
                else
                    savePropertyObject(batch, batchProperties, context.getUser());
            }

            if (!transformResult.getRunProperties().isEmpty())
            {
                Map<DomainProperty, String> props = transformResult.getRunProperties();
                List<ValidationError> errors = validateProperties(props);
                if (!errors.isEmpty())
                    throw new ValidationException(errors);
                savePropertyObject(run, props, context.getUser());
            }
            else
                savePropertyObject(run, runProperties, context.getUser());

            if (transformResult.getTransformedData().isEmpty())
            {
                insertedDatas.addAll(inputDatas.keySet());
                insertedDatas.addAll(outputDatas.keySet());

                for (ExpData insertedData : insertedDatas)
                {
                    insertedData.findDataHandler().importFile(insertedData, insertedData.getFile(), info, LOG, xarContext);
                }
            }
            else
            {
                ExpData data = ExperimentService.get().createData(context.getContainer(), getProvider().getDataType());
                ExperimentDataHandler handler = data.findDataHandler();

                // this should assert to always be true
                if (handler instanceof TransformDataHandler)
                {
                    for (Map.Entry<ExpData, List<Map<String, Object>>> entry : transformResult.getTransformedData().entrySet())
                    {
                        ExpData expData = entry.getKey();

                        expData.setSourceApplication(run.getOutputProtocolApplication());
                        expData.setRun(run);
                        expData.save(context.getUser());

                        run.getOutputProtocolApplication().addDataInput(context.getUser(), expData, "Data");
                        // Add to the cached list of outputs
                        run.getDataOutputs().add(expData);

                        ((TransformDataHandler)handler).importTransformDataMap(expData, context, run, entry.getValue());
                    }
                }
            }
            validate(context, run);

            scope.commitTransaction();

            AssayService.get().ensureUniqueBatchName(batch, context.getProtocol(), context.getUser());

            List<String> copyErrors = AssayPublishService.get().autoCopyResults(context.getProtocol(), run, context.getUser(), context.getContainer());
            if (!copyErrors.isEmpty())
            {
                StringBuilder errorMessage = new StringBuilder();
                for (String copyError : copyErrors)
                {
                    errorMessage.append(copyError);
                    errorMessage.append("\n");
                }
                throw new ExperimentException(errorMessage.toString());
            }

            return batch;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            scope.closeConnection();
        }
    }

    public void validate(AssayRunUploadContext context, ExpRun run) throws ValidationException
    {
        DataValidator validator = getDataValidator();
        if (validator != null)
            validator.validate(context, run);
    }

    protected void addInputMaterials(AssayRunUploadContext context, Map<ExpMaterial, String> inputMaterials, ParticipantVisitResolverType resolverType) throws ExperimentException
    {
    }

    protected void addInputDatas(AssayRunUploadContext context, Map<ExpData, String> inputDatas, ParticipantVisitResolverType resolverType) throws ExperimentException
    {
    }

    public static ExpData createData(Container c, File file, String name, DataType dataType)
    {
        ExpData data = null;
        if (file != null)
        {
            data = ExperimentService.get().getExpDataByURL(file, c);
        }
        if (data == null)
        {
            data = ExperimentService.get().createData(c, dataType, name);
            data.setLSID(ExperimentService.get().generateGuidLSID(c, dataType));
            if (file != null)
            {
                data.setDataFileURI(FileUtil.getAbsoluteCaseSensitiveFile(file).toURI());
            }
        }
        else
        {
            if (!dataType.matches(new Lsid(data.getLSID())))
            {
                // Reset its LSID so that it's the correct type
                data.setLSID(ExperimentService.get().generateGuidLSID(c, dataType));
            }
        }
        return data;
    }

    protected void addOutputMaterials(AssayRunUploadContext context, Map<ExpMaterial, String> outputMaterials, ParticipantVisitResolverType resolverType) throws ExperimentException
    {
    }

    protected void addOutputDatas(AssayRunUploadContext context, Map<ExpData, String> outputDatas, ParticipantVisitResolverType resolverType) throws ExperimentException
    {
        Map<String, File> files;
        try
        {
            files = context.getUploadedData();
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }

        for (Map.Entry<String, File> entry : files.entrySet())
        {
            ExpData data = DefaultAssayRunCreator.createData(context.getContainer(), entry.getValue(), entry.getValue().getName(), getProvider().getDataType());
            outputDatas.put(data, "Data");
        }

        File primaryFile = files.get(AssayDataCollector.PRIMARY_FILE);
        if (primaryFile != null)
        {
            addRelatedOutputDatas(context.getContainer(), outputDatas, primaryFile, Collections.<AssayDataType>emptyList());
        }
    }

    /**
     * Add files that follow the general naming convention (same basename) as the primary file
     * @param knownRelatedDataTypes data types that should be given a particular LSID or role, others file types
     * will have them auto-generated based on their extension
     */
    public void addRelatedOutputDatas(Container container, Map<ExpData, String> outputDatas, final File primaryFile, List<AssayDataType> knownRelatedDataTypes) throws ExperimentException
    {
        final String baseName = getProvider().getDataType().getFileType().getBaseName(primaryFile);
        if (baseName != null)
        {
            // Grab all the files that are related based on naming convention
            File[] relatedFiles = primaryFile.getParentFile().listFiles(getRelatedOutputDataFileFilter(primaryFile, baseName));
            if (relatedFiles != null)
            {
                for (File relatedFile : relatedFiles)
                {
                    Pair<ExpData, String> dataOutput = createdRelatedOutputData(container, knownRelatedDataTypes, baseName, relatedFile);
                    if (dataOutput != null)
                    {
                        outputDatas.put(dataOutput.getKey(), dataOutput.getValue());
                    }
                }
            }
        }
    }

    protected void resolveExtraRunData(ParticipantVisitResolver resolver,
                                  AssayRunUploadContext context,
                                  Map<ExpMaterial, String> inputMaterials,
                                  Map<ExpData, String> inputDatas,
                                  Map<ExpMaterial, String> outputMaterials,
                                  Map<ExpData, String> outputDatas) throws ExperimentException
    {

    }

    /**
     * Create an ExpData object for the file, and figure out what its role name should be
     * @return null if the file is already linked to another run 
     */
    @Nullable
    public static Pair<ExpData, String> createdRelatedOutputData(Container container, List<AssayDataType> knownRelatedDataTypes, String baseName, File relatedFile)
    {
        String roleName = null;
        DataType dataType = null;
        for (AssayDataType inputType : knownRelatedDataTypes)
        {
            // Check if we recognize it as a specially handled file type
            if (inputType.getFileType().isMatch(relatedFile.getName(), baseName))
            {
                roleName = inputType.getRole();
                dataType = inputType;
                break;
            }
        }
        // If not, make up a new type and role for it
        if (roleName == null)
        {
            roleName = relatedFile.getName().substring(baseName.length());
            while (roleName.length() > 0 && (roleName.startsWith(".") || roleName.startsWith("-") || roleName.startsWith("_") || roleName.startsWith(" ")))
            {
                roleName = roleName.substring(1);
            }
            if ("".equals(roleName))
            {
                roleName = null;
            }
            dataType = AbstractAssayProvider.RELATED_FILE_DATA_TYPE;
        }
        ExpData data = createData(container, relatedFile, relatedFile.getName(), dataType);
        if (data.getSourceApplication() == null)
        {
            return new Pair<ExpData, String>(data, roleName);
        }

        // The file is already linked to another run, so this one must have not created it
        return null;
    }

    protected void savePropertyObject(ExpObject object, Map<DomainProperty, String> properties, User user) throws ExperimentException
    {
        try
        {
            for (Map.Entry<DomainProperty, String> entry : properties.entrySet())
            {
                // Treat the empty string as a null in the database, which is our normal behavior when receiving data
                // from HTML forms.
                String value = entry.getValue();
                if (value != null && !value.equals(""))
                {
                    DomainProperty pd = entry.getKey();
                    object.setProperty(user, pd.getPropertyDescriptor(), value);
                }
            }
        }
        catch (ValidationException ve)
        {
            throw new ExperimentException(ve.getMessage(), ve);
        }
    }

    public static List<ValidationError> validateColumnProperties(Map<ColumnInfo, String> properties)
    {
        List<ValidationError> errors = new ArrayList<ValidationError>();
        for (Map.Entry<ColumnInfo, String> entry : properties.entrySet())
        {      
            validateProperty(entry.getValue(), entry.getKey().getName(), false, entry.getKey().getJavaClass(), errors);
        }
        return errors;
    }

    public static List<ValidationError> validateProperties(Map<DomainProperty, String> properties)
    {
        List<ValidationError> errors = new ArrayList<ValidationError>();

        for (Map.Entry<DomainProperty, String> entry : properties.entrySet())
        {
            DomainProperty dp = entry.getKey();
            String value = entry.getValue();
            String label = dp.getPropertyDescriptor().getNonBlankCaption();
            PropertyType type = dp.getPropertyDescriptor().getPropertyType();
            validateProperty(value, label, dp.isRequired(), type.getJavaType(), errors);
        }
        return errors;
    }

    private static void validateProperty(String value, String label, Boolean required, Class type, List<ValidationError> errors)
    {
        boolean missing = (value == null || value.length() == 0);
        if (required && missing)
        {
            errors.add(new SimpleValidationError(label + " is required and must be of type " + ColumnInfo.getFriendlyTypeName(type) + "."));
        }
        else if (!missing)
        {
            try
            {
                ConvertUtils.convert(value, type);
            }
            catch (ConversionException e)
            {
                String message = label + " must be of type " + ColumnInfo.getFriendlyTypeName(type) + ".";
                message +=  "  Value \"" + value + "\" could not be converted";
                if (e.getCause() instanceof ArithmeticException)
                    message +=  ": " + e.getCause().getLocalizedMessage();
                else
                    message += ".";

                errors.add(new SimpleValidationError(message));
            }
        }
    }

    protected FileFilter getRelatedOutputDataFileFilter(final File primaryFile, final String baseName)
    {
        return new FileFilter()
        {
            public boolean accept(File f)
            {
                // baseName doesn't include the trailing '.', so add it here.  We want to associate myRun.jpg
                // with myRun.xls, but we don't want to associate myRun2.xls with myRun.xls (which will happen without
                // the trailing dot in the check).
                return f.getName().startsWith(baseName + ".") && !primaryFile.equals(f);
            }
        };
    }

    protected ProviderType getProvider()
    {
        return _provider;
    }

    public DataTransformer getDataTransformer()
    {
        return new DefaultDataTransformer();
    }

    public DataValidator getDataValidator()
    {
        return new DefaultDataTransformer();
    }
}
