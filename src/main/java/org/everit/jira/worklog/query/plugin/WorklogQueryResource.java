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

import java.util.List;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.atlassian.jira.rest.api.util.StringList;
import com.atlassian.jira.rest.v2.issue.RESTException;

/**
 * The WorklogQueryResource class. The class contains the findWorklogs method. The class grant the
 * JIRA worklog query.
 *
 */
@Path("/find")
public class WorklogQueryResource {

  private final WorklogQueryCore worklogQueryResource = new WorklogQueryCoreImpl();

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
    try {
      return worklogQueryResource.findUpdatedWorklogs(startDate, endDate, user, group, project,
          fields);
    } catch (WorklogQueryException e) {
      return Response.status(Response.Status.BAD_REQUEST)
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
    try {
      return worklogQueryResource.findWorklogs(startDate, endDate, user, group, project, fields);
    } catch (WorklogQueryException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(e.getMessage()).build();
    }
  }

  /**
   * FindWorklogsByIssues REST method.
   *
   * @param startDate
   *          The query start date.
   * @param endDate
   *          The query end date.
   * @param user
   *          The searched user. Optional.
   * @param group
   *          The searched group. Optional.
   * @param jql
   *          Plus jql. Default empty String.
   * @param startAt
   *          Start the query result list from this element. Default 0.
   * @param maxResults
   *          Max number of results. Default 25.
   * @param fields
   *          List of the queried fields.
   * @return The found worklogs.
   */
  @GET
  @Path("/worklogsByIssues")
  @Produces({ MediaType.APPLICATION_JSON })
  public SearchResultsBeanWithTimespent findWorklogsByIssues(
      @QueryParam("startDate") final String startDate,
      @QueryParam("endDate") final String endDate,
      @QueryParam("user") final String user,
      @QueryParam("group") final String group,
      @DefaultValue("") @QueryParam("jql") final String jql,
      @DefaultValue("0") @QueryParam("startAt") final int startAt,
      @DefaultValue("25") @QueryParam("maxResults") final int maxResults,
      @DefaultValue("emptyFieldValue") @QueryParam("fields") final List<StringList> fields) {
    FindWorklogsByIssuesParam findWorklogsByIssuesParam =
        new FindWorklogsByIssuesParam()
            .startDate(startDate)
            .endDate(endDate)
            .user(user)
            .group(group)
            .jql(jql)
            .startAt(startAt)
            .maxResults(maxResults)
            .fields(fields);
    try {
      return worklogQueryResource.findWorklogsByIssues(findWorklogsByIssuesParam);
    } catch (WorklogQueryException e) {
      throw new RESTException(Status.BAD_REQUEST, e);
    }
  }

}
