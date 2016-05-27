using System;
using System.Collections.Generic;

namespace com.sss.sqlfs
{

	using Context = android.content.Context;
	using ContentValues = android.content.ContentValues;
	using Cursor = android.database.Cursor;
	using SQLiteDatabase = android.database.sqlite.SQLiteDatabase;
	using SQLiteException = android.database.sqlite.SQLiteException;

	using FsErr = com.sss.sqlfs.SqlFsErrCode.FsErr;
	using com.sss.sqlfs.helper;


	/// <summary>
	///  Class to access the Sql file system
	/// 
	///  Note:
	///     i) All instances (SqlFs, SqlFsNode, SqlFile, SqlDir) can only be used in one thread only 
	///        and must not be passed to another thread
	///     ii) Each thread should have its own instance of SqlFs even if several threads access the same DB.
	/// </summary>
	public class SqlFs
	{
		internal enum DBNAMES
		{
			//! the master table which contains table info
			SQLITE_MASTER,
			//! table names
			FsBlock,
			FsInfo,
			//! master column names
			type,
			table,
			name
		}

		internal enum FSBLOCK
		{
			//! FsBlock column
			fsID,
			fsType,
			fsCreateTime,
			fsLastModTime,
			fsFileSize,
			fsName,
			fsParent,
			fsChild,

			// it is not part of columns
			fsChildCount,
		}

		internal enum FSINFO
		{
			infoName,
			infoVal
		}

		//! default fields in FsInfo
		public enum FSINFOFIELDS
		{
			version,
			createTimeUtc,
			fsLabel,
			IDSize,
		}

	 // table names       
		private static readonly string[] TABLIST = new string[]{DBNAMES.FsBlock.ToString(), DBNAMES.FsInfo.ToString(), IFileData.DTABLENAME};
		// FsBlock column
		private static readonly string[][] COLFSBLOCK = new string[][] {new string[]{FSBLOCK.fsID.ToString(), "integer primary key autoincrement"}, new string[]{FSBLOCK.fsType.ToString(), "integer"}, new string[]{FSBLOCK.fsCreateTime.ToString(), "integer"}, new string[]{FSBLOCK.fsLastModTime.ToString(), "integer"}, new string[]{FSBLOCK.fsFileSize.ToString(), "integer"}, new string[]{FSBLOCK.fsName.ToString(), "varchar(512)"}, new string[]{FSBLOCK.fsParent.ToString(), "integer"}, new string[]{FSBLOCK.fsChild.ToString(), "blob"}};
		// FsInfo column
		private static readonly string[][] COLFSINFO = new string[][] {new string[]{FSINFO.infoName.ToString(), "varchar(128) primary key"}, new string[]{FSINFO.infoVal.ToString(), "varchar(128)"}};

		private SQLiteDatabase db = null; ///< sqlite database
		private string dbPath = null; ///< database full path
		private SqlFsLocker fsLocker = null; ///< locker to ensure single access

		private SqlFs()
		{
		}

		private SqlFs(Context ctxt, string dbPath)
		{
		   if (dbPath.StartsWith(SqlFsConst.STRPATHSEP))
		   {
			  this.dbPath = dbPath; // use full path already
		   }
		   else
		   {
			  this.dbPath = ctxt.getDatabasePath(dbPath).AbsolutePath;
		   }

		   this.fsLocker = SqlFsLocker.getFsLocker(this.dbPath);
		}

		/// <summary>
		///  Create db using the default "SimpleFileData"
		/// </summary>
		///  @param [in] dbPath -- absolute or relative path (inside phone memory) to the db file </param>
		public static SqlFs create(string dbPath, Context ctxt)
		{
		   AtomicBoolean isNewTableCreated = new AtomicBoolean(false);
		   return SqlFs.create(dbPath, new SimpleFileData(), ctxt, isNewTableCreated);
		}

