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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang.StringEscapeUtils;
import org.ofbiz.core.entity.EntityCondition;
import org.ofbiz.core.entity.EntityExpr;
import org.ofbiz.core.entity.EntityOperator;
import org.ofbiz.core.entity.GenericEntityException;
import org.ofbiz.core.entity.GenericValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.core.ofbiz.CoreFactory;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.ComponentManager;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.bc.issue.search.SearchService.ParseResult;
import com.atlassian.jira.bc.project.component.ProjectComponent;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.exception.DataAccessException;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueFieldConstants;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.comments.Comment;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.label.Label;
import com.atlassian.jira.issue.priority.Priority;
import com.atlassian.jira.issue.resolution.Resolution;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.jql.parser.JqlParseException;
import com.atlassian.jira.ofbiz.DefaultOfBizConnectionFactory;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.rest.api.util.StringList;
import com.atlassian.jira.rest.v2.issue.IncludedFields;
import com.atlassian.jira.rest.v2.issue.IssueBean;
import com.atlassian.jira.rest.v2.issue.IssueBeanBuilder;
import com.atlassian.jira.rest.v2.issue.builder.BeanBuilderFactory;
import com.atlassian.jira.rest.v2.search.SearchResultsBean;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.Permissions;
import com.atlassian.jira.usercompatibility.UserCompatibilityHelper;
import com.atlassian.jira.util.json.JSONArray;
import com.atlassian.jira.util.json.JSONException;
import com.atlassian.jira.util.json.JSONObject;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.plugins.rest.common.expand.EntityCrawler;
import com.atlassian.plugins.rest.common.expand.SelfExpandingExpander;
import com.atlassian.plugins.rest.common.expand.parameter.DefaultExpandParameter;
import com.atlassian.plugins.rest.common.expand.parameter.ExpandParameter;
import com.atlassian.plugins.rest.common.expand.resolver.EntityExpanderResolver;
import com.atlassian.util.concurrent.Nullable;
import com.google.common.base.Function;

/**
 * The WorklogQueryResource class. The class contains the findWorklogs method. The class grant the JIRA worklog query.
 *
 * @param <V>
 */
@Path("/find")
public class WorklogQueryResource<V> {

    private class IssueToIssueBean implements Function<Issue, IssueBean>
    {
        private final IncludedFields fields;
        private final ExpandParameter expand;
        private final String expandAsString;
        private final EntityExpanderResolver expandResolver = new SelfExpandingExpander.Resolver();
        private final EntityCrawler entityCrawler = new EntityCrawler();
        private BeanBuilderFactory beanBuilderFactory;

        public IssueToIssueBean(final IncludedFields fields, final StringList expand)
        {
            this.fields = fields;
            this.expand = new DefaultExpandParameter(expand != null ? expand.asList()
                    : Collections.<String> emptyList());
            this.expandAsString = expand != null ? expand.toQueryParam() : null;
        }

        @Override
        public IssueBean apply(@Nullable final Issue issue)
        {
            IssueBean bean = beanBuilderFactory.newIssueBeanBuilder(issue, fields).expand(expandAsString).build();

            // explicity crawl all over this motha, since atlassian-rest didn't take care of business.
            entityCrawler.crawl(bean, expand, expandResolver);
            return bean;
        }
    }

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
    private final ComponentManager componentManagerInstance = ComponentManager.getInstance();

