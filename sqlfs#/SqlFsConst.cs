namespace com.sss.sqlfs
{

	public class SqlFsConst
	{
	   private SqlFsConst()
	   {
	   }

	   public enum FSTYPE
	   {
		  UNKNOWN = -1,
		  ANY = -1,
		  DIR = 0,
		  FILE = 1

//JAVA TO C# CONVERTER TODO TASK: Enums cannot contain fields in .NET:
//		  int val;

//JAVA TO C# CONVERTER TODO TASK: Enums cannot contain methods in .NET:
//		  FSTYPE(int v)
	//	  {
	//		 this.val = v;
	//	  }


//JAVA TO C# CONVERTER TODO TASK: Enums cannot contain methods in .NET:
//		  public static FSTYPE toFSTYPE(int fsType)
	//	  {
	//		 for (FSTYPE t : FSTYPE.values())
	//		 {
	//			if (t.val == fsType)
	//			   return t;
	//		 }
	//
	//		 return FSTYPE.UNKNOWN;
	//	  }

	   }
	public static partial class EnumExtensionMethods
	{
		  public static int v(this FSTYPE instance)
		  {
			 return val;
		  }
	}

	   internal enum FSOP
	   {
		  ADD,
		  REPLACE,
		  DEL
	   }

	   internal const int TRUE = 1;
	   internal const int FALSE = 0;

	   internal const int BUFSIZE = 128;

	   // root dir
	   internal static readonly FsID ROOTPARENTID = new FsID((int)0);
	   internal static readonly FsID ROOTDIRID = new FsID((int)1);
	   internal const string ROOTDIRNAME = "___?root?___";

	   // invalid ID
	   public static readonly FsID INVALIDID = new FsID((int)0);
	   // no file data ID
	   public static readonly FsID NOFILEDATAID = new FsID((int)-1);
	   // invalid filename characters
	   internal static readonly char[] INVALIDCHARS = new char[]{'\\', '/', ':', '*', '?', '"', '<', '>', '|'};
	   // characters to trim
	   public static readonly char[] CHARSTOTRIM = new char[]{' ', '\r', '\n', '\t'};

	   // path separator
	   internal const char PATHSEP = '/';
	   internal const string STRPATHSEP = "/";
	   // parent dir
	   internal const string PARENTDIR = "..";
	   // current dir
	   internal const string CURDIR = ".";

	   // default fs label
	   internal const string DEFFSLABEL = "SQLFS";


	}

}