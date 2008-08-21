/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.security;

import org.labkey.api.action.UrlProvider;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.Container;

/**
 * User: adam
 * Date: Jul 30, 2008
 * Time: 5:26:28 PM
 */
public interface UserUrls extends UrlProvider
{
    ActionURL getSiteUsersURL();
    ActionURL getProjectMembersURL(Container container);
    ActionURL getUserAccessURL(Container container, int userId);
    ActionURL getUserDetailsURL(Container container, int userId);
    ActionURL getUserUpdateURL(ActionURL returnURL);          // TODO: Add userId?
}
