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

import java.net.URI;
import java.net.URISyntaxException;
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
import java.util.Set;
import java.util.TreeSet;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.ofbiz.core.entity.GenericEntityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.bc.issue.search.SearchService.ParseResult;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.APKeys;
import com.atlassian.jira.exception.DataAccessException;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.fields.Field;
import com.atlassian.jira.issue.fields.FieldException;
import com.atlassian.jira.issue.fields.NavigableField;
import com.atlassian.jira.issue.fields.OrderableField;
import com.atlassian.jira.issue.fields.ProjectSystemField;
import com.atlassian.jira.issue.fields.layout.field.FieldLayout;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutItem;
import com.atlassian.jira.issue.fields.rest.FieldJsonRepresentation;
import com.atlassian.jira.issue.fields.rest.RestAwareField;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.jql.parser.JqlParseException;
import com.atlassian.jira.ofbiz.DefaultOfBizConnectionFactory;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.rest.api.util.StringList;
import com.atlassian.jira.rest.v2.issue.IncludedFields;
import com.atlassian.jira.rest.v2.issue.IssueBean;
import com.atlassian.jira.rest.v2.issue.RESTException;
import com.atlassian.jira.rest.v2.search.SearchResultsBean;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.collect.CollectionBuilder;
import com.atlassian.jira.util.json.JSONArray;
import com.atlassian.jira.util.json.JSONException;
import com.atlassian.jira.util.json.JSONObject;
import com.atlassian.jira.web.bean.PagerFilter;

/**
 * The WorklogQueryResource class. The class contains the findWorklogs method. The class grant the
 * JIRA worklog query.
 *
 * @param <V>
 */
@Path("/find")
public class WorklogQueryResource<V> {

  private class IssueBeanWithTimespent extends IssueBean {
    @XmlElement
    private Long timespent = 0L;

    public IssueBeanWithTimespent(final Long id, final String key, final URI selfUri,
        final Long timespent) {
      super(id, key, selfUri);
      this.timespent = timespent;
    }

    public Long getTimeSpent() {
      return timespent;
    }
  }

  @XmlRootElement
  @JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
  private class SearchResultsBeanWithTimespent extends SearchResultsBean {
    public List<IssueBeanWithTimespent> issues;

    public SearchResultsBeanWithTimespent(final Integer startAt, final Integer maxResults,
        final Integer total,
        final List<IssueBeanWithTimespent> issues) {
      this.startAt = startAt;
      this.maxResults = maxResults;
      this.total = total;
      this.issues = issues;
    }
  }

  private static final int DEFAULT_MAXRESULT_PARAM = 25;

  private static final int DEFAULT_STARTAT_PARAM = 0;

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
   * The logger used to log.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(WorklogQueryResource.class);

  private void addFields(final Issue issue, final IssueBean bean) {
    // iterate over all the visible layout items from the field layout for this issue and attempt to
    // add them
    // to the result
    final ApplicationUser loggedInUser =
        ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
    final FieldLayout layout = ComponentAccessor.getFieldLayoutManager().getFieldLayout(issue);
    final List<FieldLayoutItem> fieldLayoutItems =
        layout.getVisibleLayoutItems(issue.getProjectObject(),
            CollectionBuilder.list(issue.getIssueType().getId()));
    for (final FieldLayoutItem fieldLayoutItem : fieldLayoutItems) {
      final OrderableField field = fieldLayoutItem.getOrderableField();
      final FieldJsonRepresentation fieldValue = getFieldValue(fieldLayoutItem, issue);
      if ((fieldValue != null) && (fieldValue.getStandardData() != null)) {
        bean.addField(field, fieldValue, false);
      }
    }
    // Then we try to add "NavigableFields" which aren't "OrderableFields" unless they ae special
    // ones.
    // These aren't included in the Field Layout.
    // This is a bit crap because "getAvailableNavigableFields" doesn't take the issue into account.
    // All it means is the field is not hidden in at least one project the user has BROWSE
    // permission on.
    try {
      final Set<NavigableField> fields = ComponentAccessor.getFieldManager()
          .getAvailableNavigableFields(loggedInUser);
      for (NavigableField field : fields) {
        if (!bean.hasField(field.getId())) {
          if (!(field instanceof OrderableField) || (field instanceof ProjectSystemField)) {
            if (field instanceof RestAwareField) {
              addRestAwareField(issue, bean, field, (RestAwareField) field);
            }
          }
        }
      }
    } catch (FieldException e) {
      // ignored...display as much as we can.
    }
  }

