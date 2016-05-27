package com.sss.sqlfs;

import android.database.sqlite.SQLiteDatabase;
import com.sss.sqlfs.helper.*;

class SqlFsTransaction implements IDisposable
{
   private SQLiteDatabase db;
   
   SqlFsTransaction(SQLiteDatabase db)
   {
	   this.db = db;
	   this.db.beginTransaction();
   }
   
   void fsOpSuccess()
   {
	   db.setTransactionSuccessful();
   }
	
   public void dispose()
   {
	   db.endTransaction();
   }
}
