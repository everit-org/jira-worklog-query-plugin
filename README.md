jira-worklog-query-plugin
=========================

A JIRA plugin to query worklogs and return them in JSON format via an authenticated RESTful service.
There are two main features this plugin provides:

* Querying worklogs.
* Summarize worked time by issues.

##Querying worklogs
**_/rest/jira-worklog-query/1.2.0/find/worklogs_**

Returns worklogs filtered by the given parameters.

|parameter |value       |optional|description|
|----------|------------|--------|-----------|
|startDate |"YYYY-MM-DD"|false   |The result will only contain worklogs after this date|
|endDate   |"YYYY-MM-DD"|true    |The result will only contain worklogs before this date. The default value is the current date.|
|group/user|string      |false   |A JIRA group or user name whose worklogs are queried. One of them is required.|
|project   |string      |true    |The result will only contain worklogs which are logged to this project. By default, all projects worklogs are returned.|
|fields    |string list |true    |A list of additional fields to return. Available fields are: comment, updated.|

Available response representations:

* 200 - application/json 
   Example:
```
	[
	   [
	    {
	      "id": 10204,
	      "startDate": "2014-07-31T08:00:00+0200",
	      "issueKey": "TEST-5",
	      "userId": "admin",
	      "duration": 22440
	    },
	    {
	      "id": 10207,
	      "startDate": "2014-07-31T14:14:00+0200",
	      "issueKey": "TEST-2",
	      "userId": "admin",
	      "duration": 2340
	    }
	  ]
	]
```
* 400 - Returned if there is a problem running the query.
   Example:
```
   Error running search: There is no project matching the given 'project' parameter: NOPROJECT
```
   
##Summarize worked time by issues
**_/rest/jira-worklog-query/1.2.0/find/worklogsByIssues_**

This function queries worklogs - filtered by the given parameters - and summarize them by issues.
The returned issues can be filtered by a JQL expression.
The response are paginated, with default page size of 25 issues. 

|parameter |value       |optional|description|
|----------|------------|--------|-----------|
|startDate |"YYYY-MM-DD"|false   |The result will only contain worklogs after this date|
|endDate   |"YYYY-MM-DD"|false   |The result will only contain worklogs before this date. The default value is the current date.|
|group/user|string      |false   |A JIRA group or user name whose worklogs are queried. One of them is required.|
|jql       |string      |true    |The returned issues are filtered by this JQL expression. By default, all issues are returned.|
|fields    |string list |true    |The list of fields to return for each issue. By default, no additional fields are returned. [More info](https://docs.atlassian.com/jira/REST/latest/#d2e423)|
|startAt   |int         |true    |The index of the first issue to return (0-based)|
|maxResults|int         |true    |The maximum number of issues to return (default is 25).|

Available response representations:

* 200 - application/json 
   Example response with no additional fields parameter:
```
	{
	  "startAt": 0,
	  "maxResults": 25,
	  "total": 2,
	  "issues": [
	    {
	      "id": "13405",
	      "self": "http://localhost:8080/rest/api/2/issue/13405",
	      "key": "TEST-1",
	      "timespent": 1440
	    },
	    {
	      "id": "13406",
	      "self": "http://localhost:8080/rest/api/2/issue/13406",
	      "key": "TEST-2",
	      "timespent": 35760
	    }
	  ]
	}
```


   Example response with "fields=summary,progress" parameter:
	
```
	{
	  "startAt": 0,
	  "maxResults": 25,
	  "total": 1,
	  "issues": [
	    {
	      "id": "13411",
	      "self": "http://localhost:8080/rest/api/2/issue/13411",
	      "key": "NEW-1",
	      "fields": {
	        "progress": {
	          "progress": 84000,
	          "total": 84000,
	          "percent": 100
	        },
	        "summary": "new issue1"
	      },
	      "timespent": 67080
	    }
	  ]
	}
```
* 400 - application/json 
   Returned if there is a problem running the query.
   Example:
```
	   {
	  "errorMessages": [
	    "Cannot parse the 'startDate' parameter: 2013-0ghjg6-25"
	  ],
	  "errors": {
	  }
	}
```

[![Analytics](https://ga-beacon.appspot.com/UA-15041869-4/everit-org/jira-worklog-query-plugin-1.x)](https://github.com/igrigorik/ga-beacon)
