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

package org.labkey.api.study.actions;

import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

/**
 * User: brittp
* Date: Jul 26, 2007
* Time: 7:23:24 PM
*/
@RequiresPermission(ACL.PERM_READ) //will check explicity in code below
public class DeleteAction extends BaseAssayAction<ProtocolIdForm>
{
    public ModelAndView getView(ProtocolIdForm protocolIdForm, BindException errors) throws Exception
    {
        ExpProtocol protocol = protocolIdForm.getProtocol();
        
        if(!allowDelete(protocol))
            HttpView.throwUnauthorized("You do not have sufficient permissions to delete this assay design.");

        ExperimentService.get().deleteProtocolByRowIds(getViewContext().getContainer(), getViewContext().getUser(), protocolIdForm.getProtocol().getRowId());
        HttpView.throwRedirect(new ActionURL("assay", "begin", getViewContext().getContainer()));
        return null;
    }

    private boolean allowDelete(ExpProtocol protocol)
    {
        ViewContext ctx = getViewContext();
        return ctx.hasPermission(ACL.PERM_DELETE) ||
                    (ctx.hasPermission(ACL.PERM_DELETEOWN) && ctx.getUser().equals(protocol.getCreatedBy()));
    }
}
