using System;
using System.Collections.Generic;

namespace com.sss.sqlfs
{

	using SQLiteDatabase = android.database.sqlite.SQLiteDatabase;
	using Cursor = android.database.Cursor;

	using FsErr = com.sss.sqlfs.SqlFsErrCode.FsErr;
	using com.sss.sqlfs.helper;

	/// <summary>
	///  a class for dir
	/// </summary>
	public class SqlDir : SqlFsNode
	{

		internal static SqlDir getDir(SQLiteDatabase db, SqlFsLocker fsLocker, FsID id)
		{
		   SqlDir d = new SqlDir(db, fsLocker, id);
		   return d;
		}

		internal static SqlDir getDir(SQLiteDatabase db, SqlFsLocker fsLocker, Cursor c)
		{
		   FsID id = SqlFsFunc.getID(c, SqlFs.FSBLOCK.fsID.ordinal());
		   return SqlDir.getDir(db, fsLocker, id);
		}

		private SqlDir()
		{
		}

		private SqlDir(SQLiteDatabase db, SqlFsLocker fsLocker, FsID id) : base(db, fsLocker, id)
		{
		}

		public override bool Dir
		{
			get
			{
			   return true;
			}
		}

		public virtual int ChildCount
		{
			get
			{
			   SqlFsErrCode.CurrentError = FsErr.OK;
    
			   return (int?)getField(SqlFs.FSBLOCK.fsChildCount);
			}
		}

		///  @param [in] name -- if null, get all entry </param>
		///  @param [in] type -- dir, file or any ? </param>
		private Cursor getEntryByName(string name, SqlFsConst.FSTYPE type)
		{
		   // conditions
		   List<object> conds = new List<object>(10);
		   conds.Add(new SqlStr.SqlSimpCond(SqlFs.FSBLOCK.fsParent.ToString(), "=", this.ID));

		   if (name != null)
		   {
			  conds.Add("and");
			  conds.Add(new SqlStr.SqlSimpCond(SqlFs.FSBLOCK.fsName.ToString(), "=", name));
		   }

		   if (type != SqlFsConst.FSTYPE.ANY)
		   {
			  conds.Add("and");
			  conds.Add(new SqlStr.SqlSimpCond(SqlFs.FSBLOCK.fsType.ToString(), "=", type.v()));
		   }

		   string @where = SqlStr.genWhere(conds);

		   // query from DB
		   Cursor c = null;
		   try
		   {
			   c = db.query(SqlFs.DBNAMES.FsBlock.ToString(), new string[]{SqlFs.FSBLOCK.fsID.ToString(), SqlFs.FSBLOCK.fsType.ToString()}, @where, null, null, null, null);
		   }
		   catch (Exception e)
		   {
			  SqlFsLog.debug(e);
			  SqlFsErrCode.CurrentError = SqlFsErrCode.FsErr.NoEntryByName;
		   }

		   // close and set to null if no rows at all
		   if (c != null && c.Count == 0)
		   {
			  c.close();
			  c = null;
		   }

		   return c;
		}

		public virtual bool isAlreadyExist(string name)
		{
			SqlFsErrCode.CurrentError = FsErr.OK;

			fsLocker.FsLock;
			try
			{
			   return __isAlreadyExist(name);
			}
			finally
			{
				fsLocker.dispose();
			}
		}

		/// <summary>
		///  Find if there is any child with the same name
		/// </summary>
		private bool __isAlreadyExist(string name)
		{
		   Cursor c = getEntryByName(name, SqlFsConst.FSTYPE.ANY);
		   bool isExist = false;
		   if (c != null)
		   {
			  isExist = (c.Count > 0);
			  c.close();
		   }

		   return isExist;
		}

		/// <summary>
		///  Create an empty dir
		/// </summary>
		///  <returns> new ID for the inserted dir </returns>
		internal static FsID addDir(SQLiteDatabase db, string dirName, FsID parentID)
		{
		   return SqlFsNode.addFsNode(db, SqlFsConst.FSTYPE.DIR, dirName, parentID);
		}

