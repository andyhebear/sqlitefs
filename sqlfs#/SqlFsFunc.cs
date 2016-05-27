using System;
using System.Text;

namespace com.sss.sqlfs
{


	using com.sss.sqlfs.helper;

	using Cursor = android.database.Cursor;

	/// <summary>
	///  Contains static functions used by any parts of this SqlFs module
	/// </summary>
	internal class SqlFsFunc
	{
	   private SqlFsFunc()
	   {
	   }

	   /// <summary>
	   ///  Use a function to get ID since length of ID may be changed in future
	   /// </summary>
	   internal static FsID getID(Cursor cur, int index)
	   {
		  if (FsID.LongID)
		  {
			 return FsID.toFsID(cur.getLong(index));
		  }
		  return FsID.toFsID(cur.getInt(index));
	   }

	   /// <summary>
	   ///  Convert hex digit to ascii character
	   /// </summary>
	   private static char hex2Ascii(int a)
	   {
		  if (a >= 10)
		  {
			 return (char)(a - 10 + 0x61);
		  }

		  return (char)(a + 0x30);
	   }

	   /// <summary>
	   ///  Generate a hash from a source string
	   /// </summary>
	   internal static string genHash(string strSrc)
	   {
		  string strHash = "";

		  try
		  {
			  MessageDigest md = MessageDigest.getInstance("SHA-1");
			  sbyte[] inBuf = strSrc.GetBytes("UTF-8");
			  md.update(inBuf);
			  sbyte[] finalVal = md.digest();

			  // convert the hash value to string
			  StringBuilder sb = new StringBuilder(finalVal.Length * 2);
			  for (int i = 0; i < finalVal.Length; ++i)
			  {
				 sb.Append(hex2Ascii(((int)finalVal[i] >> 4) & 0x0f));
				 sb.Append(hex2Ascii((int)finalVal[i] & 0x0f));
			  }

			  strHash = sb.ToString();

		  }
		  catch (Exception e)
		  {
			  SqlFsLog.debug(e);
		  }

		  return strHash;
	   }

	   /// <summary>
	   /// Convert Windows filetime to calendar
	   /// </summary>
	   internal static DateTime fileTimeToCal(long fileTimeUtc)
	   {
		  return TimeConverter.fileTimeToCalendar(fileTimeUtc);
	   }

	   /// <summary>
	   /// Convert calendar to Windows filetime
	   /// </summary>
	   internal static long calToFileTime(DateTime cal)
	   {
		  return TimeConverter.calendarToFileTime(cal);
	   }

	   internal static void close(Cursor c)
	   {
		  if (c != null)
		  {
			  c.close();
		  }
	   }

	   internal static bool isNullOrEmpty(string s)
	   {
		  return (s == null || s.Length == 0);
	   }

	   internal static string Trim(string s, char[] charsToTrim)
	   {
		  return __trim(s, charsToTrim, true, true);
	   }

	   internal static string trimEnd(string s, char[] charsToTrim)
	   {
		  return __trim(s, charsToTrim, false, true);
	   }

	   private static string __trim(string s, char[] charsToTrim, bool isTrimStart, bool isTrimEnd)
	   {
		  bool isFound = false;
		  int start = 0, end = s.Length;

		  if (isTrimStart)
		  {
			  // trim front characters
			  for (int i = 0; i < s.Length; ++i)
			  {
				 char c = s[i];
				 isFound = false;
				 foreach (char tempC in charsToTrim)
				 {
					if (tempC == c)
					{
					   isFound = true;
					   break;
					}
				 }

				 if (!isFound)
				 {
					break;
				 }

				 ++start;
			  }
		  }

		  if (isTrimEnd)
		  {
			  // trim rear characters
			  for (int i = s.Length - 1; i > start; --i)
			  {
				 char c = s[i];
				 isFound = false;
				 foreach (char tempC in charsToTrim)
				 {
					if (tempC == c)
					{
					   isFound = true;
					   break;
					}
				 }

				 if (!isFound)
				 {
					break;
				 }

				 --end;
			  }
		  }

		  if (end <= start)
		  {
			  return "";
		  }

		  if (start == 0 && end == s.Length) // no invalid characters
		  {
			  return s;
		  }

		  return s.Substring(start, end - start);
	   }

	   internal static int indexOfAny(string s, char[] charsToFind)
	   {
		  int pos = -1;

		  for (int i = 0; i < s.Length; ++i)
		  {
			 char c = s[i];
			 bool isFound = false;
			 foreach (char tempC in charsToFind)
			 {
				if (tempC == c)
				{
				   isFound = true;
				   break;
				}
			 }

			 if (isFound)
			 {
				pos = i;
				break;
			 }

		  }

		  return pos;
	   }

	}

}