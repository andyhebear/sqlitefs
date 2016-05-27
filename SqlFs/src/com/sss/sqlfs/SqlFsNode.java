package com.sss.sqlfs;

import java.util.Calendar;
import java.util.ArrayList;

import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;
import android.content.ContentValues;

import com.sss.sqlfs.SqlFsErrCode.FsErr;
import com.sss.sqlfs.helper.*;

/**
 *  base for SqlDir and SqlFile
 */
public abstract class SqlFsNode 
{
	protected SQLiteDatabase db;   ///< sqlite connection
    protected FsID id;             ///< ID in FsBlock
    protected SqlFsLocker fsLocker;  ///< FS lock

    protected SqlFsNode() { }
    
    protected SqlFsNode(SQLiteDatabase db, SqlFsLocker fsLocker, FsID id)
    {
       this.db = db;
       this.fsLocker = fsLocker;
       this.id = id;
    }
    
    FsID getID()
    {
       return this.id;
    }
    
    public Calendar getCreateTime()
    {
       SqlFsErrCode.setCurrentError(FsErr.OK);
    	
       long fileTimeUtc = (Long)getField(SqlFs.FSBLOCK.fsCreateTime);
       return SqlFsFunc.fileTimeToCal(fileTimeUtc);
    }
    
    public Calendar getLastModTime()
    {
       SqlFsErrCode.setCurrentError(FsErr.OK);
    	
       long fileTimeUtc = (Long)getField(SqlFs.FSBLOCK.fsLastModTime);
       return SqlFsFunc.fileTimeToCal(fileTimeUtc);
    }
    
    public int getFileSize()
    {
       SqlFsErrCode.setCurrentError(FsErr.OK);
    	
       int fileSize = (Integer)getField(SqlFs.FSBLOCK.fsFileSize);
       return fileSize;
    }
    
    public String getName()
    {
       SqlFsErrCode.setCurrentError(FsErr.OK);
    	
       return (String)getField(SqlFs.FSBLOCK.fsName);
    }

    /**
     *  SqlFsConst.FSTYPE
     */
    public SqlFsConst.FSTYPE getType()
    {
       SqlFsErrCode.setCurrentError(FsErr.OK);
    	
       return SqlFsConst.FSTYPE.toFSTYPE((Integer)getField(SqlFs.FSBLOCK.fsType));       
    }      
    
    public SqlDir getParent()
    {
       SqlFsErrCode.setCurrentError(FsErr.OK);
    	
       FsID parentID = (FsID)getField(SqlFs.FSBLOCK.fsParent);
       return (SqlDir)SqlFs.getFsNodeByID(db, fsLocker, parentID);
    }
    
    
    public abstract boolean isDir();
    
    
    /**
     *  Convert ID list to byte array (little endian)
     */
    private static byte[] idList2Blob(ArrayList<FsID> idList)
    {
       if (idList == null || idList.size() == 0)
          return null;

       int j = 0;
       byte[] blob = new byte[idList.size() * FsID.getIDSize()];
       byte b;

       for(FsID item : idList) {
          // save integer in blob (little endian)
    	  long id = item.getVal();
          for (int i = 0; i < FsID.getIDSize(); ++i) {
             b = (byte)((id >> (i * 8)) & 0xff);
             blob[j++] = b;
          }
       }

       return blob;
    }
    
    /**
     *  Convert byte array to ID list (little endian)
     */
    private static ArrayList<FsID> blob2idList(byte[] blob)
    {
       if (blob == null)
          return null;

       ArrayList<FsID> idList = new ArrayList<FsID>(blob.length / FsID.getIDSize());

       for (int i = 0; i < blob.length; i += FsID.getIDSize()) {
          long t = 0;
          // get integer from blob (little endian)
          for (int j = 0; j < FsID.getIDSize(); ++j)
             t |= (((long)blob[i + j] & 0xff) << (j * 8));

          idList.add(new FsID(t));
       }

       return idList;
    }
    

    /**
     *  Get a fsNode from the result
     */
    static SqlFsNode getFsNode(SQLiteDatabase db, SqlFsLocker fsLocker, Cursor c)
    {
       SqlFsNode fsNode = null;
       SqlFsConst.FSTYPE type = SqlFsConst.FSTYPE.toFSTYPE(c.getInt(SqlFs.FSBLOCK.fsType.ordinal()));

       if (type == SqlFsConst.FSTYPE.DIR)
          fsNode = SqlDir.getDir(db, fsLocker, c);
       else if (type == SqlFsConst.FSTYPE.FILE)
          fsNode = SqlFile.getFile(db, fsLocker, c);

       return fsNode;
    }
    
