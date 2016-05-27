namespace com.sss.sqlfs
{

	public class SqlFsVersion
	{
	   private SqlFsVersion()
	   {
	   }

	   private const string major = "0";
	   private const string minor = "10";
	   private const string build = "0";

	   /// <summary>
	   ///  Get SqlFs version
	   /// </summary>
	   public static string SqlFsVersion
	   {
		   get
		   {
			  return major + "." + minor + "." + build;
		   }
	   }
	}

}