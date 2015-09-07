/*
 * Copyright (c) 2008-2015 LabKey Corporation
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
package org.labkey.core;

import org.labkey.api.module.FirstRequestHandler;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.view.ViewServlet;

import javax.servlet.http.HttpServletRequest;

/**
 * User: adam
 * Date: Nov 5, 2008
 * Time: 2:04:48 PM
 */
public class CoreFirstRequestHandler implements FirstRequestHandler.FirstRequestListener
{
    public void handleFirstRequest(HttpServletRequest request)
    {
        ViewServlet.initialize();
        ModuleLoader.getInstance().initControllerToModule();
        AuthenticationManager.initialize();
    }
}
