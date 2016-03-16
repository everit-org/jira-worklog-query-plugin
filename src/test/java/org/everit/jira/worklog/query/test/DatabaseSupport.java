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
package org.everit.jira.worklog.query.test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

public final class DatabaseSupport {

  private static void createAppUserTable(final Statement createStatement) throws SQLException {
    createStatement.execute("CREATE TABLE \"PUBLIC\".APP_USER ("
        + " ID BIGINT NOT NULL,"
        + " USER_KEY VARCHAR(2147483647),"
        + " LOWER_USER_NAME VARCHAR(2147483647),"
        + " CONSTRAINT PK_APP_USER PRIMARY KEY (ID)"
        + " );");
  }

  private static void createCwdUserTable(final Statement createStatement) throws SQLException {
    createStatement.execute("CREATE TABLE \"PUBLIC\".CWD_USER ("
        + " ID BIGINT NOT NULL,"
        + " DIRECTORY_ID BIGINT,"
        + " USER_NAME VARCHAR(2147483647),"
        + " LOWER_USER_NAME VARCHAR(2147483647),"
        + " ACTIVE INTEGER,"
        + " CREATED_DATE TIMESTAMP,"
        + " UPDATED_DATE TIMESTAMP,"
        + " FIRST_NAME VARCHAR(2147483647),"
        + " LOWER_FIRST_NAME VARCHAR(2147483647),"
        + " LAST_NAME VARCHAR(2147483647),"
        + " LOWER_LAST_NAME VARCHAR(2147483647),"
        + " DISPLAY_NAME VARCHAR(2147483647),"
        + " LOWER_DISPLAY_NAME VARCHAR(2147483647),"
        + " EMAIL_ADDRESS VARCHAR(2147483647),"
        + " LOWER_EMAIL_ADDRESS VARCHAR(2147483647),"
        + " CREDENTIAL VARCHAR(2147483647),"
        + " DELETED_EXTERNALLY INTEGER,"
        + " EXTERNAL_ID VARCHAR(2147483647),"
        + " CONSTRAINT PK_CWD_USER PRIMARY KEY (ID)"
        + " );");
  }

  private static void createJiraIssueTable(final Statement createStatement) throws SQLException {
    createStatement.execute("CREATE TABLE \"PUBLIC\".JIRAISSUE ("
        + " ID BIGINT NOT NULL,"
        + " PKEY VARCHAR(2147483647),"
        + " ISSUENUM BIGINT,"
        + " PROJECT BIGINT,"
        + " REPORTER VARCHAR(2147483647),"
        + " ASSIGNEE VARCHAR(2147483647),"
        + " CREATOR VARCHAR(2147483647),"
        + " ISSUETYPE VARCHAR(2147483647),"
        + " SUMMARY VARCHAR(2147483647),"
        + " DESCRIPTION VARCHAR(2147483647),"
        + " ENVIRONMENT VARCHAR(2147483647),"
        + " PRIORITY VARCHAR(2147483647),"
        + " RESOLUTION VARCHAR(2147483647),"
        + " ISSUESTATUS VARCHAR(2147483647),"
        + " CREATED TIMESTAMP,"
        + " UPDATED TIMESTAMP,"
        + " DUEDATE TIMESTAMP,"
        + " RESOLUTIONDATE TIMESTAMP,"
        + " VOTES BIGINT,"
        + " WATCHES BIGINT,"
        + " TIMEORIGINALESTIMATE BIGINT,"
        + " TIMEESTIMATE BIGINT,"
        + " TIMESPENT BIGINT,"
        + " WORKFLOW_ID BIGINT,"
        + " \"SECURITY\" BIGINT,"
        + " FIXFOR BIGINT,"
        + " COMPONENT BIGINT,"
        + " CONSTRAINT PK_JIRAISSUE PRIMARY KEY (ID)"
        + " );");
  }

