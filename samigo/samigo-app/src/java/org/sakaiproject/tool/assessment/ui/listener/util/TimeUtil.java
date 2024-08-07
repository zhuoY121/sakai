/**********************************************************************************
 * $URL$
 * $Id$
 ***********************************************************************************
 *
 * Copyright (c) 2006, 2008 The Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.opensource.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.tool.assessment.ui.listener.util;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.commons.lang3.StringUtils;
import org.sakaiproject.samigo.util.SamigoConstants;
import org.sakaiproject.time.api.UserTimeService;
import org.sakaiproject.util.ResourceLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.context.support.SpringBeanAutowiringSupport;

import lombok.extern.slf4j.Slf4j;
/**
 * <p>Description: Time conversion utility class</p>
 */
@Slf4j
public class TimeUtil extends SpringBeanAutowiringSupport {

    @Autowired
    @Qualifier("org.sakaiproject.time.api.UserTimeService")
    private UserTimeService userTimeService;

    private TimeZone m_client_timezone;
    private TimeZone m_server_timezone;

    private static final ResourceLoader selectIndexMessages = new ResourceLoader(SamigoConstants.SELECT_INDEX_BUNDLE);

    public TimeUtil() {
        m_client_timezone = userTimeService.getLocalTimeZone();
        m_server_timezone = TimeZone.getDefault();
    }

  /**
   *  @deprecated use {@link org.sakaiproject.time.api.UserTimeService} instead
   * This will return a formatted date/time without adjustment for client time zone.
   * If instructor is located in Michigan and teaches on Sakai based in Chicago,
   * @param ndf
   * @param serverDate
   * @return
   */
@Deprecated
public String getDisplayDateTime(SimpleDateFormat ndf, Date serverDate) {
     //we can't format a null date
    if (serverDate == null) {
      return "";
    }
    
    try {
      return ndf.format(serverDate);
    }
    catch (RuntimeException e){
      log.warn("can not format the Date to a string", e);
    }
    return "";
  }

  /**
   * SAM-2323: this is useful for simple sorting by jQuery tablesorter plugin
   * In USA, I expect a date like 2015-02-15 04:00pm
   * In Sweden, I expect a date like 2015-02-15 16:00
   * @param dateToConvert
   * @return
   	*/
public String getIsoDateWithLocalTime(Date dateToConvert) {
      if (dateToConvert == null) {
          return null;
      }
      
      LocalDateTime i = LocalDateTime.ofInstant(dateToConvert.toInstant(), ZoneOffset.UTC);
      DateTimeFormatter fmDate = DateTimeFormatter.ISO_LOCAL_DATE;
      Locale locale = new ResourceLoader().getLocale();
      DateTimeFormatter fmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale);
      
      
         // If the client browser is in a different timezone than server, need to modify date
         if (m_client_timezone !=null && m_server_timezone!=null && !m_client_timezone.hasSameRules(m_server_timezone)) {
           ZoneId dateTimeZone = m_client_timezone.toZoneId();
           fmt = fmt.withZone(dateTimeZone);
           fmDate = fmDate.withZone(dateTimeZone);
         }
         
         return i.format(fmDate) + " " + i.format(fmt);
  }

  /**
 * @param dateToConvert
 * @return
 */
public String getDateTimeWithTimezoneConversion(Date dateToConvert) {
      if (dateToConvert == null) {
          return null;
      }
      LocalDateTime dt = LocalDateTime.ofInstant(dateToConvert.toInstant(), ZoneOffset.UTC);
      
      Locale locale = new ResourceLoader().getLocale();
      DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE.withLocale(locale);
      DateTimeFormatter fmtTime = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(locale);

      // If the client browser is in a different timezone than server, need to modify date
      if (m_client_timezone !=null && m_server_timezone!=null && !m_client_timezone.hasSameRules(m_server_timezone)) {
        ZoneId dateTimeZone = m_client_timezone.toZoneId();
        fmt = fmt.withZone(dateTimeZone);
        fmtTime = fmtTime.withZone(dateTimeZone);
      }
      return dt.format(fmt) + " " + dt.format(fmtTime);
  }

  /**
   * User could be in a different timezone and modifying dates in the date picker.
   * We need to take the user date and convert back to server time zone for storage in database.
   * @param dateString
   * @return
   */
public Date parseISO8601String(final String dateString) {
	  if (StringUtils.isBlank(dateString)) {
		  return null;
	  }

	  try {
		  // Hidden field from the datepicker will look like: 2015-02-19T02:25:00-06:00
		  // But that timezone offset is the client browser time zone offset (not necessarily their preferred time zone).
		  // So bring in the time as LocalDateTime and then do the zone manipulation later.
		  // 2015-02-19T02:25:00 = 19 characters
		  final String localDateString = StringUtils.left(dateString, 19);
		  LocalDateTime ldt = LocalDateTime.parse(localDateString);
		  log.debug("parseISO8601String: string={}, localdate={}", dateString, ldt.toString());

		  if (ldt != null && m_client_timezone != null && m_server_timezone != null && !m_client_timezone.hasSameRules(m_server_timezone)) {
			  ZonedDateTime zdt = ldt.atZone(m_client_timezone.toZoneId());
			  log.debug("parseISO8601String: original={}, zoned={}", dateString, zdt.toString());
			  return Date.from(zdt.toInstant());
		  }
		  else if (ldt != null && m_server_timezone != null) {
			  ZonedDateTime zdt = ldt.atZone(m_server_timezone.toZoneId());
			  return Date.from(zdt.toInstant());
		  }
		  else if (ldt != null) {
			  return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
		  }
	  } catch (Exception e) {
		  log.error("parseISO8601String could not parse: {}", dateString);
	  }

	  return null;
  }

  /**
   * Get the time in seconds and transform into a formatted Time
   *
   * @param seconds The time in seconds
   * @return timeElapsedInString - formatted Time
   */
  public static String getFormattedTime(int seconds) {
    String timeElapsedInString = "";
    if ( ((int) seconds) > 0 ) {
      int hr = ((int) seconds) / 3600;
      int min = (((int) seconds) % 3600)/60;
      int sec = (((int) seconds) % 3600)%60;
      timeElapsedInString = "";
      if (hr > 0) timeElapsedInString += hr + " " + selectIndexMessages.getString("hour") + " ";
      if (min > 0) timeElapsedInString += min + " " + selectIndexMessages.getString("minutes") + " ";
      if (sec > 0) timeElapsedInString += sec + " " + selectIndexMessages.getString("seconds") + " ";
    }
    return timeElapsedInString;
  }
 
}
