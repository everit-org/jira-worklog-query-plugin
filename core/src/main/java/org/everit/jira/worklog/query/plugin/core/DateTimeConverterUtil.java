package org.everit.jira.worklog.query.plugin.core;

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

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * The utility class of date and time conversions.
 */
public final class DateTimeConverterUtil {

    /**
     * The date format of the input parameters.
     */
    private static final String INPUT_DATE_FORMAT = "yyyy-MM-dd";

    /**
     * The date format of JIRA.
     */
    private static final String JIRA_OUTPUT_DATE_TIME_FORMAT = "yyyy-MM-dd hh:mm:ss.s";

    /**
     * The date format of the output.
     */
    private static final String OUTPUT_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";

    /**
     * Convert the date to String ({@value #OUTPUT_DATE_TIME_FORMAT}).
     * 
     * @param date
     *            The Date to convert.
     * @return The result time.
     */
    private static String dateToString(final Date date) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(OUTPUT_DATE_TIME_FORMAT);
        String dateString = simpleDateFormat.format(date);
        return dateString;
    }

    /**
     * Convert String ({@value #INPUT_DATE_FORMAT}) to Calendar.
     * 
     * @param dateString
     *            The String date to convert.
     * @return The result Date.
     * @throws ParseException
     *             If can't parse the date.
     */
    public static Calendar inputStringToCalendar(final String dateString) throws ParseException {
        DateFormat dateFormat = new SimpleDateFormat(INPUT_DATE_FORMAT);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(dateFormat.parse(dateString));
        return calendar;
    }

    /**
     * Set the calendar hour, minute and second value.
     * 
     * @param originalCalendar
     *            The original calendar.
     * @param hourOfDay
     *            The hour of the day to set.
     * @param minute
     *            The minute to set.
     * @param second
     *            The second to set.
     * @return The new calendar object.
     */
    public static Calendar setCalendarHourMinSec(final Calendar originalCalendar, final int hourOfDay,
            final int minute, final int second) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(
                originalCalendar.get(Calendar.YEAR),
                originalCalendar.get(Calendar.MONTH),
                originalCalendar.get(Calendar.DAY_OF_MONTH),
                hourOfDay,
                minute,
                second);
        return calendar;
    }

    /**
     * Format a String date to valid ISO-8601 format String date.
     * 
     * @param dateString
     *            The date.
     * @return The formated String date.
     * @throws ParseException
     *             If cannot parse the String to Date.
     */
    public static String stringDateToISO8601FormatString(final String dateString) throws ParseException {
        DateFormat dateFormat = new SimpleDateFormat(JIRA_OUTPUT_DATE_TIME_FORMAT);
        Date date = dateFormat.parse(dateString);
        return DateTimeConverterUtil.dateToString(date);
    }

    /**
     * Private constructor.
     */
    private DateTimeConverterUtil() {
    }

}