		/// 
		///  @param [in] dbPath -- absolute or relative path (inside phone memory) to the db file </param>
		///  @param [in] dummyInst -- a dummy instance of a class inherited from IFileData. </param>
		public static SqlFs create(string dbPath, IFileData dummyInst, Context ctxt)
		{
		   AtomicBoolean isNewTableCreated = new AtomicBoolean(false);
		   return SqlFs.create(dbPath, dummyInst, ctxt, isNewTableCreated);
		}

		public static SqlFs create(string dbPath, IFileData dummyInst, Context ctxt, AtomicBoolean isNewTableCreated)
		{
		   SqlFsErrCode.CurrentError = FsErr.OK;

		   SqlFs fs = null;

		   try
		   {
			  if (dbPath != null)
			  {
				 fs = new SqlFs(ctxt, dbPath);
				 if (fs != null)
				 {
					fs.fsLocker.FsLock;
					try
					{
					   fs.open();
					   fs.prepare(dummyInst, isNewTableCreated);
					}
					finally
					{
					   fs.fsLocker.dispose();
					}
				 }
			  }
		   }
		   catch (Exception e)
		   {
			  SqlFsLog.debug("ERROR: Cannot open database, " + e.Message);
			  SqlFsErrCode.CurrentError = FsErr.CannotOpenDB;
		   }

		   return fs;
		}

		private void open()
		{
		   this.db = SQLiteDatabase.openOrCreateDatabase(this.dbPath, null);
		   if (this.fsLocker == null)
		   {
			   this.fsLocker = SqlFsLocker.getFsLocker(this.dbPath);
		   }
		}

		/// <summary>
		///  Should close the DB after use.
		/// </summary>
		public virtual void close()
		{
		   SqlFsErrCode.unset();

		   SqlFsLocker.close(fsLocker);
		   fsLocker = null;

		   if (this.db != null)
		   {
			  this.db.close();
			  this.db = null;
		   }
		}

		/// <summary>
		///  Prepare database tables if not already exists
		/// </summary>
		private void prepare(IFileData dummyInst, AtomicBoolean isNewTableCreated)
		{
		   if (!checkTables())
		   {

			  // create new tables
			  db.execSQL(SqlStr.genCreateTable(DBNAMES.FsBlock.ToString(), COLFSBLOCK));
			  db.execSQL(SqlStr.genCreateTable(DBNAMES.FsInfo.ToString(), COLFSINFO));
			  db.execSQL(SqlStr.genCreateTable(IFileData.DTABLENAME, dummyInst.ColSchema));

			  // create root dir, too
			  createRootDir();

			  // Write default info
			  writeInfo(FSINFOFIELDS.version.ToString(), SqlFsVersion.SqlFsVersion);
			  writeInfo(FSINFOFIELDS.createTimeUtc.ToString(), TimeConverter.calendarToReadableDateTime(DateTime.getInstance(TimeZone.getTimeZone("GMT+00:00"))));
			  writeInfo(FSINFOFIELDS.fsLabel.ToString(), SqlFsConst.DEFFSLABEL);
			  writeInfo(FSINFOFIELDS.IDSize.ToString(), Convert.ToString(FsID.IDSize));

			  isNewTableCreated.set(true);
		   }

		}

		/// <summary>
		///  Check existence of tables
		/// </summary>
		///  <returns> true -- all tables present </returns>
		///  <returns> false -- should create new tables </returns>
		private bool checkTables()
		{
		   int tabCount = 0;

		   string @where = SqlStr.genWhere(new SqlStr.SqlSimpCond(DBNAMES.type.ToString(), "=", DBNAMES.table.ToString())
								  );

		   Cursor c = db.query(DBNAMES.SQLITE_MASTER.ToString(), new string[]{DBNAMES.name.ToString()}, @where, null, null, null, null);

		   // retrieve all table names and check against our list
		   if (c != null && c.moveToFirst())
		   {
			   do
			   {
				  string tabName = c.getString(0);
				  foreach (string n in TABLIST)
				  {
					 if (n.Equals(tabName, StringComparison.CurrentCultureIgnoreCase))
					 {
						tabCount += 1;
						break;
					 }
				  }
			   } while (c.moveToNext());
		   }
		   SqlFsFunc.close(c);

		   bool isCheckingOK = false;

		   do
		   {

			  // not all tables exists, may be some corruption ...
			  if (tabCount < TABLIST.Length)
			  {
				 break;
			  }

			  // may be more checking here ...


			  isCheckingOK = true;

		   } while (false);

		   if (tabCount != 0 && !isCheckingOK)
		   {
			  close();
			  backup();
			  open();
		   }

		   return isCheckingOK;
		}

