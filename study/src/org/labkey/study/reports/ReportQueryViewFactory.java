package org.labkey.study.reports;

import org.labkey.api.reports.report.view.ReportQueryView;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.HttpView;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.DataRegion;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;
import org.apache.commons.lang.StringUtils;

import javax.servlet.ServletException;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Apr 24, 2007
 */
public class ReportQueryViewFactory
{
    private static ReportQueryViewFactory _instance = new ReportQueryViewFactory();

    public static ReportQueryViewFactory get()
    {
        return _instance;
    }

    private ReportQueryViewFactory(){};

    public ReportQueryView generateQueryView(ViewContext context, ReportDescriptor descriptor,
                                                    String queryName, String viewName) throws Exception
    {
        StudyQuerySchema schema = getStudyQuerySchema(context, ACL.PERM_READ, descriptor);
        if (schema != null)
        {
            QuerySettings settings = new QuerySettings(context.getActionURL(), descriptor.getProperty(QueryParam.dataRegionName.toString()));
            settings.setSchemaName(schema.getSchemaName());
            settings.setQueryName(queryName);
            settings.setViewName(viewName);

            ReportQueryView view = new StudyReportQueryView(schema, settings);
            final String filterParam = descriptor.getProperty("filterParam");
            if (!StringUtils.isEmpty(filterParam))
            {
                final String filterValue = context.getActionURL().getParameter(filterParam);
                if (filterValue != null)
                {
                    view.setFilter(new SimpleFilter(filterParam, filterValue));
                }
            }
            return view;
        }

        return null;
    }

    /** return true if report should do regular permission checking
     * by checking ACLs on underlying datasets using dataset.getTableInfo(user)
     * false if no permission checking is required.  and throws if
     * user does not have permission.
     */
    private boolean mustCheckDatasetPermissions(User user, ReportDescriptor descriptor) throws ServletException
    {
        ACL acl = descriptor.getACL();
        if (acl == null || acl.isEmpty())
            return true;    // normal permission checking

        if ((descriptor.getPermissions(user) & ACL.PERM_READ) == 0)
            HttpView.throwUnauthorized();

        return false;   // user is OK, don't check permissions
    }

    private StudyQuerySchema getStudyQuerySchema(ViewContext context, int perm, ReportDescriptor descriptor) throws ServletException
    {
        if (perm != ACL.PERM_READ)
            throw new IllegalArgumentException("only PERM_READ supported");
        Study study = StudyManager.getInstance().getStudy(context.getContainer());
        boolean mustCheckUserPermissions = mustCheckDatasetPermissions(context.getUser(), descriptor);

        if (study != null)
            return new StudyQuerySchema(study, context.getUser(), mustCheckUserPermissions);
        return null;
    }

    public static class StudyReportQueryView extends ReportQueryView
    {
        public StudyReportQueryView(UserSchema schema, QuerySettings settings)
        {
            super(schema, settings);
        }

        public DataRegion createDataRegion()
        {
            DataRegion data = super.createDataRegion();
            StudyManager.getInstance().applyDefaultFormats(getContainer(), data.getDisplayColumns());
            return data;
        }
    }
}
