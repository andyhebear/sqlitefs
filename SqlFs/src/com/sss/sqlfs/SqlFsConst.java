package com.sss.sqlfs;

import com.sss.sqlfs.FsID;

public class SqlFsConst 
{
   private SqlFsConst() { }
   
   public enum FSTYPE 
   {
	  UNKNOWN(-1),
      ANY(-1),
      DIR(0),
      FILE(1);
      
      int val;
      
      FSTYPE(int v)
      {
    	 this.val = v;
      }
      
      public int v()
      {
    	 return val;
      }
      
      public static FSTYPE toFSTYPE(int fsType)
      {
    	 for (FSTYPE t : FSTYPE.values()) {
    	    if (t.val == fsType)
    	       return t;
    	 }
    	 
    	 return FSTYPE.UNKNOWN;
      }
      
   };

   enum FSOP
   {
      ADD,
      REPLACE,
      DEL
   };
   
   static final int TRUE = 1;
   static final int FALSE = 0;

   static final int BUFSIZE = 128;

   // root dir
   static final FsID ROOTPARENTID = new FsID((int)0);
   static final FsID ROOTDIRID =  new FsID((int)1);
   static final String ROOTDIRNAME = "___?root?___";

   // invalid ID
   public static final FsID INVALIDID = new FsID((int)0);
   // no file data ID
   public static final FsID NOFILEDATAID = new FsID((int)-1);
   // invalid filename characters
   static final char[] INVALIDCHARS = new char[]{'\\', '/', ':', '*', '?', '"', '<', '>', '|'};
   // characters to trim
   public static final char[] CHARSTOTRIM = new char[]{' ', '\r', '\n', '\t'};

   // path separator
   static final char PATHSEP = '/';
   static final String STRPATHSEP = "/";
   // parent dir
   static final String PARENTDIR = "..";
   // current dir
   static final String CURDIR = ".";

   // default fs label
   static final String DEFFSLABEL = "SQLFS";
   

}
