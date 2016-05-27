using System;
using System.Collections.Generic;

namespace com.sss.sqlfs
{


	using SQLiteDatabase = android.database.sqlite.SQLiteDatabase;
	using Cursor = android.database.Cursor;
	using ContentValues = android.content.ContentValues;

	using FsErr = com.sss.sqlfs.SqlFsErrCode.FsErr;
	using com.sss.sqlfs.helper;

	/// <summary>
	///  base for SqlDir and SqlFile
	/// </summary>
	public abstract class SqlFsNode
	{
		protected internal SQLiteDatabase db; ///< sqlite connection
		protected internal FsID id; ///< ID in FsBlock
		protected internal SqlFsLocker fsLocker; ///< FS lock

		protected internal SqlFsNode()
		{
		}

		protected internal SqlFsNode(SQLiteDatabase db, SqlFsLocker fsLocker, FsID id)
		{
		   this.db = db;
		   this.fsLocker = fsLocker;
		   this.id = id;
		}

		internal virtual FsID ID
		{
			get
			{
			   return this.id;
			}
		}

		public virtual DateTime CreateTime
		{
			get
			{
			   SqlFsErrCode.CurrentError = FsErr.OK;
    
			   long fileTimeUtc = (long?)getField(SqlFs.FSBLOCK.fsCreateTime);
			   return SqlFsFunc.fileTimeToCal(fileTimeUtc);
			}
		}

		public virtual DateTime LastModTime
		{
			get
			{
			   SqlFsErrCode.CurrentError = FsErr.OK;
    
			   long fileTimeUtc = (long?)getField(SqlFs.FSBLOCK.fsLastModTime);
			   return SqlFsFunc.fileTimeToCal(fileTimeUtc);
			}
		}

		public virtual int FileSize
		{
			get
			{
			   SqlFsErrCode.CurrentError = FsErr.OK;
    
			   int fileSize = (int?)getField(SqlFs.FSBLOCK.fsFileSize);
			   return fileSize;
			}
		}

		public virtual string Name
		{
			get
			{
			   SqlFsErrCode.CurrentError = FsErr.OK;
    
			   return (string)getField(SqlFs.FSBLOCK.fsName);
			}
		}

		/// <summary>
		///  SqlFsConst.FSTYPE
		/// </summary>
		public virtual SqlFsConst.FSTYPE Type
		{
			get
			{
			   SqlFsErrCode.CurrentError = FsErr.OK;
    
			   return SqlFsConst.FSTYPE.toFSTYPE((int?)getField(SqlFs.FSBLOCK.fsType));
			}
		}

		public virtual SqlDir Parent
		{
			get
			{
			   SqlFsErrCode.CurrentError = FsErr.OK;
    
			   FsID parentID = (FsID)getField(SqlFs.FSBLOCK.fsParent);
			   return (SqlDir)SqlFs.getFsNodeByID(db, fsLocker, parentID);
			}
		}


		public abstract bool Dir {get;}


		/// <summary>
		///  Convert ID list to byte array (little endian)
		/// </summary>
		private static sbyte[] idList2Blob(List<FsID> idList)
		{
		   if (idList == null || idList.Count == 0)
		   {
			  return null;
		   }

		   int j = 0;
		   sbyte[] blob = new sbyte[idList.Count * FsID.IDSize];
		   sbyte b;

		   foreach (FsID item in idList)
		   {
			  // save integer in blob (little endian)
			  long id = item.Val;
			  for (int i = 0; i < FsID.IDSize; ++i)
			  {
				 b = unchecked((sbyte)((id >> (i * 8)) & 0xff));
				 blob[j++] = b;
			  }
		   }

		   return blob;
		}

		/// <summary>
		///  Convert byte array to ID list (little endian)
		/// </summary>
		private static List<FsID> blob2idList(sbyte[] blob)
		{
		   if (blob == null)
		   {
			  return null;
		   }

		   List<FsID> idList = new List<FsID>(blob.Length / FsID.IDSize);

		   for (int i = 0; i < blob.Length; i += FsID.IDSize)
		   {
			  long t = 0;
			  // get integer from blob (little endian)
			  for (int j = 0; j < FsID.IDSize; ++j)
			  {
				 t |= (((long)blob[i + j] & 0xff) << (j * 8));
			  }

			  idList.Add(new FsID(t));
		   }

		   return idList;
		}


		/// <summary>
		///  Get a fsNode from the result
		/// </summary>
		internal static SqlFsNode getFsNode(SQLiteDatabase db, SqlFsLocker fsLocker, Cursor c)
		{
		   SqlFsNode fsNode = null;
		   SqlFsConst.FSTYPE type = SqlFsConst.FSTYPE.toFSTYPE(c.getInt(SqlFs.FSBLOCK.fsType.ordinal()));

		   if (type == SqlFsConst.FSTYPE.DIR)
		   {
			  fsNode = SqlDir.getDir(db, fsLocker, c);
		   }
		   else if (type == SqlFsConst.FSTYPE.FILE)
		   {
			  fsNode = SqlFile.getFile(db, fsLocker, c);
		   }

		   return fsNode;
		}

