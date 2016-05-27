package com.sss.sqlfs;

import java.security.MessageDigest;
import java.util.Calendar;

import com.sss.sqlfs.helper.*;

import android.database.Cursor;

/**
 *  Contains static functions used by any parts of this SqlFs module
 */
class SqlFsFunc 
{
   private SqlFsFunc() { }
   
   /**
    *  Use a function to get ID since length of ID may be changed in future
    */
   static FsID getID(Cursor cur, int index)
   {
      if (FsID.isLongID())
         return FsID.toFsID(cur.getLong(index));
      return FsID.toFsID(cur.getInt(index));
   }
   
   /**
	*  Convert hex digit to ascii character
	*/
   private static char hex2Ascii(int a)
   {
      if (a >= 10)
         return (char)(a - 10 + 0x61);

      return (char)(a + 0x30);
   }

   /**
	*  Generate a hash from a source string
	*/
   static String genHash(String strSrc) 
   {
	  String strHash = "";
	  
	  try {
          MessageDigest md = MessageDigest.getInstance("SHA-1");
          byte[] inBuf = strSrc.getBytes("UTF-8");
          md.update(inBuf);
          byte[] finalVal = md.digest();

          // convert the hash value to string
          StringBuilder sb = new StringBuilder(finalVal.length * 2);              
          for (int i = 0; i < finalVal.length; ++i) {
             sb.append(hex2Ascii(((int)finalVal[i] >> 4) & 0x0f));
             sb.append(hex2Ascii((int)finalVal[i] & 0x0f));
          }

          strHash = sb.toString();
          
      }
      catch(Exception e) {
          SqlFsLog.debug(e);
      }

      return strHash;
   }
   
   /**
    * Convert Windows filetime to calendar
    */
   static Calendar fileTimeToCal(long fileTimeUtc)
   {
	  return TimeConverter.fileTimeToCalendar(fileTimeUtc);
   }
   
   /**
    * Convert calendar to Windows filetime
    */
   static long calToFileTime(Calendar cal)
   {
	  return TimeConverter.calendarToFileTime(cal);
   }
   
   static void close(Cursor c)
   {
	  if (c != null)
		  c.close();
   }
   
   static boolean isNullOrEmpty(String s)
   {
	  return (s == null || s.length() == 0);
   }
   
   static String trim(String s, char[] charsToTrim)
   {
	  return __trim(s, charsToTrim, true, true);
   }
   
   static String trimEnd(String s, char[] charsToTrim)
   {
	  return __trim(s, charsToTrim, false, true);
   }
   
   private static String __trim(String s, char[] charsToTrim, boolean isTrimStart, boolean isTrimEnd)
   {
	  boolean isFound = false;
	  int start = 0, end = s.length();
	  
	  if (isTrimStart) {
		  // trim front characters
		  for (int i = 0; i < s.length(); ++i) {
			 char c = s.charAt(i);
			 isFound = false;
			 for (char tempC : charsToTrim) {
				if (tempC == c) {
				   isFound = true;
				   break;
				}
			 }
			 
			 if (!isFound)
			    break;
			 
			 ++start;
		  }
	  }
	  
	  if (isTrimEnd) {
		  // trim rear characters
		  for (int i = s.length() - 1; i > start; --i) {
			 char c = s.charAt(i);
			 isFound = false;
			 for (char tempC : charsToTrim) {
				if (tempC == c) {
				   isFound = true;
				   break;
				}
			 }
			 
			 if (!isFound)
			    break;
			 
			 --end;
		  }
	  }
	  
	  if (end <= start)
		  return "";
	  
	  if (start == 0 && end == s.length()) // no invalid characters
		  return s;
	  
	  return s.substring(start, end);
   }
   
   static int indexOfAny(String s, char[] charsToFind)
   {
	  int pos = -1;
	  
	  for (int i = 0; i < s.length(); ++i) {
		 char c = s.charAt(i);
		 boolean isFound = false;
		 for (char tempC : charsToFind) {
			if (tempC == c) {
			   isFound = true;
			   break;
			}
		 }
		 
		 if (isFound) {
			pos = i;
		    break;
		 }
		 
	  }
	  
	  return pos;
   }
   
}
