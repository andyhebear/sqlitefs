package com.sss.sqlfs.helper;

import java.util.Calendar;
import java.util.TimeZone;

public class TimeConverter 
{
	private static final long TICKS_PERDAY = 864000000000L;
    private static final long TICKS_PERHOUR = 36000000000L;
    private static final long TICKS_PERMIN = 600000000;
    private static final long TICKS_PERSEC = 10000000;
    private static final long TICKS_PERMILLISEC = 10000;
    
	/**
	 * FileTime is number of 100 nano seconds since 1601 Jan, 1
	 */
	public static long calendarToFileTime(Calendar calendar)
	{
		Calendar cal = (Calendar)calendar.clone();
		cal.setTimeZone(TimeZone.getTimeZone("GMT+00:00")); // make sure it is UTC
		
		long ticks = __countDaysByYear(cal.get(Calendar.YEAR)) * TICKS_PERDAY;
        ticks += (__countDaysByMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1) * TICKS_PERDAY); // Calendar.MONTH starts from 0
        ticks += ((cal.get(Calendar.DAY_OF_MONTH) - 1) * TICKS_PERDAY);
        ticks += (cal.get(Calendar.HOUR_OF_DAY) * TICKS_PERHOUR);
        ticks += (cal.get(Calendar.MINUTE) * TICKS_PERMIN);
        ticks += (cal.get(Calendar.SECOND) * TICKS_PERSEC);
        ticks += (cal.get(Calendar.MILLISECOND) * TICKS_PERMILLISEC);
        
        return ticks;
	}
	
	public static Calendar fileTimeToCalendar(long fileTimeUtc)
	{
		Calendar epochCal = Calendar.getInstance(TimeZone.getTimeZone("GMT+00:00"));
		epochCal.set(1970, 0, 1, 0, 0, 0); epochCal.set(Calendar.MILLISECOND, 0);
		
		long epochMillisec = (fileTimeUtc - calendarToFileTime(epochCal)) / TICKS_PERMILLISEC;
		
		Calendar retCal = Calendar.getInstance(TimeZone.getTimeZone("GMT+00:00"));
		retCal.setTimeInMillis(epochMillisec);
		retCal.setTimeZone(TimeZone.getDefault());  // change it to system (local) timezone
		
		return retCal;
	}
	

    /**
     *  To count number of days since year 1601
     */
    private static int __countDaysByYear(int year)
    {
       year -= 1601;
       return (year * 365) + (year / 4 - year / 100 + year / 400);
    }

    /**
     *  To count number of days until 'month' since the start of this year
     */
    private static int __countDaysByMonth(int year, int month)
    {
       int nrDays = 0;
       for (int i = 1; i < month; ++i)
          nrDays += daysInMonth(year, i);
       return nrDays;
    }

    private static boolean isLeapYear(int year)
    {
       boolean isLeapYear = false;

       if (year % 4 == 0) {
          if (year % 100 == 0) {
             if (year % 400 == 0) {
                isLeapYear = true;
             }
          }
          else {
             isLeapYear = true;
          }
       }

       return isLeapYear;
    }

    /**
     *  Number of days in a month
     */
    private static int daysInMonth(int year, int month)
    {
       switch (month) {
          case 1:
          case 3:
          case 5:
          case 7:
          case 8:
          case 10:
          case 12:
             return 31;
          case 4:
          case 6:
          case 9:
          case 11:
             return 30;
          case 2:
             return isLeapYear(year) ? 29 : 28;
       }

       return 0;
    }
    
    /**
     * Convert calendar to readable "yyyy-mm-dd HH:MM:SS"
     */
    public static String calendarToReadableDateTime(Calendar cal)
    {
       StringBuilder sb = new StringBuilder(20);
       sb.append(cal.get(Calendar.YEAR)).append('-');
       
       append2Digit(sb, cal.get(Calendar.MONTH) + 1).append('-');
       append2Digit(sb, cal.get(Calendar.DAY_OF_MONTH)).append(' ');
       append2Digit(sb, cal.get(Calendar.HOUR_OF_DAY)).append(':');
       append2Digit(sb, cal.get(Calendar.MINUTE)).append(':');
       append2Digit(sb, cal.get(Calendar.SECOND));
       
       return sb.toString();
    }
    
    private static StringBuilder append2Digit(StringBuilder sb, int n)
    {
       if (n < 10) 
    	   sb.append("0");
       sb.append(n);
       
       return sb;
    }

}
