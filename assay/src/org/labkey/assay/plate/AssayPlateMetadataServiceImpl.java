package org.labkey.assay.plate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayResultDomainKind;
import org.labkey.api.assay.AssayRunDomainKind;
import org.labkey.api.assay.AssaySchema;
import org.labkey.api.assay.plate.AssayPlateMetadataService;
import org.labkey.api.assay.plate.PlateService;
import org.labkey.api.assay.plate.PlateTemplate;
import org.labkey.api.assay.plate.Position;
import org.labkey.api.assay.plate.PositionImpl;
import org.labkey.api.assay.plate.WellGroupTemplate;
import org.labkey.api.assay.security.DesignAssayPermission;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.security.User;
import org.labkey.assay.TsvAssayProvider;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AssayPlateMetadataServiceImpl implements AssayPlateMetadataService
{
    private boolean _domainDirty;

    @Override
    public void addAssayPlateMetadata(ExpData resultData, ExpData plateMetadata, Container container, User user, ExpRun run, AssayProvider provider, ExpProtocol protocol,
                                      List<Map<String, Object>> inserted, Map<Integer, String> rowIdToLsidMap) throws ExperimentException
    {
        PlateTemplate template = getPlateTemplate(run, provider, protocol);
        if (template != null)
        {
            try
            {
                Map<String, PlateLayer> layers = parseDataFile(plateMetadata.getFile());
                Map<Position, Map<String, Object>> plateData = new HashMap<>();
                Domain domain = ensureDomain(protocol);

                // map the metadata to the plate template
                for (int row=0; row < template.getRows(); row++)
                {
                    for (int col=0; col < template.getColumns(); col++)
                    {
                        Position pos = template.getPosition(row, col);
                        Map<String, Object> wellProps = new CaseInsensitiveHashMap<>();
                        plateData.put(pos, wellProps);

                        for (WellGroupTemplate group : template.getWellGroups(pos))
                        {
                            PlateLayer plateLayer = layers.get(group.getType().name());
                            if (plateLayer != null)
                            {
                                PlateLayer.WellGroup wellGroup = plateLayer.getWellGroups().get(group.getName());
                                if (wellGroup != null)
                                {
                                    for (Map.Entry<String, Object> entry : wellGroup.getProperties().entrySet())
                                    {
                                        DomainProperty domainProperty = ensureDomainProperty(user, domain, entry);
                                        if (!wellProps.containsKey(domainProperty.getName()))
                                            wellProps.put(domainProperty.getName(), entry.getValue());
                                        else
                                            throw new ExperimentException("The metadata property name : " + domainProperty.getName() + " already exists from a different well group for " +
                                                    "the well location : " + pos.getDescription() + ". If well groups overlap from different layers, their metadata property names " +
                                                    "need to be unique.");
                                    }
                                }
                            }
                        }
                    }
                }

                if (_domainDirty)
                {
                    domain.save(user);
                    domain = getPlateDataDomain(protocol);
                }

                Map<String, PropertyDescriptor> descriptorMap = domain.getProperties().stream().collect(Collectors.toMap(DomainProperty :: getName, DomainProperty :: getPropertyDescriptor));
                List<Map<String, Object>> jsonData = new ArrayList<>();
                Set<PropertyDescriptor> propsToInsert = new HashSet<>();

                // merge the plate data with the uploaded result data
                for (Map<String, Object> row : inserted)
                {
                    // ensure the result data includes a wellLocation field with values like : A1, F12, etc
                    if (row.containsKey(AssayResultDomainKind.WELL_LOCATION_COLUMN_NAME))
                    {
                        Object rowId = row.get("RowId");
                        if (rowId != null)
                        {
                            PositionImpl well = new PositionImpl(container, String.valueOf(row.get(AssayResultDomainKind.WELL_LOCATION_COLUMN_NAME)));
                            // need to adjust the column value to be 0 based to match the template locations
                            well.setColumn(well.getColumn()-1);

                            if (plateData.containsKey(well))
                            {
                                Map<String, Object> jsonRow = new HashMap<>();
                                plateData.get(well).forEach((k, v) -> {
                                    if (descriptorMap.containsKey(k))
                                    {
                                        jsonRow.put(descriptorMap.get(k).getURI(), v);
                                        propsToInsert.add(descriptorMap.get(k));
                                    }
                                });
                                jsonRow.put("RowId", rowId);
                                jsonData.add(jsonRow);
                            }
                        }
                    }
                    else
                        throw new ExperimentException("Imported data must contain a WellLocation column to support plate metadata integration");
                }

                if (!jsonData.isEmpty())
                {
                    try
                    {
                        final OntologyManager.ImportHelper helper = new OntologyManager.ImportHelper()
                        {
                            @Override
                            public String beforeImportObject(Map<String, Object> map) throws SQLException
                            {
                                Integer rowId = (Integer) map.get("RowId");
                                if (rowId != null)
                                {
                                    return rowIdToLsidMap.get(rowId);
                                }
                                else
                                    throw new SQLException("No RowId found in the row map.");
                            }

                            @Override
                            public void afterBatchInsert(int currentRow) throws SQLException
                            {
                            }

                            @Override
                            public void updateStatistics(int currentRow) throws SQLException
                            {
                            }
                        };

                        int objectId = OntologyManager.ensureObject(container, resultData.getLSID());
                        OntologyManager.insertTabDelimited(container, user, objectId, helper, new ArrayList<>(propsToInsert), jsonData, true);
                    }
                    catch (Exception e)
                    {
                        throw new ExperimentException(e);
                    }
                }
            }
            catch (Exception e)
            {
                throw new ExperimentException(e);
            }
        }
        else
        {
            throw new ExperimentException("Unable to resolve the plate template for the run");
        }
    }

    @Nullable
    private PlateTemplate getPlateTemplate(ExpRun run, AssayProvider provider, ExpProtocol protocol)
    {
        Domain runDomain = provider.getRunDomain(protocol);
        DomainProperty plateTemplate = runDomain.getPropertyByName(AssayRunDomainKind.PLATE_TEMPLATE_COLUMN_NAME);
        if (plateTemplate != null)
        {
            Object templateLsid = run.getProperty(plateTemplate);
            if (templateLsid instanceof String)
            {
                return PlateService.get().getPlateTemplateFromLsid(protocol.getContainer(), (String)templateLsid);
            }
        }
        return null;
    }

    private Map<String, PlateLayer> parseDataFile(File dataFile) throws ExperimentException
    {
        try
        {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, PlateLayer> layers = new CaseInsensitiveHashMap<>();

            JsonNode rootNode = mapper.readTree(dataFile);

            JsonNode metadata = rootNode.get("metadata");
            metadata.fields().forEachRemaining(layerEntry -> {

                String layerName = layerEntry.getKey();
                if (!layers.containsKey(layerName))
                    layers.put(layerName, new PlateLayer(layerName));

                PlateLayer currentLayer = layers.get(layerName);

                layerEntry.getValue().fields().forEachRemaining(wellEntry -> {

                    if (!currentLayer.getWellGroups().containsKey(wellEntry.getKey()))
                        currentLayer.addWellGroup(new PlateLayer.WellGroup(wellEntry.getKey()));

                    PlateLayer.WellGroup currentWellGroup = currentLayer.getWellGroups().get(wellEntry.getKey());
                    wellEntry.getValue().fields().forEachRemaining(propEntry -> {
                        if (propEntry.getValue().isTextual())
                            currentWellGroup.addProperty(propEntry.getKey(), propEntry.getValue().textValue());
                        else if (propEntry.getValue().isNumber())
                            currentWellGroup.addProperty(propEntry.getKey(), propEntry.getValue().numberValue());
                        else if (propEntry.getValue().isBoolean())
                            currentWellGroup.addProperty(propEntry.getKey(), propEntry.getValue().booleanValue());
                        else
                        {
                            // log a warning
                        }
                    });
                });
            });

            return layers;
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }
    }

    @Override
    public @Nullable Domain getPlateDataDomain(ExpProtocol protocol)
    {
        String uri = getPlateDataDomainUri(protocol);
        return PropertyService.get().getDomain(protocol.getContainer(), uri);
    }

    private String getPlateDataDomainUri(ExpProtocol protocol)
    {
        DomainKind domainKind = PropertyService.get().getDomainKindByName(AssayPlateDataDomainKind.KIND_NAME);
        return domainKind.generateDomainURI(AssaySchema.NAME, protocol.getName(), protocol.getContainer(), null);
    }

    private Domain ensureDomain(ExpProtocol protocol)
    {
        Domain domain = getPlateDataDomain(protocol);
        if (domain == null)
        {
            _domainDirty = true;
            domain = PropertyService.get().createDomain(protocol.getContainer(), getPlateDataDomainUri(protocol), "PlateDataDomain");
        }
        return domain;
    }

    private DomainProperty ensureDomainProperty(User user, Domain domain, Map.Entry<String, Object> prop) throws ExperimentException
    {
        DomainProperty domainProperty = domain.getPropertyByName(prop.getKey());
        if (domainProperty == null && prop.getValue() != null)
        {
            // we are dynamically adding a new property to the plate data domain, ensure the user has at least
            // the DesignAssayPermission
            if (!domain.getContainer().hasPermission(user, DesignAssayPermission.class))
                throw new ExperimentException("This import will create a new plate metadata field : " + prop.getKey() + ". Only users with the AssayDesigner role are allowed to do this.");

            _domainDirty = true;
            PropertyStorageSpec spec = new PropertyStorageSpec(prop.getKey(), JdbcType.valueOf(prop.getValue().getClass()));
            return domain.addProperty(spec);
        }
        return domainProperty;
    }

    private static class PlateLayer
    {
        private String _name;
        private Map<String, WellGroup> _wellGroupMap = new CaseInsensitiveHashMap<>();

        public PlateLayer(String name)
        {
            _name = name;
        }

        public String getName()
        {
            return _name;
        }

        public Map<String, WellGroup> getWellGroups()
        {
            return _wellGroupMap;
        }

        public void addWellGroup(WellGroup wellGroup)
        {
            _wellGroupMap.put(wellGroup.getName(), wellGroup);
        }

        public static class WellGroup
        {
            private String _name;
            private Map<String, Object> _properties = new CaseInsensitiveHashMap<>();

            public WellGroup(String name)
            {
                _name = name;
            }

            public String getName()
            {
                return _name;
            }

            public Map<String, Object> getProperties()
            {
                return _properties;
            }

            public void addProperty(String name, Object value)
            {
                _properties.put(name, value);
            }
        }
    }
}
