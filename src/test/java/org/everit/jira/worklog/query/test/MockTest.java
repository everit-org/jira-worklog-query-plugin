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

import java.io.IOException;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.Executor;

import javax.sql.XADataSource;
import javax.transaction.xa.XAException;
import javax.ws.rs.core.Response;

import org.apache.commons.dbcp2.managed.BasicManagedDataSource;
import org.apache.geronimo.transaction.manager.GeronimoTransactionManager;
import org.everit.jira.worklog.query.plugin.FindWorklogsByIssuesParam;
import org.everit.jira.worklog.query.plugin.IssueBeanWithTimespent;
import org.everit.jira.worklog.query.plugin.SearchResultsBeanWithTimespent;
import org.everit.jira.worklog.query.plugin.WorklogQueryCoreImpl;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.ofbiz.core.entity.config.DatasourceInfo;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.bc.issue.search.SearchService.ParseResult;
import com.atlassian.jira.exception.DataAccessException;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.mock.component.MockComponentWorker;
import com.atlassian.jira.mock.issue.MockIssue;
import com.atlassian.jira.ofbiz.DefaultOfBizConnectionFactory;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.rest.api.util.StringList;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.security.*")
@PrepareForTest({ DefaultOfBizConnectionFactory.class, DatasourceInfo.class, ParseResult.class })
public class MockTest {

  public class SinkConnection implements Connection {

    private Connection conn;

    @Override
    public void abort(final Executor executor) throws SQLException {
      getConnection().abort(executor);
    }

    @Override
    public void clearWarnings() throws SQLException {
      getConnection().clearWarnings();
    }

    @Override
    public void close() throws SQLException {
      getConnection().close();
    }

    @Override
    public void commit() throws SQLException {
      getConnection().commit();
    }

    @Override
    public Array createArrayOf(final String typeName, final Object[] elements) throws SQLException {
      return getConnection().createArrayOf(typeName, elements);
    }

    @Override
    public Blob createBlob() throws SQLException {
      return getConnection().createBlob();
    }

    @Override
    public Clob createClob() throws SQLException {
      return getConnection().createClob();
    }

    @Override
    public NClob createNClob() throws SQLException {
      return getConnection().createNClob();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
      return getConnection().createSQLXML();
    }

    @Override
    public Statement createStatement() throws SQLException {
      return getConnection().createStatement();
    }

    @Override
    public Statement createStatement(final int resultSetType, final int resultSetConcurrency)
        throws SQLException {
      return getConnection().createStatement(resultSetType, resultSetConcurrency);
    }

    @Override
    public Statement createStatement(final int resultSetType, final int resultSetConcurrency,
        final int resultSetHoldability) throws SQLException {
      return getConnection().createStatement(resultSetType, resultSetConcurrency,
          resultSetHoldability);
    }

    @Override
    public Struct createStruct(final String typeName, final Object[] attributes)
        throws SQLException {
      return getConnection().createStruct(typeName, attributes);
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
      return getConnection().getAutoCommit();
    }

    @Override
    public String getCatalog() throws SQLException {
      return getConnection().getCatalog();
    }

    @Override
    public Properties getClientInfo() throws SQLException {
      return getConnection().getClientInfo();
    }

    @Override
    public String getClientInfo(final String name) throws SQLException {
      return getConnection().getClientInfo(name);
    }

    private Connection getConnection() {
      try {
        if ((conn == null) || conn.isClosed()) {
          conn = createXADatasource().getConnection();
        }
      } catch (SQLException e) {
      }
      return conn;
    }

    @Override
    public int getHoldability() throws SQLException {
      return getConnection().getHoldability();
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
      return getConnection().getMetaData();
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
      return getConnection().getNetworkTimeout();
    }

    @Override
    public String getSchema() throws SQLException {
      return getConnection().getSchema();
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
      return getConnection().getTransactionIsolation();
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
      return getConnection().getTypeMap();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
      return getConnection().getWarnings();
    }

    @Override
    public boolean isClosed() throws SQLException {
      return getConnection().isClosed();
    }