		private void backup()
		{
		   // construct a backup file name
		   int p = this.dbPath.LastIndexOf(SqlFsConst.STRPATHSEP);
		   string tempPath = this.dbPath.Substring(0, p + 1);
		   tempPath = tempPath + TimeConverter.calendarToReadableDateTime(DateTime.getInstance(TimeZone.getTimeZone("GMT+00:00"))) + ".db";

		   File f = new File(this.dbPath);
		   f.renameTo(new File(tempPath)); // rename it
		}


		/// <summary>
		///  Create root dir entry, which will have an ID of 1 and parent 0
		/// </summary>
		private void createRootDir()
		{
		   SqlDir.addDir(db, SqlFsConst.ROOTDIRNAME, SqlFsConst.ROOTPARENTID);
		}

		//////////////////////////FS Info /////////////////////////////////////////

		public virtual string getInfo(string infoName)
		{
			SqlFsErrCode.CurrentError = FsErr.OK;

			fsLocker.FsLock;
			try
			{
			   return __getInfo(infoName);
			}
			finally
			{
				 fsLocker.dispose();
			}
		}

		/// <summary>
		///  Get a single info
		/// </summary>
		///  <returns> info value </returns>
		private string __getInfo(string infoName)
		{
		   string value = null;
		   string @where = SqlStr.genWhere(new SqlStr.SqlSimpCond(FSINFO.infoName.ToString(), "=", infoName));

		   Cursor c = null;
		   try
		   {
			  c = db.query(DBNAMES.FsInfo.ToString(), new string[]{FSINFO.infoVal.ToString()}, @where, null, null, null, null);

			  if (c != null && c.moveToFirst())
			  {
				 value = c.getString(0);
			  }
		   }
		   catch (SQLiteException e)
		   {
			  SqlFsLog.debug(e);
			  SqlFsErrCode.CurrentError = FsErr.GetFsInfoErr;
		   }
		   finally
		   {
			  SqlFsFunc.close(c);
		   }

		   return value;
		}

		public virtual void writeInfo(string infoName, string infoVal)
		{
			SqlFsErrCode.CurrentError = FsErr.OK;

			fsLocker.FsLock;
			SqlFsTransaction fsTran = new SqlFsTransaction(db);
			try
			{
				__writeInfo(infoName, infoVal);
				fsTran.fsOpSuccess();
			}
			catch (SQLiteException)
			{

			}
			finally
			{
				fsTran.dispose();
				fsLocker.dispose();
			}
		}

		/// <summary>
		///  Write a single info
		///  Update existing entry or add a new one if not present
		/// </summary>
		private void __writeInfo(string infoName, string infoVal)
		{

		   List<object> colsAndValues = new List<object>();
		   colsAndValues.Add(FSINFO.infoName.ToString());
		   colsAndValues.Add(infoName);
		   colsAndValues.Add(FSINFO.infoVal.ToString());
		   colsAndValues.Add(infoVal);
		   ContentValues contVals = SqlStr.genContentValues(colsAndValues);
		   string @where = SqlStr.genWhere(new SqlStr.SqlSimpCond(FSINFO.infoName.ToString(), "=", infoName));

		   try
		   {
			  // try update first   
			  if (db.update(DBNAMES.FsInfo.ToString(), contVals, @where, null) == 0)
			  {
				 // nothing is updated, insert to table
				 db.insert(DBNAMES.FsInfo.ToString(), null, contVals);
			  }
		   }
		   catch (SQLiteException e)
		   {
			  SqlFsLog.debug(e);
			  SqlFsErrCode.CurrentError = FsErr.WriteFsInfoErr;
			  throw e;
		   }
		}


