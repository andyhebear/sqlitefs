using System;

namespace com.sss.sqlfs
{

	using Log = android.util.Log;

	/// <summary>
	///  A class for logging
	/// </summary>
	internal class SqlFsLog
	{
		private SqlFsLog()
		{
		}

		internal static void debug(Exception e)
		{
		   Log.d("SqlFsLog", e.Message);
		}

		internal static void debug(string eMsg)
		{
			Log.d("SqlFsLog", eMsg);
		}
	}

}