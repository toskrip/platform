package org.labkey.study.samples;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.query.FieldKey;
import org.labkey.study.assay.AssayPublishManager;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 7/18/13
 */
public class SpecimenCommentAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String SPECIMEN_COMMENT_EVENT = "SpecimenCommentEvent";

    public static final String COLUMN_NAME_VIAL_ID = "VialId";

    @Override
    protected DomainKind getDomainKind()
    {
        return new SpecimenCommentAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return SPECIMEN_COMMENT_EVENT;
    }

    @Override
    public String getLabel()
    {
        return "Specimen Comments and QC";
    }

    @Override
    public String getDescription()
    {
        return "Specimen Comments and QC";
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        SpecimenCommentAuditEvent bean = new SpecimenCommentAuditEvent();
        copyStandardFields(bean, event);

        bean.setVialId(event.getKey1());

        return (K)bean;
    }

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyNames = super.legacyNameMap();
        legacyNames.put(FieldKey.fromParts("key1"), COLUMN_NAME_VIAL_ID);
        return legacyNames;
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)SpecimenCommentAuditEvent.class;
    }

    public static class SpecimenCommentAuditEvent extends AuditTypeEvent
    {
        private String _vialId;

        public SpecimenCommentAuditEvent()
        {
            super();
        }

        public SpecimenCommentAuditEvent(String container, String comment)
        {
            super(AssayPublishManager.ASSAY_PUBLISH_AUDIT_EVENT, container, comment);
        }

        public String getVialId()
        {
            return _vialId;
        }

        public void setVialId(String vialId)
        {
            _vialId = vialId;
        }
    }

    public static class SpecimenCommentAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "SpecimenCommentAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private static final Set<PropertyStorageSpec> _fields = new LinkedHashSet<>();

        static {
            _fields.add(createFieldSpec(COLUMN_NAME_VIAL_ID, JdbcType.VARCHAR));
        }

        @Override
        protected Set<PropertyStorageSpec> getColumns()
        {
            return _fields;
        }

        @Override
        protected String getNamespacePrefix()
        {
            return NAMESPACE_PREFIX;
        }

        @Override
        public String getKindName()
        {
            return NAME;
        }
    }
}