		//////////////////////////FS operations ///////////////////////////////////

		///  @param [in] id -- get entry using ID directly </param>
		internal static SqlFsNode getFsNodeByID(SQLiteDatabase db, SqlFsLocker fsLocker, FsID id)
		{
		   SqlFsNode fsNode = null;

		   string @where = SqlStr.genWhere(new SqlStr.SqlSimpCond(SqlFs.FSBLOCK.fsID.ToString(), "=", id));
		   Cursor c = null;

		   try
		   {

			  c = db.query(SqlFs.DBNAMES.FsBlock.ToString(), new string[]{SqlFs.FSBLOCK.fsID.ToString(), SqlFs.FSBLOCK.fsType.ToString()}, @where, null, null, null, null);

			  if (c.moveToFirst())
			  {
				 fsNode = SqlFsNode.getFsNode(db, fsLocker, c);
			  }
		   }
		   catch (Exception e)
		   {
				 SqlFsLog.debug(e);
				 SqlFsErrCode.CurrentError = FsErr.GetFieldError;
		   }
		   finally
		   {
				 SqlFsFunc.close(c);
		   }

		   return fsNode;
		}

		internal virtual SqlFsNode getFsNodeByID(FsID id)
		{
		   return SqlFs.getFsNodeByID(db, fsLocker, id);
		}

		/// <summary>
		///  Delete entry in a table using ID
		/// </summary>
		///  @param [in] tableName -- table name </param>
		///  @param [in] idColName -- ID column name </param>
		///  @param [in] id -- the actual ID </param>
		internal static bool deleteEntryByID(SQLiteDatabase db, string tableName, string idColName, FsID id)
		{
		   string @where = SqlStr.genWhere(new SqlStr.SqlSimpCond(idColName, "=", id));

		   bool isOK = false;
		   try
		   {
			  if (db.delete(tableName, @where, null) > 0)
			  {
				 isOK = true;
			  }
		   }
		   catch (Exception e)
		   {
				 SqlFsLog.debug(e);
				 SqlFsErrCode.CurrentError = FsErr.DeleteFsEntryError;
		   }

		   return isOK;
		}


		/// <summary>
		///  Get the last (autoIncrement) rowID after a 'INSERT'
		/// </summary>
		internal static FsID getLastInsertID(SQLiteDatabase db)
		{
		   // retrieve the ID of the new entry
		   FsID newID = SqlFsConst.INVALIDID;
		   string sql = "SELECT last_insert_rowid() as [id]";
		   Cursor c = null;
		   try
		   {
			  c = db.rawQuery(sql, null);
			  if (c.moveToFirst())
			  {
				 newID = SqlFsFunc.getID(c, 0);
			  }
		   }
		   catch (Exception e)
		   {
			  SqlFsLog.debug(e);
			  SqlFsErrCode.CurrentError = FsErr.GetLastInsertIDError;
		   }
		   finally
		   {
			  SqlFsFunc.close(c);
		   }

		   return newID;
		}

		public virtual SqlDir RootDir
		{
			get
			{
				SqlFsErrCode.CurrentError = FsErr.OK;
    
				fsLocker.FsLock;
				try
				{
				   return __getRootDir();
				}
				finally
				{
					 fsLocker.dispose();
				}
			}
		}

