/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.User;

import java.util.Map;

/**
 * User: adam
 * Date: 10/11/13
 * Time: 4:55 PM
 */
public interface PropertyStore
{
    @NotNull Map<String, String> getProperties(User user, Container container, String category);
    @NotNull Map<String, String> getProperties(Container container, String category);
    @NotNull Map<String, String> getProperties(String category);

    // If create == true, then never returns null. If create == false, will return null if property set doesn't exist.
    public PropertyManager.PropertyMap getWritableProperties(User user, Container container, String category, boolean create);
    public PropertyManager.PropertyMap getWritableProperties(Container container, String category, boolean create);
    public PropertyManager.PropertyMap getWritableProperties(String category, boolean create);

    public void deletePropertySet(User user, Container container, String category);
    public void deletePropertySet(Container container, String category);
    public void deletePropertySet(String category);

    // Map must be a PropertyMap
    public void saveProperties(Map<String, String> map);
}