    /**  
     *  Create a new entry in FsBlock
     *
     *  @return new ID for the inserted node
     */
    static FsID addFsNode(SQLiteDatabase db, SqlFsConst.FSTYPE type, String dirName, FsID parentID)
    {
       long curTime = SqlFsFunc.calToFileTime(Calendar.getInstance());
       ArrayList<Object> colsAndValues = new ArrayList<Object>(10);
       
       colsAndValues.add(SqlFs.FSBLOCK.fsCreateTime.toString()); colsAndValues.add(curTime);
       colsAndValues.add(SqlFs.FSBLOCK.fsLastModTime.toString()); colsAndValues.add(curTime);
       colsAndValues.add(SqlFs.FSBLOCK.fsFileSize.toString()); colsAndValues.add(0);
       colsAndValues.add(SqlFs.FSBLOCK.fsType.toString()); colsAndValues.add(type.v());
       colsAndValues.add(SqlFs.FSBLOCK.fsName.toString()); colsAndValues.add(dirName);
       colsAndValues.add(SqlFs.FSBLOCK.fsParent.toString()); colsAndValues.add(parentID);
       
       ContentValues contValues = SqlStr.genContentValues(colsAndValues);
       
       try {
          db.insert(SqlFs.DBNAMES.FsBlock.toString(), null, contValues);
       }
       catch (Exception e) {
    	  SqlFsLog.debug(e);
          SqlFsErrCode.setCurrentError(FsErr.AddFsNodeError);
          return SqlFsConst.INVALIDID;
       }

       // retrieve the ID of the new entry
       return SqlFs.getLastInsertID(db);
    }
        
    protected Object getField(SqlFs.FSBLOCK field)
    { 
       fsLocker.getFsLock();
       try {
          return __getField(field);
       }
       finally {
    	  fsLocker.dispose();
       }
    }
    
    
    /**
     *  Get a field from DB using ID
     */
    private Object __getField(SqlFs.FSBLOCK field) 
    {
    	String where = SqlStr.genWhere(new SqlStr.SqlSimpCond(SqlFs.FSBLOCK.fsID.toString(), "=", this.getID()));
        // little adjustment
        SqlFs.FSBLOCK tField = (field == SqlFs.FSBLOCK.fsChildCount) ? SqlFs.FSBLOCK.fsChild : field;
        Cursor c = null;
        Object val = null;
        
        try {
        	c = db.query(SqlFs.DBNAMES.FsBlock.toString(), 
                    new String[]{tField.toString()}, 
                    where, null, null, null, null);
        	
        	if (c.moveToFirst() && !c.isNull(0)) {
        		switch (field) {
        		 case fsCreateTime:
        		 case fsLastModTime:
                    val = c.getLong(0);
                    break;
        		 case fsFileSize:
                 case fsType:
                    val = c.getInt(0);
                    break;
                 case fsName:
                    val = c.getString(0);
                    break;
                 case fsParent:
                    val = SqlFsFunc.getID(c, 0);
                    break;
                 case fsChild:
                    {
                	   byte[] buf = c.getBlob(0);
                	   val = blob2idList(buf);
                    }
                    break;
                 case fsChildCount:
                    {
                	   byte[] buf = c.getBlob(0);
                       val = buf.length / FsID.getIDSize();
                    }
                    break;
        		}
        	}
        	else {
        		val = __getDefaultValue(field);
        	}
        }
        catch (Exception e) {
        	SqlFsLog.debug(e);
        	SqlFsErrCode.setCurrentError(FsErr.GetFieldError);
        	val = __getDefaultValue(field); // return a default value here 
        }
        finally {
        	SqlFsFunc.close(c);
        }
        
        return val;
    }
    
    private Object __getDefaultValue(SqlFs.FSBLOCK field)
    {
    	Object val = null;
    	
    	// set default values
        switch (field) {
           case fsCreateTime:
           case fsLastModTime:
              val = (long)0;
              break;
           case fsFileSize:
           case fsType:
              val = (int)0;
              break;
           case fsName:
              val = "";
              break;
           case fsParent:
              val = SqlFsConst.INVALIDID;
              break;
           case fsChildCount:
              val = (int)0;
              break;
        }
        
        return val;
    }
    
