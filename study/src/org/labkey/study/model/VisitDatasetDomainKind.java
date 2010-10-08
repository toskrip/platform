package org.labkey.study.model;

import org.labkey.api.exp.property.Domain;
import org.labkey.api.study.TimepointType;

import java.util.Collections;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Oct 8, 2010
 * Time: 12:28:23 PM
 */
public class VisitDatasetDomainKind extends DatasetDomainKind
{
    @Override
    public String getKindName()
    {
        return "StudyDatasetVisit";
    }

    public boolean isDomainType(String domainURI)
    {
        DataSetDefinition def  = getDatasetDefinition(domainURI);
        return null!=def && def.getStudy().getTimepointType() == TimepointType.VISIT;
    }
    
    @Override
    public Set<String> getReservedPropertyNames(Domain domain)
    {
        return Collections.unmodifiableSet(DataSetDefinition.DEFAULT_VISIT_FIELDS);
    }
}