  private static void createProjectTable(final Statement createStatement) throws SQLException {
    createStatement.execute("CREATE TABLE \"PUBLIC\".PROJECT ("
        + " ID BIGINT NOT NULL,"
        + " PNAME VARCHAR(2147483647),"
        + " URL VARCHAR(2147483647),"
        + " LEAD VARCHAR(2147483647),"
        + " DESCRIPTION VARCHAR(2147483647),"
        + " PKEY VARCHAR(2147483647),"
        + " PCOUNTER BIGINT,"
        + " ASSIGNEETYPE BIGINT,"
        + " AVATAR BIGINT,"
        + " ORIGINALKEY VARCHAR(2147483647),"
        + " PROJECTTYPE VARCHAR(2147483647),"
        + " CONSTRAINT PK_PROJECT PRIMARY KEY (ID)"
        + " );");
  }

  private static void createWorklogTable(final Statement createStatement) throws SQLException {
    createStatement.execute("CREATE TABLE \"PUBLIC\".WORKLOG ("
        + " ID BIGINT NOT NULL,"
        + " ISSUEID BIGINT,"
        + " AUTHOR VARCHAR(2147483647),"
        + " GROUPLEVEL VARCHAR(2147483647),"
        + " ROLELEVEL BIGINT,"
        + " WORKLOGBODY VARCHAR(2147483647),"
        + " CREATED TIMESTAMP,"
        + " UPDATEAUTHOR VARCHAR(2147483647),"
        + " UPDATED TIMESTAMP,"
        + " STARTDATE TIMESTAMP,"
        + " TIMEWORKED BIGINT,"
        + " CONSTRAINT PK_WORKLOG PRIMARY KEY (ID)"
        + " );");
  }

  public static void dropTables(final DataSource datasource) throws SQLException {
    try (Connection connection = datasource.getConnection();
        Statement createStatement = connection.createStatement();) {
      createStatement.execute("DROP TABLE \"PUBLIC\".WORKLOG");
      createStatement.execute("DROP TABLE \"PUBLIC\".PROJECT");
      createStatement.execute("DROP TABLE \"PUBLIC\".JIRAISSUE");
      createStatement.execute("DROP TABLE \"PUBLIC\".CWD_USER");
      createStatement.execute("DROP TABLE \"PUBLIC\".APP_USER");
    }
  }

  public static void initializeDatabase(final DataSource datasource) throws SQLException {
    try (Connection connection = datasource.getConnection();
        Statement createStatement = connection.createStatement();) {
      DatabaseSupport.createWorklogTable(createStatement);

      DatabaseSupport.createJiraIssueTable(createStatement);

      DatabaseSupport.createProjectTable(createStatement);

      DatabaseSupport.createCwdUserTable(createStatement);

      DatabaseSupport.createAppUserTable(createStatement);

      DatabaseSupport.insertJiraIssueRows(createStatement);

      DatabaseSupport.insertWorklogRows(createStatement);

      DatabaseSupport.insertProjectRows(createStatement);

      DatabaseSupport.insertCwdUserRows(createStatement);

      DatabaseSupport.insertAppUserRows(createStatement);
    }
  }

  private static void insertAppUserRows(final Statement createStatement) throws SQLException {
    createStatement.execute("INSERT INTO app_user VALUES (10000, "
        + "'test-user@everit.biz', 'test-user@everit.biz');");
  }

  private static void insertCwdUserRows(final Statement createStatement) throws SQLException {
    createStatement.execute(
        "INSERT INTO cwd_user VALUES (10000, 1, 'test-user@everit.biz', "
            + "'test-user@everit.biz', 1, '2016-03-07 14:06:16.89+01', "
            + "'2016-03-07 14:06:16.89+01', 'Zsigmond', 'zsigmond', 'Czine', 'czine', "
            + "'Zsigmond Czine', 'zsigmond czine', 'test-user@everit.biz', "
            + "'test-user@everit.biz', "
            + "'asdf', NULL, "
            + "'7c717a76-20bc-4d80-9087-a073e057cf78');");
  }

