/*
 * Copyright (C) 2013 Everit Kft. (http://www.everit.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.everit.jira.worklog.query.plugin;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.Response;

import org.everit.jira.querydsl.support.QuerydslSupport;
import org.everit.jira.querydsl.support.ri.QuerydslSupportImpl;
import org.everit.jira.worklog.query.plugin.query.FindWorklogsByIssuesQuery;
import org.everit.jira.worklog.query.plugin.query.FindWorklogsQuery;
import org.everit.jira.worklog.query.plugin.query.JsonWorklog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.bc.issue.search.SearchService.ParseResult;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.APKeys;
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
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.rest.api.util.StringList;
import com.atlassian.jira.rest.v2.issue.IncludedFields;
import com.atlassian.jira.rest.v2.issue.IssueBean;
import com.atlassian.jira.rest.v2.issue.RESTException;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.collect.CollectionBuilder;
import com.atlassian.jira.util.json.JSONArray;
import com.atlassian.jira.web.bean.PagerFilter;

/**
 * The implementations of the WorklogQueryCore.
 */
public class WorklogQueryCoreImpl implements WorklogQueryCore {

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
  private static final Logger LOGGER = LoggerFactory.getLogger(WorklogQueryCoreImpl.class);

  private QuerydslSupport querydslSupport;

  /**
   * Simple constructor. Create {@link QuerydslSupport} instance.
   */
  public WorklogQueryCoreImpl() {
    try {
      querydslSupport = new QuerydslSupportImpl();
    } catch (Exception e) {
      throw new RuntimeException("Cannot create Worklog Query instance.", e);
    }
  }