    /**
     * Check the required (or optional) parameters. If any parameter missing or conflict return with the right Response
     * what describes the problem. If everything is right then return with null.
     *
     * @param startDate
     *            The startDate parameter.
     * @param endDate
     *            The endDate parameter.
     * @param user
     *            The user parameter.
     * @param group
     *            The group parameter.
     *
     * @return If a bad parameter was found then return with Response else null.
     */
    private Response checkRequiredFindWorklogsByIssuesParameter(final String startDate, final String endDate,
            final String user, final String group) {
        if (isStringEmpty(startDate)) {
            return Response.status(Response.Status.BAD_REQUEST).
                    entity("The 'startDate' parameter is missing!").build();
        }
        if (isStringEmpty(endDate)) {
            return Response.status(Response.Status.BAD_REQUEST).
                    entity("The 'endDate' parameter is missing!").build();
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
     * Check the required (or optional) parameters. If any parameter missing or conflict return with the right Response
     * what describes the problem. If everything is right then return with null.
     *
     * @param startDate
     *            The findWorklogs startDate parameter.
     * @param user
     *            The findWorklogs user parameter.
     * @param group
     *            The findWorklogs group parameter.
     * @return If find bad parameter then return with Response else null.
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
     * Creates a list of project Id's. Filtering based on project permission and the query projectString parameter.
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
    private List<Long> createProjects(final String projectString, final User user) {

        List<Project> projects = (List<Project>) componentManagerInstance.getPermissionManager().getProjectObjects(
                Permissions.BROWSE,
                user);

        List<Long> projectList = new ArrayList<Long>();
        for (Project project : projects) {
            if ((projectString != null) && (projectString.length() != 0)) {
                if (projectString.equals(project.getKey())) {
                    projectList.add(project.getId());
                }
            } else {
                projectList.add(project.getId());
            }
        }
        return projectList;
    }

    /**
     * Creates a list of user's. If the group variable is defined, then collect all of the user's keys in that group. If
     * userName is defined then add the users key to the list.
     *
     * @param userName
     *            the user name of the user
     * @param group
     *            the name of the group
     * @return
     */
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
            User user = componentManagerInstance.getUserUtil().getUserObject(userName);
            if (user != null) {
                String userKey = UserCompatibilityHelper.getKeyForUser(user);
                users.add(userKey);
            }
        }
        return users;
    }