		/// <summary>
		///  Create a new entry in FsBlock
		/// </summary>
		///  <returns> new ID for the inserted node </returns>
		internal static FsID addFsNode(SQLiteDatabase db, SqlFsConst.FSTYPE type, string dirName, FsID parentID)
		{
		   long curTime = SqlFsFunc.calToFileTime(new DateTime());
		   List<object> colsAndValues = new List<object>(10);

		   colsAndValues.Add(SqlFs.FSBLOCK.fsCreateTime.ToString());
		   colsAndValues.Add(curTime);
		   colsAndValues.Add(SqlFs.FSBLOCK.fsLastModTime.ToString());
		   colsAndValues.Add(curTime);
		   colsAndValues.Add(SqlFs.FSBLOCK.fsFileSize.ToString());
		   colsAndValues.Add(0);
		   colsAndValues.Add(SqlFs.FSBLOCK.fsType.ToString());
		   colsAndValues.Add(type.v());
		   colsAndValues.Add(SqlFs.FSBLOCK.fsName.ToString());
		   colsAndValues.Add(dirName);
		   colsAndValues.Add(SqlFs.FSBLOCK.fsParent.ToString());
		   colsAndValues.Add(parentID);

		   ContentValues contValues = SqlStr.genContentValues(colsAndValues);

		   try
		   {
			  db.insert(SqlFs.DBNAMES.FsBlock.ToString(), null, contValues);
		   }
		   catch (Exception e)
		   {
			  SqlFsLog.debug(e);
			  SqlFsErrCode.CurrentError = FsErr.AddFsNodeError;
			  return SqlFsConst.INVALIDID;
		   }

		   // retrieve the ID of the new entry
		   return SqlFs.getLastInsertID(db);
		}

		protected internal virtual object getField(SqlFs.FSBLOCK field)
		{
		   fsLocker.FsLock;
		   try
		   {
			  return __getField(field);
		   }
		   finally
		   {
			  fsLocker.dispose();
		   }
		}


		/// <summary>
		///  Get a field from DB using ID
		/// </summary>
		private object __getField(SqlFs.FSBLOCK field)
		{
			string @where = SqlStr.genWhere(new SqlStr.SqlSimpCond(SqlFs.FSBLOCK.fsID.ToString(), "=", this.ID));
			// little adjustment
			SqlFs.FSBLOCK tField = (field == SqlFs.FSBLOCK.fsChildCount) ? SqlFs.FSBLOCK.fsChild : field;
			Cursor c = null;
			object val = null;

			try
			{
				c = db.query(SqlFs.DBNAMES.FsBlock.ToString(), new string[]{tField.ToString()}, @where, null, null, null, null);

				if (c.moveToFirst() && !c.isNull(0))
				{
					switch (field)
					{
					 case com.sss.sqlfs.SqlFs.FSBLOCK.fsCreateTime:
					 case com.sss.sqlfs.SqlFs.FSBLOCK.fsLastModTime:
						val = c.getLong(0);
						break;
					 case com.sss.sqlfs.SqlFs.FSBLOCK.fsFileSize:
					 case com.sss.sqlfs.SqlFs.FSBLOCK.fsType:
						val = c.getInt(0);
						break;
					 case com.sss.sqlfs.SqlFs.FSBLOCK.fsName:
						val = c.getString(0);
						break;
					 case com.sss.sqlfs.SqlFs.FSBLOCK.fsParent:
						val = SqlFsFunc.getID(c, 0);
						break;
					 case com.sss.sqlfs.SqlFs.FSBLOCK.fsChild:
					 {
						   sbyte[] buf = c.getBlob(0);
						   val = blob2idList(buf);
					 }
						break;
					 case com.sss.sqlfs.SqlFs.FSBLOCK.fsChildCount:
					 {
						   sbyte[] buf = c.getBlob(0);
						   val = buf.Length / FsID.IDSize;
					 }
						break;
					}
				}
				else
				{
					val = __getDefaultValue(field);
				}
			}
			catch (Exception e)
			{
				SqlFsLog.debug(e);
				SqlFsErrCode.CurrentError = FsErr.GetFieldError;
				val = __getDefaultValue(field); // return a default value here
			}
			finally
			{
				SqlFsFunc.close(c);
			}

			return val;
		}

		private object __getDefaultValue(SqlFs.FSBLOCK field)
		{
			object val = null;

