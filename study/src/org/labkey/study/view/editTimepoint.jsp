<%
/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.study.DataSet"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.CohortFilterFactory" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.CohortImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.VisitDataSet" %>
<%@ page import="org.labkey.study.model.VisitDataSetType" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.study.Visit" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    JspView<StudyController.VisitSummaryBean> me = (JspView<StudyController.VisitSummaryBean>) HttpView.currentView();
    StudyController.VisitSummaryBean visitBean = me.getModelBean();
    VisitImpl visit = visitBean.getVisit();
    List<CohortImpl> cohorts = StudyManager.getInstance().getCohorts(me.getViewContext().getContainer(), me.getViewContext().getUser());
%>
<labkey:errors/>
<form action="<%=h(buildURL(StudyController.VisitSummaryAction.class))%>" method="POST">
<input type="hidden" name=".oldValues" value="<%=PageFlowUtil.encodeObject(visit)%>">
<input type="hidden" name="id" value="<%=visit.getRowId()%>">
    <table>
<%--        <tr>
            <td class="labkey-form-label">Name&nbsp;<%=helpPopup("Name", "Short unique name, e.g. 'Enroll'")%></td>
            <td>
                <input type="text" size="50" name="name" value="<%= h(visit.getName()) %>">
            </td>
        </tr> --%>
        <tr>
            <td class="labkey-form-label">Label&nbsp;<%=helpPopup("Label", "Descriptive Label e.g. 'Week 2'")%></td>
            <td>
                <input type="text" size="50" name="label" value="<%= h(visit.getLabel()) %>">
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Day Range&nbsp;<%=helpPopup("Day Range", "Days from start date encompassing this visit. E.g. 11-17 for Week 2")%></td>
            <td>
                <input type="text" size="26" name="sequenceNumMin" value="<%= (int) visit.getSequenceNumMin() %>">-<input type="text" size="26" name="sequenceNumMax" value="<%= (int) visit.getSequenceNumMax() %>">
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Description&nbsp;<%=helpPopup("Description", "A short description of the visit, appears as hovertext on visit headers in study navigator and visit column in datasets.")%></td>
            <td>
                <textarea name="description" cols="50" rows="3"><%= h(visit.getDescription()) %></textarea>
            </td>
        </tr>
        <tr>
            <td class=labkey-form-label>Type</td>
            <td>
                <select name="typeCode">
                    <option value="">[None]</option>
                    <%
                        for (Visit.Type type : Visit.Type.values())
                        {
                            boolean selected = (visit.getType() == type);
                    %>
                    <option value="<%= type.getCode() %>"<%=selected(selected)%>><%=h(type.getMeaning())%></option>
                    <%
                        }
                    %>
                </select>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Cohort</td>
            <td>
                <%
                    if (cohorts == null || cohorts.size() == 0)
                    {
                %>
                    <em>No cohorts defined</em>
                <%
                    }
                    else
                    {
                    %>
                    <select name="<%= h(CohortFilterFactory.Params.cohortId.name()) %>">
                        <option value="">All</option>
                    <%

                        for (CohortImpl cohort : cohorts)
                        {
                    %>
                        <option value="<%= cohort.getRowId()%>"<%=selected(visit.getCohortId() != null && visit.getCohortId() == cohort.getRowId()) %>>
                            <%= h(cohort.getLabel())%>
                        </option>
                    <%
                        }
                    %>
                    </select>
                    <%
                    }
                %>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Show By Default</td>
            <td>
                <input type="checkbox" name="showByDefault"<%=checked((visit.isShowByDefault()))%>>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label" valign="top">Associated Datasets</td>
            <td>
                <table>
                <%
                    HashMap<Integer, VisitDataSetType> typeMap = new HashMap<>();
                    for (VisitDataSet vds : visit.getVisitDataSets())
                        typeMap.put(vds.getDataSetId(), vds.isRequired() ? VisitDataSetType.REQUIRED : VisitDataSetType.OPTIONAL);

                    for (DataSet dataSet : getDataSets())
                    {
                        VisitDataSetType type = typeMap.get(dataSet.getDataSetId());
                        if (null == type)
                            type = VisitDataSetType.NOT_ASSOCIATED;
                %>
                        <tr>
                            <td><%= h(dataSet.getDisplayString()) %></td>
                            <td>
                                <input type="hidden" name="dataSetIds" value="<%= dataSet.getDataSetId() %>">
                                <select name="dataSetStatus">
                                    <option value="<%= h(VisitDataSetType.NOT_ASSOCIATED.name()) %>"
                                        <%=selected(type == VisitDataSetType.NOT_ASSOCIATED)%>></option>
                                    <option value="<%= h(VisitDataSetType.OPTIONAL.name()) %>"
                                        <%=selected(type == VisitDataSetType.OPTIONAL)%>>Optional</option>
                                    <option value="<%= h(VisitDataSetType.REQUIRED.name()) %>"
                                        <%=selected(type == VisitDataSetType.REQUIRED)%>>Required</option>
                                </select>
                            </td>
                        </tr>
                <%
                    }
                %>
                </table>
            </td>
        </tr>
    </table>
    <table>
        <tr>
            <td><%= generateSubmitButton("Save")%>&nbsp;<%= generateButton("Delete visit", buildURL(StudyController.ConfirmDeleteVisitAction.class, "id="+visit.getRowId()))%>&nbsp;<%= generateButton("Cancel", StudyController.ManageVisitsAction.class)%></td>
        </tr>
    </table>
</form>