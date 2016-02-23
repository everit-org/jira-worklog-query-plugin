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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonSerialize;

import com.atlassian.jira.rest.v2.search.SearchResultsBean;

/**
 * SearchResultsBeanWithTimespent extends the original SearchResultsBean class with issues
 * timespent.
 */
@XmlRootElement
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class SearchResultsBeanWithTimespent extends SearchResultsBean {

  @XmlElement
  private List<IssueBeanWithTimespent> issues;

  /**
   * SearchResultsBeanWithTimespent constructor with fields.
   *
   * @param startAt
   *          Start the result list from.
   * @param maxResults
   *          Max number of results.
   * @param total
   *          Total number of found result.
   * @param issues
   *          List of the found issues.
   */
  public SearchResultsBeanWithTimespent(final Integer startAt, final Integer maxResults,
      final Integer total, final List<IssueBeanWithTimespent> issues) {
    this.startAt = startAt;
    this.maxResults = maxResults;
    this.total = total;
    setIssues(issues);
  }

  @SuppressWarnings("unused")
  public List<IssueBeanWithTimespent> getIssues() {
    return issues;
  }

  public void setIssues(final List<IssueBeanWithTimespent> issues) {
    this.issues = issues;
  }

}
