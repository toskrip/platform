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

package org.labkey.api.study;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.List;
import java.sql.SQLException;

/**
 * User: jeckels
 * Date: Apr 23, 2007
 */
public interface PlateTypeHandler
{
    public String getAssayType();

    public List<String> getTemplateTypes();

    /**
     * createPlate will be given a null value for templateTypeName when it is creating a new template which is a 
     * default for that assay type.
     */
    public PlateTemplate createPlate(String templateTypeName, Container container) throws SQLException;

    public WellGroup.Type[] getWellGroupTypes();
}