  private static void insertJiraIssueRows(final Statement createStatement) throws SQLException {
    createStatement.execute(
        "INSERT INTO jiraissue VALUES (10002, NULL, 3, 10000, 'test-user@everit.biz', NULL, "
            + "'test-user@everit.biz', '10001', 'harom', NULL, NULL, '3', NULL, '10000', "
            + "'2016-03-07 14:07:41.156+01', '2016-03-07 14:07:41.156+01', NULL, NULL, 0, 1,"
            + " NULL, NULL, NULL, 10002, NULL, NULL, NULL);");
    createStatement.execute(
        "INSERT INTO jiraissue VALUES (10001, NULL, 2, 10000, 'test-user@everit.biz', NULL, "
            + "'test-user@everit.biz', '10001', 'ketto', NULL, NULL, '3', NULL, '10000', "
            + "'2016-03-07 14:07:38.683+01', '2016-03-07 14:08:09.022+01', NULL, NULL, 0, 1, "
            + "NULL, 0, 22020, 10001, NULL, NULL, NULL);");
    createStatement.execute(
        "INSERT INTO jiraissue VALUES (10003, NULL, 4, 10000, 'test-user@everit.biz', NULL, "
            + "'test-user@everit.biz', '10001', 'negy', NULL, NULL, '3', NULL, '10000', "
            + "'2016-03-07 14:07:43.854+01', '2016-03-07 14:08:24.627+01', NULL, NULL, 0, 1, "
            + "NULL, 0, 22080, 10003, NULL, NULL, NULL);");
    createStatement.execute(
        "INSERT INTO jiraissue VALUES (10004, NULL, 5, 10000, 'test-user@everit.biz', NULL, "
            + "'test-user@everit.biz', '10001', 'ot', NULL, NULL, '3', NULL, '10000', "
            + "'2016-03-07 14:07:45.756+01', '2016-03-07 14:08:33.903+01', NULL, NULL, 0, 1, "
            + "NULL, 0, 22080, 10004, NULL, NULL, NULL);");
    createStatement.execute(
        "INSERT INTO jiraissue VALUES (10000, NULL, 1, 10000, 'test-user@everit.biz', NULL, "
            + "'test-user@everit.biz', '10001', 'egy', NULL, NULL, '3', NULL, '10000', "
            + "'2016-03-07 14:07:33.368+01', '2016-03-11 10:46:46.357+01', NULL, NULL, 0, 1, "
            + "NULL, 0, 22020, 10000, NULL, NULL, NULL);");
  }

  private static void insertProjectRows(final Statement createStatement) throws SQLException {
    createStatement.execute("INSERT INTO project VALUES (10000, 'SAMPLE', '', "
        + "'test-user@everit.biz', '', 'SAM', 5, 3, 10324, 'SAM', 'software');");
  }

  private static void insertWorklogRows(final Statement createStatement) throws SQLException {
    createStatement.execute(
        "INSERT INTO worklog VALUES (10001, 10001, 'test-user@everit.biz', NULL, NULL, '', "
            + "'2016-03-07 14:08:08.967+01', 'test-user@everit.biz', '2016-03-07 14:08:08.967+01', "
            + "'2016-03-01 08:00:00+01', 22020);");
    createStatement.execute(
        "INSERT INTO worklog VALUES (10002, 10003, 'test-user@everit.biz', NULL, NULL, '', "
            + "'2016-03-07 14:08:24.536+01', 'test-user@everit.biz', '2016-03-07 14:08:24.536+01', "
            + "'2016-02-24 08:00:00+01', 22080);");
    createStatement.execute(
        "INSERT INTO worklog VALUES (10003, 10004, 'test-user@everit.biz', NULL, NULL, '', "
            + "'2016-03-07 14:08:33.899+01', 'test-user@everit.biz', '2016-03-07 14:08:33.899+01', "
            + "'2016-02-23 08:00:00+01', 22080);");
    createStatement.execute(
        "INSERT INTO worklog VALUES (10000, 10000, 'test-user@everit.biz', NULL, NULL, 'asdfasf', "
            + "'2016-03-07 14:07:55.675+01', 'test-user@everit.biz', '2016-03-11 10:46:46.277+01', "
            + "'2016-03-07 08:00:00+01', 22020);");
  }

  private DatabaseSupport() {
    // TODO Auto-generated constructor stub
  }
}
