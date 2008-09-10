/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.experiment.api.property;

import org.labkey.api.exp.property.*;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.data.Container;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.sql.SQLException;

public class PropertyServiceImpl implements PropertyService.Interface
{
    List<DomainKind> _domainTypes = new ArrayList<DomainKind>();
    Map<String, ValidatorKind> _validatorTypes = new HashMap<String, ValidatorKind>();


    public IPropertyType getType(Container container, String typeURI)
    {
        Domain domain = getDomain(container, typeURI);
        if (domain != null)
        {
            return domain;
        }
        return new PrimitiveType(PropertyType.getFromURI(null, typeURI));
    }

    public Domain getDomain(Container container, String domainURI)
    {
        DomainDescriptor dd = OntologyManager.getDomainDescriptor(domainURI, container);
        if (dd == null)
            return null;
        return new DomainImpl(dd);
    }

    public Domain getDomain(int domainId)
    {
        DomainDescriptor dd = OntologyManager.getDomainDescriptor(domainId);
        if (dd == null)
            return null;
        return new DomainImpl(dd);
    }

    public Domain createDomain(Container container, String typeURI, String name)
    {
        return new DomainImpl(container, typeURI, name);
    }

    public DomainKind getDomainKind(String typeURI)
    {
        for (DomainKind type : _domainTypes)
        {
            if (type.isDomainType(typeURI))
                return type;
        }
        return null;
    }

    public void registerDomainKind(DomainKind type)
    {
        _domainTypes.add(type);
    }

    public Domain[] getDomains(Container container)
    {
        try
        {
            DomainDescriptor[] dds = OntologyManager.getDomainDescriptors(container);
            Domain[] ret = new Domain[dds.length];
            for (int i = 0; i < dds.length; i ++)
            {
                ret[i] = new DomainImpl(dds[i]);
            }
            return ret;
        }
        catch(SQLException e)
        {
            return new Domain[0];
        }
    }

    public void registerValidatorKind(ValidatorKind validatorKind)
    {
        if (_validatorTypes.containsKey(validatorKind.getTypeURI()))
            throw new IllegalArgumentException("Validator type : " + validatorKind.getTypeURI() + " is already registered");

        _validatorTypes.put(validatorKind.getTypeURI(), validatorKind);
    }

    public ValidatorKind getValidatorKind(String typeURI)
    {
        return _validatorTypes.get(typeURI);
    }

    public IPropertyValidator createValidator(String typeURI)
    {
        ValidatorKind kind = getValidatorKind(typeURI);
        if (kind != null)
            return kind.createInstance();
        return null;
    }

    public IPropertyValidator[] getPropertyValidators(PropertyDescriptor desc)
    {
        List<IPropertyValidator> validators = new ArrayList<IPropertyValidator>();

        for (PropertyValidator v :DomainPropertyManager.get().getValidators(desc))
        {
            validators.add(new PropertyValidatorImpl(v));            
        }
        return validators.toArray(new IPropertyValidator[0]);
    }

    public void deleteValidatorsForPropertyDescriptor(int descriptorId) throws SQLException
    {
        DomainPropertyManager.get().removeValidatorsForPropertyDescriptor(descriptorId);
    }

    public void deleteValidatorsForContainer(Container c) throws SQLException
    {
        DomainPropertyManager.get().deleteAllValidators(c);
    }
}
