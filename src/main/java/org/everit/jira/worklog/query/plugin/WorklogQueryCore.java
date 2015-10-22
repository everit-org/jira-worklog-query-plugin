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

import javax.ws.rs.core.Response;

import com.atlassian.jira.rest.api.util.StringList;

/**
 * The interface of the core part of the WorklogQueryResource class.
 */
public interface WorklogQueryCore {

  Response findUpdatedWorklogs(String startDate, String endDate, String user, String group,
      String project, List<StringList> fields) throws WorklogQueryException;

  Response findWorklogs(String startDate, String endDate, String user, String group,
      String project, List<StringList> fields) throws WorklogQueryException;

  SearchResultsBeanWithTimespent findWorklogsByIssues(
      FindWorklogsByIssuesParam findWorklogsByIssuesParam)
          throws WorklogQueryException;
}
