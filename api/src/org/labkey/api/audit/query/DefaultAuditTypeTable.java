package org.labkey.api.audit.query;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.data.DbSchema;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 7/11/13
 */
public class DefaultAuditTypeTable extends FilteredTable<UserSchema>
{
    private AuditTypeProvider _provider;

    public DefaultAuditTypeTable(AuditTypeProvider provider, Domain domain, DbSchema dbSchema, UserSchema schema)
    {
        super(StorageProvisioner.createTableInfo(domain, dbSchema), schema);

        _provider = provider;

        wrapAllColumns(true);
    }

    @Nullable
    @Override
    public QueryUpdateService getUpdateService()
    {
        return new DefaultQueryUpdateService(this, this.getRealTable());
    }

    @Override
    public String getDescription()
    {
        if (_provider != null)
        {
            return StringUtils.defaultIfEmpty(_provider.getDescription(), super.getDescription());
        }
        return super.getDescription();
    }

    private boolean isGuest(UserPrincipal user)
    {
        return user instanceof User && user.isGuest();
    }

    @Override
    public boolean hasPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        // Don't allow deletes or updates for audit events, and don't let guests insert
        return ((perm.equals(InsertPermission.class) && !isGuest(user)) || perm.equals(ReadPermission.class)) &&
            getContainer().hasPermission(user, perm);
    }
}
