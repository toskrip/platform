/*
 * Copyright (c) 2009-2016 LabKey Corporation
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

package org.labkey.api.writer;

import org.labkey.api.security.User;
import org.labkey.api.data.Container;

/*
* User: adam
* Date: Aug 25, 2009
* Time: 9:21:07 AM
*/
public interface ContainerUser
{
    User getUser();
    Container getContainer();

    /** Creates a minimal implementation of the interface */
    static ContainerUser create(final Container c, final User u)
    {
        return new ContainerUser()
        {
            @Override
            public User getUser()
            {
                return u;
            }

            @Override
            public Container getContainer()
            {
                return c;
            }
        };
    }
}
