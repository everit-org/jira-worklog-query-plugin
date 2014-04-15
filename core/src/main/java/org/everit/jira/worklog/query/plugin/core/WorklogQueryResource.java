package org.everit.jira.worklog.query.plugin.core;

/*
 * Copyright (c) 2013, Everit Kft.
 *
 * All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */

import java.sql.Timestamp;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.ofbiz.core.entity.EntityCondition;
import org.ofbiz.core.entity.EntityConditionList;
import org.ofbiz.core.entity.EntityExpr;
import org.ofbiz.core.entity.EntityOperator;
import org.ofbiz.core.entity.GenericEntityException;
import org.ofbiz.core.entity.GenericValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.core.ofbiz.CoreFactory;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.ComponentManager;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.Permissions;
import com.atlassian.jira.usercompatibility.UserCompatibilityHelper;
import com.atlassian.jira.util.json.JSONArray;
import com.atlassian.jira.util.json.JSONException;
import com.atlassian.jira.util.json.JSONObject;

/**
 * The WorklogQueryResource class. The class contains the findWorklogs method. The class grant the JIRA worklog query.
 */
@Path("/find")
public class WorklogQueryResource {

    /**
     * The logger used to log.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(WorklogQueryResource.class);

    /**
     * The last hour of a day.
     */
    private static final int LAST_HOUR_OF_DAY = 23;

    /**
     * The last minute of an hour.
     */
    private static final int LAST_MINUTE_OF_HOUR = 59;

    /**
     * The last second of a minute.
     */
    private static final int LAST_SECOND_OF_MINUTE = 59;

    /**
     * The ComponentManager Instance.
     */
    private ComponentManager componentManagerInstance;

