package org.everit.jira.worklog.query.test;

/*
 * Copyright (c) 2013, Everit Kft.
 *
 * All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */

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
    public static final String PASSWORD = "admin";

    /**
     * The WorklogQueryTest class main method.
     * 
     * @param args
     *            The main args.
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
     *             If any Exception happen.
     */
    public static void simpleClientTest() throws Exception {
        String url =
                "http://localhost:8080/"
                        + "rest/jira-worklog-query/1.1.0/"
                        + "find/"
                        + "worklogs?startDate=2012-12-12&user=admin";
        LOGGER.info("Start the simple test");
        byte[] authByteArray = Base64.encode(USERNAME + ":" + PASSWORD);
        String auth = new String(authByteArray, "UTF8");
        Client client = Client.create();
        WebResource webResource = client.resource(url);
        ClientResponse response = webResource.header("Authorization", "Basic " + auth).type("application/json")
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
     *             If any Exception happen.
     */
    public static void simpleClientUpdateTest() throws Exception {
        String url =
                "http://localhost:8080/"
                        + "rest/jira-worklog-query/1.1.0/"
                        + "find/"
                        + "updatedWorklogs?startDate=2013-04-15&user=admin";
        LOGGER.info("Start the simple test");
        byte[] authByteArray = Base64.encode(USERNAME + ":" + PASSWORD);
        String auth = new String(authByteArray, "UTF8");
        Client client = Client.create();
        WebResource webResource = client.resource(url);
        ClientResponse response = webResource.header("Authorization", "Basic " + auth).type("application/json")
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
