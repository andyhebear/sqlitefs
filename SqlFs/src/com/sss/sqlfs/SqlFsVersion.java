package com.sss.sqlfs;

public class SqlFsVersion 
{
   private SqlFsVersion() { }
   
   private static final String major = "0";
   private static final String minor = "10";
   private static final String build = "0";
    
   /**
    *  Get SqlFs version
    */
   public static String getSqlFsVersion()
   {
	  return major + "." + minor + "." + build;
   }
}
