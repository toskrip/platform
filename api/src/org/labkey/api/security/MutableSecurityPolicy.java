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
package org.labkey.api.security;

import org.apache.commons.beanutils.ConversionException;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.security.roles.NoPermissionsRole;
import org.labkey.api.util.DateUtil;
import org.json.JSONArray;

import java.util.*;

/*
* User: Dave
* Date: Jun 1, 2009
* Time: 11:14:22 AM
*/

/**
 * A version of a security policy that may be changed and saved to the database. Note that this class
 * is <b>not thread-safe</b> so do not share an instance of this between threads. When modifying
 * an existing policy, create a new instance of this class passing the existing SecurityPolicy instance
 * to the constructor. This will create a copy of the role assignments that you can then modify.
 * To save the policy, pass the instance of this class to SecurityManager.savePolicy().
 */
public class MutableSecurityPolicy extends SecurityPolicy
{
    public MutableSecurityPolicy(@NotNull SecurityPolicy sourcePolicy)
    {
        super(sourcePolicy);
    }

    public MutableSecurityPolicy(@NotNull SecurableResource resource)
    {
        super(resource);
    }

    public MutableSecurityPolicy(@NotNull SecurableResource resource, @NotNull SecurityPolicy sourcePolicy)
    {
        super(resource, sourcePolicy);
    }

    public void addRoleAssignment(@NotNull UserPrincipal principal, @NotNull Class<? extends Role> roleClass)
    {
        addRoleAssignment(principal, RoleManager.getRole(roleClass));
    }

    public void addRoleAssignment(@NotNull UserPrincipal principal, @NotNull Role role)
    {
        if (role.getExcludedPrincipals().contains(principal))
            throw new IllegalArgumentException("The principal " + principal.getName() + " may not be assigned the role " + role.getName() + "!");
        
        RoleAssignment assignment = new RoleAssignment(getResourceId(), principal, role);
        assignment.setUserId(principal.getUserId());
        assignment.setRole(role);
        addAssignment(assignment);
    }

    protected void addAssignment(RoleAssignment assignment)
    {
        _assignments.add(assignment);
    }

    public void removeRoleAssignment(@NotNull UserPrincipal principal, @NotNull Role role)
    {
        if (role.getExcludedPrincipals().contains(principal))
            throw new IllegalArgumentException("The principal " + principal.getName() + " may not be assigned the role " + role.getName() + "!");

        RoleAssignment assignment = new RoleAssignment(getResourceId(), principal, role);
        assignment.setUserId(principal.getUserId());
        assignment.setRole(role);
        removeAssignment(assignment);
    }

    protected void removeAssignment(RoleAssignment assignment)
    {
        _assignments.remove(assignment);
    }

    /**
     * Creates and initializes a policy from the supplied map.
     * Most often, this map will have been generated by the toMap() method,
     * sent to the client, modified, and sent back.
     * A runtime exception will be thrown if the map does not contain
     * correct/sufficient information.
     * @param map A map of policy information
     * @param resource The resource
     * @return An initialized SecurityPolicy
     */
    @NotNull
    public static MutableSecurityPolicy fromMap(@NotNull Map<String, Object> map, @NotNull SecurableResource resource)
    {
        MutableSecurityPolicy policy = new MutableSecurityPolicy(resource);

        Object modified = map.get("modified");
        if (modified instanceof Date)
        {
            policy._modified = (Date)modified;
        }
        else
        {
            String modifiedStr = String.valueOf(modified);
            try
            {
                policy._modified = (modifiedStr == null || modifiedStr.length() == 0) ? null : new Date(DateUtil.parseDateTime(modifiedStr));
            }
            catch (ConversionException x)
            {
            /* */
            }
        }


        //ensure that if there is a property called 'assignments', that it is indeed a list
        if (map.containsKey("assignments"))
        {
            JSONArray assignments;
            if (map.get("assignments") instanceof JSONArray)
                assignments = (JSONArray)map.get("assignments");
            else if (map.get("assignments") instanceof List)
                assignments = new JSONArray(map.get("assignments"));
            else
                throw new IllegalArgumentException("The assignements property does not contain a list!");

            for (Object element : assignments.toMapList())
            {
                if (!(element instanceof Map))
                    throw new IllegalArgumentException("An element within the assignments property was not a map!");
                Map assignmentProps = (Map) element;

                //assignment map must have userId and role props
                if (!assignmentProps.containsKey("userId") || !assignmentProps.containsKey("role"))
                    throw new IllegalArgumentException("A map within the assignments list did not have a userId or role property!");

                //resolve the role and principal
                Role role = RoleManager.getRole((String) assignmentProps.get("role"));
                if (null == role)
                    throw new IllegalArgumentException("The role '" + assignmentProps.get("role") + "' is not a valid role name");

                Integer userId = (Integer) assignmentProps.get("userId");
                if (null == userId)
                    throw new IllegalArgumentException("Null user id passed in role assignment!");

                UserPrincipal principal = SecurityManager.getPrincipal(userId.intValue());
                if (null == principal)
                    continue; //silently ignore--this could happen if the principal was deleted in between the get and save

                policy.addRoleAssignment(principal, role);
            }
        }

        return policy;
    }

    /**
     * This will normalize the policy by performing a few clean-up actions. For instance it will
     * remove all redundant NoPermissionsRole assignments.
     */
    public void normalize()
    {
        if (isEmpty())
            return;

        //remove all NoPermissionsRole assignments
        Role noPermsRole = RoleManager.getRole(NoPermissionsRole.class);
        Iterator<RoleAssignment> iter = _assignments.iterator();
        while (iter.hasNext())
        {
            RoleAssignment ra = iter.next();
            if(noPermsRole.equals(ra.getRole()))
                iter.remove();
        }

        //if we are now empty, we need to add a no perms role assignment for guests to keep the Policy from
        //getting ignored. Otherwise, the SecurityManager will return the parent policy and potentially
        //grant users access who did not have access before
        if (isEmpty())
            addRoleAssignment(SecurityManager.getGroup(Group.groupGuests), noPermsRole);
    }

    /**
     * Clears assigned roles for the user principal
     * @param principal The principal
     */
    public void clearAssignedRoles(@NotNull UserPrincipal principal)
    {
        List<RoleAssignment> toRemove = new ArrayList<>();
        for(RoleAssignment assignment : _assignments)
        {
            if(assignment.getUserId() == principal.getUserId())
                toRemove.add(assignment);
        }
        _assignments.removeAll(toRemove);
    }
}