using System.Collections.Generic;

namespace com.sss.sqlfs
{

	using SQLiteDatabase = android.database.sqlite.SQLiteDatabase;
	using Cursor = android.database.Cursor;

	using FSBLOCK = com.sss.sqlfs.SqlFs.FSBLOCK;
	using FsErr = com.sss.sqlfs.SqlFsErrCode.FsErr;
	using com.sss.sqlfs.helper;

	/// <summary>
	///  a class for file
	/// </summary>
	public class SqlFile : SqlFsNode
	{
		internal static SqlFile getFile(SQLiteDatabase db, SqlFsLocker fsLocker, FsID id)
		{
		   SqlFile f = new SqlFile(db, fsLocker, id);
		   return f;
		}

		internal static SqlFile getFile(SQLiteDatabase db, SqlFsLocker fsLocker, Cursor c)
		{
		   FsID id = SqlFsFunc.getID(c, SqlFs.FSBLOCK.fsID.ordinal());
		   return SqlFile.getFile(db, fsLocker, id);
		}

		/// <summary>
		///  Create an empty file
		/// </summary>
		///  <returns> new ID for the inserted dir </returns>
		internal static FsID addFile(SQLiteDatabase db, string fileName, FsID parentID)
		{
		   return SqlFsNode.addFsNode(db, SqlFsConst.FSTYPE.FILE, fileName, parentID);
		}

		private SqlFile()
		{
		}

		private SqlFile(SQLiteDatabase db, SqlFsLocker fsLocker, FsID id) : base(db, fsLocker, id)
		{
		}

		public override bool Dir
		{
			get
			{
			   return false;
			}
		}

		internal virtual FsID getDataBlockID()
		{
		   List<FsID> dbID = (List<FsID>)getField(SqlFs.FSBLOCK.fsChild);
		   if (dbID != null && dbID.Count > 0)
		   {
			  return dbID[0];
		   }
		   return SqlFsConst.INVALIDID;
		}

		internal virtual void setDataBlockID(FsID value)
		{
		   List<FsID> dbID = new List<FsID>(1);
		   dbID.Add(value);
		   setField(SqlFs.FSBLOCK.fsChild, dbID);
		}

		public override bool delete()
		{
			SqlFsErrCode.CurrentError = FsErr.OK;

			bool isOK = false;
			fsLocker.FsLock;
			SqlFsTransaction fsTran = new SqlFsTransaction(db);
			try
			{
			   isOK = __delete();
			   if (isOK)
			   {
				  fsTran.fsOpSuccess();
			   }
			}
			finally
			{
				fsTran.dispose();
				fsLocker.dispose();
			}

			return isOK;
		}

		private bool __delete()
		{
		   bool isOK = false;

		   do
		   {
			  // delete itself from parent
			  SqlDir parent = this.Parent;
			  if (parent == null)
			  {
				 SqlFsErrCode.CurrentError = FsErr.NoParent;
				 break;
			  }

			  if (!parent.updateChildList(SqlFsConst.FSOP.DEL, this.ID, FsID.toFsID(0)))
			  {
				 break;
			  }

			  // delete entry in data block table
			  FsID dataBlockID = this.getDataBlockID();
			  if (dataBlockID.compare(SqlFsConst.INVALIDID) <= 0)
			  {
	//SqlFsLog.debug("+++ dataBlockID = " + dataBlockID.getVal());
				 SqlFsErrCode.CurrentError = FsErr.DataBlockIDNotValid;
				 break;
			  }

			  if (!SqlFs.deleteEntryByID(db, IFileData.DTABLENAME, IFileData.IDCOL, dataBlockID))
			  {
				 SqlFsErrCode.CurrentError = FsErr.CannotDeleteDataBlockEntry;
				 break;
			  }

			  // delete its own entry
			  if (!SqlFs.deleteEntryByID(db, SqlFs.DBNAMES.FsBlock.ToString(), SqlFs.FSBLOCK.fsID.ToString(), this.ID))
			  {
				 SqlFsErrCode.CurrentError = FsErr.CannotDeleteFsEntry;
				 break;
			  }

			  isOK = true;

		   } while (false);

		   return isOK;
		}

		public virtual bool getFileData(IFileData fileData)
		{
			SqlFsErrCode.CurrentError = FsErr.OK;

			fsLocker.FsLock;
			try
			{
			   return __getFileData(fileData);
			}
			finally
			{
				 fsLocker.dispose();
			}
		}

		/// <summary>
		///  Retrieve data from data block table
		/// </summary>
		private bool __getFileData(IFileData fileData)
		{
		   FsID dataBlockID = this.getDataBlockID();
		   if (dataBlockID.compare(SqlFsConst.INVALIDID) <= 0)
		   {
			  SqlFsErrCode.CurrentError = FsErr.DataBlockIDNotValid;
			  return false;
		   }

		   return fileData.getData(db, dataBlockID);
		}

		public virtual bool saveFileData(IFileData fileData)
		{
			SqlFsErrCode.CurrentError = FsErr.OK;

			bool isOK = false;
			fsLocker.FsLock;
			SqlFsTransaction fsTran = new SqlFsTransaction(db);
			try
			{
			   isOK = __saveFileData(fileData);
			   if (isOK)
			   {
				  fsTran.fsOpSuccess();
			   }
			}
			finally
			{
				fsTran.dispose();
				fsLocker.dispose();
			}

			return isOK;
		}

		/// <summary>
		///  Save data to data block table
		/// </summary>
		private bool __saveFileData(IFileData fileData)
		{
		   bool isOK = false;

		   do
		   {
			   FsID dataBlockID = this.getDataBlockID();
			   if (dataBlockID.compare(SqlFsConst.INVALIDID) == 0) // new file without data will be -1
			   {
				  SqlFsErrCode.CurrentError = FsErr.DataBlockIDNotValid;
				  break;
			   }

			   FsID newDataBlockID = fileData.saveData(db, dataBlockID);
			   if (newDataBlockID.compare(SqlFsConst.INVALIDID) <= 0)
			   {
				  SqlFsErrCode.CurrentError = FsErr.SaveFileDataErr;
				  break;
			   }

			   // update file size
			   this.setField(FSBLOCK.fsFileSize, fileData.DataSizeInByte);

			   // update itself (data block table) so that last mod time can be updated
			   this.setDataBlockID(newDataBlockID);

			   isOK = true;
		   } while (false);

		   return isOK;
		}
	}

}