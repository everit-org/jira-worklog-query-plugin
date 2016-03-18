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
import java.util.Calendar;
import java.util.List;
import java.util.Set;

import org.everit.jira.querydsl.schema.QJiraissue;
import org.everit.jira.querydsl.schema.QProject;
import org.everit.jira.querydsl.schema.QWorklog;
import org.everit.jira.querydsl.support.QuerydslCallable;
import org.everit.jira.worklog.query.plugin.IssueBeanWithTimespent;

import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.SimpleExpression;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.core.types.dsl.StringExpressions;
import com.querydsl.sql.Configuration;
import com.querydsl.sql.SQLExpressions;
import com.querydsl.sql.SQLQuery;

/**
 * Query to find worklogs by issues.
 */
public class FindWorklogsByIssuesQuery implements QuerydslCallable<List<IssueBeanWithTimespent>> {

  private final Calendar endDate;

  private final Set<Long> issueIds;

  private final String jiraBaseUrl;

  private final long limit;

  private final long offset;

  private final Calendar startDate;

  private final List<String> userKeys;

  /**
   * Simple constructor.
   *
   * @param startDate
   *          the start date of worklogs.
   * @param endDate
   *          the end date of worklogs
   * @param userKeys
   *          a list of user keys.
   * @param issueIds
   *          a collection of user ids.
   * @param offset
   *          the offset for the query results.
   * @param limit
   *          the limit / max results for the query results.
   * @param jiraBaseUrl
   *          the JIRA base url.
   */
  public FindWorklogsByIssuesQuery(final Calendar startDate, final Calendar endDate,
      final List<String> userKeys, final Set<Long> issueIds, final long offset, final long limit,
      final String jiraBaseUrl) {
    this.endDate = endDate;
    this.startDate = startDate;
    this.userKeys = userKeys;
    this.issueIds = issueIds;
    this.offset = offset;
    this.limit = limit;
    this.jiraBaseUrl = jiraBaseUrl;
  }

  @Override
  public List<IssueBeanWithTimespent> call(final Connection connection,
      final Configuration configuration)
          throws SQLException {
    QWorklog worklog = new QWorklog("worklog");
    QJiraissue issue = new QJiraissue("issue");
    QProject project = new QProject("project");

    Timestamp startTimestamp = new Timestamp(startDate.getTimeInMillis());
    Timestamp endTimestamp = new Timestamp(endDate.getTimeInMillis());

    StringExpression issueKey = project.pkey.concat("-").concat(issue.issuenum.stringValue());
    SimpleExpression<Long> timeworked = SQLExpressions.sum(worklog.timeworked).as("timeworked");
    Expression<String> jiraBaseUrlExpression = Expressions.constant(jiraBaseUrl);
    StringExpression jiraBaseUrlStringExpression = StringExpressions.ltrim(jiraBaseUrlExpression);

    StringExpression concat = jiraBaseUrlStringExpression.concat(issue.id.stringValue());
    return new SQLQuery<List<IssueBeanWithTimespent>>(connection, configuration)
        .select(Projections.constructor(IssueBeanWithTimespent.class,
            issue.id,
            issueKey,
            concat,
            timeworked))
        .from(worklog)
        .join(issue).on(issue.id.eq(worklog.issueid))
        .join(project).on(project.id.eq(issue.project))
        .where(worklog.startdate.goe(startTimestamp)
            .and(worklog.startdate.lt(endTimestamp))
            .and(worklog.author.in(userKeys))
            .and(worklog.issueid.in(issueIds)))
        .groupBy(issue.id, project.pkey, issue.issuenum)
        .offset(offset)
        .limit(limit)
        .orderBy(issue.id.asc())
        .fetch();
  }

}
