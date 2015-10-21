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

import java.io.Serializable;
import java.util.List;

import com.atlassian.jira.rest.api.util.StringList;

/**
 * FindWorklogsByIssues method parameter container class.
 */
public class FindWorklogsByIssuesParameterDTO implements Serializable {

  /**
   * Serial Version UID.
   */
  private static final long serialVersionUID = -4947183929460600358L;
  /**
   * The query start date parameter.
   */
  public String startDate;
  /**
   * The query end date parameter.
   */
  public String endDate;
  /**
   * The query user parameter.
   */
  public String user;
  /**
   * The query group parameter.
   */
  public String group;
  /**
   * The query jql parameter.
   */
  public String jql;
  /**
   * The query start At parameter.
   */
  public int startAt;
  /**
   * The query max Result parameter.
   */
  public int maxResults;
  /**
   * The query fields parameter.
   */
  public List<StringList> fields;

  public FindWorklogsByIssuesParameterDTO endDate(final String endDate) {
    this.endDate = endDate;
    return this;
  }

  public FindWorklogsByIssuesParameterDTO fields(final List<StringList> fields) {
    this.fields = fields;
    return this;
  }

  public FindWorklogsByIssuesParameterDTO group(final String group) {
    this.group = group;
    return this;
  }

  public FindWorklogsByIssuesParameterDTO jql(final String jql) {
    this.jql = jql;
    return this;
  }

  public FindWorklogsByIssuesParameterDTO maxResults(final int maxResults) {
    this.maxResults = maxResults;
    return this;
  }

  private void readObject(final java.io.ObjectInputStream stream) throws java.io.IOException,
      ClassNotFoundException {
    stream.close();
    throw new java.io.NotSerializableException(getClass().getName());
  }

  public FindWorklogsByIssuesParameterDTO startAt(final int startAt) {
    this.startAt = startAt;
    return this;
  }

  public FindWorklogsByIssuesParameterDTO startDate(final String startDate) {
    this.startDate = startDate;
    return this;
  }

  public FindWorklogsByIssuesParameterDTO user(final String user) {
    this.user = user;
    return this;
  }

  private void writeObject(final java.io.ObjectOutputStream stream) throws java.io.IOException {
    stream.close();
    throw new java.io.NotSerializableException(getClass().getName());
  }
}
