/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.query.view;

import org.labkey.api.view.*;
import org.labkey.api.query.QueryWebPart;
import org.labkey.api.data.Container;

import java.lang.reflect.InvocationTargetException;

public class QueryWebPartFactory extends BaseWebPartFactory
{
    public QueryWebPartFactory()
    {
        super("Query", null, true, true);
    }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
    {
        QueryWebPart ret = new QueryWebPart(portalCtx, webPart);
        populateProperties(ret, webPart.getPropertyMap());
        return ret;
    }

    public HttpView getEditView(Portal.WebPart webPart)
    {
        return new EditQueryView(webPart);
    }

    public boolean isAvailable(Container c, String location)
    {
        return location.equals(getDefaultLocation());
    }
}