  private void addRestAwareField(final Issue issue, final IssueBean bean, final Field field,
      final RestAwareField restAware) {
    FieldJsonRepresentation fieldJsonFromIssue = restAware.getJsonFromIssue(issue, false, null);
    if ((fieldJsonFromIssue != null) && (fieldJsonFromIssue.getStandardData() != null)) {
      bean.addField(field, fieldJsonFromIssue, false);
    }
  }

  /**
   * Check the required (or optional) parameters. If any parameter missing or conflict return with
   * the right Response what describes the problem. If everything is right then return with null.
   *
   * @param startDate
   *          The startDate parameter.
   * @param endDate
   *          The endDate parameter.
   * @param user
   *          The user parameter.
   * @param group
   *          The group parameter.
   *
   * @return If a bad parameter was found then return with Response else null.
   */
  private void checkRequiredFindWorklogsByIssuesParameter(final String startDate,
      final String endDate,
      final String user, final String group) {
    if (isStringEmpty(startDate)) {
      throw new RESTException(Response.Status.BAD_REQUEST, "The 'startDate' parameter is missing!");
    }
    if (isStringEmpty(endDate)) {
      throw new RESTException(Response.Status.BAD_REQUEST, "The 'endDate' parameter is missing!");
    }
    if ((isStringEmpty(user)) && (isStringEmpty(group))) {
      throw new RESTException(Response.Status.BAD_REQUEST,
          "The 'user' or the 'group' parameter is missing!");
    }
    if ((!isStringEmpty(user)) && (!isStringEmpty(group))) {
      throw new RESTException(Response.Status.BAD_REQUEST,
          "The 'user' and the 'group' parameters cannot be present at the same time.");
    }
  }

