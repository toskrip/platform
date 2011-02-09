/*
 * Copyright (c) 2005-2011 LabKey Corporation
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
package org.labkey.pipeline;

import org.labkey.api.view.*;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.pipeline.status.StatusController;
import org.labkey.pipeline.status.PipelineQueryView;

import java.io.PrintWriter;

public class PipelineWebPart extends WebPartView
{
    private static final String partName = "Data Pipeline";

    public static String getPartName()
    {
        return partName;
    }

    public PipelineWebPart(ViewContext viewContext)
    {
        setTitle(getPartName());
        setTitleHref(StatusController.urlShowList(viewContext.getContainer(), false));
    }

    @Override
    protected void renderView(Object model, PrintWriter out) throws Exception
    {
        if (!PipelineService.get().hasValidPipelineRoot(getViewContext().getContainer()) &&
                !PipelineService.get().canModifyPipelineRoot(getViewContext().getUser(), getViewContext().getContainer()))
        {
            HtmlView view = new HtmlView("<table class=\"DataRegion\"><tr><td>Setup required.  Please contact your project administrator.</td></tr></table>");
            view.setTitle(getPartName());
            include(view);
        }
        else
        {
            PipelineQueryView gridView = new PipelineQueryView(getViewContext(), null, StatusController.ShowPartRegionAction.class, true);
            gridView.setPortalLinks(getPortalLinks());
            include(gridView);
        }
    }
}
