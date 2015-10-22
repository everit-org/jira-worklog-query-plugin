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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.Base64;

/**
 * The WorklogQueryTest class help test the plugin authorization and the plugin query.
 */
public final class WorklogQueryTest {
  /**
   * The logger used to log.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(WorklogQueryTest.class);

  /**
   * The status code of the unsuccessful authorization.
   */
  public static final int INVALID_AUTHOR_STATUS = 401;
  /**
   * The user name for authentication.
   */
  public static final String USERNAME = "admin";
  /**
   * The password for authentication.
   */
  public static final String PASSWORD = "admin_ps";

  /**
   * The WorklogQueryTest class main method.
   *
   * @param args
   *          The main args.
   */
  public static void main(final String[] args) {
    try {
      WorklogQueryTest.simpleClientTest();
      WorklogQueryTest.simpleClientUpdateTest();
    } catch (Exception e) {
      LOGGER.error("Fail to test jira-worklog-query", e);
    }
  }

  /**
   * The jira-worklog-query HTTP BASIC AUTHORIZATION test.
   *
   * @throws Exception
   *           If any Exception happen.
   */
  public static void simpleClientTest() throws Exception {
    String url =
        "http://localhost:8080rest/jira-worklog-query/1.1.0/find/"
            + "worklogs?startDate=2012-12-12&user=admin";
    LOGGER.info("Start the simple test");
    byte[] authByteArray = Base64.encode(USERNAME + ":" + PASSWORD);
    String auth = new String(authByteArray, "UTF8");
    Client client = Client.create();
    WebResource webResource = client.resource(url);
    ClientResponse response =
        webResource.header("Authorization", "Basic " + auth).type("application/json")
            .accept("application/json").get(ClientResponse.class);
    int statusCode = response.getStatus();

    if (statusCode == INVALID_AUTHOR_STATUS) {
      throw new Exception("Invalid Username or Password");
    }
    final String stringResponse = response.getEntity(String.class);
    LOGGER.info("sr: " + stringResponse);

  }

  /**
   * The jira-worklog-query HTTP BASIC AUTHORIZATION test.
   *
   * @throws Exception
   *           If any Exception happen.
   */
  public static void simpleClientUpdateTest() throws Exception {
    String url =
        "http://localhost:8080rest/jira-worklog-query/1.1.0/find/"
            + "updatedWorklogs?startDate=2013-04-15&user=admin";
    LOGGER.info("Start the simple test");
    byte[] authByteArray = Base64.encode(USERNAME + ":" + PASSWORD);
    String auth = new String(authByteArray, "UTF8");
    Client client = Client.create();
    WebResource webResource = client.resource(url);
    ClientResponse response =
        webResource.header("Authorization", "Basic " + auth).type("application/json")
            .accept("application/json").get(ClientResponse.class);
    int statusCode = response.getStatus();

    if (statusCode == INVALID_AUTHOR_STATUS) {
      throw new Exception("Invalid Username or Password");
    }
    final String stringResponse = response.getEntity(String.class);
    LOGGER.info("sr: " + stringResponse);

  }

  /**
   * Simple private constructor.
   */
  private WorklogQueryTest() {

  }

}
