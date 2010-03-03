/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.search.view;

import org.labkey.api.util.Search;
import org.labkey.api.view.*;

import java.lang.reflect.InvocationTargetException;

/**
 * User: adam
 * Date: Jan 19, 2010
 * Time: 2:03:13 PM
 */
public class SearchWebPartFactory extends AlwaysAvailableWebPartFactory
{
    public SearchWebPartFactory(String name, String location)
    {
        super(name, location, true, false);
        addLegacyNames("Narrow Search");
    }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
    {
        boolean includeSubfolders = Search.includeSubfolders(webPart);

        if ("right".equals(webPart.getLocation()))
        {
            return new SearchWebPart(includeSubfolders, 0, false);
        }
        else
        {
            return new SearchWebPart(includeSubfolders, 40, true);
        }
    }


    @Override
    public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
    {
        return new JspView<Portal.WebPart>("/org/labkey/search/view/customizeSearchWebPart.jsp", webPart);
    }
}