    protected boolean setField(SqlFs.FSBLOCK field, Object val)
    {
    	fsLocker.getFsLock();
        try {
           return __setField(field, val);
        }
        finally {
      	   fsLocker.dispose();
        }
    }
    
    /** 
     *  Save field to DB using ID
     */
    private boolean __setField(SqlFs.FSBLOCK field, Object val)
    {
    	ArrayList<Object> colsAndValues = new ArrayList<Object>(4);

        switch (field) {
          case fsFileSize:
          case fsType:
          case fsName:
          case fsParent:
             colsAndValues.add(field.toString());
             colsAndValues.add(val);
             break;
          case fsChild:
             byte[] blob = idList2Blob((ArrayList<FsID>)val);
             colsAndValues.add(field.toString());
             colsAndValues.add(blob);
             break;
        }

        // update last mod time as well
        colsAndValues.add(SqlFs.FSBLOCK.fsLastModTime.toString());
        colsAndValues.add(SqlFsFunc.calToFileTime(Calendar.getInstance()));
        ContentValues contValues = SqlStr.genContentValues(colsAndValues);

    	String where = SqlStr.genWhere(new SqlStr.SqlSimpCond(SqlFs.FSBLOCK.fsID.toString(), "=", this.getID()));
    	
    	int rowAffected = 0;
    	
        try {
           rowAffected = db.update(SqlFs.DBNAMES.FsBlock.toString(), contValues, where, null);
        }
        catch (Exception e) {
           SqlFsLog.debug(e);
           SqlFsErrCode.setCurrentError(FsErr.SetFieldError);
        }

        return (rowAffected > 0);
    }
    
    /**
     *  Check if a dir/file name contains invalid character
     *
     *  @param [in] name -- contains dir/file name to be checked 
     *                      on return, it contains a whitespace-trimmed version
     *
     *  @return null -- contains invalid characters
     *  @return String -- may be modified name
     */
    protected String checkInvalidChars(String name)
    {
       if (SqlFsFunc.isNullOrEmpty(name))
          return null;

       String trimmedStr = SqlFsFunc.trim(name, SqlFsConst.CHARSTOTRIM);

       if (SqlFsFunc.isNullOrEmpty(trimmedStr))  // check once again
          return null;

       if (SqlFsFunc.indexOfAny(trimmedStr, SqlFsConst.INVALIDCHARS) >= 0)
          return null;

       if (trimmedStr.equals(SqlFsConst.CURDIR)) // can't be '.'
          return null;

       if (trimmedStr.equals(SqlFsConst.PARENTDIR)) // can't be '..'
          return null;

       return trimmedStr;
    }
    
    public boolean rename(String newName)
    {
       SqlFsErrCode.setCurrentError(FsErr.OK);
    	
       boolean isOK = false;
       fsLocker.getFsLock();
       SqlFsTransaction fsTran = new SqlFsTransaction(db);
       try {
    	  isOK = __rename(newName);
    	  if (isOK)
             fsTran.fsOpSuccess();
       }
       finally {
    	  fsTran.dispose();
    	  fsLocker.dispose();
       }
       
       return isOK;
    }

    /**
     *  Rename dir/file
     */
    private boolean __rename(String newName)
    {
       if ((newName = checkInvalidChars(newName)) == null) {
    	  SqlFsErrCode.setCurrentError(FsErr.InvalidChars);
          return false;
       }

       if (this.getID().equals(SqlFsConst.ROOTDIRID)) {  // can't rename root
    	  SqlFsErrCode.setCurrentError(FsErr.CannotRenameRoot); 
          return false;
       }
       
       SqlDir parentDir = this.getParent();
       if (parentDir == null) {
    	  SqlFsErrCode.setCurrentError(FsErr.NoParent);
          return false;
       }

       if (parentDir.isAlreadyExist(newName)) { // already exists or not ?
    	  SqlFsErrCode.setCurrentError(FsErr.NameAlreadyExists);
          return false;
       }

       setField(SqlFs.FSBLOCK.fsName, newName);

       return true;
    }
    
    /**
     *  delete itself
     */
    public abstract boolean delete();

    public boolean isAncestor(SqlDir dir)
    {
       SqlFsErrCode.setCurrentError(FsErr.OK);
    	
       fsLocker.getFsLock();
       try {
          return __isAncestor(dir);
       }
       finally {
     	  fsLocker.dispose();
       }
    }