  private void addFields(final Issue issue, final IssueBean bean) {
    // iterate over all the visible layout items from the field layout for this issue and attempt to
    // add them
    // to the result
    ApplicationUser loggedInUser =
        ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
    FieldLayout layout = ComponentAccessor.getFieldLayoutManager().getFieldLayout(issue);
    List<FieldLayoutItem> fieldLayoutItems =
        layout.getVisibleLayoutItems(issue.getProjectObject(),
            CollectionBuilder.list(issue.getIssueType().getId()));
    for (FieldLayoutItem fieldLayoutItem : fieldLayoutItems) {
      OrderableField<?> field = fieldLayoutItem.getOrderableField();
      FieldJsonRepresentation fieldValue = getFieldValue(fieldLayoutItem, issue);
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
      Set<NavigableField> fields = ComponentAccessor.getFieldManager()
          .getAvailableNavigableFields(loggedInUser);
      for (NavigableField field : fields) {
        if (!bean.hasField(field.getId())
            && (!(field instanceof OrderableField) || (field instanceof ProjectSystemField))
            && (field instanceof RestAwareField)) {
          addRestAwareField(issue, bean, field, (RestAwareField) field);
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
    Map<Long, Issue> result = new HashMap<>();
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
  private Calendar convertEndDate(final String endDateString) throws WorklogQueryException {
    Calendar endDate;
    if ((endDateString == null) || (endDateString.length() == 0)) {
      endDate = Calendar.getInstance();
    } else {
      try {
        endDate = DateTimeConverterUtil.inputStringToCalendar(endDateString);
      } catch (ParseException e) {
        LOGGER.error("Failed to convert end date", e);
        throw new WorklogQueryException("Cannot parse the 'endDate' parameter: " + endDateString,
            e);
      }
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
  private Calendar convertStartDate(final String startDateString) throws WorklogQueryException {
    Calendar startDate;
    try {
      startDate = DateTimeConverterUtil.inputStringToCalendar(startDateString);
    } catch (ParseException e) {
      LOGGER.error("Failed to convert start date", e);
      throw new WorklogQueryException("Cannot parse the 'startDate' parameter: " + startDateString,
          e);
    }
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
   */
  private List<Long> createProjects(final String projectString, final ApplicationUser user) {

    Collection<Project> projects = ComponentAccessor.getPermissionManager()
        .getProjects(ProjectPermissions.BROWSE_PROJECTS, user);

    List<Long> projectList = new ArrayList<>();
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

  private List<String> createUsers(final String userName, final String group) {
    List<String> users = new ArrayList<>();
    if ((group != null) && (group.length() != 0)) {
      Set<ApplicationUser> groupUsers = ComponentAccessor.getUserUtil()
          .getAllUsersInGroupNames(
              Arrays.asList(new String[] { group }));
      for (ApplicationUser groupUser : groupUsers) {
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
   * The findUpdatedWorklogs REST method core implementation.
   *
   * @param startDate
   *          The start Date parameter of the REST.
   * @param endDate
   *          The end Date parameter of the REST.
   * @param user
   *          The user parameter of the REST.
   * @param group
   *          The group parameter of the REST.
   * @param project
   *          The project parameter of the REST.
   * @param fields
   *          The fields parameter of the REST.
   * @return The founded worklogs.
   *
   */
  @Override
  public Response findUpdatedWorklogs(final String startDate, final String endDate,
      final String user, final String group,
      final String project, final List<StringList> fields) throws WorklogQueryException {
    Response checkRequiredFindWorklogsParamResponse = checkRequiredFindWorklogsParameter(startDate,
        user, group);
    if (checkRequiredFindWorklogsParamResponse != null) {
      return checkRequiredFindWorklogsParamResponse;
    }
    Calendar startDateCalendar = convertStartDate(startDate);
    Calendar endDateCalendar = convertEndDate(endDate);
    try {
      return worklogQuery(startDateCalendar, endDateCalendar, user, group, project, fields, true);
    } catch (Exception e) {
      LOGGER.error("Failed to query the worklogs", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(e.getMessage()).build();
    }
  }

  /**
   * The findWorklogs REST method core implementation.
   *
   * @param startDate
   *          The start Date parameter of the REST.
   * @param endDate
   *          The end Date parameter of the REST.
   * @param user
   *          The user parameter of the REST.
   * @param group
   *          The group parameter of the REST.
   * @param project
   *          The project parameter of the REST.
   * @param fields
   *          The fields parameter of the REST.
   * @return The founded worklogs.
   */
  @Override
  public Response findWorklogs(final String startDate, final String endDate, final String user,
      final String group, final String project, final List<StringList> fields)
      throws WorklogQueryException {
    Response checkRequiredFindWorklogsParamResponse = checkRequiredFindWorklogsParameter(startDate,
        user, group);
    if (checkRequiredFindWorklogsParamResponse != null) {
      return checkRequiredFindWorklogsParamResponse;
    }
    Calendar startDateCalendar = convertStartDate(startDate);
    Calendar endDateCalendar = convertEndDate(endDate);
    try {
      return worklogQuery(startDateCalendar, endDateCalendar, user, group, project, fields, false);
    } catch (Exception e) {
      LOGGER.error("Failed to query the worklogs", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(e.getMessage()).build();
    }
  }

  /**
   *
   * The findWorklogsByIssues REST method core implementation.
   *
   * @param findWorklogsByIssuesParam
   *          The parameters object of the findWorklogsByIssues method parameters.
   * @return The search result.
   */
  @Override
  public SearchResultsBeanWithTimespent findWorklogsByIssues(
      final FindWorklogsByIssuesParam findWorklogsByIssuesParam)
      throws WorklogQueryException {
    int tmpStartAt = findWorklogsByIssuesParam.startAt;
    int tmpMaxResults = findWorklogsByIssuesParam.maxResults;
    checkRequiredFindWorklogsByIssuesParameter(findWorklogsByIssuesParam.startDate,
        findWorklogsByIssuesParam.endDate, findWorklogsByIssuesParam.user,
        findWorklogsByIssuesParam.group);

    Calendar startDateCalendar = convertStartDate(findWorklogsByIssuesParam.startDate);
    Calendar endDateCalendar = convertEndDate(findWorklogsByIssuesParam.endDate);
    if (tmpStartAt < 0) {
      tmpStartAt = DEFAULT_STARTAT_PARAM;
    }
    if (tmpMaxResults < 0) {
      tmpMaxResults = DEFAULT_MAXRESULT_PARAM;
    }
    List<String> users =
        createUsers(findWorklogsByIssuesParam.user, findWorklogsByIssuesParam.group);
    if (users.isEmpty()) {
      throw new WorklogQueryException(
          "Error running search: There is no group or user matching the given parameters.");
    }
    List<Issue> issues = null;
    try {
      issues = getIssuesByJQL(findWorklogsByIssuesParam.jql);
    } catch (SearchException e) {
      LOGGER.error("Failed to query the worklogs", e);
      throw new WorklogQueryException("Error running search: ", e);
    } catch (JqlParseException e) {
      LOGGER.error("Failed to parse the JQL", e);
      throw new WorklogQueryException(e.getMessage(), e);
    }

    Map<Long, Issue> issueIdIssue = collectIssueIds(issues);

    List<IssueBeanWithTimespent> issueBeans = null;
    try {
      String jiraBaseUrl = ComponentAccessor.getApplicationProperties()
          .getString(APKeys.JIRA_BASEURL) + "/rest/api/2/issue/";
      issueBeans = querydslSupport.execute(new FindWorklogsByIssuesQuery(startDateCalendar,
          endDateCalendar, users, issueIdIssue.keySet(), tmpStartAt, tmpMaxResults, jiraBaseUrl));

      addFieldsToIssueBeans(findWorklogsByIssuesParam.fields, issueIdIssue, issueBeans);
    } catch (Exception e) {
      LOGGER.error("Error when try collectig issue beans.", e);
      throw new WorklogQueryException("Error when try collectig issue beans.", e);
    }
    SearchResultsBeanWithTimespent searchResultsBean =
        new SearchResultsBeanWithTimespent(tmpStartAt, tmpMaxResults, issueBeans.size(),
            issueBeans);

    return searchResultsBean;
  }

  private FieldJsonRepresentation getFieldValue(final FieldLayoutItem fieldLayoutItem,
      final Issue issue) {
    OrderableField<?> field = fieldLayoutItem.getOrderableField();

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
   *           Atlassian Search Service excaption.
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
    ParseResult parseResult = searchService.parseQuery(loggedInUser, jql);
    if (parseResult.isValid()) {
      SearchResults results = searchService.search(loggedInUser,
          parseResult.getQuery(), PagerFilter.getUnlimitedFilter());
      issues = results.getResults();
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
   */
  private Response worklogQuery(final Calendar startDate, final Calendar endDate,
      final String userString, final String groupString, final String projectString,
      final List<StringList> fields, final boolean updated) {

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

    List<JsonWorklog> jsonWorklogs =
        querydslSupport.execute(new FindWorklogsQuery(startDate, endDate, fields,
            users, projects, updated));
    JSONArray jsonArrayResult = new JSONArray();
    jsonArrayResult.put(jsonWorklogs);

    return Response.ok(jsonArrayResult.toString()).build();
  }

}
