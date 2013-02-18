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

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.Base64;

public class WorklogQueryTest {

    public static void main(final String[] args) {
        try {
            WorklogQueryTest.simpleClientTest();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void simpleClientTest() throws Exception {
        String username = "admin";
        String password = "admin";
        String url = "http://127.0.0.1:8080/rest/jira-worklog-query/1.0.0-SNAPSHOT/findWorklogs?startDate=2012-12-12&user=admin&project=TESTTWO";
        System.out.println("Start the simple test");
        final String auth = new String(Base64.encode(username + ":" + password));
        final Client client = Client.create();
        final WebResource webResource = client.resource(url);
        final ClientResponse response = webResource.header("Authorization", "Basic " + auth).type("application/json")
                .accept("application/json").get(ClientResponse.class);
        final int statusCode = response.getStatus();
        if (statusCode == 401) {
            throw new Exception("Invalid Username or Password");
        }
        final String stringResponse = response.getEntity(String.class);
        System.out.println("sr: " + stringResponse);

    }

}