			// set default values
			switch (field)
			{
			   case com.sss.sqlfs.SqlFs.FSBLOCK.fsCreateTime:
			   case com.sss.sqlfs.SqlFs.FSBLOCK.fsLastModTime:
				  val = (long)0;
				  break;
			   case com.sss.sqlfs.SqlFs.FSBLOCK.fsFileSize:
			   case com.sss.sqlfs.SqlFs.FSBLOCK.fsType:
				  val = (int)0;
				  break;
			   case com.sss.sqlfs.SqlFs.FSBLOCK.fsName:
				  val = "";
				  break;
			   case com.sss.sqlfs.SqlFs.FSBLOCK.fsParent:
				  val = SqlFsConst.INVALIDID;
				  break;
			   case com.sss.sqlfs.SqlFs.FSBLOCK.fsChildCount:
				  val = (int)0;
				  break;
			}

			return val;
		}

		protected internal virtual bool setField(SqlFs.FSBLOCK field, object val)
		{
			fsLocker.FsLock;
			try
			{
			   return __setField(field, val);
			}
			finally
			{
				 fsLocker.dispose();
			}
		}

		/// <summary>
		///  Save field to DB using ID
		/// </summary>
		private bool __setField(SqlFs.FSBLOCK field, object val)
		{
			List<object> colsAndValues = new List<object>(4);

			switch (field)
			{
			  case com.sss.sqlfs.SqlFs.FSBLOCK.fsFileSize:
			  case com.sss.sqlfs.SqlFs.FSBLOCK.fsType:
			  case com.sss.sqlfs.SqlFs.FSBLOCK.fsName:
			  case com.sss.sqlfs.SqlFs.FSBLOCK.fsParent:
				 colsAndValues.Add(field.ToString());
				 colsAndValues.Add(val);
				 break;
			  case com.sss.sqlfs.SqlFs.FSBLOCK.fsChild:
				 sbyte[] blob = idList2Blob((List<FsID>)val);
				 colsAndValues.Add(field.ToString());
				 colsAndValues.Add(blob);
				 break;
			}

			// update last mod time as well
			colsAndValues.Add(SqlFs.FSBLOCK.fsLastModTime.ToString());
			colsAndValues.Add(SqlFsFunc.calToFileTime(new DateTime()));
			ContentValues contValues = SqlStr.genContentValues(colsAndValues);

			string @where = SqlStr.genWhere(new SqlStr.SqlSimpCond(SqlFs.FSBLOCK.fsID.ToString(), "=", this.ID));

			int rowAffected = 0;

			try
			{
			   rowAffected = db.update(SqlFs.DBNAMES.FsBlock.ToString(), contValues, @where, null);
			}
			catch (Exception e)
			{
			   SqlFsLog.debug(e);
			   SqlFsErrCode.CurrentError = FsErr.SetFieldError;
			}

			return (rowAffected > 0);
		}

		/// <summary>
		///  Check if a dir/file name contains invalid character
		/// </summary>
		///  @param [in] name -- contains dir/file name to be checked 
		///                      on return, it contains a whitespace-trimmed version
		/// </param>
		///  <returns> null -- contains invalid characters </returns>
		///  <returns> String -- may be modified name </returns>
		protected internal virtual string checkInvalidChars(string name)
		{
		   if (SqlFsFunc.isNullOrEmpty(name))
		   {
			  return null;
		   }

		   string trimmedStr = SqlFsFunc.Trim(name, SqlFsConst.CHARSTOTRIM);

		   if (SqlFsFunc.isNullOrEmpty(trimmedStr)) // check once again
		   {
			  return null;
		   }

		   if (SqlFsFunc.indexOfAny(trimmedStr, SqlFsConst.INVALIDCHARS) >= 0)
		   {
			  return null;
		   }

		   if (trimmedStr.Equals(SqlFsConst.CURDIR)) // can't be '.'
		   {
			  return null;
		   }

		   if (trimmedStr.Equals(SqlFsConst.PARENTDIR)) // can't be '..'
		   {
			  return null;
		   }

		   return trimmedStr;
		}

