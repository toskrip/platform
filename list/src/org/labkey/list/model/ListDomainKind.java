package org.labkey.list.model;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.AbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;

import java.sql.Types;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * User: Nick
 * Date: 5/8/13
 * Time: 4:12 PM
 */
public abstract class ListDomainKind extends AbstractDomainKind
{
    private String KEY_FIELD = "Key";

    /*
     * the columns common to all lists
     */
    private final static Set<PropertyStorageSpec> BASE_PROPERTIES;

    static
    {
        PropertyStorageSpec[] props =
        {
            new PropertyStorageSpec("created", Types.TIMESTAMP),
            new PropertyStorageSpec("modified", Types.TIMESTAMP),
            new PropertyStorageSpec("createdBy", Types.INTEGER),
            new PropertyStorageSpec("modifiedBy", Types.INTEGER),
            new PropertyStorageSpec("entityId", Types.VARCHAR),
            new PropertyStorageSpec("lastIndexed", Types.TIMESTAMP)
        };

        BASE_PROPERTIES = new HashSet<>(Arrays.asList(props));
    }

    @Override
    public String getTypeLabel(Domain domain)
    {
        return "HD List '" + domain.getName() + "'";
    }

    @Override
    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        throw new UnsupportedOperationException("sqlObjectIdsInDomain NYI for ListDomainKind");
    }

    @Override
    public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
        ListDefinition listDef = ListService.get().getList(domain);
        if (null == listDef)
            return null;
        return listDef.urlShowData();
    }

    @Nullable
    @Override
    public ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        ListDefinition listDef = ListService.get().getList(domain);
        if (null == listDef)
            return null;
        return listDef.urlShowDefinition();
    }

    @Override
    public Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        return getKindName().equals(lsid.getNamespacePrefix()) ? Priority.MEDIUM : null;
    }


    @Override
    public Set<PropertyStorageSpec> getBaseProperties()
    {
        Set<PropertyStorageSpec> specs = new HashSet<>(BASE_PROPERTIES);
        specs.add(getKeyProperty(KEY_FIELD));
        specs.addAll(super.getBaseProperties());
        return specs;
    }

    abstract PropertyStorageSpec getKeyProperty(String keyColumnName);

    @Override
    public Set<String> getReservedPropertyNames(Domain domain)
    {
        return PageFlowUtil.set(KEY_FIELD, "EntityId", "Created", "CreatedBy", "Modified", "ModifiedBy", "LastIndexed"); // ObjectId?
    }

    @Override
    public String getStorageSchemaName()
    {
        return ListSchema.getInstance().getSchemaName();
    }

    @Override
    public DbScope getScope()
    {
        return ListSchema.getInstance().getSchema().getScope();
    }

    public static Lsid generateDomainURI(String name, Container c, ListDefinition.KeyType keyType)
    {
        String type;
        if (ListDefinitionImpl.ontologyBased())
        {
            type = ListDefinitionImpl.NAMESPACE_PREFIX;
        }
        else
        {
            switch (keyType)
            {
                case Integer:
                case AutoIncrementInteger:
                    type = IntegerListDomainKind.NAMESPACE_PREFIX;
                    break;
                case Varchar:
                    type = VarcharListDomainKind.NAMESPACE_PREFIX;
                    break;
                default:
                    throw new IllegalStateException();
            }
        }

        String typeURI = "urn:lsid:" + PageFlowUtil.encode(AppProps.getInstance().getDefaultLsidAuthority()) + ":" + type + ".Folder-" + c.getRowId() + ":" + PageFlowUtil.encode(name);
        //bug 13131.  uniqueify the lsid for situations where a preexisting list was renamed
        int i = 1;
        String uniqueURI = typeURI;
        while (OntologyManager.getDomainDescriptor(uniqueURI, c) != null)
        {
            uniqueURI = typeURI + '-' + (i++);
        }
        return new Lsid(uniqueURI);
    }
}