		/// <summary>
		///  Get root directory
		/// </summary>
		private SqlDir __getRootDir()
		{
		   SqlFsNode fsNode = getFsNodeByID(SqlFsConst.ROOTDIRID);
		   return (SqlDir)fsNode;
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

		///  @param [in] absolute dirPath -- e.g. "/path/to/dir" </param>
		private SqlFsNode __getFsNode(string path)
		{
		   if (SqlFsFunc.isNullOrEmpty(path))
		   {
			  SqlFsErrCode.CurrentError = FsErr.EmptyString;
			  return null;
		   }

		   if (!path.StartsWith(SqlFsConst.STRPATHSEP)) // must start with '/'
		   {
			  SqlFsErrCode.CurrentError = FsErr.MustUseAbsolutePath;
			  return null;
		   }

		   path = SqlFsFunc.Trim(path, new char[]{SqlFsConst.PATHSEP});
		   SqlDir rootDir = RootDir;
		   if (rootDir == null)
		   {
			  SqlFsErrCode.CurrentError = FsErr.CannotAccessRoot;
			  return null;
		   }

		   if (SqlFsFunc.isNullOrEmpty(path)) // if empty after trim, it refers to root
		   {
			  return rootDir;
		   }

		   return rootDir.getFsNode(path);
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

		///  @param [in] absolute dirPath -- e.g. "/path/to/dir" </param>
		private SqlDir __getDir(string dirPath)
		{
		   if (SqlFsFunc.isNullOrEmpty(dirPath))
		   {
			  SqlFsErrCode.CurrentError = FsErr.EmptyString;
			  return null;
		   }

		   if (!dirPath.StartsWith(SqlFsConst.STRPATHSEP)) // must start with '/'
		   {
			  SqlFsErrCode.CurrentError = FsErr.MustUseAbsolutePath;
			  return null;
		   }

		   dirPath = SqlFsFunc.Trim(dirPath, new char[]{SqlFsConst.PATHSEP});
		   SqlDir rootDir = RootDir;
		   if (rootDir == null)
		   {
			  SqlFsErrCode.CurrentError = FsErr.CannotAccessRoot;
			  return null;
		   }

		   if (SqlFsFunc.isNullOrEmpty(dirPath)) // if empty after trim, it refers to root
		   {
			  return rootDir;
		   }

		   return rootDir.getDir(dirPath);
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

		///  @param [in] absolute filePath -- e.g. "/path/to/file" </param>
		private SqlFile __getFile(string filePath)
		{
		   if (SqlFsFunc.isNullOrEmpty(filePath))
		   {
			  SqlFsErrCode.CurrentError = FsErr.EmptyString;
			  return null;
		   }

		   if (!filePath.StartsWith(SqlFsConst.STRPATHSEP)) // must start with '/'
		   {
			  SqlFsErrCode.CurrentError = FsErr.MustUseAbsolutePath;
			  return null;
		   }

		   filePath = SqlFsFunc.Trim(filePath, new char[]{SqlFsConst.PATHSEP});

		   SqlDir rootDir = RootDir;
		   if (rootDir == null)
		   {
			  SqlFsErrCode.CurrentError = FsErr.CannotAccessRoot;
			  return null;
		   }

		   return rootDir.getFile(filePath);
		}

		public virtual bool exists(string path)
		{
			SqlFsErrCode.CurrentError = FsErr.OK;

			fsLocker.FsLock;
			try
			{
			   return __exists(path);
			}
			finally
			{
				 fsLocker.dispose();
			}
		}

		/// <summary>
		///  Test if a absolute path (dir/file) exists
		/// </summary>
		private bool __exists(string path)
		{
		   if (SqlFsFunc.isNullOrEmpty(path))
		   {
			   SqlFsErrCode.CurrentError = FsErr.EmptyString;
			  return false;
		   }

		   if (!path.StartsWith(SqlFsConst.STRPATHSEP)) // must start with '/'
		   {
			   SqlFsErrCode.CurrentError = FsErr.MustUseAbsolutePath;
			  return false;
		   }

		   path = SqlFsFunc.Trim(path, new char[]{SqlFsConst.PATHSEP});
		   if (SqlFsFunc.isNullOrEmpty(path)) // if empty after trim, it refers to root
		   {
			  return true;
		   }

		   SqlDir rootDir = RootDir;
		   if (rootDir == null)
		   {
			  SqlFsErrCode.CurrentError = FsErr.CannotAccessRoot;
			  return false;
		   }

		   SqlFsNode fsNode = rootDir.getFsNode(path);

		   return (fsNode != null);
		}

	}

}