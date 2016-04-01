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

import java.net.URI;
import java.net.URISyntaxException;

import javax.xml.bind.annotation.XmlElement;

import com.atlassian.jira.rest.v2.issue.IssueBean;

/**
 * IssueBeanWithTimespent extends the original IssueBean class with spent time value.
 */
public class IssueBeanWithTimespent extends IssueBean {
  @XmlElement
  private Long timespent = 0L;

  public IssueBeanWithTimespent(final Long id, final String key, final String selfUri,
      final Long timespent) throws URISyntaxException {
    super(id, key, new URI(selfUri));
    this.timespent = timespent;
  }

  public Long getTimeSpent() {
    return timespent;
  }
}
