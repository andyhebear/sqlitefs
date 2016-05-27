package com.sss.sqlfs;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import com.sss.sqlfs.SqlFsErrCode.FsErr;
import com.sss.sqlfs.helper.*;

import java.io.File;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *  Class to access the Sql file system
 *
 *  Note:
 *     i) All instances (SqlFs, SqlFsNode, SqlFile, SqlDir) can only be used in one thread only 
 *        and must not be passed to another thread
 *     ii) Each thread should have its own instance of SqlFs even if several threads access the same DB.
 */
public class SqlFs 
{
	enum DBNAMES 
	{
        //! the master table which contains table info
        SQLITE_MASTER,
        //! table names
        FsBlock, FsInfo,
        //! master column names
        type, table, name
    };
     
	enum FSBLOCK 
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
    };
    
    enum FSINFO 
    { 
        infoName,
        infoVal
    };

    //! default fields in FsInfo
    public enum FSINFOFIELDS 
    {
        version,
        createTimeUtc,
        fsLabel,
        IDSize,
    };
    
 // table names       
    private static final String[] TABLIST = new String[]{DBNAMES.FsBlock.toString(),
                                                         DBNAMES.FsInfo.toString(), 
                                                         IFileData.DTABLENAME};
    // FsBlock column
    private static final String[][] COLFSBLOCK = new String[][] {
                       new String[]{FSBLOCK.fsID.toString(), "integer primary key autoincrement"},
                       new String[]{FSBLOCK.fsType.toString(), "integer"},
                       new String[]{FSBLOCK.fsCreateTime.toString(), "integer"},
                       new String[]{FSBLOCK.fsLastModTime.toString(), "integer"},
                       new String[]{FSBLOCK.fsFileSize.toString(), "integer"},
                       new String[]{FSBLOCK.fsName.toString(), "varchar(512)"},
                       new String[]{FSBLOCK.fsParent.toString(), "integer"},
                       new String[]{FSBLOCK.fsChild.toString(), "blob"}
                                                                  };
    // FsInfo column
    private static final String[][] COLFSINFO = new String[][] {
                       new String[]{FSINFO.infoName.toString(), "varchar(128) primary key"},
                       new String[]{FSINFO.infoVal.toString(), "varchar(128)"}
                                                                  };
    
    private SQLiteDatabase db = null;     ///< sqlite database
    private String dbPath = null;         ///< database full path 
    private SqlFsLocker fsLocker = null;  ///< locker to ensure single access
    
    private SqlFs() { }

    private SqlFs(Context ctxt, String dbPath)
    {
       if (dbPath.startsWith(SqlFsConst.STRPATHSEP))
          this.dbPath = dbPath; // use full path already
       else 
    	  this.dbPath = ctxt.getDatabasePath(dbPath).getAbsolutePath();
       
       this.fsLocker = SqlFsLocker.getFsLocker(this.dbPath);
    }
    
    /**
     *  Create db using the default "SimpleFileData"
     *  
     *  @param [in] dbPath -- absolute or relative path (inside phone memory) to the db file
     */
    public static SqlFs create(String dbPath, Context ctxt)
    {
	   AtomicBoolean isNewTableCreated = new AtomicBoolean(false);
       return SqlFs.create(dbPath, new SimpleFileData(), ctxt, isNewTableCreated);
    }
    
    /**
     *
     *  @param [in] dbPath -- absolute or relative path (inside phone memory) to the db file
     *  @param [in] dummyInst -- a dummy instance of a class inherited from IFileData.
     */
    public static SqlFs create(String dbPath, IFileData dummyInst, Context ctxt)
    {
	   AtomicBoolean isNewTableCreated = new AtomicBoolean(false);
       return SqlFs.create(dbPath, dummyInst, ctxt, isNewTableCreated);
    }

    public static SqlFs create(String dbPath, IFileData dummyInst, 
	 	                       Context ctxt, AtomicBoolean isNewTableCreated) 
    {
       SqlFsErrCode.setCurrentError(FsErr.OK);
    	
       SqlFs fs = null;

       try {
          if (dbPath != null) {
             fs = new SqlFs(ctxt, dbPath);
             if (fs != null) {
                fs.fsLocker.getFsLock();
                try {
                   fs.open();
                   fs.prepare(dummyInst, isNewTableCreated);
                }
                finally {
            	   fs.fsLocker.dispose(); 
                }
             }
          }             
       }
       catch(Exception e) {
          SqlFsLog.debug("ERROR: Cannot open database, " + e.getMessage());
          SqlFsErrCode.setCurrentError(FsErr.CannotOpenDB);
       }

       return fs;
    }
    
    private void open()
    {
       this.db = SQLiteDatabase.openOrCreateDatabase(this.dbPath, null);
       if (this.fsLocker == null)
    	   this.fsLocker = SqlFsLocker.getFsLocker(this.dbPath);
    }
    
    /**
	 *  Should close the DB after use.
	 */
    public void close()
    {
       SqlFsErrCode.unset();
       
       SqlFsLocker.close(fsLocker);
       fsLocker = null;
       
       if (this.db != null) {
    	  this.db.close();
    	  this.db = null;
       }
    }
    
    /**
	 *  Prepare database tables if not already exists
	 */
    private void prepare(IFileData dummyInst, AtomicBoolean isNewTableCreated)
    {
       if (!checkTables()) {

          // create new tables
    	  db.execSQL(SqlStr.genCreateTable(DBNAMES.FsBlock.toString(), COLFSBLOCK));
    	  db.execSQL(SqlStr.genCreateTable(DBNAMES.FsInfo.toString(), COLFSINFO));
    	  db.execSQL(SqlStr.genCreateTable(IFileData.DTABLENAME, dummyInst.getColSchema()));
            
          // create root dir, too
          createRootDir();

          // Write default info
          writeInfo(FSINFOFIELDS.version.toString(), SqlFsVersion.getSqlFsVersion());
          writeInfo(FSINFOFIELDS.createTimeUtc.toString(), 
         		            TimeConverter.calendarToReadableDateTime(
         		            		Calendar.getInstance(TimeZone.getTimeZone("GMT+00:00"))));
          writeInfo(FSINFOFIELDS.fsLabel.toString(), SqlFsConst.DEFFSLABEL);
          writeInfo(FSINFOFIELDS.IDSize.toString(), Integer.toString(FsID.getIDSize()));

          isNewTableCreated.set(true);
       }
             
    }

    /**
	 *  Check existence of tables
	 * 
	 *  @return true -- all tables present
	 *  @return false -- should create new tables
	 */
    private boolean checkTables()
    {
       int tabCount = 0;
       
	   String where = SqlStr.genWhere(
               new SqlStr.SqlSimpCond(DBNAMES.type.toString(), "=", DBNAMES.table.toString())
                               );    
 
	   Cursor c = db.query(DBNAMES.SQLITE_MASTER.toString(), 
	                       new String[]{DBNAMES.name.toString()}, 
	                       where, null, null, null, null);
	       
	   // retrieve all table names and check against our list
       if (c != null && c.moveToFirst()) {
	       do {
	          String tabName = c.getString(0);
	          for (String n : TABLIST) {
	        	 if (n.equalsIgnoreCase(tabName)) {
	                tabCount += 1;
	                break;
	        	 }
	          }
	       } while (c.moveToNext());
       }
       SqlFsFunc.close(c);
       
       boolean isCheckingOK = false;

       do {
          
          // not all tables exists, may be some corruption ...
          if (tabCount < TABLIST.length) 
             break;
       
          // may be more checking here ...
          
          
          isCheckingOK = true;

       } while(false);

       if (tabCount != 0 && !isCheckingOK) {
          close();
          backup();
          open();
       }

       return isCheckingOK;
    }

    private void backup()
    {
       // construct a backup file name
       int p = this.dbPath.lastIndexOf(SqlFsConst.STRPATHSEP);
       String tempPath = this.dbPath.substring(0, p + 1);
       tempPath = tempPath + TimeConverter.calendarToReadableDateTime(
       		                     Calendar.getInstance(TimeZone.getTimeZone("GMT+00:00"))) + ".db";
       
       File f = new File(this.dbPath);
       f.renameTo(new File(tempPath));  // rename it
    }

    
    /**
     *  Create root dir entry, which will have an ID of 1 and parent 0
     */
    private void createRootDir()
    {
       SqlDir.addDir(db, SqlFsConst.ROOTDIRNAME, SqlFsConst.ROOTPARENTID);
    }

    //////////////////////////FS Info /////////////////////////////////////////
    
    public String getInfo(String infoName)
    {
    	SqlFsErrCode.setCurrentError(FsErr.OK);
    	
    	fsLocker.getFsLock();
        try {
           return __getInfo(infoName);
        }
        finally {
      	   fsLocker.dispose();
        }
    }

    /**
	 *  Get a single info
	 * 
	 *  @return info value
	 */
    private String __getInfo(String infoName)
    {
       String value = null;
       String where = SqlStr.genWhere(new SqlStr.SqlSimpCond(FSINFO.infoName.toString(), "=", infoName));                                                    
       
       Cursor c = null;
       try {
    	  c = db.query(DBNAMES.FsInfo.toString(), new String[]{FSINFO.infoVal.toString()}, 
    			       where, null, null, null, null); 
          
          if (c != null && c.moveToFirst()) {
             value = c.getString(0);             
          }
       } 
       catch (SQLiteException e) {
          SqlFsLog.debug(e);
          SqlFsErrCode.setCurrentError(FsErr.GetFsInfoErr); 
       }
       finally {
    	  SqlFsFunc.close(c); 
       }
       
       return value;
    }
    
    public void writeInfo(String infoName, String infoVal)
    {
    	SqlFsErrCode.setCurrentError(FsErr.OK);
    	
    	fsLocker.getFsLock();
        SqlFsTransaction fsTran = new SqlFsTransaction(db);
        try {
        	__writeInfo(infoName, infoVal);
        	fsTran.fsOpSuccess();
        }
        catch (SQLiteException e) {
        	
        }
        finally {
     	   fsTran.dispose();
     	   fsLocker.dispose();
        }
    }

    /**
	    *  Write a single info
	    *  Update existing entry or add a new one if not present
	    */
    private void __writeInfo(String infoName, String infoVal)
    {
       
       ArrayList<Object> colsAndValues = new ArrayList<Object>();
       colsAndValues.add(FSINFO.infoName.toString()); colsAndValues.add(infoName);
       colsAndValues.add(FSINFO.infoVal.toString()); colsAndValues.add(infoVal);
       ContentValues contVals = SqlStr.genContentValues(colsAndValues);
       String where = SqlStr.genWhere(new SqlStr.SqlSimpCond(FSINFO.infoName.toString(), "=", infoName));
       
       try {
    	  // try update first   
          if (db.update(DBNAMES.FsInfo.toString(), contVals, where, null) == 0) {
             // nothing is updated, insert to table
        	 db.insert(DBNAMES.FsInfo.toString(), null, contVals);
          }
       }
       catch(SQLiteException e) {
          SqlFsLog.debug(e);
          SqlFsErrCode.setCurrentError(FsErr.WriteFsInfoErr);
          throw e;
       }
    }

    
    //////////////////////////FS operations ///////////////////////////////////

    /**
     *  @param [in] id -- get entry using ID directly
     */
    static SqlFsNode getFsNodeByID(SQLiteDatabase db, SqlFsLocker fsLocker, FsID id)
    {
       SqlFsNode fsNode = null;

       String where = SqlStr.genWhere(new SqlStr.SqlSimpCond(SqlFs.FSBLOCK.fsID.toString(), "=", id));
       Cursor c = null;
       
       try {
    	   
    	  c = db.query(SqlFs.DBNAMES.FsBlock.toString(), 
                   new String[]{SqlFs.FSBLOCK.fsID.toString(), SqlFs.FSBLOCK.fsType.toString()}, 
                   where, null, null, null, null);
    	   
    	  if (c.moveToFirst()) {
    		 fsNode = SqlFsNode.getFsNode(db, fsLocker, c);
    	  }
       } 
       catch (Exception e) {
       	  SqlFsLog.debug(e);
       	  SqlFsErrCode.setCurrentError(FsErr.GetFieldError);
       }
       finally {
       	  SqlFsFunc.close(c);
       }

       return fsNode;
    }
    
    SqlFsNode getFsNodeByID(FsID id)
    {
       return SqlFs.getFsNodeByID(db, fsLocker, id);
    }
    
    /**
     *  Delete entry in a table using ID
     *
     *  @param [in] tableName -- table name
     *  @param [in] idColName -- ID column name
     *  @param [in] id -- the actual ID
     */
    static boolean deleteEntryByID(SQLiteDatabase db, String tableName, String idColName, FsID id)
    {  
       String where = SqlStr.genWhere(new SqlStr.SqlSimpCond(idColName, "=", id));
       
       boolean isOK = false;
       try {
    	  if (db.delete(tableName, where, null) > 0)
    	     isOK = true;
       }
       catch (Exception e) {
       	  SqlFsLog.debug(e);
       	  SqlFsErrCode.setCurrentError(FsErr.DeleteFsEntryError);
       }
       
       return isOK;
    }

    
    /**
     *  Get the last (autoIncrement) rowID after a 'INSERT'
     */
    static FsID getLastInsertID(SQLiteDatabase db)
    {
       // retrieve the ID of the new entry
       FsID newID = SqlFsConst.INVALIDID;
       String sql = "SELECT last_insert_rowid() as [id]";
       Cursor c = null;
       try {
          c = db.rawQuery(sql, null);
          if (c.moveToFirst()) {
             newID = SqlFsFunc.getID(c, 0);
          }
       }
       catch (Exception e) {
          SqlFsLog.debug(e);
          SqlFsErrCode.setCurrentError(FsErr.GetLastInsertIDError);
       }
       finally {
    	  SqlFsFunc.close(c);
       }

       return newID;
    }

    public SqlDir getRootDir()
    {
    	SqlFsErrCode.setCurrentError(FsErr.OK);
    	
    	fsLocker.getFsLock();
        try {
           return __getRootDir();
        }
        finally {
      	   fsLocker.dispose();
        }
    }

    /**
     *  Get root directory
     */
    private SqlDir __getRootDir()
    {
       SqlFsNode fsNode = getFsNodeByID(SqlFsConst.ROOTDIRID);
       return (SqlDir)fsNode;
    }
    
    public SqlFsNode getFsNode(String path)
    {
    	SqlFsErrCode.setCurrentError(FsErr.OK);
    	
    	fsLocker.getFsLock();
        try {
           return __getFsNode(path);
        }
        finally {
      	   fsLocker.dispose();
        }
    }

    /**
     *  @param [in] absolute dirPath -- e.g. "/path/to/dir"
     */
    private SqlFsNode __getFsNode(String path)
    {
       if (SqlFsFunc.isNullOrEmpty(path)) {
    	  SqlFsErrCode.setCurrentError(FsErr.EmptyString);
          return null;
       }

       if (!path.startsWith(SqlFsConst.STRPATHSEP)) {  // must start with '/'
    	  SqlFsErrCode.setCurrentError(FsErr.MustUseAbsolutePath);
          return null;
       }

       path = SqlFsFunc.trim(path, new char[]{SqlFsConst.PATHSEP});
       SqlDir rootDir = getRootDir();
       if (rootDir == null) {
    	  SqlFsErrCode.setCurrentError(FsErr.CannotAccessRoot);
          return null;
       }

       if (SqlFsFunc.isNullOrEmpty(path))   // if empty after trim, it refers to root
          return rootDir;

       return rootDir.getFsNode(path);
    }
    
    public SqlDir getDir(String dirPath)
    {
    	SqlFsErrCode.setCurrentError(FsErr.OK);
    	
    	fsLocker.getFsLock();
        try {
           return __getDir(dirPath);
        }
        finally {
      	   fsLocker.dispose();
        }
    }

    /**
     *  @param [in] absolute dirPath -- e.g. "/path/to/dir"
     */
    private SqlDir __getDir(String dirPath)
    {
       if (SqlFsFunc.isNullOrEmpty(dirPath)) {
    	  SqlFsErrCode.setCurrentError(FsErr.EmptyString);
          return null;
       }

       if (!dirPath.startsWith(SqlFsConst.STRPATHSEP)) {  // must start with '/'
    	  SqlFsErrCode.setCurrentError(FsErr.MustUseAbsolutePath);
          return null;
       }

       dirPath = SqlFsFunc.trim(dirPath, new char[]{SqlFsConst.PATHSEP});
       SqlDir rootDir = getRootDir();
       if (rootDir == null) {
    	  SqlFsErrCode.setCurrentError(FsErr.CannotAccessRoot);
          return null;
       }

       if (SqlFsFunc.isNullOrEmpty(dirPath))   // if empty after trim, it refers to root
          return rootDir;

       return rootDir.getDir(dirPath);
    }
    
    public SqlFile getFile(String filePath)
    {
    	SqlFsErrCode.setCurrentError(FsErr.OK);
    	
    	fsLocker.getFsLock();
        try {
           return __getFile(filePath);
        }
        finally {
      	   fsLocker.dispose();
        }
    }

    /**
     *  @param [in] absolute filePath -- e.g. "/path/to/file"
     */
    private SqlFile __getFile(String filePath)
    {
       if (SqlFsFunc.isNullOrEmpty(filePath)) {
    	  SqlFsErrCode.setCurrentError(FsErr.EmptyString);
          return null;
       }

       if (!filePath.startsWith(SqlFsConst.STRPATHSEP)) {  // must start with '/'
    	  SqlFsErrCode.setCurrentError(FsErr.MustUseAbsolutePath);
          return null;
       }

       filePath = SqlFsFunc.trim(filePath, new char[]{SqlFsConst.PATHSEP});
       
       SqlDir rootDir = getRootDir();
       if (rootDir == null) {
    	  SqlFsErrCode.setCurrentError(FsErr.CannotAccessRoot);
          return null;
       }

       return rootDir.getFile(filePath);
    }
    
    public boolean exists(String path)
    {
    	SqlFsErrCode.setCurrentError(FsErr.OK);
    	
    	fsLocker.getFsLock();
        try {
           return __exists(path);
        }
        finally {
      	   fsLocker.dispose();
        }
    }

    /**
     *  Test if a absolute path (dir/file) exists
     */
    private boolean __exists(String path)
    {
       if (SqlFsFunc.isNullOrEmpty(path)) {
     	  SqlFsErrCode.setCurrentError(FsErr.EmptyString);
          return false;
       }

       if (!path.startsWith(SqlFsConst.STRPATHSEP)) {  // must start with '/'
     	  SqlFsErrCode.setCurrentError(FsErr.MustUseAbsolutePath);
          return false;
       }

       path = SqlFsFunc.trim(path, new char[]{SqlFsConst.PATHSEP});
       if (SqlFsFunc.isNullOrEmpty(path))   // if empty after trim, it refers to root
          return true;

       SqlDir rootDir = getRootDir();
       if (rootDir == null) {
    	  SqlFsErrCode.setCurrentError(FsErr.CannotAccessRoot);
          return false;
       }

       SqlFsNode fsNode = rootDir.getFsNode(path);
       
       return (fsNode != null);
    }

}
