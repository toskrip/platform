/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
package org.labkey.api.gwt.client.ui;

import com.google.gwt.user.client.ui.HTML;

/**
 * User: jeckels
 * Date: Feb 22, 2012
 */
public class LabKeyLinkHTML extends HTML
{
    public LabKeyLinkHTML(String linkText)
    {
        this(linkText, null);
    }

    public LabKeyLinkHTML(String linkText, String href)
    {
        setLabKeyLinkHTML(linkText, href);
    }

    public void setLabKeyLinkHTML(String linkText)
    {
        setLabKeyLinkHTML(linkText, null);
    }

    public void setLabKeyLinkHTML(String linkText, String href)
    {
        if (href == null)
        {
            href = "javascript: void(0)";
        }
        setHTML("<a class='labkey-text-link' href=\"" + href + "\">" + linkText + "</a>");
    }
}