		public virtual bool rename(string newName)
		{
		   SqlFsErrCode.CurrentError = FsErr.OK;

		   bool isOK = false;
		   fsLocker.FsLock;
		   SqlFsTransaction fsTran = new SqlFsTransaction(db);
		   try
		   {
			  isOK = __rename(newName);
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
		///  Rename dir/file
		/// </summary>
		private bool __rename(string newName)
		{
		   if ((newName = checkInvalidChars(newName)) == null)
		   {
			  SqlFsErrCode.CurrentError = FsErr.InvalidChars;
			  return false;
		   }

		   if (this.ID.Equals(SqlFsConst.ROOTDIRID)) // can't rename root
		   {
			  SqlFsErrCode.CurrentError = FsErr.CannotRenameRoot;
			  return false;
		   }

		   SqlDir parentDir = this.Parent;
		   if (parentDir == null)
		   {
			  SqlFsErrCode.CurrentError = FsErr.NoParent;
			  return false;
		   }

		   if (parentDir.isAlreadyExist(newName)) // already exists or not ?
		   {
			  SqlFsErrCode.CurrentError = FsErr.NameAlreadyExists;
			  return false;
		   }

		   setField(SqlFs.FSBLOCK.fsName, newName);

		   return true;
		}

		/// <summary>
		///  delete itself
		/// </summary>
		public abstract bool delete();

		public virtual bool isAncestor(SqlDir dir)
		{
		   SqlFsErrCode.CurrentError = FsErr.OK;

		   fsLocker.FsLock;
		   try
		   {
			  return __isAncestor(dir);
		   }
		   finally
		   {
			   fsLocker.dispose();
		   }
		}

		/// <summary>
		///  Check if passed in 'dir' is one of its ancestor
		/// </summary>
		private bool __isAncestor(SqlDir dir)
		{
		   bool found = false;
		   SqlFsNode curNode = this;

		   do
		   {
			  curNode = curNode.Parent;
			  if (curNode == null)
			  {
				 break;
			  }

			  if (curNode.ID.Equals(dir.ID))
			  {
				 found = true;
				 break;
			  }

			  if (curNode.ID.Equals(SqlFsConst.ROOTDIRID)) // reach root
			  {
				 break;
			  }

		   } while (true);

		   return found;
		}

		public virtual bool move(string destPath)
		{
			SqlFsErrCode.CurrentError = FsErr.OK;

			bool isOK = false;
			fsLocker.FsLock;
			SqlFsTransaction fsTran = new SqlFsTransaction(db);
			try
			{
				isOK = __move(destPath);
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
		///  Move itself to a destination path (absolute or relative)
		/// </summary>
		private bool __move(string destPath)
		{
		   if (SqlFsFunc.isNullOrEmpty(destPath))
		   {
			  SqlFsErrCode.CurrentError = FsErr.EmptyString;
			  return false;
		   }

		   // determine destination dir
		   SqlDir destDir = null;
		   if (destPath.StartsWith(SqlFsConst.STRPATHSEP))
		   {
			  // absolute path
			  SqlDir rootDir = (SqlDir)SqlFs.getFsNodeByID(db, fsLocker, SqlFsConst.ROOTDIRID); // get root
			  destPath = SqlFsFunc.Trim(destPath, new char[]{SqlFsConst.PATHSEP});
			  // if empty after trim, it refers to root
			  destDir = SqlFsFunc.isNullOrEmpty(destPath) ? rootDir : rootDir.getDir(destPath);
		   }
		   else
		   {
			  // relative path
			  SqlDir parent = this.Parent;
			  if (parent != null)
			  {
				 destDir = parent.getDir(destPath);
			  }
		   }

		   if (destDir != null)
		   {
			  return __move(destDir);
		   }

		   SqlFsErrCode.CurrentError = FsErr.DestDirNotFound;
		   return false;
		}

		public virtual bool move(SqlDir destDir)
		{
			SqlFsErrCode.CurrentError = FsErr.OK;

			bool isOK = false;
			fsLocker.FsLock;
			SqlFsTransaction fsTran = new SqlFsTransaction(db);
			try
			{
				isOK = __move(destDir);
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
		///  Move itself to a destination dir
		/// </summary>
		private bool __move(SqlDir destDir)
		{
		   do
		   {
			  if (this.ID.Equals(SqlFsConst.ROOTDIRID)) // can't move root
			  {
				 SqlFsErrCode.CurrentError = FsErr.CannotMoveRoot;
				 break;
			  }

			  if (this.ID.Equals(destDir.ID)) // can't move to itself
			  {
				 SqlFsErrCode.CurrentError = FsErr.CannotMoveToSelf;
				 break;
			  }

			  if (this.Dir && destDir.isAncestor((SqlDir)this)) // if it is a DIR, can't move to its subdir
			  {
				 SqlFsErrCode.CurrentError = FsErr.CannotMoveToSubdir;
				 break;
			  }

			  if (destDir.isAlreadyExist(this.Name)) // can't move if there is one with the same name
			  {
				 SqlFsErrCode.CurrentError = FsErr.NameAlreadyExists;
				 break;
			  }

			  // unlink itself from parent
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

			  // update parent ID
			  if (!setField(SqlFs.FSBLOCK.fsParent, destDir.ID))
			  {
				 break;
			  }

			  // add itself to dest dir
			  if (!destDir.updateChildList(SqlFsConst.FSOP.ADD, this.ID, FsID.toFsID(0)))
			  {
				 break;
			  }

			  return true;

		   } while (false);

		   return false;
		}

	}

}