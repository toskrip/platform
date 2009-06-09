/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
package org.labkey.study;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.security.User;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.sql.SQLException;

/**
 * User: jgarms
 * Date: Jul 21, 2008
 * Time: 5:07:08 PM
 */
public class StudyUpgrader
{
    private static final String UPGRADE_REQUIRED = "UPGRADE_REQUIRED";

    /**
     * Update extensible tables update required for changes in 8.3
     */
    public static void upgradeExtensibleTables_83(User user) throws SQLException
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        DbScope scope = schema.getScope();
        boolean transactionOwner = !scope.isTransactionActive();

        StudyImpl[] studies = StudyManager.getInstance().getAllStudies();

        for (StudyImpl study : studies)
        {
            try
            {
                if (transactionOwner)
                    scope.beginTransaction();
                addLsids(user, study);
                if (transactionOwner)
                    scope.commitTransaction();
            }
            finally
            {
                if (transactionOwner)
                    scope.closeConnection();
            }
        }
    }

    private static void addLsids(User user, StudyImpl study) throws SQLException
    {
        StudyManager manager = StudyManager.getInstance();
        if (UPGRADE_REQUIRED.equals(study.getLsid()))
        {
            study.initLsid();
            manager.updateStudy(user, study);
        }

        for (CohortImpl cohort : study.getCohorts(user))
        {
            if (UPGRADE_REQUIRED.equals(cohort.getLsid()))
            {
                cohort = cohort.createMutable();
                cohort.initLsid();
                manager.updateCohort(user, cohort);
            }
        }

    }
}