		/// <summary>
		///  Update the INT child list to db
		/// </summary>
		///  @param [in] fsOp -- ADD, REPLACE or DEL </param>
		///  @param [in] id1 -- id to be added or replaced or deleted </param>
		///  @param [in] id2 -- for REPLACE only (the new ID) </param>
		internal virtual bool updateChildList(SqlFsConst.FSOP fsOp, FsID id1, FsID id2)
		{
		   List<FsID> childList = (List<FsID>)getField(SqlFs.FSBLOCK.fsChild);

		   bool isMod = false;

		   switch (fsOp)
		   {
			  case com.sss.sqlfs.SqlFsConst.FSOP.ADD:
				 if (childList == null)
				 {
					childList = new List<FsID>(1); // make a new one if empty
				 }

				 if (childList.IndexOf(id1) < 0) // add only if it is not already there
				 {
					childList.Add(id1);
					isMod = true;
				 }
				 break;
			  case com.sss.sqlfs.SqlFsConst.FSOP.REPLACE:
				 if (childList == null)
				 {
					break;
				 }

				 int index = childList.IndexOf(id1);
				 if (index >= 0)
				 {
					childList[index] = id2;
					isMod = true;
				 }
				 break;
			  case com.sss.sqlfs.SqlFsConst.FSOP.DEL:
				 if (childList == null)
				 {
					break;
				 }
				 if (childList.Remove(id1))
				 {
					isMod = true;
				 }
				 break;
		   }

		   if (isMod)
		   {
			  return setField(SqlFs.FSBLOCK.fsChild, childList);
		   }

		   SqlFsErrCode.CurrentError = FsErr.ChildListNotUpdated;
		   return false;
		}

		public virtual SqlDir addDir(string dirName)
		{
			SqlFsErrCode.CurrentError = FsErr.OK;

			SqlDir childDir = null;
			fsLocker.FsLock;
			SqlFsTransaction fsTran = new SqlFsTransaction(db);
			try
			{
			   childDir = __addDir(dirName);
			   if (childDir != null)
			   {
				  fsTran.fsOpSuccess();
			   }
			}
			finally
			{
				fsTran.dispose();
				fsLocker.dispose();
			}

			return childDir;
		}

		/// <summary>
		///   Add an empty dir
		/// </summary>
		///   <returns> the newly created dir </returns>
		private SqlDir __addDir(string dirName)
		{
		   if ((dirName = checkInvalidChars(dirName)) == null)
		   {
			  SqlFsErrCode.CurrentError = FsErr.InvalidChars;
			  return null;
		   }

		   if (__isAlreadyExist(dirName))
		   {
			  SqlFsErrCode.CurrentError = FsErr.NameAlreadyExists;
			  return null;
		   }

		   FsID newID = SqlDir.addDir(db, dirName, this.ID);
		   if (newID.compare(SqlFsConst.INVALIDID) <= 0)
		   {
			  SqlFsErrCode.CurrentError = FsErr.NoNewIDForNewFsNode;
			  return null;
		   }

		   if (!updateChildList(SqlFsConst.FSOP.ADD, newID, FsID.toFsID(0)))
		   {
			  // delete entry just created
			  SqlFs.deleteEntryByID(db, SqlFs.DBNAMES.FsBlock.ToString(), SqlFs.FSBLOCK.fsID.ToString(), newID);
			  return null;
		   }

		   return SqlDir.getDir(db, fsLocker, newID);
		}

		public virtual SqlFile addFile(string fileName)
		{
			SqlFsErrCode.CurrentError = FsErr.OK;

			SqlFile newFile = null;
			fsLocker.FsLock;
			SqlFsTransaction fsTran = new SqlFsTransaction(db);
			try
			{
				newFile = __addFile(fileName);
				if (newFile != null)
				{
				   fsTran.fsOpSuccess();
				}
			}
			finally
			{
				fsTran.dispose();
				fsLocker.dispose();
			}

			return newFile;
		}

