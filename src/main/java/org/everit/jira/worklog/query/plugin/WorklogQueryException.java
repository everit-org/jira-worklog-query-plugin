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

/**
 * The exception to handle Worklog Query Plugin exceptions.
 */
public class WorklogQueryException extends RuntimeException {

  /**
   * The generated serial version UID.
   */
  private static final long serialVersionUID = -3704417971282723535L;

  /**
   * The simple constructor.
   *
   * @param msg
   *          the detail message.
   */
  protected WorklogQueryException(final String msg) {
    super(msg);
  }

  /**
   * The simple constructor.
   *
   * @param msg
   *          the detail message.
   * @param cause
   *          the cause.
   */
  protected WorklogQueryException(final String msg, final Throwable cause) {
    super(msg, cause);
  }

}