    /**
     * Check the required (or optional) parameters. If any parameter missing or conflict return whit the right Response
     * what describe the problem. If everything right the return whit null.
     * 
     * @param startDate
     *            The findWorklogs startDate parameter.
     * @param user
     *            The findWorklogs user parameter.
     * @param group
     *            The findWorklogs group parameter.
     * @return If find bad parameter then return whit Response else null.
     */
    private Response checkRequiredFindWorklogsParameter(final String startDate, final String user, final String group) {
        if (isStringEmpty(startDate)) {
            return Response.status(Response.Status.BAD_REQUEST).
                    entity("The 'startDate' parameter is missing!").build();
        }
        if ((isStringEmpty(user)) && (isStringEmpty(group))) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("The 'user' or the 'group' parameter is missing!").build();
        }
        if ((!isStringEmpty(user)) && (!isStringEmpty(group))) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("The 'user' and the 'group' parameters cannot be present at the same time.").build();
        }
        return null;
    }

    /**
     * Convert the endDate String to Calendar.
     * 
     * @param endDateString
     *            The endDate parameter.
     * @return The formated, valid calendar.
     * @throws ParseException
     *             If cannot parse the String to Calendar.
     */
    private Calendar convertEndDate(final String endDateString) throws ParseException {
        Calendar endDate;
        if ((endDateString == null) || (endDateString.length() == 0)) {
            endDate = Calendar.getInstance();
        } else {
            endDate = DateTimeConverterUtil.inputStringToCalendar(endDateString);
        }
        endDate = DateTimeConverterUtil.setCalendarHourMinSec(endDate,
                LAST_HOUR_OF_DAY, LAST_MINUTE_OF_HOUR, LAST_SECOND_OF_MINUTE);
        return endDate;
    }

    /**
     * Convert the startDate String to Calendar.
     * 
     * @param startDateString
     *            The startDate parameter.
     * @return The formated, valid calendar.
     * @throws ParseException
     *             Id cannot parse the String to Calendar.
     */
    private Calendar convertStartDate(final String startDateString) throws ParseException {
        Calendar startDate = DateTimeConverterUtil.inputStringToCalendar(startDateString);
        startDate = DateTimeConverterUtil.setCalendarHourMinSec(startDate, 0, 0, 0);
        return startDate;
    }

    /**
     * Create a list of entity expression. The expressions define the worklog query condition for issue parameter.
     * Filtering based project permission and the query projectString parameter.
     * 
     * @param projectString
     *            The query projectString parameter.
     * @param user
     *            The logged user.
     * 
     * @return The list of the issues conditions.
     * @throws GenericEntityException
     *             If the GenericDelegator throw a GenericEntityException.
     */
    private List<EntityExpr> createIssuesConditions(final String projectString, final User user)
            throws GenericEntityException {

        List<Project> projects = (List<Project>) componentManagerInstance.getPermissionManager().getProjectObjects(
                Permissions.BROWSE,
                user);

        // Issues query
        List<EntityExpr> issuesProjectExpr = new ArrayList<EntityExpr>();
        EntityExpr projectExpr;
        for (Project project : projects) {
            if ((projectString != null) && (projectString.length() != 0)) {
                if (projectString.equals(project.getKey())) {
                    projectExpr = new EntityExpr("project", EntityOperator.EQUALS, project.getId());
                    issuesProjectExpr.add(projectExpr);
                }
            } else {
                projectExpr = new EntityExpr("project", EntityOperator.EQUALS, project.getId());
                issuesProjectExpr.add(projectExpr);
            }
        }
        List<EntityExpr> issuesConditions = new ArrayList<EntityExpr>();
        if (!issuesProjectExpr.isEmpty()) {
            List<GenericValue> issueGvList = CoreFactory.getGenericDelegator().findByOr("Issue", issuesProjectExpr);
            for (GenericValue issue : issueGvList) {
                issuesConditions.add(new EntityExpr("issue", EntityOperator.EQUALS, issue.getLong("id")));
            }
        }
        return issuesConditions;
    }

    /**
     * Create a list of entity expressions. The expressions define the worklog query condition for author parameter.
     * Collect the given group users or the given user.
     * 
     * @param user
     *            The query user parameter.
     * @param group
     *            The query gruopString parameter.
     * 
     * @return The list of the users conditions.
     */
    private List<EntityExpr> createUsersConditions(final String userName, final String group) {
        List<EntityExpr> usersConditions = new ArrayList<EntityExpr>();
        if ((group != null) && (group.length() != 0)) {
            Set<User> groupUsers = componentManagerInstance.getUserUtil().getAllUsersInGroupNames(
                    Arrays.asList(new String[] { group }));
            Set<String> assigneeIds = new TreeSet<String>();
            for (User groupUser : groupUsers) {
                assigneeIds.add(groupUser.getName());
                String userKey = UserCompatibilityHelper.getKeyForUser(groupUser);
                usersConditions.add(new EntityExpr("author", EntityOperator.EQUALS, userKey));
            }
        } else if ((userName != null) && (userName.length() != 0)) {
            User user = ComponentManager.getInstance().getUserUtil().getUserObject(userName);
            String userKey = UserCompatibilityHelper.getKeyForUser(user);
            usersConditions.add(new EntityExpr("author", EntityOperator.EQUALS, userKey));
        }
        return usersConditions;
    }

    /**
     * Convert the genericvalue worklog to a JSonObject.
     * 
     * @param worklog
     *            The worklog.
     * @return The worklog JSonObject.
     * 
     * @throws JSONException
     *             If can't put value to the JSonObject.
     * @throws ParseException
     *             If ParserException when parse the startDate.
     */
    private JSONObject createWorklogJSONObject(final GenericValue worklog) throws JSONException, ParseException {
        JSONObject jsonWorklog = new JSONObject();
        jsonWorklog.put("id", worklog.getLong("id"));
        String startDate = worklog.getString("startdate");
        jsonWorklog.put("startDate", DateTimeConverterUtil.stringDateToISO8601FormateString(startDate));
        IssueManager issueManager = ComponentManager.getInstance().getIssueManager();
        String issueKey = issueManager.getIssueObject(worklog.getLong("issue")).getKey();
        jsonWorklog.put("issueKey", issueKey);
        
        //TODO JIRA version 6.2.2 must be configured in POM to get the .getUserByKey method of DefaultUserManager 
        //implementation of the UserManager interface.
        //It should be sth like this: String userName = defaultUserManager.getUserByKey(worklog.getString("author"))
        //.getName
        String userName = worklog.getString("author");
        jsonWorklog.put("userId", userName);
        long timeSpentInSec = worklog.getLong("timeworked").longValue();
        jsonWorklog.put("duration", timeSpentInSec);
        return jsonWorklog;
    }

    /**
     * The updatedWorklogs restful api method.
     * 
     * @param startDate
     *            The query startDate parameter.
     * @param endDate
     *            The query endDate parameter, optional. Default value is the current time.
     * @param user
     *            The query user parameter, optional. This or the group parameter is required.
     * @param group
     *            The query group parameter, optional. This or the user parameter is required.
     * @param project
     *            The query project parameter, optional. Default is all project.
     * @return {@link Response} what contains the result of the query. If the method parameters was wrong then a message
     *         what contains the description of the bad request. In case of any exception return {@link Response} with
     *         INTERNAL_SERVER_ERROR status what contains the original exception message.
     */
    @GET
    @Produces("*/*")
    @Path("/updatedWorklogs")
    public Response findUpdatedWorklogs(
            @QueryParam("startDate") final String startDate,
            @QueryParam("endDate") final String endDate,
            @QueryParam("user") final String user,
            @QueryParam("group") final String group,
            @QueryParam("project") final String project) {

        Response checkRequiredFindWorklogsParamResponse = checkRequiredFindWorklogsParameter(startDate, user, group);
        if (checkRequiredFindWorklogsParamResponse != null) {
            return checkRequiredFindWorklogsParamResponse;
        }
        Calendar startDateCalendar;
        try {
            startDateCalendar = convertStartDate(startDate);
        } catch (ParseException e) {
            LOGGER.debug("Failed to convert start date", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Cannot parse the 'startDate' parameter: " + startDate).build();
        }
        Calendar endDateCalendar;
        try {
            endDateCalendar = convertEndDate(endDate);
        } catch (ParseException e) {
            LOGGER.debug("Failed to convert end date", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Cannot parse the 'endDate' parameter: " + endDate).build();
        }
        try {
            return Response.ok(worklogQuery(startDateCalendar, endDateCalendar, user, group, project, true)).build();
        } catch (Exception e) {
            LOGGER.error("Failed to query the worklogs", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(e.getMessage()).build();
        }
    }

    /**
     * The worklogs restful api method.
     * 
     * @param startDate
     *            The query startDate parameter.
     * @param endDate
     *            The query endDate parameter, optional. Default value is the current time.
     * @param user
     *            The query user parameter, optional. This or the group parameter is required.
     * @param group
     *            The query group parameter, optional. This or the user parameter is required.
     * @param project
     *            The query project parameter, optional. Default is all project.
     * @return {@link Response} what contains the result of the query. If the method parameters was wrong then a message
     *         what contains the description of the bad request. In case of any exception return {@link Response} with
     *         INTERNAL_SERVER_ERROR status what contains the original exception message.
     */
    @GET
    @Produces("*/*")
    @Path("/worklogs")
    public Response findWorklogs(
            @QueryParam("startDate") final String startDate,
            @QueryParam("endDate") final String endDate,
            @QueryParam("user") final String user,
            @QueryParam("group") final String group,
            @QueryParam("project") final String project) {

        Response checkRequiredFindWorklogsParamResponse = checkRequiredFindWorklogsParameter(startDate, user, group);
        if (checkRequiredFindWorklogsParamResponse != null) {
            return checkRequiredFindWorklogsParamResponse;
        }
        Calendar startDateCalendar;
        try {
            startDateCalendar = convertStartDate(startDate);
        } catch (ParseException e) {
            LOGGER.debug("Failed to convert start date", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Cannot parse the 'startDate' parameter: " + startDate).build();
        }
        Calendar endDateCalendar;
        try {
            endDateCalendar = convertEndDate(endDate);
        } catch (ParseException e) {
            LOGGER.debug("Failed to convert end date", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Cannot parse the 'endDate' parameter: " + endDate).build();
        }
        try {
            return Response.ok(worklogQuery(startDateCalendar, endDateCalendar, user, group, project, false)).build();
        } catch (Exception e) {
            LOGGER.error("Failed to query the worklogs", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(e.getMessage()).build();
        }
    }

    /**
     * Check the given String is empty.
     * 
     * @param theString
     *            The String variable.
     * @return If the String is null or the String length equals whit 0 then true, else false.
     */
    private boolean isStringEmpty(final String theString) {
        if ((theString == null) || (theString.length() == 0)) {
            return true;
        }
        return false;
    }

    /**
     * The method build a worklog query.
     * 
     * @param startDate
     *            The startDate calendar parameter.
     * @param endDate
     *            The endDate calendar parameter.
     * @param userString
     *            The user String parameter.
     * @param groupString
     *            The group String parameter.
     * @param projectString
     *            The project String parameter.
     * @param updated
     *            True if the method give back the worklogs which were created or updated in the given period, else
     *            false. The false give back the worklogs of the period.
     * @return JSONString what contains a list of queried worklogs.
     * @throws ParseException
     *             If can't parse the dates.
     * @throws GenericEntityException
     *             If the GenericDelegator throw a GenericEntityException.
     * @throws JSONException
     *             If the createWorklogJSONObject method throw a JSONException.
     */
    private String worklogQuery(final Calendar startDate, final Calendar endDate, final String userString,
            final String groupString, final String projectString, final boolean updated)
            throws ParseException, GenericEntityException, JSONException {

        List<JSONObject> worklogs = new ArrayList<JSONObject>();

        componentManagerInstance = ComponentManager.getInstance();
        JiraAuthenticationContext authenticationContext = componentManagerInstance.getJiraAuthenticationContext();
        User user = authenticationContext.getLoggedInUser();

        // Date expr
        EntityExpr startExpr;
        EntityExpr endExpr;
        if (updated) {
            startExpr = new EntityExpr("updated", EntityOperator.GREATER_THAN_EQUAL_TO,
                    new Timestamp(startDate.getTimeInMillis()));
            endExpr = new EntityExpr("updated", EntityOperator.LESS_THAN,
                    new Timestamp(endDate.getTimeInMillis()));
        } else {
            startExpr = new EntityExpr("startdate", EntityOperator.GREATER_THAN_EQUAL_TO,
                    new Timestamp(startDate.getTimeInMillis()));
            endExpr = new EntityExpr("startdate", EntityOperator.LESS_THAN,
                    new Timestamp(endDate.getTimeInMillis()));
        }
        // set the users condition
        List<EntityExpr> usersConditions = createUsersConditions(userString, groupString);
        EntityCondition userCondition = new EntityConditionList(usersConditions, EntityOperator.OR);
        // set the issue condition
        List<EntityExpr> issuesConditions = createIssuesConditions(projectString, user);
        EntityCondition issueCond = new EntityConditionList(issuesConditions, EntityOperator.OR);
        // put together the issue and user condition
        if (!usersConditions.isEmpty() && !issuesConditions.isEmpty()) {
            EntityExpr userAndIssueExpr = new EntityExpr(userCondition, EntityOperator.AND, issueCond);

            List<EntityExpr> exprList = new ArrayList<EntityExpr>();
            exprList.add(startExpr);
            exprList.add(endExpr);
            exprList.add(userAndIssueExpr);

            List<GenericValue> worklogGVList = CoreFactory.getGenericDelegator().findByAnd("Worklog", exprList);

            for (GenericValue worklogGv : worklogGVList) {
                worklogs.add(createWorklogJSONObject(worklogGv));
            }
        }
        JSONArray jsonArrayResult = new JSONArray();
        jsonArrayResult.put(worklogs);
        return jsonArrayResult.toString();
    }
}
