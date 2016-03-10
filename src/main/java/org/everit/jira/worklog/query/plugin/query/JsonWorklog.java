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

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.everit.jira.worklog.query.plugin.DateTimeConverterUtil;

import com.atlassian.jira.util.json.JSONException;
import com.atlassian.jira.util.json.JSONObject;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.QBean;
import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.SimpleExpression;
import com.querydsl.core.types.dsl.StringExpression;

/**
 * JsonWorklog is an unordered collection of name/value pairs. Contains information of worklog.
 */
public class JsonWorklog extends JSONObject {

  private static final String COMMENT = "comment";

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

  public void setComment(final String comment) throws JSONException {
    put(COMMENT, comment);
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
