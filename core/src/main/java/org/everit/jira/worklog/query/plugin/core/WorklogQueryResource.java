/**
 * This file is part of org.everit.jira.worklog.query.plugin.core.
 *
 * org.everit.jira.worklog.query.plugin.core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * org.everit.jira.worklog.query.plugin.core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with org.everit.jira.worklog.query.plugin.core.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.everit.jira.worklog.query.plugin.core;

import java.net.URISyntaxException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.everit.jira.querydsl.support.QuerydslSupport;
import org.everit.jira.querydsl.support.ri.QuerydslSupportImpl;
import org.everit.jira.worklog.query.plugin.query.FindWorklogsByIssuesQuery;
import org.everit.jira.worklog.query.plugin.query.FindWorklogsQuery;
import org.everit.jira.worklog.query.plugin.query.JsonWorklog;
import org.ofbiz.core.entity.GenericEntityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.bc.issue.search.SearchService.ParseResult;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.APKeys;
import com.atlassian.jira.exception.DataAccessException;
import com.atlassian.jira.issue.Issue;
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
import com.atlassian.jira.project.Project;
import com.atlassian.jira.rest.api.util.StringList;
import com.atlassian.jira.rest.v2.issue.IncludedFields;
import com.atlassian.jira.rest.v2.issue.IssueBean;
import com.atlassian.jira.rest.v2.issue.RESTException;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.Permissions;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.ApplicationUsers;
import com.atlassian.jira.util.collect.CollectionBuilder;
import com.atlassian.jira.util.json.JSONArray;
import com.atlassian.jira.util.json.JSONException;
import com.atlassian.jira.web.bean.PagerFilter;


/**
 * The WorklogQueryResource class. The class contains the findWorklogs method. The class grant the
 * JIRA worklog query.
 *
 * @param <V>
 */
@Path("/find")
public class WorklogQueryResource<V> {

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

  private QuerydslSupport querydslSupport;

  private void addFields(final Issue issue, final IssueBean bean) {
    // iterate over all the visible layout items from the field layout for this issue and attempt to
    // add them
    // to the result
    final FieldLayout layout = ComponentAccessor.getFieldLayoutManager().getFieldLayout(issue);
    final List<FieldLayoutItem> fieldLayoutItems = layout.getVisibleLayoutItems(
        issue.getProjectObject(), CollectionBuilder.list(issue.getIssueTypeObject().getId()));
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
      ApplicationUser loggedInUser = ComponentAccessor.getJiraAuthenticationContext().getUser();
      final Set<NavigableField> fields = ComponentAccessor.getFieldManager()
          .getAvailableNavigableFields(ApplicationUsers.toDirectoryUser(loggedInUser));
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

  private void addFieldsToIssueBeans(final List<StringList> fields,
      final Map<Long, Issue> issueIdIssue, final List<IssueBeanWithTimespent> issueBeans) {
    IncludedFields includedFields = IncludedFields.includeNavigableByDefault(fields);
    boolean isEmptyField = StringList.joinLists(fields)
        .asList().contains("emptyFieldValue");
    for (IssueBeanWithTimespent issueBean : issueBeans) {
      issueBean.fieldsToInclude(includedFields);
      if (!isEmptyField) {
        addFields(issueIdIssue.get(Long.valueOf(issueBean.getId())), issueBean);
      }
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

  private Map<Long, Issue> collectIssueIds(final List<Issue> issues) {
    Map<Long, Issue> result = new HashMap<Long, Issue>();
    for (Issue issue : issues) {
      result.put(issue.getId(), issue);
    }
    return result;
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
        .getProjects(Permissions.BROWSE, user);

    List<Long> projectIdList = new ArrayList<>();
    
    if (projectString != null && (projectString.length() != 0)) {
      String[] projectStrArray = projectString.split("\\s*,\\s*");
      Set<String> projectSet = new HashSet<>(Arrays.asList(projectStrArray));
      
      for (Project project : projects) {
        if (projectSet.contains(project.getKey())) {
          projectIdList.add(project.getId());
        }
      }
    }
    
    return projectIdList;
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
      Set<User> groupUsers = ComponentAccessor.getUserUtil().getAllUsersInGroupNames(
          Arrays.asList(new String[] { group }));
      List<ApplicationUser> appUsers = ApplicationUsers.from(groupUsers);
      for (ApplicationUser user : appUsers) {
        users.add(user.getKey());
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
      return worklogQuery(startDateCalendar, endDateCalendar, user, group, project, fields, true);
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

    Map<Long, Issue> issueIdIssue = collectIssueIds(issues);

    List<IssueBeanWithTimespent> issueBeans = null;
    try {
      String jiraBaseUrl = ComponentAccessor.getApplicationProperties()
          .getString(APKeys.JIRA_BASEURL) + "/rest/api/2/issue/";
      issueBeans = getQuerydslSupport().execute(new FindWorklogsByIssuesQuery(startDateCalendar,
          endDateCalendar, users, issueIdIssue.keySet(), startAt, maxResults, jiraBaseUrl));

      addFieldsToIssueBeans(fields, issueIdIssue, issueBeans);
    } catch (Exception e) {
      throw new RESTException(Response.Status.BAD_REQUEST,
          "Error when try collectig issue beans. " + e.getMessage());
    }
    SearchResultsBeanWithTimespent searchResultsBean =
        new SearchResultsBeanWithTimespent(startAt, maxResults, issueBeans.size(),
            issueBeans);

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
    User loggedInUser = ApplicationUsers.toDirectoryUser(authenticationContext.getUser());
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

  private QuerydslSupport getQuerydslSupport() {
    if (querydslSupport == null) {
      try {
        querydslSupport = new QuerydslSupportImpl();
      } catch (Exception e) {
        throw new RuntimeException("Cannot create Worklog Query instance.", e);
      }
    }
    return querydslSupport;
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

    JiraAuthenticationContext authenticationContext = ComponentAccessor
        .getJiraAuthenticationContext();
    ApplicationUser loggedInUser = authenticationContext.getUser();
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

    List<JsonWorklog> jsonWorklogs =
        getQuerydslSupport().execute(new FindWorklogsQuery(startDate, endDate, fields,
            users, projects, updated));
    JSONArray jsonArrayResult = new JSONArray();
    jsonArrayResult.put(jsonWorklogs);

    return Response.ok(jsonArrayResult.toString()).build();

    // 2.0.0
    // JSONObject jsonResult = new JSONObject();
    // jsonResult.put("worklogs", worklogs);
    // return Response.ok(jsonResult.toString()).build();
  }
}
