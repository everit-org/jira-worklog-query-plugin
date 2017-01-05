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

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.everit.jira.worklog.query.plugin.core.DateTimeConverterUtil;

import com.atlassian.jira.util.json.JSONException;
import com.atlassian.jira.util.json.JSONObject;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.QBean;
import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.SimpleExpression;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.sql.SQLQuery;

/**
 * JsonWorklog is an unordered collection of name/value pairs. Contains information of worklog.
 */
public class JsonWorklog extends JSONObject {

  private static final String COMMENT = "commentBody";

  private static final String DURATION = "duration";

  private static final String ID = "id";

  private static final String ISSUE_KEY = "issueKey";

  private static final String START_DATE = "startDate";

  private static final String UPDATED = "updated";

  private static final String USER_ID = "userId";

  /**
   * Create a JsonWorklog Bean populating projection for the given type and expressions.
   *
   * @param worklogId
   *          the id of the worklog expression.
   * @param startDate
   *          the worklog startdate expression.
   * @param issueKey
   *          the issue key expression.
   * @param userId
   *          the user id SQL subquery.
   * @param duration
   *          the worklog duration expression.
   * @param comment
   *          the worklog comment expression.
   * @param updated
   *          the worklog updated date expression.
   * @return the JsonWorklog Bean population projection.
   */
  public static QBean<JsonWorklog> createProjection(final NumberExpression<Long> worklogId,
      final DateTimeExpression<Timestamp> startDate, final StringExpression issueKey,
      final SQLQuery<String> userId, final NumberExpression<Long> duration,
      final StringExpression comment, final DateTimeExpression<Timestamp> updated) {

    List<SimpleExpression<?>> expressionList = new ArrayList<SimpleExpression<?>>();
    expressionList.add(worklogId.as(ID));
    expressionList.add(startDate.as(START_DATE));
    expressionList.add(issueKey.as(ISSUE_KEY));
    expressionList.add(userId.as(USER_ID));
    expressionList.add(duration.as(DURATION));
    if (comment != null) {
      expressionList.add(comment.as(COMMENT));
    }
    if (updated != null) {
      expressionList.add(updated.as(UPDATED));
    }

    SimpleExpression<?>[] expressions = new SimpleExpression<?>[expressionList.size()];

    return Projections.bean(JsonWorklog.class, expressionList.toArray(expressions));
  }

  /**
   * Create a JsonWorklog Bean populating projection for the given type and expressions.
   *
   * @param worklogId
   *          the id of the worklog expression.
   * @param startDate
   *          the worklog startdate expression.
   * @param issueKey
   *          the issue key expression.
   * @param userId
   *          the user id expression.
   * @param duration
   *          the worklog duration expression.
   * @param comment
   *          the worklog comment expression.
   * @param updated
   *          the worklog updated date expression.
   * @return the JsonWorklog Bean population projection.
   */
  public static QBean<JsonWorklog> createProjection(final NumberExpression<Long> worklogId,
      final DateTimeExpression<Timestamp> startDate, final StringExpression issueKey,
      final StringExpression userId, final NumberExpression<Long> duration,
      final StringExpression comment, final DateTimeExpression<Timestamp> updated) {

    List<SimpleExpression<?>> expressionList = new ArrayList<SimpleExpression<?>>();
    expressionList.add(worklogId.as(ID));
    expressionList.add(startDate.as(START_DATE));
    expressionList.add(issueKey.as(ISSUE_KEY));
    expressionList.add(userId.as(USER_ID));
    expressionList.add(duration.as(DURATION));
    if (comment != null) {
      expressionList.add(comment.as(COMMENT));
    }
    if (updated != null) {
      expressionList.add(updated.as(UPDATED));
    }

    SimpleExpression<?>[] expressions = new SimpleExpression<?>[expressionList.size()];

    return Projections.bean(JsonWorklog.class, expressionList.toArray(expressions));
  }

  public void setCommentBody(final String comment) throws JSONException {
    put("comment", comment);
  }

  public void setDuration(final long duration) throws JSONException {
    put(DURATION, duration);
  }

  public void setId(final long id) throws JSONException {
    put(ID, Long.valueOf(id));
  }

  public void setIssueKey(final String issueKey) throws JSONException {
    put(ISSUE_KEY, issueKey);
  }

  public void setStartDate(final Timestamp startDate) throws JSONException {
    put(START_DATE, DateTimeConverterUtil.stringDateToISO8601FormatString(startDate));
  }

  public void setUpdated(final Timestamp updated) throws JSONException {
    put(UPDATED, DateTimeConverterUtil.stringDateToISO8601FormatString(updated));
  }

  public void setUserId(final String userId) throws JSONException {
    put(USER_ID, userId);
  }

}
