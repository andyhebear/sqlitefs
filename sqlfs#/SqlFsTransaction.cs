namespace com.sss.sqlfs
{

	using SQLiteDatabase = android.database.sqlite.SQLiteDatabase;
	using com.sss.sqlfs.helper;

	internal class SqlFsTransaction : IDisposable
	{
	   private SQLiteDatabase db;

	   internal SqlFsTransaction(SQLiteDatabase db)
	   {
		   this.db = db;
		   this.db.beginTransaction();
	   }

	   internal virtual void fsOpSuccess()
	   {
		   db.setTransactionSuccessful();
	   }

	   public virtual void dispose()
	   {
		   db.endTransaction();
	   }
	}

}