    @Override
    public boolean isReadOnly() throws SQLException {
      return getConnection().isReadOnly();
    }

    @Override
    public boolean isValid(final int timeout) throws SQLException {
      return getConnection().isValid(timeout);
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
      return getConnection().isWrapperFor(iface);
    }

    @Override
    public String nativeSQL(final String sql) throws SQLException {
      return getConnection().nativeSQL(sql);
    }

    @Override
    public CallableStatement prepareCall(final String sql) throws SQLException {
      return getConnection().prepareCall(sql);
    }

    @Override
    public CallableStatement prepareCall(final String sql, final int resultSetType,
        final int resultSetConcurrency)
        throws SQLException {
      return getConnection().prepareCall(sql, resultSetType, resultSetConcurrency);
    }

    @Override
    public CallableStatement prepareCall(final String sql, final int resultSetType,
        final int resultSetConcurrency,
        final int resultSetHoldability) throws SQLException {
      return getConnection().prepareCall(sql, resultSetType, resultSetConcurrency,
          resultSetHoldability);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql) throws SQLException {
      return getConnection().prepareStatement(sql);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int autoGeneratedKeys)
        throws SQLException {
      return getConnection().prepareStatement(sql, autoGeneratedKeys);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType,
        final int resultSetConcurrency) throws SQLException {
      return getConnection().prepareStatement(sql, resultSetType, resultSetConcurrency);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType,
        final int resultSetConcurrency, final int resultSetHoldability) throws SQLException {
      return getConnection().prepareCall(sql, resultSetType, resultSetConcurrency,
          resultSetHoldability);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int[] columnIndexes)
        throws SQLException {
      return getConnection().prepareStatement(sql, columnIndexes);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final String[] columnNames)
        throws SQLException {
      return getConnection().prepareStatement(sql, columnNames);
    }

    @Override
    public void releaseSavepoint(final Savepoint savepoint) throws SQLException {
      getConnection().releaseSavepoint(savepoint);
    }

    @Override
    public void rollback() throws SQLException {
      getConnection().rollback();
    }

    @Override
    public void rollback(final Savepoint savepoint) throws SQLException {
      getConnection().rollback(savepoint);
    }

    @Override
    public void setAutoCommit(final boolean autoCommit) throws SQLException {
      getConnection().setAutoCommit(autoCommit);
    }

    @Override
    public void setCatalog(final String catalog) throws SQLException {
      getConnection().setCatalog(catalog);
    }

    @Override
    public void setClientInfo(final Properties properties) throws SQLClientInfoException {
      getConnection().setClientInfo(properties);
    }

    @Override
    public void setClientInfo(final String name, final String value) throws SQLClientInfoException {
      getConnection().setClientInfo(name, value);
    }

    @Override
    public void setHoldability(final int holdability) throws SQLException {
      getConnection().setHoldability(holdability);
    }

    @Override
    public void setNetworkTimeout(final Executor executor, final int milliseconds)
        throws SQLException {
      getConnection().setNetworkTimeout(executor, milliseconds);
    }

    @Override
    public void setReadOnly(final boolean readOnly) throws SQLException {
      getConnection().setReadOnly(readOnly);
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
      return getConnection().setSavepoint();
    }

    @Override
    public Savepoint setSavepoint(final String name) throws SQLException {
      return getConnection().setSavepoint(name);
    }

    @Override
    public void setSchema(final String schema) throws SQLException {
      getConnection().setSchema(schema);
    }

    @Override
    public void setTransactionIsolation(final int level) throws SQLException {
      getConnection().setTransactionIsolation(level);
    }

    @Override
    public void setTypeMap(final Map<String, Class<?>> map) throws SQLException {
      getConnection().setTypeMap(map);
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
      return getConnection().unwrap(iface);
    }

  }

  private static final long N_10000 = 10000L;

  private BasicManagedDataSource managedDataSource = null;

  private String TEST_USER = "test-user@everit.biz";

  private WorklogQueryCoreImpl worklogQuery;

