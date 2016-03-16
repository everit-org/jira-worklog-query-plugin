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
import org.everit.jira.worklog.query.plugin.core.IssueBeanWithTimespent;
import org.everit.persistence.querydsl.support.QuerydslCallable;

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
