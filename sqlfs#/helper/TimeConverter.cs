using System;
using System.Text;

namespace com.sss.sqlfs.helper
{


	public class TimeConverter
	{
		private const long TICKS_PERDAY = 864000000000L;
		private const long TICKS_PERHOUR = 36000000000L;
		private const long TICKS_PERMIN = 600000000;
		private const long TICKS_PERSEC = 10000000;
		private const long TICKS_PERMILLISEC = 10000;

		/// <summary>
		/// FileTime is number of 100 nano seconds since 1601 Jan, 1
		/// </summary>
		public static long calendarToFileTime(DateTime calendar)
		{
			DateTime cal = (DateTime)calendar.clone();
			cal.TimeZone = TimeZone.getTimeZone("GMT+00:00"); // make sure it is UTC

			long ticks = __countDaysByYear(cal.Year) * TICKS_PERDAY;
			ticks += (__countDaysByMonth(cal.Year, cal.Month + 1) * TICKS_PERDAY); // Calendar.MONTH starts from 0
			ticks += ((cal.Day - 1) * TICKS_PERDAY);
			ticks += (cal.get(DateTime.HOUR_OF_DAY) * TICKS_PERHOUR);
			ticks += (cal.Minute * TICKS_PERMIN);
			ticks += (cal.Second * TICKS_PERSEC);
			ticks += (cal.Millisecond * TICKS_PERMILLISEC);

			return ticks;
		}

		public static DateTime fileTimeToCalendar(long fileTimeUtc)
		{
			DateTime epochCal = DateTime.getInstance(TimeZone.getTimeZone("GMT+00:00"));
			epochCal = new DateTime(1970, 0, 1, 0, 0, 0);
			epochCal.set(DateTime.MILLISECOND, 0);

			long epochMillisec = (fileTimeUtc - calendarToFileTime(epochCal)) / TICKS_PERMILLISEC;

			DateTime retCal = DateTime.getInstance(TimeZone.getTimeZone("GMT+00:00"));
			retCal.TimeInMillis = epochMillisec;
			retCal.TimeZone = TimeZone.Default; // change it to system (local) timezone

			return retCal;
		}


		/// <summary>
		///  To count number of days since year 1601
		/// </summary>
		private static int __countDaysByYear(int year)
		{
		   year -= 1601;
		   return (year * 365) + (year / 4 - year / 100 + year / 400);
		}

		/// <summary>
		///  To count number of days until 'month' since the start of this year
		/// </summary>
		private static int __countDaysByMonth(int year, int month)
		{
		   int nrDays = 0;
		   for (int i = 1; i < month; ++i)
		   {
			  nrDays += daysInMonth(year, i);
		   }
		   return nrDays;
		}

		private static bool isLeapYear(int year)
		{
		   bool isLeapYear = false;

		   if (year % 4 == 0)
		   {
			  if (year % 100 == 0)
			  {
				 if (year % 400 == 0)
				 {
					isLeapYear = true;
				 }
			  }
			  else
			  {
				 isLeapYear = true;
			  }
		   }

		   return isLeapYear;
		}

		/// <summary>
		///  Number of days in a month
		/// </summary>
		private static int daysInMonth(int year, int month)
		{
		   switch (month)
		   {
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

		/// <summary>
		/// Convert calendar to readable "yyyy-mm-dd HH:MM:SS"
		/// </summary>
		public static string calendarToReadableDateTime(DateTime cal)
		{
		   StringBuilder sb = new StringBuilder(20);
		   sb.Append(cal.Year).Append('-');

		   append2Digit(sb, cal.Month + 1).Append('-');
		   append2Digit(sb, cal.Day).Append(' ');
		   append2Digit(sb, cal.get(DateTime.HOUR_OF_DAY)).Append(':');
		   append2Digit(sb, cal.Minute).Append(':');
		   append2Digit(sb, cal.Second);

		   return sb.ToString();
		}

		private static StringBuilder append2Digit(StringBuilder sb, int n)
		{
		   if (n < 10)
		   {
			   sb.Append("0");
		   }
		   sb.Append(n);

		   return sb;
		}

	}

}