    private JSONObject createWorklogJSONObject(final GenericValue rs) throws JSONException, SQLException,
    ParseException {

        JSONObject jsonWorklog = new JSONObject();
        jsonWorklog.put("id", rs.getLong("id"));

        Timestamp sDate = rs.getTimestamp("startdate");
        jsonWorklog.put("startDate", DateTimeConverterUtil.stringDateToISO8601FormatString(sDate.toString()));

        IssueManager issueManager = ComponentManager.getInstance().getIssueManager();
        String issueKey = issueManager.getIssueObject(rs.getLong("issue")).getKey();
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

    @GET
    @Path("/worklogsByIssues")
    @Produces({ MediaType.APPLICATION_JSON })
    public SearchResultsBean findWorklogsByIssues(
            @QueryParam("startDate") final String startDate,
            @QueryParam("endDate") final String endDate,
            @QueryParam("user") final String user,
            @QueryParam("group") final String group,
            @QueryParam("jql") String jql,
            @QueryParam("fields") final List<StringList> fields) {

        Response checkRequiredParamResponse = checkRequiredFindWorklogsByIssuesParameter(startDate,
                endDate, user, group);
        if (checkRequiredParamResponse != null) {
            // return checkRequiredParamResponse;
        }

        Calendar startDateCalendar = null;
        try {
            startDateCalendar = convertStartDate(startDate);
        } catch (ParseException e) {
            // return Response.status(Response.Status.BAD_REQUEST)
            // .entity("Cannot parse the 'startDate' parameter: " + startDate).build();
        }
        Calendar endDateCalendar = null;
        try {
            endDateCalendar = convertEndDate(endDate);
        } catch (ParseException e) {
            // return Response.status(Response.Status.BAD_REQUEST)
            // .entity("Cannot parse the 'endDate' parameter: " + endDate).build();
        }

        List<String> users = createUsers(user, group);
        if (users.isEmpty()) {
            // return Response.status(Response.Status.BAD_REQUEST)
            // .entity("Error running search: There is no group or user matching the given parameters.").build();
        }

        // get issues based on the jql filter
        if (jql == null) {
            jql = "";
        }
        List<Issue> issues = null;
        try {
            issues = getIssuesByJQL(jql);
        } catch (SearchException e) {
            // return Response.status(Response.Status.BAD_REQUEST).entity("Error running search: " + e).build();
        } catch (JqlParseException e) {
            // return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }

        Map<Long, Long> result = new HashMap<Long, Long>();
        for (Issue issue : issues) {
            result.put(issue.getId(), 0L);
        }

        sumWorklogsToResultNOSQL(startDateCalendar, endDateCalendar, users, result);

        // order the selected fields to JSON
        List<String> fieldList = new ArrayList<String>();
        if (fields != null) {
            // fieldList = new LinkedList<String>(Arrays.asList(fields.split("\\s*,\\s*")));
        }
        JSONObject returnJSON = new JSONObject();
        try {
            List<JSONObject> worklogs = resultToJSONOWithFields(result, fieldList);
            returnJSON.put("total", worklogs.size());
            returnJSON.put("issues", worklogs);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        List<IssueBean> issuesRes = new ArrayList<IssueBean>();
        IssueManager issueManager = ComponentAccessor.getIssueManager();
        BeanBuilderFactory beanBuilderFactory = ComponentAccessor
                .getOSGiComponentInstanceOfType(BeanBuilderFactory.class);
        IncludedFields includeFields = IncludedFields.includeNavigableByDefault(fields);

        EntityExpanderResolver expandResolver = new SelfExpandingExpander.Resolver();
        EntityCrawler entityCrawler = new EntityCrawler();

        StringList expand = null;
        ExpandParameter expandPar = new DefaultExpandParameter(expand != null ? expand.asList()
                : Collections.<String> emptyList());
        String expandAsString = expand != null ? expand.toQueryParam() : null;

        for (Entry<Long, Long> entry : result.entrySet()) {
            Issue issueObject = issueManager.getIssueObject(entry.getKey());

            IssueBeanBuilder issueBeanBuilder = beanBuilderFactory.newIssueBeanBuilder(issueObject, includeFields);
            IssueBean bean = issueBeanBuilder.expand(expandAsString).build();

            entityCrawler.crawl(bean, expandPar, expandResolver);
            issuesRes.add(bean);
        }

        return new SearchResultsBean(0, 10, issuesRes.size(), issuesRes);

        // return Response.ok(returnJSON.toString()).build();
    }

    @GET
    @Produces("*/*")
    @Path("/worklogsss")
    public Response findWorklogsss(
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
            return Response.ok(worklogQueryyy(startDateCalendar, endDateCalendar, user, group, project, false)).build();
        } catch (Exception e) {
            LOGGER.error("Failed to query the worklogs", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(e.getMessage()).build();
        }
    }

    /**
     * Collects the given fields of the given Issue object and puts them in the JSONObject.
     *
     * @param jsonWorklog
     *            The JSONObject where the fields to be placed.
     * @param fieldsToShow
     *            The list of fields which will be collected.
     * @param issueObject
     *            The Issue object, whose fields will be placed in the JSONObject.
     * @throws JSONException
     */
    private void getIssueFieldsToJSON(final JSONObject jsonWorklog, final List<String> fieldsToShow,
            final Issue issueObject) throws JSONException {
        final String ALL_FIELDS = "*all";
        boolean ContALLFIELDS = false;

        if (fieldsToShow.contains(ALL_FIELDS)) {
            ContALLFIELDS = true;
        }
        if ((fieldsToShow.contains(IssueFieldConstants.ASSIGNEE) || ContALLFIELDS)
                && !fieldsToShow.contains('-' + IssueFieldConstants.ASSIGNEE)) {
            User assigneeUser = issueObject.getAssigneeUser();
            if (assigneeUser != null) {
                jsonWorklog.put(IssueFieldConstants.ASSIGNEE, assigneeUser.getName());
            } else {
                jsonWorklog.put(IssueFieldConstants.ASSIGNEE, "");
            }
        }
        if ((fieldsToShow.contains(IssueFieldConstants.REPORTER) || ContALLFIELDS)
                && !fieldsToShow.contains('-' + IssueFieldConstants.REPORTER)) {
            User reporterUser = issueObject.getReporterUser();
            if (reporterUser != null) {
                jsonWorklog.put(IssueFieldConstants.REPORTER, reporterUser.getName());
            } else {
                jsonWorklog.put(IssueFieldConstants.REPORTER, "");
            }
        }
        if ((fieldsToShow.contains(IssueFieldConstants.CREATED) || ContALLFIELDS)
                && !fieldsToShow.contains('-' + IssueFieldConstants.CREATED)) {
            Timestamp created = issueObject.getCreated();
            if (created != null) {
                jsonWorklog.put(IssueFieldConstants.CREATED, created.toString());
            } else {
                jsonWorklog.put(IssueFieldConstants.CREATED, "");
            }
        }
        if ((fieldsToShow.contains(IssueFieldConstants.DUE_DATE) || ContALLFIELDS)
                && !fieldsToShow.contains('-' + IssueFieldConstants.DUE_DATE)) {
            Timestamp duedate = issueObject.getDueDate();
            if (duedate != null) {
                jsonWorklog.put(IssueFieldConstants.DUE_DATE, duedate.toString());
            } else {
                jsonWorklog.put(IssueFieldConstants.DUE_DATE, "");
            }
        }
        if ((fieldsToShow.contains(IssueFieldConstants.RESOLUTION_DATE) || ContALLFIELDS)
                && !fieldsToShow.contains('-' + IssueFieldConstants.RESOLUTION_DATE)) {
            Timestamp resDate = issueObject.getResolutionDate();
            if (resDate != null) {
                jsonWorklog.put(IssueFieldConstants.RESOLUTION_DATE, resDate.toString());
            } else {
                jsonWorklog.put(IssueFieldConstants.RESOLUTION_DATE, "");
            }
        }
        if ((fieldsToShow.contains(IssueFieldConstants.UPDATED) || ContALLFIELDS)
                && !fieldsToShow.contains('-' + IssueFieldConstants.UPDATED)) {
            Timestamp updated = issueObject.getUpdated();
            if (updated != null) {
                jsonWorklog.put(IssueFieldConstants.UPDATED, updated.toString());
            } else {
                jsonWorklog.put(IssueFieldConstants.UPDATED, "");
            }
        }
        if ((fieldsToShow.contains(IssueFieldConstants.SUMMARY) || ContALLFIELDS)
                && !fieldsToShow.contains('-' + IssueFieldConstants.SUMMARY)) {
            String summary = issueObject.getSummary();
            if (summary != null) {
                jsonWorklog.put(IssueFieldConstants.SUMMARY, summary);
            } else {
                jsonWorklog.put(IssueFieldConstants.SUMMARY, "");
            }
        }
        if ((fieldsToShow.contains(IssueFieldConstants.DESCRIPTION) || ContALLFIELDS)
                && !fieldsToShow.contains('-' + IssueFieldConstants.DESCRIPTION)) {
            String desc = issueObject.getDescription();
            if (desc != null) {
                jsonWorklog.put(IssueFieldConstants.DESCRIPTION,
                        StringEscapeUtils.escapeHtml(desc.replaceAll("\"", "\\\"")));
            } else {
                jsonWorklog.put(IssueFieldConstants.DESCRIPTION, "");
            }
        }
        if ((fieldsToShow.contains(IssueFieldConstants.RESOLUTION) || ContALLFIELDS)
                && !fieldsToShow.contains('-' + IssueFieldConstants.RESOLUTION)) {
            Resolution res = issueObject.getResolutionObject();
            if (res != null) {
                jsonWorklog.put(IssueFieldConstants.RESOLUTION, res.getName());
            } else {
                jsonWorklog.put(IssueFieldConstants.RESOLUTION, "");
            }
        }
        if ((fieldsToShow.contains(IssueFieldConstants.TIME_ESTIMATE) || ContALLFIELDS)
                && !fieldsToShow.contains('-' + IssueFieldConstants.TIME_ESTIMATE)) {
            Long estimate = issueObject.getEstimate();
            if (estimate != null) {
                jsonWorklog.put(IssueFieldConstants.TIME_ESTIMATE, estimate);
            } else {
                jsonWorklog.put(IssueFieldConstants.TIME_ESTIMATE, "");
            }
        }
        if ((fieldsToShow.contains(IssueFieldConstants.TIME_ORIGINAL_ESTIMATE) || ContALLFIELDS)
                && !fieldsToShow.contains('-' + IssueFieldConstants.TIME_ORIGINAL_ESTIMATE)) {
            Long orig_est = issueObject.getOriginalEstimate();
            if (orig_est != null) {
                jsonWorklog.put(IssueFieldConstants.TIME_ORIGINAL_ESTIMATE, orig_est);
            } else {
                jsonWorklog.put(IssueFieldConstants.TIME_ORIGINAL_ESTIMATE, "");
            }
        }
        if ((fieldsToShow.contains(IssueFieldConstants.TIME_SPENT) || ContALLFIELDS)
                && !fieldsToShow.contains('-' + IssueFieldConstants.TIME_SPENT)) {
            Long timespent = issueObject.getTimeSpent();
            if (timespent != null) {
                jsonWorklog.put(IssueFieldConstants.TIME_SPENT, timespent);
            } else {
                jsonWorklog.put(IssueFieldConstants.TIME_SPENT, "");
            }
        }
        if ((fieldsToShow.contains(IssueFieldConstants.VOTES) || ContALLFIELDS)
                && !fieldsToShow.contains('-' + IssueFieldConstants.VOTES)) {
            Long votes = issueObject.getVotes();
            if (votes != null) {
                jsonWorklog.put(IssueFieldConstants.VOTES, votes);
            } else {
                jsonWorklog.put(IssueFieldConstants.VOTES, "");
            }
        }
        if ((fieldsToShow.contains(IssueFieldConstants.WATCHES)
                || ContALLFIELDS) && !fieldsToShow.contains('-' + IssueFieldConstants.WATCHES)) {
            Long watches = issueObject.getWatches();
            if (watches != null) {
                jsonWorklog.put(IssueFieldConstants.WATCHES, watches);
            } else {
                jsonWorklog.put(IssueFieldConstants.WATCHES, "");
            }
        }
        if ((fieldsToShow.contains(IssueFieldConstants.SECURITY)
                || ContALLFIELDS) && !fieldsToShow.contains('-' + IssueFieldConstants.SECURITY)) {
            Long sec = issueObject.getSecurityLevelId();
            if (sec != null) {
                jsonWorklog.put(IssueFieldConstants.SECURITY, sec);
            } else {
                jsonWorklog.put(IssueFieldConstants.SECURITY, "");
            }
        }
        if ((fieldsToShow.contains(IssueFieldConstants.ENVIRONMENT)
                || ContALLFIELDS) && !fieldsToShow.contains('-' + IssueFieldConstants.ENVIRONMENT)) {
            String env = issueObject.getEnvironment();
            if (env != null) {
                jsonWorklog.put(IssueFieldConstants.ENVIRONMENT, env);
            } else {
                jsonWorklog.put(IssueFieldConstants.ENVIRONMENT, "");
            }
        }
        if ((fieldsToShow.contains(IssueFieldConstants.PRIORITY)
                || ContALLFIELDS) && !fieldsToShow.contains('-' + IssueFieldConstants.PRIORITY)) {
            Priority priority = issueObject.getPriorityObject();
            if (priority != null) {
                jsonWorklog.put(IssueFieldConstants.PRIORITY, priority.getName());
            } else {
                jsonWorklog.put(IssueFieldConstants.PRIORITY, "");
            }
        }
        if ((fieldsToShow.contains(IssueFieldConstants.STATUS)
                || ContALLFIELDS) && !fieldsToShow.contains('-' + IssueFieldConstants.STATUS)) {
            Status status = issueObject.getStatusObject();
            if (status != null) {
                jsonWorklog.put(IssueFieldConstants.STATUS, status.getName());
            } else {
                jsonWorklog.put(IssueFieldConstants.STATUS, "");
            }
        }
        if ((fieldsToShow.contains(IssueFieldConstants.PROJECT)
                || ContALLFIELDS) && !fieldsToShow.contains('-' + IssueFieldConstants.PROJECT)) {
            Project project = issueObject.getProjectObject();
            if (project != null) {
                jsonWorklog.put(IssueFieldConstants.PROJECT, project.getKey());
            } else {
                jsonWorklog.put(IssueFieldConstants.PROJECT, "");
            }
        }
        if ((fieldsToShow.contains(IssueFieldConstants.ISSUE_TYPE) || ContALLFIELDS)
                && !fieldsToShow.contains('-' + IssueFieldConstants.ISSUE_TYPE)) {
            IssueType issueType = issueObject.getIssueTypeObject();
            if (issueType != null) {
                jsonWorklog.put(IssueFieldConstants.ISSUE_TYPE, issueType.getName());
            } else {
                jsonWorklog.put(IssueFieldConstants.ISSUE_TYPE, "");
            }
        }
        if ((fieldsToShow.contains(IssueFieldConstants.COMMENT) || ContALLFIELDS)
                && !fieldsToShow.contains('-' + IssueFieldConstants.COMMENT)) {
            List<Comment> comments = componentManagerInstance.getCommentManager().getComments(issueObject);
            String JSONComment = "";
            for (Comment comment : comments) {
                JSONComment += comment.getAuthor() + ": "
                        + StringEscapeUtils.escapeHtml(comment.getBody().replaceAll("\"", "\\\"")) + ",";
            }
            if (!JSONComment.equals("")) {
                JSONComment = JSONComment.substring(0, JSONComment.length() - 1);
            }
            jsonWorklog.put("issue." + IssueFieldConstants.COMMENT, JSONComment);
        }
        if ((fieldsToShow.contains(IssueFieldConstants.COMPONENTS) || ContALLFIELDS)
                && !fieldsToShow.contains('-' + IssueFieldConstants.COMPONENTS)) {
            Collection<ProjectComponent> componentObjects = issueObject.getComponentObjects();
            String JSONComponents = "";
            for (ProjectComponent component : componentObjects) {
                JSONComponents += component.getName() + ",";
            }
            if (!JSONComponents.equals("")) {
                JSONComponents = JSONComponents.substring(0, JSONComponents.length() - 1);
            }
            jsonWorklog.put(IssueFieldConstants.COMPONENTS, JSONComponents);
        }
        if ((fieldsToShow.contains(IssueFieldConstants.LABELS) || ContALLFIELDS)
                && !fieldsToShow.contains('-' + IssueFieldConstants.LABELS)) {
            Set<Label> labels = issueObject.getLabels();
            String JSONLabels = "";
            for (Label label : labels) {
                JSONLabels += label.getLabel() + ",";
            }
            if (!JSONLabels.equals("")) {
                JSONLabels = JSONLabels.substring(0, JSONLabels.length() - 1);
            }
            jsonWorklog.put(IssueFieldConstants.LABELS, JSONLabels);
        }
        if ((fieldsToShow.contains(IssueFieldConstants.FIX_FOR_VERSIONS) || ContALLFIELDS)
                && !fieldsToShow.contains('-' + IssueFieldConstants.FIX_FOR_VERSIONS)) {
            Collection<Version> versions = issueObject.getFixVersions();
            String JSONVersions = "";
            for (Version version : versions) {
                JSONVersions += version.getName() + ",";
            }
            if (!JSONVersions.equals("")) {
                JSONVersions = JSONVersions.substring(0, JSONVersions.length() - 1);
            }
            jsonWorklog.put(IssueFieldConstants.FIX_FOR_VERSIONS, JSONVersions);
        }
        if ((fieldsToShow.contains(IssueFieldConstants.SUBTASKS) || ContALLFIELDS)
                && !fieldsToShow.contains('-' + IssueFieldConstants.SUBTASKS)) {
            Collection<Issue> subtasks = issueObject.getSubTaskObjects();
            String JSONSubtasks = "";
            for (Issue subtask : subtasks) {
                JSONSubtasks += subtask.getKey() + ",";
            }
            if (!JSONSubtasks.equals("")) {
                JSONSubtasks = JSONSubtasks.substring(0, JSONSubtasks.length() - 1);
            }
            jsonWorklog.put(IssueFieldConstants.SUBTASKS, JSONSubtasks);
        }
    }

    /**
     * Returns the selected issues based on the given JQL filter.
     *
     * @param jql
     *            JQL filter the search is based on.
     * @return List of the matching JIRA Issues.
     * @throws SearchException
     * @throws JqlParseException
     *             Thrown when the given JQL is not valid.
     */
    private List<Issue> getIssuesByJQL(final String jql) throws SearchException,
            JqlParseException {
        JiraAuthenticationContext authenticationContext = componentManagerInstance.getJiraAuthenticationContext();
        User loggedInUser = authenticationContext.getLoggedInUser();

        List<Issue> issues = null;
        SearchService searchService = componentManagerInstance.getSearchService();
        final ParseResult parseResult = searchService.parseQuery(loggedInUser, jql);
        if (parseResult.isValid()) {
            final SearchResults results = searchService.search(loggedInUser,
                    parseResult.getQuery(), PagerFilter.getUnlimitedFilter());
            issues = results.getIssues();
        }
        else {
            throw new JqlParseException(null, "Error parsing jqlQuery: " + parseResult.getErrors());
        }
        return issues;
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
     * Collects the values of the given fields from the Issues represented in the Map's keys and returns them in a list
     * of JSON Objects.
     *
     * @param result
     *            Map which keys are issue ID's and values are the worked times in seconds.
     * @param fieldList
     *            List of field which will be collected from the Issue
     * @return
     * @throws JSONException
     */
    private List<JSONObject> resultToJSONOWithFields(final Map<Long, Long> result, final List<String> fieldList)
            throws JSONException {
        IssueManager issueManager = ComponentManager.getInstance().getIssueManager();
        List<JSONObject> worklogs = new ArrayList<JSONObject>();

        for (Entry<Long, Long> entry : result.entrySet()) {
            Issue issueObject = issueManager.getIssueObject(entry.getKey());

            JSONObject jsonWorklog = new JSONObject();
            jsonWorklog.put("id", issueObject.getId());
            jsonWorklog.put("key", issueObject.getKey());
            jsonWorklog.put("timeworkedininterval", entry.getValue());
            getIssueFieldsToJSON(jsonWorklog, fieldList, issueObject);
            worklogs.add(jsonWorklog);
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
        return worklogs;
    }

    private void sumWorklogsToResult(final Calendar startDateCalendar, final Calendar endDateCalendar,
            final User loggedInUser,
            final Map<Long, Long> result) throws SQLException {
        String schemaName = new DefaultOfBizConnectionFactory().getDatasourceInfo().getSchemaName();
        String query = "SELECT worklog.id, worklog.startdate, worklog.issueid, worklog.author, worklog.timeworked"
                + " FROM " + schemaName + ".worklog, " + schemaName + ".jiraissue"
                + " WHERE worklog.issueid=jiraissue.id"
                + " AND worklog.startdate>=? AND worklog.startdate<?"
                + " AND worklog.author IN (?)";

        Connection conn = new DefaultOfBizConnectionFactory().getConnection();
        PreparedStatement ps = null;
        ps = conn.prepareStatement(query);
        int preparedIndex = 1;
        ps.setTimestamp(preparedIndex++, new Timestamp(startDateCalendar.getTimeInMillis()));
        ps.setTimestamp(preparedIndex++, new Timestamp(endDateCalendar.getTimeInMillis()));
        String userKey = UserCompatibilityHelper.getKeyForUser(loggedInUser);
        ps.setString(preparedIndex++, userKey);

        ResultSet rs = ps.executeQuery();
        while (rs.next())
        {
            Long worklogIssueId = rs.getLong("issueid");
            if (result.containsKey(worklogIssueId)) {
                Long prevValue = result.get(worklogIssueId);
                result.put(worklogIssueId, prevValue + rs.getLong("timeworked"));
            }
        }
        rs.close();
        ps.close();
        conn.close();
    }

    /**
     * Summarize the worked time by issue.
     *
     * @param startDateCalendar
     *            The starting date of the search
     * @param endDateCalendar
     *            The ending date of the search
     * @param users
     *            The List of users whose worklog's are collected.
     * @param result
     *            The Map which keys are Issue ID's and values will be the worked seconds.
     */
    private void sumWorklogsToResultNOSQL(final Calendar startDateCalendar, final Calendar endDateCalendar,
            final List<String> users,
            final Map<Long, Long> result) {

        EntityExpr startExpr = new EntityExpr("startdate",
                EntityOperator.GREATER_THAN_EQUAL_TO, new Timestamp(
                        startDateCalendar.getTimeInMillis()));
        EntityExpr endExpr = new EntityExpr("startdate",
                EntityOperator.LESS_THAN, new Timestamp(endDateCalendar.getTimeInMillis()));
        EntityExpr userExpr = new EntityExpr("author", EntityOperator.IN,
                users);

        List<EntityCondition> exprList = new ArrayList<EntityCondition>();
        exprList.add(userExpr);
        if (startExpr != null) {
            exprList.add(startExpr);
        }
        if (endExpr != null) {
            exprList.add(endExpr);
        }

        List<GenericValue> worklogGVList;
        try {
            worklogGVList = CoreFactory.getGenericDelegator().findByAnd(
                    "Worklog", exprList);

            for (GenericValue worklogGv : worklogGVList) {
                Long worklogIssueId = worklogGv.getLong("issue");
                if (result.containsKey(worklogIssueId)) {
                    Long prevValue = result.get(worklogIssueId);
                    result.put(worklogIssueId, prevValue + worklogGv.getLong("timeworked"));
                }
            }
        } catch (GenericEntityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        while (result.values().remove(0L)) {
        }

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

        JiraAuthenticationContext authenticationContext = componentManagerInstance.getJiraAuthenticationContext();
        User loggedInUser = authenticationContext.getLoggedInUser();

        List<Long> projects = createProjects(projectString, loggedInUser);
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
                    + " FROM " + schemaName + ".worklog, " + schemaName + ".jiraissue"
                    + " WHERE worklog.issueid=jiraissue.id"
                    + " AND worklog.startdate>=? AND worklog.startdate<?"
                    + " AND worklog.author IN ("
                    + usersPreparedParams.toString() + ")"
                    + " AND jiraissue.project IN ("
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
            for (Long project : projects) {
                ps.setLong(preparedIndex++, project);
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

    private String worklogQueryyy(final Calendar startDate, final Calendar endDate, final String userString,
            final String groupString, final String projectString, final boolean updated) throws DataAccessException,
            SQLException, JSONException, ParseException, SearchException, JqlParseException {

        List<JSONObject> worklogs = new ArrayList<JSONObject>();

        JiraAuthenticationContext authenticationContext = componentManagerInstance.getJiraAuthenticationContext();
        User loggedInUser = authenticationContext.getLoggedInUser();

        String jql = "";
        List<Issue> issues = null;
        SearchService searchService = componentManagerInstance.getSearchService();
        final ParseResult parseResult = searchService.parseQuery(loggedInUser, jql);
        if (parseResult.isValid()) {
            final SearchResults results = searchService.search(loggedInUser,
                    parseResult.getQuery(), PagerFilter.getUnlimitedFilter());
            issues = results.getIssues();
        }
        else {
            throw new JqlParseException(null, "Error parsing jqlQuery: " + parseResult.getErrors());
        }

        List<Long> result = new ArrayList<Long>();
        for (Issue issue : issues) {
            result.add(issue.getId());
        }

        List<String> users = createUsers(userString, groupString);

        if (!users.isEmpty()) {

            EntityExpr startExpr = new EntityExpr("startdate",
                    EntityOperator.GREATER_THAN_EQUAL_TO, new Timestamp(
                            startDate.getTimeInMillis()));
            EntityExpr endExpr = new EntityExpr("startdate",
                    EntityOperator.LESS_THAN, new Timestamp(endDate.getTimeInMillis()));
            EntityExpr userExpr = new EntityExpr("author", EntityOperator.IN,
                    users);

            List<EntityCondition> exprList = new ArrayList<EntityCondition>();
            exprList.add(userExpr);
            if (startExpr != null) {
                exprList.add(startExpr);
            }
            if (endExpr != null) {
                exprList.add(endExpr);
            }

            List<GenericValue> worklogGVList;
            try {
                worklogGVList = CoreFactory.getGenericDelegator().findByAnd(
                        "Worklog", exprList);

                for (GenericValue worklogGv : worklogGVList) {
                    if (result.contains(worklogGv.getLong("issue"))) {
                        worklogs.add(createWorklogJSONObject(worklogGv));
                    }
                }
            } catch (GenericEntityException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        JSONArray jsonArrayResult = new JSONArray();
        jsonArrayResult.put(worklogs);
        return jsonArrayResult.toString();
    }
}
