using System;

namespace com.sss.sqlfs
{

	using SQLiteDatabase = android.database.sqlite.SQLiteDatabase;
	using Cursor = android.database.Cursor;
	using Log = android.util.Log;
	using ContentValues = android.content.ContentValues;

	using FsErr = com.sss.sqlfs.SqlFsErrCode.FsErr;
	using SqlStr = com.sss.sqlfs.helper.SqlStr;

	/// <summary>
	///  abstract class of file data
	/// </summary>
	public abstract class IFileData
	{
		//! data table name
		public const string DTABLENAME = "DataBlock";
		//! required ID column name
		public const string IDCOL = "dID";
		//! ID column type
		public const string IDCOLTYPE = "integer primary key autoincrement";

		/// <summary>
		///  For derived class to return DataBlock table schema
		/// </summary>
		public abstract string[][] ColSchema {get;}

		protected internal virtual FsID getLastInsertID(SQLiteDatabase db)
		{
		   return SqlFs.getLastInsertID(db);
		}

		/// <summary>
		///  Called by SqlFile.GetFileData
		/// </summary>
		internal virtual bool getData(SQLiteDatabase db, FsID dataBlockID)
		{
		   if (!__getData(db, dataBlockID))
		   {
			  SqlFsErrCode.CurrentError = FsErr.GetFileDataErr;
			  return false;
		   }

		   return true;
		}

		/// <summary>
		///  Get data from data block table
		/// </summary>
		///  @param [in] dataBlockID -- the data block ID
		/// </param>
		///  <returns> true if OK </returns>
		///  <returns> false if failed </returns>
		private bool __getData(SQLiteDatabase db, FsID dataBlockID)
		{
			string @where = SqlStr.genWhere(new SqlStr.SqlSimpCond(IFileData.IDCOL, "=", dataBlockID));
			 Cursor c = null;

			try
			{

			   c = db.query(IFileData.DTABLENAME.ToString(), null, @where, null, null, null, null);

			   if (c.moveToFirst())
			   {
				  __getData(c);
			   }
			}
			catch (Exception e)
			{
			  Log.d("IFileData.__getData", e.Message);
			  return false;
			}
			finally
			{
				  if (c != null)
				  {
					  c.close();
				  }
			}

			return true;
		}

		/// <summary>
		/// Overridden by derived class to get data from cursor 
		/// </summary>
		/// <param name="c"> -- cursor queried </param>
		protected internal abstract void __getData(Cursor c);

		/// <summary>
		///  Called by SqlFile.SaveFileData
		/// </summary>
		internal virtual FsID saveData(SQLiteDatabase db, FsID dataBlockID)
		{
		   return __saveData(db, dataBlockID);
		}

		/// 
		/// <summary>
		///  Save data to data block table
		/// </summary>
		///  @param [in] dataBlockID -- the data block ID if exist. Pass 0 if not exist
		/// </param>
		///  <returns> dataBlockID if OK </returns>
		///  <returns> 0 if failed </returns>
		private FsID __saveData(SQLiteDatabase db, FsID dataBlockID)
		{

			ContentValues contValues = __saveData();

			try
			{

				if (dataBlockID.compare(SqlFsConst.INVALIDID) <= 0)
				{

				   // save new data
				   if (db.insert(IFileData.DTABLENAME.ToString(), null, contValues) < 0)
				   {
					  dataBlockID = SqlFsConst.INVALIDID;
				   }
				   else
				   {
					  dataBlockID = getLastInsertID(db);
				   }
				}
				else
				{

				   // update data
				   string @where = SqlStr.genWhere(new SqlStr.SqlSimpCond(IFileData.IDCOL, "=", dataBlockID));
				   if (db.update(IFileData.DTABLENAME.ToString(), contValues, @where, null) == 0)
				   {
					  dataBlockID = SqlFsConst.INVALIDID;
				   }
				}
			}
			catch (Exception e)
			{
			   Log.d("IFileData.__saveData", e.Message);
			   dataBlockID = SqlFsConst.INVALIDID;
			}

			return dataBlockID;
		}

		/// <summary>
		/// Overridden by derived class to return a ContentValues 
		/// to be inserted or updated to DB
		/// 
		/// @return
		/// </summary>
		protected internal abstract ContentValues __saveData();

		/// <summary>
		/// Overridden by derived class to return the number of bytes occupied by the underlying 
		/// file data
		/// </summary>
		public abstract int DataSizeInByte {get;}
	}

}