    /**
     *  Check if passed in 'dir' is one of its ancestor
     */
    private boolean __isAncestor(SqlDir dir)
    {
       boolean found = false;
       SqlFsNode curNode = this;

       do {
          curNode = curNode.getParent();
          if (curNode == null)
             break;

          if (curNode.getID().equals(dir.getID())) {
             found = true;
             break;
          }

          if (curNode.getID().equals(SqlFsConst.ROOTDIRID)) // reach root
             break;

       } while(true);

       return found;
    }

    public boolean move(String destPath)
    {
    	SqlFsErrCode.setCurrentError(FsErr.OK);
    	
    	boolean isOK = false;
        fsLocker.getFsLock();
        SqlFsTransaction fsTran = new SqlFsTransaction(db);
        try {
     	   isOK = __move(destPath);
     	   if (isOK)
     	      fsTran.fsOpSuccess();
        }
        finally {
     	   fsTran.dispose();
     	   fsLocker.dispose();
        }
        
        return isOK;
    }
    
    /**
     *  Move itself to a destination path (absolute or relative)
     */
    private boolean __move(String destPath)
    {
       if (SqlFsFunc.isNullOrEmpty(destPath)) {
    	  SqlFsErrCode.setCurrentError(FsErr.EmptyString); 
          return false;
       }

       // determine destination dir
       SqlDir destDir = null;
       if (destPath.startsWith(SqlFsConst.STRPATHSEP)) { 
          // absolute path
          SqlDir rootDir = (SqlDir)SqlFs.getFsNodeByID(db, fsLocker, SqlFsConst.ROOTDIRID); // get root
          destPath = SqlFsFunc.trim(destPath, new char[]{SqlFsConst.PATHSEP});
          // if empty after trim, it refers to root
          destDir = SqlFsFunc.isNullOrEmpty(destPath) ? rootDir : rootDir.getDir(destPath);
       }
       else {
          // relative path
          SqlDir parent = this.getParent();
          if (parent != null)
             destDir = parent.getDir(destPath);
       }

       if (destDir != null)
          return __move(destDir);

       SqlFsErrCode.setCurrentError(FsErr.DestDirNotFound);
       return false;
    }
    
    public boolean move(SqlDir destDir)
    {
    	SqlFsErrCode.setCurrentError(FsErr.OK);
    	
    	boolean isOK = false;
        fsLocker.getFsLock();
        SqlFsTransaction fsTran = new SqlFsTransaction(db);
        try {
     	   isOK = __move(destDir);
     	   if (isOK)
     	      fsTran.fsOpSuccess();
        }
        finally {
     	   fsTran.dispose();
     	   fsLocker.dispose();
        }
        
        return isOK;
    }

    /**
     *  Move itself to a destination dir
     */
    private boolean __move(SqlDir destDir)
    {
       do {
          if (this.getID().equals(SqlFsConst.ROOTDIRID)) {  // can't move root
    	     SqlFsErrCode.setCurrentError(FsErr.CannotMoveRoot);
             break;
          }

          if (this.getID().equals(destDir.getID())) {   // can't move to itself
        	 SqlFsErrCode.setCurrentError(FsErr.CannotMoveToSelf);
        	 break;
          }

          if (this.isDir() && destDir.isAncestor((SqlDir)this)) { // if it is a DIR, can't move to its subdir
    	     SqlFsErrCode.setCurrentError(FsErr.CannotMoveToSubdir);
             break;
          }

          if (destDir.isAlreadyExist(this.getName())) { // can't move if there is one with the same name
    	     SqlFsErrCode.setCurrentError(FsErr.NameAlreadyExists);
             break;
          }

          // unlink itself from parent
          SqlDir parent = this.getParent();
          if (parent == null) {
    	     SqlFsErrCode.setCurrentError(FsErr.NoParent);
             break;
          }

          if (!parent.updateChildList(SqlFsConst.FSOP.DEL, this.getID(), FsID.toFsID(0)))
    	     break;

          // update parent ID
          if (!setField(SqlFs.FSBLOCK.fsParent, destDir.getID()))
        	 break;

          // add itself to dest dir
          if (!destDir.updateChildList(SqlFsConst.FSOP.ADD, this.getID(), FsID.toFsID(0)))
    	     break;
       
          return true;
          
       } while(false);

       return false;
    }

}
