/*
 * Copyright (c) 2012 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.search.SearchResultTemplate;
import org.labkey.api.search.SearchScope;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;

/**
 * User: adam
 * Date: 2/18/12
 * Time: 10:17 AM
 */
public class DefaultSearchResultTemplate implements SearchResultTemplate
{
    @Override
    public String getName()
    {
        return null;
    }

    @Override
    public String getCategories()
    {
        return null;
    }

    @Override
    public SearchScope getSearchScope()
    {
        return null;
    }

    @NotNull
    @Override
    public String getResultName()
    {
        return "result";
    }

    @Override
    public boolean includeNavigationLinks()
    {
        return true;
    }

    @Override
    public boolean includeAdvanceUI()
    {
        return true;
    }

    @Override
    public String getExtraHtml(ViewContext ctx)
    {
        return null;
    }

    @Override
    public String reviseQuery(ViewContext ctx, String q)
    {
        return q;
    }

    @Override
    public NavTree appendNavTrail(NavTree root, ViewContext ctx, @NotNull SearchScope scope, @Nullable String category)
    {
        Container c = ctx.getContainer();
        String title = "Search";

        switch (scope)
        {
            case All:
                title += " site";
                break;
            case Project:
                title += " project '" + c.getProject().getName() + "'";
                break;
            case Folder:
            case FolderAndSubfolders:
                title += " folder '";
                if ("".equals(c.getName()))
                    title += "root'";
                else
                    title += c.getName() + "'";
                break;
        }

        if (null != category)
            title += " for " + category.replaceAll(" ", "s, ") + "s";

        return root.addChild(title);
    }
}
