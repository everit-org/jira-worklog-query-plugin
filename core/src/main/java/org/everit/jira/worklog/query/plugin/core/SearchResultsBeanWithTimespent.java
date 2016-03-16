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
package org.everit.jira.worklog.query.plugin.core;

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