  /**
   * Check the required (or optional) parameters. If any parameter missing or conflict return with
   * the right Response what describes the problem. If everything is right then return with null.
   *
   * @param startDate
   *          The findWorklogs startDate parameter.
   * @param user
   *          The findWorklogs user parameter.
   * @param group
   *          The findWorklogs group parameter.
   * @return If find bad parameter then return with Response else null.
   */
  private Response checkRequiredFindWorklogsParameter(final String startDate, final String user,
      final String group) {
    if (isStringEmpty(startDate)) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("The 'startDate' parameter is missing!").build();
    }
    if ((isStringEmpty(user)) && (isStringEmpty(group))) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("The 'user' or the 'group' parameter is missing!").build();
    }
    if ((!isStringEmpty(user)) && (!isStringEmpty(group))) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("The 'user' and the 'group' parameters cannot be present at the same time.")
          .build();
    }
    return null;
  }

  /**
   * Convert the endDate String to Calendar.
   *
   * @param endDateString
   *          The endDate parameter.
   * @return The formated, valid calendar.
   * @throws ParseException
   *           If cannot parse the String to Calendar.
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
   *          The startDate parameter.
   * @return The formated, valid calendar.
   * @throws ParseException
   *           Id cannot parse the String to Calendar.
   */
  private Calendar convertStartDate(final String startDateString) throws ParseException {
    Calendar startDate = DateTimeConverterUtil.inputStringToCalendar(startDateString);
    startDate = DateTimeConverterUtil.setCalendarHourMinSec(startDate, 0, 0, 0);
    return startDate;
  }

  /**
   * Creates a list of project Id's. Filtering based on project permission and the query
   * projectString parameter.
   *
   * @param projectString
   *          The query projectString parameter.
   * @param user
   *          The logged user.
   *
   * @return The list of the issues conditions.
   * @throws GenericEntityException
   *           If the GenericDelegator throw a GenericEntityException.
   */
  private List<Long> createProjects(final String projectString, final ApplicationUser user) {

    Collection<Project> projects = ComponentAccessor.getPermissionManager()
        .getProjects(ProjectPermissions.BROWSE_PROJECTS, user);

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
   * Creates a list of user's. If the group variable is defined, then collect all of the user's keys
   * in that group. If userName is defined then add the users key to the list.
   *
   * @param userName
   *          the user name of the user
   * @param group
   *          the name of the group
   * @return
   */
  private List<String> createUsers(final String userName, final String group) {
    List<String> users = new ArrayList<String>();
    if ((group != null) && (group.length() != 0)) {
      Set<ApplicationUser> groupUsers = ComponentAccessor.getUserUtil()
          .getAllUsersInGroupNames(
              Arrays.asList(new String[] { group }));
      Set<String> assigneeIds = new TreeSet<String>();
      for (ApplicationUser groupUser : groupUsers) {
        assigneeIds.add(groupUser.getName());
        users.add(groupUser.getKey());
      }
    } else if ((userName != null) && (userName.length() != 0)) {
      ApplicationUser user = ComponentAccessor.getUserManager().getUserByName(userName);
      if (user != null) {
        users.add(user.getKey());
      }
    }
    return users;

  }

  /**
   * Convert a ResultSet object to a JSonObject.
   *
   * @param rs
   *          The ResultSet worklog.
   * @param fields
   * @return The worklog JSonObject.
   *
   * @throws JSONException
   *           If can't put value to the JSonObject.
   * @throws ParseException
   *           If ParserException when parse the startDate.
   */
  private JSONObject createWorklogJSONObject(final ResultSet rs, final List<StringList> fields)
      throws JSONException,
      SQLException, ParseException {
    JSONObject jsonWorklog = new JSONObject();
    jsonWorklog.put("id", rs.getLong("id"));

    Timestamp sDate = rs.getTimestamp("startdate");
    jsonWorklog.put("startDate",
        DateTimeConverterUtil.stringDateToISO8601FormatString(sDate.toString()));

    IssueManager issueManager = ComponentAccessor.getIssueManager();
    String issueKey = issueManager.getIssueObject(rs.getLong("issueid")).getKey();
    jsonWorklog.put("issueKey", issueKey);

    String userKey = rs.getString("author");
    ApplicationUser user = ComponentAccessor.getUserManager().getUserByKey(userKey);
    String userName = user.getName();
    jsonWorklog.put("userId", userName);

    long timeSpentInSec = rs.getLong("timeworked");
    jsonWorklog.put("duration", timeSpentInSec);

    if (fields != null) {
      if (StringList.joinLists(fields).asList().contains("comment")) {
        String comment = rs.getString("worklogbody");
        jsonWorklog.put("comment", comment);
      }
      if (StringList.joinLists(fields).asList().contains("updated")) {
        Timestamp updated = rs.getTimestamp("updated");
        jsonWorklog.put("updated",
            DateTimeConverterUtil.stringDateToISO8601FormatString(updated.toString()));
      }
    }
    return jsonWorklog;
  }

  /**
   * The updatedWorklogs restful api method.
   *
   * @param startDate
   *          The query startDate parameter.
   * @param endDate
   *          The query endDate parameter, optional. Default value is the current time.
   * @param user
   *          The query user parameter, optional. This or the group parameter is required.
   * @param group
   *          The query group parameter, optional. This or the user parameter is required.
   * @param project
   *          The query project parameter, optional. Default is all project.
   * @return {@link Response} what contains the result of the query. If the method parameters was
   *         wrong then a message what contains the description of the bad request. In case of any
   *         exception return {@link Response} with INTERNAL_SERVER_ERROR status what contains the
   *         original exception message.
   */
  @GET
  @Produces("*/*")
  @Path("/updatedWorklogs")
  public Response findUpdatedWorklogs(
      @QueryParam("startDate") final String startDate,
      @QueryParam("endDate") final String endDate,
      @QueryParam("user") final String user,
      @QueryParam("group") final String group,
      @QueryParam("project") final String project,
      @QueryParam("fields") final List<StringList> fields) {

    Response checkRequiredFindWorklogsParamResponse = checkRequiredFindWorklogsParameter(startDate,
        user, group);
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
      return Response.ok(
          worklogQuery(startDateCalendar, endDateCalendar, user, group, project, fields, true))
          .build();
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
   *          The query startDate parameter.
   * @param endDate
   *          The query endDate parameter, optional. Default value is the current time.
   * @param user
   *          The query user parameter, optional. This or the group parameter is required.
   * @param group
   *          The query group parameter, optional. This or the user parameter is required.
   * @param project
   *          The query project parameter, optional. Default is all project.
   * @return {@link Response} what contains the result of the query. If the method parameters was
   *         wrong then a message what contains the description of the bad request. In case of any
   *         exception return {@link Response} with INTERNAL_SERVER_ERROR status what contains the
   *         original exception message.
   */
  @GET
  @Produces({ MediaType.APPLICATION_JSON })
  @Path("/worklogs")
  public Response findWorklogs(
      @QueryParam("startDate") final String startDate,
      @QueryParam("endDate") final String endDate,
      @QueryParam("user") final String user,
      @QueryParam("group") final String group,
      @QueryParam("project") final String project,
      @QueryParam("fields") final List<StringList> fields) {

    Response checkRequiredFindWorklogsParamResponse = checkRequiredFindWorklogsParameter(startDate,
        user, group);
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
      return worklogQuery(startDateCalendar, endDateCalendar, user, group, project, fields, false);
    } catch (Exception e) {
      LOGGER.error("Failed to query the worklogs", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(e.getMessage()).build();
    }
  }

  @GET
  @Path("/worklogsByIssues")
  @Produces({ MediaType.APPLICATION_JSON })
  public SearchResultsBeanWithTimespent findWorklogsByIssues(
      @QueryParam("startDate") final String startDate,
      @QueryParam("endDate") final String endDate,
      @QueryParam("user") final String user,
      @QueryParam("group") final String group,
      @QueryParam("jql") String jql,
      @DefaultValue("0") @QueryParam("startAt") int startAt,
      @DefaultValue("25") @QueryParam("maxResults") int maxResults,
      @DefaultValue("emptyFieldValue") @QueryParam("fields") final List<StringList> fields)
          throws URISyntaxException, SQLException {

    checkRequiredFindWorklogsByIssuesParameter(startDate, endDate, user, group);

    Calendar startDateCalendar = null;
    try {
      startDateCalendar = convertStartDate(startDate);
    } catch (ParseException e) {
      throw new RESTException(Response.Status.BAD_REQUEST,
          "Cannot parse the 'startDate' parameter: " + startDate);
    }
    Calendar endDateCalendar = null;
    try {
      endDateCalendar = convertEndDate(endDate);
    } catch (ParseException e) {
      throw new RESTException(Response.Status.BAD_REQUEST, "Cannot parse the 'endDate' parameter: "
          + endDate);
    }
    if (startAt < 0) {
      startAt = DEFAULT_STARTAT_PARAM;
    }
    if (maxResults < 0) {
      maxResults = DEFAULT_MAXRESULT_PARAM;
    }
    List<String> users = createUsers(user, group);
    if (users.isEmpty()) {
      throw new RESTException(Response.Status.BAD_REQUEST,
          "Error running search: There is no group or user matching the given parameters.");
    }
    if (jql == null) {
      jql = "";
    }
    List<Issue> issues = null;
    try {
      issues = getIssuesByJQL(jql);
    } catch (SearchException e) {
      throw new RESTException(Response.Status.BAD_REQUEST, "Error running search: " + e);
    } catch (JqlParseException e) {
      throw new RESTException(Response.Status.BAD_REQUEST, e.getMessage());
    }

    List<Long> issueIds = new ArrayList<Long>();
    for (Issue issue : issues) {
      issueIds.add(issue.getId());
    }
    Collections.reverse(issues);

    Map<Long, Long> result = sumWorklogs(startDateCalendar, endDateCalendar, users, issueIds);

    IncludedFields includedFields = IncludedFields.includeNavigableByDefault(fields);
    String baseUrl = ComponentAccessor.getApplicationProperties().getString(APKeys.JIRA_BASEURL)
        + "/rest/api/2/issue/";
    boolean isEmptyField = StringList.joinLists(fields).asList().contains("emptyFieldValue");
    List<IssueBeanWithTimespent> issueBeans = new ArrayList<IssueBeanWithTimespent>();
    for (int i = 0, j = 0; ((j < issues.size()) && (i < (startAt + maxResults)));) {
      Issue issue = issues.get(j);
      if (result.containsKey(issue.getId())) {
        if ((i >= startAt)) {
          IssueBeanWithTimespent bean = new IssueBeanWithTimespent(issue.getId(), issue.getKey(),
              new URI(baseUrl + issue.getId()), result.get(issue.getId()));
          bean.fieldsToInclude(includedFields);
          if (!isEmptyField) {
            addFields(issue, bean);
          }
          issueBeans.add(bean);
        }
        i++;
      }
      j++;
    }
    SearchResultsBeanWithTimespent searchResultsBean = new SearchResultsBeanWithTimespent(startAt,
        maxResults,
        result.size(), issueBeans);

    return searchResultsBean;
  }

  private FieldJsonRepresentation getFieldValue(final FieldLayoutItem fieldLayoutItem,
      final Issue issue) {
    OrderableField field = fieldLayoutItem.getOrderableField();

    if (field instanceof RestAwareField) {
      RestAwareField restAware = (RestAwareField) field;
      return restAware.getJsonFromIssue(issue, false, fieldLayoutItem);
    } else {
      return null;
    }
  }

  /**
   * Returns the selected issues based on the given JQL filter.
   *
   * @param jql
   *          JQL filter the search is based on.
   * @return List of the matching JIRA Issues.
   * @throws SearchException
   * @throws JqlParseException
   *           Thrown when the given JQL is not valid.
   */
  private List<Issue> getIssuesByJQL(final String jql)
      throws SearchException,
      JqlParseException {
    JiraAuthenticationContext authenticationContext = ComponentAccessor
        .getJiraAuthenticationContext();
    ApplicationUser loggedInUser = authenticationContext.getLoggedInUser();
    List<Issue> issues = null;
    SearchService searchService = ComponentAccessor.getComponentOfType(SearchService.class);
    final ParseResult parseResult = searchService.parseQuery(loggedInUser, jql);
    if (parseResult.isValid()) {
      final SearchResults results = searchService.search(loggedInUser,
          parseResult.getQuery(), PagerFilter.getUnlimitedFilter());
      issues = results.getIssues();
    } else {
      throw new JqlParseException(null, parseResult.getErrors().toString());
    }
    return issues;
  }

  /**
   * Check the given String is empty.
   *
   * @param theString
   *          The String variable.
   * @return If the String is null or the String length equals whit 0 then true, else false.
   */
  private boolean isStringEmpty(final String theString) {
    if ((theString == null) || (theString.length() == 0)) {
      return true;
    }
    return false;
  }

  /**
   * Summarize the worked time by issue.
   *
   * @param startDateCalendar
   *          The starting date of the search
   * @param endDateCalendar
   *          The ending date of the search
   * @param users
   *          The List of users whose worklog's are collected.
   * @param issueIds
   *          A List of Issue IDs. Only these issues will be in the returned Map.
   * @return A Map which keys are issueIDs and values are the worked time on that issue.
   */
  private HashMap<Long, Long> sumWorklogs(final Calendar startDateCalendar,
      final Calendar endDateCalendar,
      final List<String> users,
      final List<Long> issueIds) throws SQLException {
    String schemaName = new DefaultOfBizConnectionFactory().getDatasourceInfo().getSchemaName();
    String worklogTablename = "";
    if ((schemaName != null) && !schemaName.equals("")) {
      worklogTablename = schemaName + ".worklog";
    } else {
      worklogTablename = "worklog";
    }
    String query = "SELECT worklog.issueid, SUM(worklog.timeworked) AS timeworked"
        + " FROM " + worklogTablename
        + " WHERE worklog.startdate>=? AND worklog.startdate<?";

    StringBuilder authorPreparedParams = new StringBuilder();
    for (int i = 0; i < users.size(); i++) {
      authorPreparedParams.append("?,");
    }
    if (authorPreparedParams.length() > 0) {
      authorPreparedParams.deleteCharAt(authorPreparedParams.length() - 1);
    }
    query += " AND worklog.author IN (" + authorPreparedParams.toString() + ")";
    query += " GROUP BY worklog.issueid";

    Connection conn = null;
    PreparedStatement ps = null;
    ResultSet rs = null;
    HashMap<Long, Long> result = new HashMap<Long, Long>();
    try {
      conn = new DefaultOfBizConnectionFactory().getConnection();
      ps = conn.prepareStatement(query);
      int preparedIndex = 1;
      ps.setTimestamp(preparedIndex++, new Timestamp(startDateCalendar.getTimeInMillis()));
      ps.setTimestamp(preparedIndex++, new Timestamp(endDateCalendar.getTimeInMillis()));
      for (String user : users) {
        ps.setString(preparedIndex++, user);
      }

      rs = ps.executeQuery();

      while (rs.next()) {
        Long worklogIssueId = rs.getLong("issueid");
        if (issueIds.contains(worklogIssueId)) {
          result.put(worklogIssueId, rs.getLong("timeworked"));
        }
      }
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch (SQLException e) {
          LOGGER.error("Cannot close ResultSet");
        }
      }
      if (ps != null) {
        try {
          ps.close();
        } catch (SQLException e) {
          LOGGER.error("Cannot close Statement");
        }
      }
      if (conn != null) {
        try {
          conn.close();
        } catch (SQLException e) {
          LOGGER.error("Cannot close Connection");
        }
      }
    }

    return result;
  }

  /**
   * The method to query worklogs.
   *
   * @param startDate
   *          The startDate calendar parameter.
   * @param endDate
   *          The endDate calendar parameter.
   * @param userString
   *          The user String parameter.
   * @param groupString
   *          The group String parameter.
   * @param projectString
   *          The project String parameter.
   * @param updated
   *          True if the method give back the worklogs which were created or updated in the given
   *          period, else false. The false give back the worklogs of the period.
   * @return JSONString what contains a list of queried worklogs.
   * @throws ParseException
   *           If can't parse the dates.
   * @throws GenericEntityException
   *           If the GenericDelegator throw a GenericEntityException.
   * @throws JSONException
   *           If the createWorklogJSONObject method throw a JSONException.
   */
  private Response worklogQuery(final Calendar startDate, final Calendar endDate,
      final String userString,
      final String groupString, final String projectString, final List<StringList> fields,
      final boolean updated)
          throws DataAccessException,
          SQLException, JSONException, ParseException {

    List<JSONObject> worklogs = new ArrayList<JSONObject>();

    JiraAuthenticationContext authenticationContext = ComponentAccessor
        .getJiraAuthenticationContext();
    ApplicationUser loggedInUser = authenticationContext.getLoggedInUser();
    List<Long> projects = createProjects(projectString, loggedInUser);
    if ((projectString != null) && projects.isEmpty()) {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity(
              "Error running search: There is no project matching the given 'project' parameter: "
                  + projectString)
          .build();
    }
    List<String> users = createUsers(userString, groupString);
    if (users.isEmpty()) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("Error running search: There is no group or user matching the given parameters.")
          .build();
    }
    String schemaName = new DefaultOfBizConnectionFactory().getDatasourceInfo().getSchemaName();
    String worklogTablename = "";
    String issueTablename = "";
    if ((schemaName != null) && !schemaName.equals("")) {
      worklogTablename = schemaName + ".worklog";
      issueTablename = schemaName + ".jiraissue";
    } else {
      worklogTablename = "worklog";
      issueTablename = "jiraissue";
    }

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

      String query =
          "SELECT worklog.id, worklog.startdate, worklog.issueid, worklog.author, worklog.timeworked, worklog.worklogbody, worklog.updated"
              + " FROM " + worklogTablename + ", " + issueTablename
              + " WHERE worklog.issueid=jiraissue.id"
              + " AND worklog.startdate>=? AND worklog.startdate<?"
              + " AND worklog.author IN ("
              + usersPreparedParams.toString() + ")"
              + " AND jiraissue.project IN ("
              + projectsPreparedParams.toString() + ")";

      Connection conn = null;
      PreparedStatement ps = null;
      ResultSet rs = null;
      try {
        conn = new DefaultOfBizConnectionFactory().getConnection();
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

        rs = ps.executeQuery();
        while (rs.next()) {
          worklogs.add(createWorklogJSONObject(rs, fields));
        }
      } finally {
        if (rs != null) {
          try {
            rs.close();
          } catch (SQLException e) {
            LOGGER.error("Cannot close ResultSet");
          }
        }
        if (ps != null) {
          try {
            ps.close();
          } catch (SQLException e) {
            LOGGER.error("Cannot close Statement");
          }
        }
        if (conn != null) {
          try {
            conn.close();
          } catch (SQLException e) {
            LOGGER.error("Cannot close Connection");
          }
        }
      }
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
    return Response.ok(jsonArrayResult.toString()).build();

    // 2.0.0
    // JSONObject jsonResult = new JSONObject();
    // jsonResult.put("worklogs", worklogs);
    // return Response.ok(jsonResult.toString()).build();
  }
}
