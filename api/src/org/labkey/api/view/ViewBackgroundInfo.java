/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
package org.labkey.api.view;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;

import java.io.Serializable;

/**
 * ViewBackgroundInfo class
 * <p/>
 * For use inside background threads with no request object.
 * <p/>
 * Created: Oct 4, 2005
 *
 * @author bmaclean
 */
public class ViewBackgroundInfo implements Serializable
{
    // Helper variables stored for use outside LabKey Server context
    private String _containerId;
    private String _pageFlow;
    private String _action;
    private String _userEmail;
    private int _userId;

    // Not supported outside the LabKey Server context
    private transient Container _container;
    private transient User _user;
    private transient ActionURL _url;

    public ViewBackgroundInfo(Container c, User u, ActionURL h)
    {
        setContainer(c);
        setUser(u);
        setUrlHelper(h);
    }

    public String getContainerId()
    {
        return _containerId;
    }

    public String getPageFlow()
    {
        return _pageFlow;
    }

    public String getAction()
    {
        return _action;
    }

    public String getUserEmail()
    {
        return _userEmail;
    }

    public int getUserId()
    {
        return _userId;
    }

    public Container getContainer()
    {
        if (_container == null && _containerId != null)
            _container = ContainerManager.getForId(_containerId);
        return _container;
    }

    public void setContainer(Container container)
    {
        _containerId = (container == null ? null : container.getId());
        _container = container;
    }

    public User getUser()
    {
        if (_user == null)
            _user = UserManager.getUser(_userId);
        return _user;
    }

    public void setUser(User user)
    {
        if (user == null)
            user = UserManager.getGuestUser();
        _userEmail = user.getEmail();
        _userId = user.getUserId();
        _user = user;
    }

    public ActionURL getUrlHelper()
    {
        if (_url == null && _pageFlow != null)
            _url = new ActionURL(_pageFlow, _action, getContainer());
        return _url;
    }

    public void setUrlHelper(ActionURL url)
    {
        _pageFlow = (url == null ? null : url.getPageFlow());
        _action = (url == null ? null : url.getAction());
        _url = url;
    }
}
