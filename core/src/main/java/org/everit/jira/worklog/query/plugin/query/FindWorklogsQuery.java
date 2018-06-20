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
import com.querydsl.sql.SQLExpressions;
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

    BooleanExpression predicate = null;
    if (updated) {
      predicate = worklog.updated.goe(startTimestamp)
          .and(worklog.updated.lt(endTimestamp));
    } else {
      predicate = worklog.startdate.goe(startTimestamp)
          .and(worklog.startdate.lt(endTimestamp));
    }
    
    predicate = predicate.and(worklog.author.in(userKeys));
    if (projectIds != null) {
        predicate = predicate.and(issue.project.in(projectIds));
    }

    return new SQLQuery<JsonWorklog>(connection, configuration)
        .select(JsonWorklog.createProjection(worklog.id,
            worklog.startdate,
            issueKey,
            SQLExpressions.select(cwduser.userName)
                .from(cwduser)
                .join(appuser).on(cwduser.lowerUserName.eq(appuser.lowerUserName))
                .where(appuser.userKey.eq(worklog.author))
                .distinct(),
            worklog.timeworked,
            useComment ? worklog.worklogbody : null,
            useUpdated ? worklog.updated : null))
        .from(worklog)
        .join(issue).on(issue.id.eq(worklog.issueid))
        .join(project).on(project.id.eq(issue.project))
        .where(predicate)
        .orderBy(worklog.id.asc())
        .fetch();
  }

}
