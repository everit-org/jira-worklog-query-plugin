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
package org.everit.jira.worklog.query.plugin.query;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import org.everit.jira.querydsl.schema.QAppUser;
import org.everit.jira.querydsl.schema.QCwdUser;
import org.everit.jira.querydsl.schema.QJiraissue;
import org.everit.jira.querydsl.schema.QProject;
import org.everit.jira.querydsl.schema.QWorklog;
import org.everit.jira.querydsl.support.QuerydslCallable;

import com.atlassian.jira.rest.api.util.StringList;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.sql.Configuration;
import com.querydsl.sql.SQLQuery;

/**
 * Query to find worklogs.
 */
public class FindWorklogsQuery implements QuerydslCallable<List<JsonWorklog>> {

  private final Calendar endDate;

  private final List<StringList> fields;

  private List<Long> projectIds;

  private final Calendar startDate;

  private final boolean updated;

  private final List<String> userKeys;

  /**
   * Simple constructor.
   *
   * @param startDate
   *          the start date of worklogs.
   * @param endDate
   *          the end date of worklogs
   * @param fields
   *          a list of additional fields.
   * @param userKeys
   *          a list of user keys.
   * @param projectIds
   *          a list of project ids.
   * @param updated
   *          True if the method give back the worklogs which were created or updated in the given
   *          period, else false. The false give back the worklogs of the period.
   */
  public FindWorklogsQuery(final Calendar startDate, final Calendar endDate,
      final List<StringList> fields, final List<String> userKeys, final List<Long> projectIds,
      final boolean updated) {
    this.startDate = startDate;
    this.endDate = endDate;
    this.fields = fields;
    this.userKeys = userKeys;
    this.projectIds = projectIds;
    this.updated = updated;
  }

  @Override
  public List<JsonWorklog> call(final Connection connection, final Configuration configuration)
      throws SQLException {
    QWorklog worklog = new QWorklog("worklog");
    QJiraissue issue = new QJiraissue("issue");
    QProject project = new QProject("project");
    QCwdUser cwduser = new QCwdUser("cwd_user");
    QAppUser appuser = new QAppUser("app_user");

    StringExpression issueKey = project.pkey.concat("-").concat(issue.issuenum.stringValue());

    Timestamp startTimestamp = new Timestamp(startDate.getTimeInMillis());
    Timestamp endTimestamp = new Timestamp(endDate.getTimeInMillis());

    List<String> fieldsAsList =
        Arrays.asList(StringList.joinLists(fields).toQueryParam().split(","));
    final boolean useComment = fieldsAsList.contains("comment");
    final boolean useUpdated = fieldsAsList.contains("updated");

    BooleanExpression intervalPredicate = null;
    if (updated) {
      intervalPredicate = worklog.updated.goe(startTimestamp)
          .and(worklog.updated.lt(endTimestamp));
    } else {
      intervalPredicate = worklog.startdate.goe(startTimestamp)
          .and(worklog.startdate.lt(endTimestamp));
    }

    return new SQLQuery<JsonWorklog>(connection, configuration)
        .select(JsonWorklog.createProjection(worklog.id,
            worklog.startdate,
            issueKey,
            cwduser.userName,
            worklog.timeworked,
            useComment ? worklog.worklogbody : null,
            useUpdated ? worklog.updated : null))
        .from(worklog)
        .join(issue).on(issue.id.eq(worklog.issueid))
        .join(project).on(project.id.eq(issue.project))
        .join(appuser).on(appuser.userKey.eq(worklog.author))
        .join(cwduser).on(cwduser.lowerUserName.eq(appuser.lowerUserName))
        .where(intervalPredicate
            .and(worklog.author.in(userKeys))
            .and(issue.project.in(projectIds)))
        .orderBy(worklog.id.asc())
        .fetch();
  }

}
