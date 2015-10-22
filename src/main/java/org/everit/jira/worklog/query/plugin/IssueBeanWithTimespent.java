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

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;

import com.atlassian.jira.rest.v2.issue.IssueBean;

/**
 * IssueBeanWithTimespent extends the original IssueBean class with spent time value.
 */
public class IssueBeanWithTimespent extends IssueBean {
  @XmlElement
  private Long timespent = 0L;

  IssueBeanWithTimespent(final Long id, final String key, final URI selfUri,
      final Long timespent) {
    super(id, key, selfUri);
    this.timespent = timespent;
  }

  public Long getTimeSpent() {
    return timespent;
  }
}
