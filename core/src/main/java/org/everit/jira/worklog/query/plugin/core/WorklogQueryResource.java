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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.ofbiz.core.entity.GenericEntityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.ComponentManager;
import com.atlassian.jira.exception.DataAccessException;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.ofbiz.DefaultOfBizConnectionFactory;
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
     * Create a list projects. Filtering based project permission and the query projectString parameter.
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
    private List<String> createProjects(final String projectString, final User user) {

        List<Project> projects = (List<Project>) componentManagerInstance.getPermissionManager().getProjectObjects(
                Permissions.BROWSE,
                user);

        List<String> projectList = new ArrayList<String>();
        for (Project project : projects) {
            if ((projectString != null) && (projectString.length() != 0)) {
                if (projectString.equals(project.getKey())) {
                    projectList.add(project.getKey());
                }
            } else {
                projectList.add(project.getKey());
            }
        }
        return projectList;
    }

    private List<String> createUsers(final String userName, final String group) {
        List<String> users = new ArrayList<String>();
        if ((group != null) && (group.length() != 0)) {
            Set<User> groupUsers = componentManagerInstance.getUserUtil().getAllUsersInGroupNames(
                    Arrays.asList(new String[] { group }));
            Set<String> assigneeIds = new TreeSet<String>();
            for (User groupUser : groupUsers) {
                assigneeIds.add(groupUser.getName());
                String userKey = UserCompatibilityHelper.getKeyForUser(groupUser);
                users.add(userKey);
            }
        } else if ((userName != null) && (userName.length() != 0)) {
            User user = ComponentManager.getInstance().getUserUtil().getUserObject(userName);
            String userKey = UserCompatibilityHelper.getKeyForUser(user);
            users.add(userKey);
        }
        return users;
    }

    /**
     * Convert a ResultSet object to a JSonObject.
     *
     * @param rs
     *            The ResultSet worklog.
     * @return The worklog JSonObject.
     *
     * @throws JSONException
     *             If can't put value to the JSonObject.
     * @throws ParseException
     *             If ParserException when parse the startDate.
     */
    private JSONObject createWorklogJSONObject(final ResultSet rs) throws JSONException, SQLException, ParseException {
        JSONObject jsonWorklog = new JSONObject();
        jsonWorklog.put("id", rs.getLong("id"));

        Timestamp sDate = rs.getTimestamp("startdate");
        jsonWorklog.put("startDate", DateTimeConverterUtil.stringDateToISO8601FormatString(sDate.toString()));

        IssueManager issueManager = ComponentManager.getInstance().getIssueManager();
        String issueKey = issueManager.getIssueObject(rs.getLong("issueid")).getKey();
        jsonWorklog.put("issueKey", issueKey);

        String userKey = rs.getString("author");
        User user = UserCompatibilityHelper.getUserForKey(userKey);
        String userName = user.getName();
        jsonWorklog.put("userId", userName);

        long timeSpentInSec = rs.getLong("timeworked");
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
     * The method to query worklogs.
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
            final String groupString, final String projectString, final boolean updated) throws DataAccessException,
            SQLException, JSONException, ParseException {

        List<JSONObject> worklogs = new ArrayList<JSONObject>();

        componentManagerInstance = ComponentManager.getInstance();
        JiraAuthenticationContext authenticationContext = componentManagerInstance.getJiraAuthenticationContext();
        User loggedInUser = authenticationContext.getLoggedInUser();

        List<String> projects = createProjects(projectString, loggedInUser);
        List<String> users = createUsers(userString, groupString);
        String schemaName = new DefaultOfBizConnectionFactory().getDatasourceInfo().getSchemaName();

        if (!projects.isEmpty() && !users.isEmpty()) {

            StringBuilder projectsPreparedParams = new StringBuilder();
            for (int i = 0; i < projects.size(); i++) {
                projectsPreparedParams.append("?,");
            }
            if (projectsPreparedParams.length() > 0) {
                projectsPreparedParams.deleteCharAt(projectsPreparedParams.length() - 1);
            }
            StringBuilder usersPreparedParams = new StringBuilder();
            for (int i = 0; i < users.size(); i++) {
                usersPreparedParams.append("?,");
            }
            if (usersPreparedParams.length() > 0) {
                usersPreparedParams.deleteCharAt(usersPreparedParams.length() - 1);
            }

            String query = "SELECT worklog.id, worklog.startdate, worklog.issueid, worklog.author, worklog.timeworked"
                    + " FROM " + schemaName + ".project, " + schemaName + ".worklog, " + schemaName + ".jiraissue"
                    + " WHERE jiraissue.project=project.id AND worklog.issueid=jiraissue.id"
                    + " AND worklog.startdate>=? AND worklog.startdate<?"
                    + " AND worklog.author IN ("
                    + usersPreparedParams.toString() + ")"
                    + " AND project.pkey IN ("
                    + projectsPreparedParams.toString() + ")";

            Connection conn = new DefaultOfBizConnectionFactory().getConnection();
            PreparedStatement ps = null;
            ps = conn.prepareStatement(query);
            int preparedIndex = 1;
            ps.setTimestamp(preparedIndex++, new Timestamp(startDate.getTimeInMillis()));
            ps.setTimestamp(preparedIndex++, new Timestamp(endDate.getTimeInMillis()));
            for (String user : users) {
                ps.setString(preparedIndex++, user);
            }
            for (String project : projects) {
                ps.setString(preparedIndex++, project);
            }

            ResultSet rs = ps.executeQuery();
            while (rs.next())
            {
                worklogs.add(createWorklogJSONObject(rs));
            }
            rs.close();
            ps.close();
            conn.close();
        }

        Collections.sort(worklogs, new Comparator<JSONObject>() {
            @Override
            public int compare(final JSONObject o1, final JSONObject o2) {
                long a = 0, b = 0;
                try {
                    a = o1.getLong("id");
                    b = o2.getLong("id");
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                return a > b ? +1 : a < b ? -1 : 0;
            }
        });
        JSONArray jsonArrayResult = new JSONArray();
        jsonArrayResult.put(worklogs);
        return jsonArrayResult.toString();
    }
}