  @After
  public void after() throws SQLException {
    DatabaseSupport.dropTables(managedDataSource);

    if (managedDataSource != null) {
      try {
        managedDataSource.close();
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Before
  public void before() throws DataAccessException, SQLException, SearchException {
    GeronimoTransactionManager transactionManager = null;
    try {
      transactionManager = new GeronimoTransactionManager(6000);
    } catch (XAException e) {
      throw new RuntimeException(e);
    }

    managedDataSource = createManagedDataSource(transactionManager, createXADatasource());

    ApplicationUser testUser = mockApplicationUser();
    Project project = mockProject();

    JiraAuthenticationContext jiraAuthenticationContext = mockJiraAuthenticationContext(testUser);

    PermissionManager permissionManager = mockPermissionManager(testUser, project);

    UserManager userManager = mockUserManager(testUser);

    SearchService searchService = Mockito.mock(SearchService.class);
    PowerMockito.mockStatic(ParseResult.class);
    ParseResult parseResult = Mockito.mock(ParseResult.class);
    Mockito.when(searchService.parseQuery(testUser, "")).thenReturn(parseResult);
    Mockito.when(parseResult.isValid()).thenReturn(Boolean.TRUE);

    SearchResults searchResults = Mockito.mock(SearchResults.class);
    List<Issue> issues = new ArrayList<>();
    issues.add(new MockIssue(10000));
    issues.add(new MockIssue(10001));
    issues.add(new MockIssue(10002));
    issues.add(new MockIssue(10003));
    issues.add(new MockIssue(10004));
    Mockito.when(searchResults.getResults()).thenReturn(issues);

    // Mockito.when(
    // searchService.search(ArgumentMatchers.any(ApplicationUser.class),
    // ArgumentMatchers.any(Query.class),
    // ArgumentMatchers.any(PagerFilter.class)))
    Mockito.when(
        searchService.search(ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any()))
        .thenReturn(searchResults);

    mockDefaultOfBizConnectionFactory();

    new MockComponentWorker()
        .addMock(JiraAuthenticationContext.class, jiraAuthenticationContext)
        .addMock(PermissionManager.class, permissionManager)
        .addMock(UserManager.class, userManager)
        .addMock(SearchService.class, searchService)
        .init();

    worklogQuery = new WorklogQueryCoreImpl();
    DatabaseSupport.initializeDatabase(managedDataSource);

    System.setProperty("user.timezone", "UTC");
    TimeZone.setDefault(null);
  }

  private BasicManagedDataSource createManagedDataSource(
      final GeronimoTransactionManager transactionManager, final XADataSource xaDataSource) {
    BasicManagedDataSource lManagedDataSource = new BasicManagedDataSource();
    lManagedDataSource.setTransactionManager(transactionManager);
    lManagedDataSource.setXaDataSourceInstance(xaDataSource);
    return lManagedDataSource;
  }

  private JdbcDataSource createXADatasource() {
    JdbcDataSource xaDatasource = new JdbcDataSource();
    xaDatasource.setURL("jdbc:h2:mem:test");
    // xaDatasource.setURL("jdbc:h2:tcp://localhost:9092/~/test");
    xaDatasource.setUser("sa");
    xaDatasource.setPassword("");
    return xaDatasource;
  }

  private Properties loadExpectedResultProperties() throws IOException {
    Properties properties = new Properties();
    properties.load(this.getClass().getResourceAsStream("/expectedResults.properties"));
    return properties;
  }

  private ApplicationUser mockApplicationUser() {
    ApplicationUser testUser = Mockito.mock(ApplicationUser.class);
    Mockito.when(testUser.getKey()).thenReturn(TEST_USER);
    Mockito.when(testUser.getId()).thenReturn(N_10000);
    return testUser;
  }

  private void mockDefaultOfBizConnectionFactory() throws SQLException {
    PowerMockito.mockStatic(DefaultOfBizConnectionFactory.class);
    DefaultOfBizConnectionFactory defaultOfBizConnectionFactory =
        Mockito.mock(DefaultOfBizConnectionFactory.class);
    PowerMockito.when(DefaultOfBizConnectionFactory.getInstance())
        .thenReturn(defaultOfBizConnectionFactory);

    PowerMockito.mockStatic(DatasourceInfo.class);
    DatasourceInfo datasourceInfo = Mockito.mock(DatasourceInfo.class);
    PowerMockito.when(defaultOfBizConnectionFactory.getDatasourceInfo()).thenReturn(datasourceInfo);
    Mockito.when(datasourceInfo.getSchemaName()).thenReturn("PUBLIC");
    Mockito.when(defaultOfBizConnectionFactory.getConnection())
        .thenReturn(new SinkConnection());
  }

  private JiraAuthenticationContext mockJiraAuthenticationContext(final ApplicationUser testUser) {
    JiraAuthenticationContext jiraAuthenticationContext =
        Mockito.mock(JiraAuthenticationContext.class);
    Mockito.when(jiraAuthenticationContext.getLoggedInUser()).thenReturn(testUser);
    return jiraAuthenticationContext;
  }

  private PermissionManager mockPermissionManager(final ApplicationUser testUser,
      final Project project) {
    ArrayList<Project> projects = new ArrayList<>();
    projects.add(project);
    PermissionManager permissionManager = Mockito.mock(PermissionManager.class);
    Mockito.when(permissionManager.getProjects(ProjectPermissions.BROWSE_PROJECTS, testUser))
        .thenReturn(projects);
    return permissionManager;
  }

  private Project mockProject() {
    Project project = Mockito.mock(Project.class);
    Mockito.when(project.getId()).thenReturn(N_10000);
    Mockito.when(project.getKey()).thenReturn("SAM");
    return project;
  }

  private UserManager mockUserManager(final ApplicationUser testUser) {
    UserManager userManager = Mockito.mock(UserManager.class);
    Mockito.when(userManager.getUserByName(TEST_USER)).thenReturn(testUser);
    return userManager;
  }

  @Test
  public void testFindWorklogs() throws IOException {
    Response findWorklogs = worklogQuery.findWorklogs("2016-02-24", "2016-03-12", TEST_USER, "", "",
        new ArrayList<StringList>());
    String json = findWorklogs.getEntity().toString();
    Properties properties = loadExpectedResultProperties();
    Assert.assertEquals(properties.get("findWorklogs"), json);
  }

  @Test
  public void testUpdateWorklogs() throws IOException {
    Response findUpdatedWorklogs =
        worklogQuery.findUpdatedWorklogs("2016-02-24", "2016-03-12", TEST_USER, "", "",
            new ArrayList<StringList>());
    String json = findUpdatedWorklogs.getEntity().toString();
    Properties properties = loadExpectedResultProperties();
    Assert.assertEquals(properties.get("findUpdatedWorklogs"), json);
  }

  @Test
  public void testWorklogsByIssues() throws IOException {
    List<StringList> fields = new ArrayList<>();
    fields.add(StringList.fromList("emptyFieldValue"));
    FindWorklogsByIssuesParam findWorklogsByIssuesParam = new FindWorklogsByIssuesParam()
        .startDate("2016-02-24")
        .endDate("2016-03-12")
        .user(TEST_USER)
        .jql("")
        .startAt(0)
        .maxResults(25)
        .fields(fields);
    SearchResultsBeanWithTimespent findWorklogsByIssues =
        worklogQuery.findWorklogsByIssues(findWorklogsByIssuesParam);

    Assert.assertEquals(3, findWorklogsByIssues.total.intValue());
    List<IssueBeanWithTimespent> issues = findWorklogsByIssues.getIssues();
    Assert.assertEquals("10000", issues.get(0).getId());
    Assert.assertEquals("SAM-1", issues.get(0).getKey());
    Assert.assertEquals("10001", issues.get(1).getId());
    Assert.assertEquals("SAM-2", issues.get(1).getKey());
    Assert.assertEquals("10003", issues.get(2).getId());
    Assert.assertEquals("SAM-4", issues.get(2).getKey());
  }
}
