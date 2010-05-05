/*
 * Copyright (c) 2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.filecontent;

import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: May 4, 2010
 * Time: 4:24:36 PM
 * To change this template use File | Settings | File Templates.
 */
public class FilePropertiesDomainKind extends DomainKind
{
    private static final String[] RESERVED_FIELDS = new String[]
    {
            "name",
            "iconHref",
            "modified",
            "size",
            "createdBy",
            "description",
            "actionHref",
            "fileExt"
    };
    private static final CaseInsensitiveHashSet _reservedFieldSet;

    static {
        _reservedFieldSet = new CaseInsensitiveHashSet(RESERVED_FIELDS);

        for (ExpDataTable.Column col : ExpDataTable.Column.values())
            _reservedFieldSet.add(col.name());
    }

    @Override
    public String getTypeLabel(Domain domain)
    {
        return domain.getName();
    }

    @Override
    public boolean isDomainType(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        return "FileProperties".equals(lsid.getNamespacePrefix());
    }

    @Override
    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        return new SQLFragment("NULL");
    }

    @Override
    public Map.Entry<TableInfo, ColumnInfo> getTableInfo(User user, Domain domain, Container[] containers)
    {
        return null;
    }

    @Override
    public ActionURL urlShowData(Domain domain)
    {
        return null;
    }

    @Override
    public ActionURL urlEditDefinition(Domain domain)
    {
        return new ActionURL(FileContentController.DesignerAction.class, domain.getContainer());
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain)
    {
        return _reservedFieldSet;
    }
}