		/// <summary>
		///  Add a new entry for file. File must be saved so as to be 
		///  appeared under its parent directory.
		/// </summary>
		private SqlFile __addFile(string fileName)
		{
		   if ((fileName = checkInvalidChars(fileName)) == null)
		   {
			  SqlFsErrCode.CurrentError = FsErr.InvalidChars;
			  return null;
		   }

		   if (__isAlreadyExist(fileName))
		   {
			  SqlFsErrCode.CurrentError = FsErr.NameAlreadyExists;
			  return null;
		   }

		   FsID newID = SqlFile.addFile(db, fileName, this.ID);
		   if (newID.compare(SqlFsConst.INVALIDID) <= 0)
		   {
			  SqlFsErrCode.CurrentError = FsErr.NoNewIDForNewFsNode;
			  return null;
		   }

		   if (!updateChildList(SqlFsConst.FSOP.ADD, newID, FsID.toFsID(0)))
		   {
			  // delete entry just created
			  SqlFs.deleteEntryByID(db, SqlFs.DBNAMES.FsBlock.ToString(), SqlFs.FSBLOCK.fsID.ToString(), newID);
			  return null;
		   }

		   SqlFile f = SqlFile.getFile(db, fsLocker, newID);
		   f.setDataBlockID(SqlFsConst.NOFILEDATAID);
		   return f;
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

		/// <summary>
		///  delete all dirs and files recursively
		/// </summary>
		private bool __delete()
		{
		   bool isOK = false;

		   do
		   {
			  // delete itself from parent
			  if (!this.ID.Equals(SqlFsConst.ROOTDIRID)) // root has no parent
			  {
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
			  }

			  // delete underlying subdirs and files
			  List<SqlFsNode> childList = this.ChildList;

			  if (childList != null)
			  {
				 // delete children one by one
				 foreach (SqlFsNode fsNode in childList)
				 {
					fsNode.delete();
				 }
			  }

			  // delete itself
			  if (this.ID.Equals(SqlFsConst.ROOTDIRID))
			  {
				 // for root, just clear all children
				 this.setField(SqlFs.FSBLOCK.fsChild, null);
			  }
			  else
			  {
				 if (!SqlFs.deleteEntryByID(db, SqlFs.DBNAMES.FsBlock.ToString(), SqlFs.FSBLOCK.fsID.ToString(), this.ID))
				 {
					 SqlFsErrCode.CurrentError = FsErr.CannotDeleteFsEntry;
					 break;
				 }
			  }

			  isOK = true;
		   } while (false);

		   return isOK;
		}


		public virtual SqlFsNode getChild(string name)
		{
			SqlFsErrCode.CurrentError = FsErr.OK;

			fsLocker.FsLock;
			try
			{
			   return __getChild(name);
			}
			finally
			{
				 fsLocker.dispose();
			}
		}

		/// <summary>
		///  Get a single child by name
		/// </summary>
		///  @param [in] name -- name of child. If name is "..", it gets parent. If name is ".", return itself </param>
		private SqlFsNode __getChild(string name)
		{
		   SqlFsNode fsNode = null;

		   if (name.Equals(SqlFsConst.CURDIR))
		   {
			  fsNode = this; // current dir
		   }
		   else if (name.Equals(SqlFsConst.PARENTDIR))
		   {
			  // parent or itself if already root
			  fsNode = (this.ID.Equals(SqlFsConst.ROOTDIRID) ? this : this.Parent);
		   }
		   else
		   {

			  Cursor c = getEntryByName(name, SqlFsConst.FSTYPE.ANY);
			  if (c != null)
			  {
				 c.moveToFirst();
				 fsNode = SqlFsNode.getFsNode(db, fsLocker, c);
				 c.close();
			  }
			  else
			  {
				 SqlFsErrCode.CurrentError = SqlFsErrCode.FsErr.NoEntryByName;
			  }
		   }

		   return fsNode;
		}

		public virtual List<SqlFsNode> ChildList
		{
			get
			{
				SqlFsErrCode.CurrentError = FsErr.OK;
    
				fsLocker.FsLock;
				try
				{
				   return __getChildList();
				}
				finally
				{
					  fsLocker.dispose();
				}
			}
		}

		/// <summary>
		///  Get a list of children (dirs and files)
		/// </summary>
		private List<SqlFsNode> __getChildList()
		{
		   List<SqlFsNode> childList = null;
		   Cursor c = getEntryByName(null, SqlFsConst.FSTYPE.ANY);
		   if (c != null)
		   {
			  c.moveToFirst();
			  childList = new List<SqlFsNode>(c.Count);
			  do
			  {
				 SqlFsNode fsNode = SqlFsNode.getFsNode(db, fsLocker, c);
				 if (fsNode != null)
				 {
					childList.Add(fsNode);
				 }
			  } while (c.moveToNext());
			  c.close();
		   }

		   return childList;
		}

		public virtual List<SqlDir> SubDirs
		{
			get
			{
				SqlFsErrCode.CurrentError = FsErr.OK;
    
				fsLocker.FsLock;
				try
				{
				   return __getSubDirs();
				}
				finally
				{
					  fsLocker.dispose();
				}
			}
		}

		/// <summary>
		///  Get list of subdirs only
		/// </summary>
		private List<SqlDir> __getSubDirs()
		{
		   List<SqlDir> dirList = null;
		   Cursor c = getEntryByName(null, SqlFsConst.FSTYPE.DIR);
		   if (c != null)
		   {
			  c.moveToFirst();
			  dirList = new List<SqlDir>(c.Count);
			  do
			  {
				 SqlDir dir = SqlDir.getDir(db, fsLocker, c);
				 if (dir != null)
				 {
					dirList.Add(dir);
				 }
			  } while (c.moveToNext());
			  c.close();
		   }

		   return dirList;
		}

		public virtual List<SqlFile> Files
		{
			get
			{
				SqlFsErrCode.CurrentError = FsErr.OK;
    
				fsLocker.FsLock;
				try
				{
				   return __getFiles();
				}
				finally
				{
					  fsLocker.dispose();
				}
			}
		}

		/// <summary>
		///  Get list of files only
		/// </summary>
		private List<SqlFile> __getFiles()
		{
		   List<SqlFile> fileList = null;
		   Cursor c = getEntryByName(null, SqlFsConst.FSTYPE.FILE);
		   if (c != null)
		   {
			  c.moveToFirst();
			  fileList = new List<SqlFile>(c.Count);
			  do
			  {
				 SqlFile f = SqlFile.getFile(db, fsLocker, c);
				 if (f != null)
				 {
					fileList.Add(f);
				 }
			  } while (c.moveToNext());
			  c.close();
		   }

		   return fileList;
		}

		public virtual SqlFsNode getFsNode(string path)
		{
			SqlFsErrCode.CurrentError = FsErr.OK;

			fsLocker.FsLock;
			try
			{
			   return __getFsNode(path);
			}
			finally
			{
				  fsLocker.dispose();
			}
		}

		///  @param [in] relative dirPath -- e.g. "path/to/dir" </param>
		private SqlFsNode __getFsNode(string path)
		{
		   if (SqlFsFunc.isNullOrEmpty(path))
		   {
			  SqlFsErrCode.CurrentError = FsErr.EmptyString;
			  return null;
		   }

		   if (path.StartsWith(SqlFsConst.STRPATHSEP)) // must *NOT* start with '/'
		   {
			  SqlFsErrCode.CurrentError = FsErr.MustUseRelativePath;
			  return null;
		   }

		   if (path.EndsWith(SqlFsConst.STRPATHSEP)) // trim trailing '/'
		   {
			  path = SqlFsFunc.trimEnd(path, new char[]{SqlFsConst.PATHSEP});
		   }

		   string[] pathSeg = path.Split(SqlFsConst.STRPATHSEP, true);
		   if (pathSeg == null || pathSeg.Length <= 0)
		   {
			  SqlFsErrCode.CurrentError = FsErr.SplitPathErr;
			  return null;
		   }

		   SqlFsNode curNode = this;

		   // start looping to target node
		   for (int i = 0; i < pathSeg.Length; ++i)
		   {
			  if (SqlFsFunc.isNullOrEmpty(pathSeg[i])) // to prevent empty space between separator
			  {
				 continue;
			  }

			  curNode = ((SqlDir)curNode).__getChild(pathSeg[i]);
			  if (curNode == null)
			  {
				 SqlFsErrCode.CurrentError = FsErr.ChildNotFound;
				 break;
			  }

			  if (!curNode.Dir && i != pathSeg.Length - 1) // if a file but not reach the end yet
			  {
				 curNode = null;
				 SqlFsErrCode.CurrentError = FsErr.NotDirInPath;
				 break;
			  }
		   }

		   return curNode;
		}

		public virtual SqlDir getDir(string dirPath)
		{
			SqlFsErrCode.CurrentError = FsErr.OK;

			fsLocker.FsLock;
			try
			{
			   return __getDir(dirPath);
			}
			finally
			{
				 fsLocker.dispose();
			}
		}

		///  @param [in] relative dirPath -- e.g. "path/to/dir" </param>
		private SqlDir __getDir(string dirPath)
		{
		   if (SqlFsFunc.isNullOrEmpty(dirPath))
		   {
			  SqlFsErrCode.CurrentError = FsErr.EmptyString;
			  return null;
		   }

		   if (dirPath.StartsWith(SqlFsConst.STRPATHSEP)) // must *NOT* start with '/'
		   {
			  SqlFsErrCode.CurrentError = FsErr.MustUseRelativePath;
			  return null;
		   }

		   if (dirPath.EndsWith(SqlFsConst.STRPATHSEP)) // trim trailing '/'
		   {
			  dirPath = SqlFsFunc.trimEnd(dirPath, new char[]{SqlFsConst.PATHSEP});
		   }

		   string[] dirSeg = dirPath.Split(SqlFsConst.STRPATHSEP, true);
		   if (dirSeg == null || dirSeg.Length <= 0)
		   {
			  SqlFsErrCode.CurrentError = FsErr.SplitPathErr;
			  return null;
		   }

		   SqlFsNode curNode = this;

		   // start looping to target dir
		   foreach (string dir in dirSeg)
		   {
			  if (SqlFsFunc.isNullOrEmpty(dir)) // to prevent empty space between separator
			  {
				 continue;
			  }

			  curNode = ((SqlDir)curNode).__getChild(dir);
			  if (curNode == null || !curNode.Dir)
			  {
				 curNode = null;
				 SqlFsErrCode.CurrentError = FsErr.ChildNotFound;
				 break;
			  }
		   }


		   return (SqlDir)curNode;
		}

		public virtual SqlFile getFile(string filePath)
		{
			SqlFsErrCode.CurrentError = FsErr.OK;

			fsLocker.FsLock;
			try
			{
			   return __getFile(filePath);
			}
			finally
			{
				 fsLocker.dispose();
			}
		}

		///  @param [in] relative filePath -- e.g. "path/to/file" </param>
		private SqlFile __getFile(string filePath)
		{
		   if (SqlFsFunc.isNullOrEmpty(filePath))
		   {
			  SqlFsErrCode.CurrentError = FsErr.EmptyString;
			  return null;
		   }

		   if (filePath.StartsWith(SqlFsConst.STRPATHSEP) || filePath.EndsWith(SqlFsConst.STRPATHSEP)) // must *NOT* start or end with '/'
		   {
			  SqlFsErrCode.CurrentError = FsErr.MustNotStartOrEndWithPathSeparator;
			  return null;
		   }

		   string[] dirSeg = filePath.Split(SqlFsConst.STRPATHSEP, true);
		   if (dirSeg == null || dirSeg.Length <= 0)
		   {
			  SqlFsErrCode.CurrentError = FsErr.SplitPathErr;
			  return null;
		   }

		   SqlFsNode curNode = this;

		   // start looping to target file
		   for (int i = 0; i < dirSeg.Length; ++i)
		   {
			  if (SqlFsFunc.isNullOrEmpty(dirSeg[i])) // to prevent empty space between separator
			  {
				 continue;
			  }

			  curNode = ((SqlDir)curNode).__getChild(dirSeg[i]);
			  if (i == dirSeg.Length - 1)
			  {
				 if (curNode == null || curNode.Dir) // last one must be a file
				 {
					curNode = null;
					SqlFsErrCode.CurrentError = FsErr.ChildNotFound;
					break;
				 }
			  }
			  else
			  {

				 if (curNode == null || !curNode.Dir) // others' should be dir
				 {
					curNode = null;
					SqlFsErrCode.CurrentError = FsErr.NotDirInPath;
					break;
				 }
			  }
		   }

		   return (SqlFile)curNode;
		}
	}

}