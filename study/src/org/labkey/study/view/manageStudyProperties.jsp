<%
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
%>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<form action="updateStudyProperties.post" method="POST">
    <table>
        <tr>
            <th>Study Label</th>
            <td><input type="text" size="40" name="label" value="<%= h(getStudy().getLabel()) %>"></td>
        </tr>
        <tr>
            <td>&nbsp;</td>
            <td><%= buttonImg("Update")%>&nbsp;<%= buttonLink("Cancel", "manageStudy.view")%></td>
        </tr>
    </table>
</form>