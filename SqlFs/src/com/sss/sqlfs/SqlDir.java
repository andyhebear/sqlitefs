package com.sss.sqlfs;

import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;

import com.sss.sqlfs.SqlFsErrCode.FsErr;
import com.sss.sqlfs.helper.*;

import java.util.ArrayList;

/**
 *  a class for dir
 */
public class SqlDir extends SqlFsNode
{
	
	static SqlDir getDir(SQLiteDatabase db, SqlFsLocker fsLocker, FsID id)
    {
       SqlDir d = new SqlDir(db, fsLocker, id);
       return d;
    }

    static SqlDir getDir(SQLiteDatabase db, SqlFsLocker fsLocker, Cursor c)
    {
       FsID id = SqlFsFunc.getID(c, SqlFs.FSBLOCK.fsID.ordinal());
       return SqlDir.getDir(db, fsLocker, id);
    }

	private SqlDir() { }

    private SqlDir(SQLiteDatabase db, SqlFsLocker fsLocker, FsID id)
    {
    	super(db, fsLocker, id);
    }
    
    @Override
    public boolean isDir()
    {
       return true; 
    }
    
    public int getChildCount()
    {
       SqlFsErrCode.setCurrentError(FsErr.OK);
    	
       return (Integer)getField(SqlFs.FSBLOCK.fsChildCount);
    }

    /**
     *  @param [in] name -- if null, get all entry
     *  @param [in] type -- dir, file or any ?
     */
    private Cursor getEntryByName(String name, SqlFsConst.FSTYPE type)
    {
       // conditions
       ArrayList<Object> conds = new ArrayList<Object>(10);
       conds.add(new SqlStr.SqlSimpCond(SqlFs.FSBLOCK.fsParent.toString(), "=", this.getID()));

       if (name != null) {
          conds.add("and");
          conds.add(new SqlStr.SqlSimpCond(SqlFs.FSBLOCK.fsName.toString(), "=", name));
       }

       if (type != SqlFsConst.FSTYPE.ANY) {
          conds.add("and");
          conds.add(new SqlStr.SqlSimpCond(SqlFs.FSBLOCK.fsType.toString(), "=", type.v()));
       }

       String where = SqlStr.genWhere(conds);

       // query from DB
       Cursor c = null;
       try {
    	   c = db.query(SqlFs.DBNAMES.FsBlock.toString(), 
                        new String[]{SqlFs.FSBLOCK.fsID.toString(), SqlFs.FSBLOCK.fsType.toString()}, 
                        where, null, null, null, null);
       } 
       catch (Exception e) {
          SqlFsLog.debug(e);
          SqlFsErrCode.setCurrentError(SqlFsErrCode.FsErr.NoEntryByName);
       }

       // close and set to null if no rows at all
       if (c != null && c.getCount() == 0) {
          c.close();
          c = null;
       }

       return c;
    }
    
    public boolean isAlreadyExist(String name)
    {
    	SqlFsErrCode.setCurrentError(FsErr.OK);
    	
    	fsLocker.getFsLock();
        try {
           return __isAlreadyExist(name);
        }
        finally {
      	  fsLocker.dispose();
        }
    }

    /**
     *  Find if there is any child with the same name
     */
    private boolean __isAlreadyExist(String name)
    {
       Cursor c = getEntryByName(name, SqlFsConst.FSTYPE.ANY);
       boolean isExist = false;
       if (c != null) {
          isExist = (c.getCount() > 0);
          c.close();
       }

       return isExist;
    }
    
    /**
     *  Create an empty dir
     *
     *  @return new ID for the inserted dir
     */
    static FsID addDir(SQLiteDatabase db, String dirName, FsID parentID)
    {
       return SqlFsNode.addFsNode(db, SqlFsConst.FSTYPE.DIR, dirName, parentID);
    }
    
    /**
     *  Update the INT child list to db
     *
     *  @param [in] fsOp -- ADD, REPLACE or DEL
     *  @param [in] id1 -- id to be added or replaced or deleted 
     *  @param [in] id2 -- for REPLACE only (the new ID)
     */
    boolean updateChildList(SqlFsConst.FSOP fsOp, FsID id1, FsID id2)
    {
       ArrayList<FsID> childList = (ArrayList<FsID>)getField(SqlFs.FSBLOCK.fsChild);
       
       boolean isMod = false;

       switch (fsOp) {
          case ADD:
             if (childList == null)
                childList = new ArrayList<FsID>(1);  // make a new one if empty

             if (childList.indexOf(id1) < 0) { // add only if it is not already there
                childList.add(id1);
                isMod = true;
             }
             break;
          case REPLACE:
             if (childList == null)
                break;

             int index = childList.indexOf(id1);
             if (index >= 0) {
                childList.set(index, id2);
                isMod = true;
             }
             break;
          case DEL:
             if (childList == null)
                break;
             if (childList.remove(id1))
                isMod = true;             
             break;
       }
       
       if (isMod)
          return setField(SqlFs.FSBLOCK.fsChild, childList);

       SqlFsErrCode.setCurrentError(FsErr.ChildListNotUpdated);
       return false;
    }
    
    public SqlDir addDir(String dirName)
    {
    	SqlFsErrCode.setCurrentError(FsErr.OK);
    	
    	SqlDir childDir = null;
    	fsLocker.getFsLock();
        SqlFsTransaction fsTran = new SqlFsTransaction(db);
        try {
           childDir = __addDir(dirName);
           if (childDir != null)
              fsTran.fsOpSuccess();
        }
        finally {
     	   fsTran.dispose();
     	   fsLocker.dispose();
        }
        
        return childDir;
    }
    
    /**
     *   Add an empty dir
     *
     *   @return the newly created dir
     */
    private SqlDir __addDir(String dirName)
    {
       if ((dirName = checkInvalidChars(dirName)) == null) {
    	  SqlFsErrCode.setCurrentError(FsErr.InvalidChars);
          return null;
       }

       if (__isAlreadyExist(dirName)) {
    	  SqlFsErrCode.setCurrentError(FsErr.NameAlreadyExists);
          return null;
       }

       FsID newID = SqlDir.addDir(db, dirName, this.getID());
       if (newID.compare(SqlFsConst.INVALIDID) <= 0) {
    	  SqlFsErrCode.setCurrentError(FsErr.NoNewIDForNewFsNode);
          return null;
       }

       if ( ! updateChildList(SqlFsConst.FSOP.ADD, newID, FsID.toFsID(0))) {
          // delete entry just created
          SqlFs.deleteEntryByID(db, SqlFs.DBNAMES.FsBlock.toString(), SqlFs.FSBLOCK.fsID.toString(), newID);
          return null;
       }

       return SqlDir.getDir(db, fsLocker, newID);
    }

    public SqlFile addFile(String fileName)
    {
    	SqlFsErrCode.setCurrentError(FsErr.OK);
    	
    	SqlFile newFile = null;
    	fsLocker.getFsLock();
        SqlFsTransaction fsTran = new SqlFsTransaction(db);
        try {
        	newFile = __addFile(fileName);
        	if (newFile != null)
        	   fsTran.fsOpSuccess();
        }
        finally {
     	   fsTran.dispose();
     	   fsLocker.dispose();
        }
        
        return newFile;
    }

    /**
     *  Add a new entry for file. File must be saved so as to be 
     *  appeared under its parent directory.
     */
    private SqlFile __addFile(String fileName)
    {
       if ((fileName = checkInvalidChars(fileName)) == null) {
    	  SqlFsErrCode.setCurrentError(FsErr.InvalidChars);
    	  return null;
       }

       if (__isAlreadyExist(fileName)) {
    	  SqlFsErrCode.setCurrentError(FsErr.NameAlreadyExists);
          return null;
       }

       FsID newID = SqlFile.addFile(db, fileName, this.getID());
       if (newID.compare(SqlFsConst.INVALIDID) <= 0) {
    	  SqlFsErrCode.setCurrentError(FsErr.NoNewIDForNewFsNode);
          return null;
       }

       if ( ! updateChildList(SqlFsConst.FSOP.ADD, newID, FsID.toFsID(0))) {
          // delete entry just created
          SqlFs.deleteEntryByID(db, SqlFs.DBNAMES.FsBlock.toString(), SqlFs.FSBLOCK.fsID.toString(), newID);
          return null;
       }

       SqlFile f = SqlFile.getFile(db, fsLocker, newID);
       f.setDataBlockID(SqlFsConst.NOFILEDATAID);
       return f;
    }

    @Override
    public boolean delete()
    {
    	SqlFsErrCode.setCurrentError(FsErr.OK);
	
    	boolean isOK = false;
    	fsLocker.getFsLock();
        SqlFsTransaction fsTran = new SqlFsTransaction(db);
        try {
           isOK = __delete();
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
     *  delete all dirs and files recursively
     */
    private boolean __delete()
    {
       boolean isOK = false;

       do {
          // delete itself from parent
          if (!this.getID().equals(SqlFsConst.ROOTDIRID)) {  // root has no parent
             SqlDir parent = this.getParent();
             if (parent == null) {
            	SqlFsErrCode.setCurrentError(FsErr.NoParent);
                break;
             }

             if (!parent.updateChildList(SqlFsConst.FSOP.DEL, this.getID(), FsID.toFsID(0)))
            	break;
          }
          
          // delete underlying subdirs and files
          ArrayList<SqlFsNode> childList = this.getChildList();

          if (childList != null) {
             // delete children one by one
             for(SqlFsNode fsNode : childList) {
                fsNode.delete();
             }
          }

          // delete itself
          if (this.getID().equals(SqlFsConst.ROOTDIRID)) {
             // for root, just clear all children
             this.setField(SqlFs.FSBLOCK.fsChild, null);
          }
          else {
             if (!SqlFs.deleteEntryByID(db, SqlFs.DBNAMES.FsBlock.toString(), 
            		                    SqlFs.FSBLOCK.fsID.toString(), this.getID())) {
            	 SqlFsErrCode.setCurrentError(FsErr.CannotDeleteFsEntry);
            	 break;
             }
          }
          
          isOK = true;
       } while(false);

       return isOK;
    }     


    public SqlFsNode getChild(String name)
    {
    	SqlFsErrCode.setCurrentError(FsErr.OK);
    	
    	fsLocker.getFsLock();
        try {
           return __getChild(name);
        }
        finally {
      	   fsLocker.dispose();
        }
    }

    /**
     *  Get a single child by name
     *  
     *  @param [in] name -- name of child. If name is "..", it gets parent. If name is ".", return itself
     */
    private SqlFsNode __getChild(String name)
    {
       SqlFsNode fsNode = null;

       if (name.equals(SqlFsConst.CURDIR)) {
          fsNode = this;  // current dir
       }
       else if (name.equals(SqlFsConst.PARENTDIR)) {
    	  // parent or itself if already root
          fsNode = (this.getID().equals(SqlFsConst.ROOTDIRID) ? this : this.getParent()); 
       }
       else {

          Cursor c = getEntryByName(name, SqlFsConst.FSTYPE.ANY);
          if (c != null) {
             c.moveToFirst();
             fsNode = SqlFsNode.getFsNode(db, fsLocker, c);
             c.close();
          }
          else {
        	 SqlFsErrCode.setCurrentError(SqlFsErrCode.FsErr.NoEntryByName);
          }
       }

       return fsNode;
    }
    
    public ArrayList<SqlFsNode> getChildList()
    {
    	SqlFsErrCode.setCurrentError(FsErr.OK);
    	
    	fsLocker.getFsLock();
        try {
           return __getChildList();
        }
        finally {
       	   fsLocker.dispose();
        }
    }
    
    /**
     *  Get a list of children (dirs and files)
     */
    private ArrayList<SqlFsNode> __getChildList()
    {          
       ArrayList<SqlFsNode> childList = null;
       Cursor c = getEntryByName(null, SqlFsConst.FSTYPE.ANY);
       if (c != null) {
    	  c.moveToFirst();
          childList = new ArrayList<SqlFsNode>(c.getCount());
          do {
             SqlFsNode fsNode = SqlFsNode.getFsNode(db, fsLocker, c);
             if (fsNode != null)
                childList.add(fsNode);
          } while(c.moveToNext());
          c.close();
       }

       return childList;
    }
    
    public ArrayList<SqlDir> getSubDirs()
    {
    	SqlFsErrCode.setCurrentError(FsErr.OK);
    	
    	fsLocker.getFsLock();
        try {
           return __getSubDirs();
        }
        finally {
       	   fsLocker.dispose();
        }
    }

    /**
     *  Get list of subdirs only
     */
    private ArrayList<SqlDir> __getSubDirs()
    {
       ArrayList<SqlDir> dirList = null;
       Cursor c = getEntryByName(null, SqlFsConst.FSTYPE.DIR);
       if (c != null) {
    	  c.moveToFirst();
          dirList = new ArrayList<SqlDir>(c.getCount());
          do {
             SqlDir dir = SqlDir.getDir(db, fsLocker, c);
             if (dir != null)
                dirList.add(dir);
          } while(c.moveToNext());
          c.close();
       }

       return dirList;
    }
    
    public ArrayList<SqlFile> getFiles()
    {
    	SqlFsErrCode.setCurrentError(FsErr.OK);
    	
    	fsLocker.getFsLock();
        try {
           return __getFiles();
        }
        finally {
       	   fsLocker.dispose();
        }
    }

    /**
     *  Get list of files only
     */
    private ArrayList<SqlFile> __getFiles()
    {
       ArrayList<SqlFile> fileList = null;
       Cursor c = getEntryByName(null, SqlFsConst.FSTYPE.FILE);
       if (c != null) {
    	  c.moveToFirst();
          fileList = new ArrayList<SqlFile>(c.getCount());
          do {
             SqlFile f = SqlFile.getFile(db, fsLocker, c);
             if (f != null)
                fileList.add(f);
          } while(c.moveToNext());
          c.close();
       }

       return fileList;
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
     *  @param [in] relative dirPath -- e.g. "path/to/dir"
     */
    private SqlFsNode __getFsNode(String path)
    {
       if (SqlFsFunc.isNullOrEmpty(path)) {
    	  SqlFsErrCode.setCurrentError(FsErr.EmptyString);
          return null;
       }

       if (path.startsWith(SqlFsConst.STRPATHSEP)) { // must *NOT* start with '/'
    	  SqlFsErrCode.setCurrentError(FsErr.MustUseRelativePath);
          return null;
       }

       if (path.endsWith(SqlFsConst.STRPATHSEP))  // trim trailing '/'
          path = SqlFsFunc.trimEnd(path, new char[]{SqlFsConst.PATHSEP});

       String[] pathSeg = path.split(SqlFsConst.STRPATHSEP);
       if (pathSeg == null || pathSeg.length <= 0) {
    	  SqlFsErrCode.setCurrentError(FsErr.SplitPathErr);
          return null;
       }

       SqlFsNode curNode = this;

       // start looping to target node
       for (int i = 0; i < pathSeg.length; ++i) {
          if (SqlFsFunc.isNullOrEmpty(pathSeg[i]))  // to prevent empty space between separator
             continue;

          curNode = ((SqlDir)curNode).__getChild(pathSeg[i]);
          if (curNode == null) {
        	 SqlFsErrCode.setCurrentError(FsErr.ChildNotFound);
             break;
          }

          if (!curNode.isDir() && i != pathSeg.length - 1) {  // if a file but not reach the end yet
             curNode = null;
             SqlFsErrCode.setCurrentError(FsErr.NotDirInPath);
             break;
          }
       }

       return curNode;
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
     *  @param [in] relative dirPath -- e.g. "path/to/dir"
     */
    private SqlDir __getDir(String dirPath)
    {
       if (SqlFsFunc.isNullOrEmpty(dirPath)) {
    	  SqlFsErrCode.setCurrentError(FsErr.EmptyString); 
          return null;
       }

       if (dirPath.startsWith(SqlFsConst.STRPATHSEP)) { // must *NOT* start with '/'
    	  SqlFsErrCode.setCurrentError(FsErr.MustUseRelativePath); 
          return null;
       }

       if (dirPath.endsWith(SqlFsConst.STRPATHSEP))  // trim trailing '/'
          dirPath = SqlFsFunc.trimEnd(dirPath, new char[]{SqlFsConst.PATHSEP});

       String[] dirSeg = dirPath.split(SqlFsConst.STRPATHSEP);
       if (dirSeg == null || dirSeg.length <= 0) {
    	  SqlFsErrCode.setCurrentError(FsErr.SplitPathErr); 
          return null;
       }

       SqlFsNode curNode = this;

       // start looping to target dir
       for(String dir : dirSeg) {
          if (SqlFsFunc.isNullOrEmpty(dir))  // to prevent empty space between separator
             continue;

          curNode = ((SqlDir)curNode).__getChild(dir);
          if (curNode == null || !curNode.isDir()) {
             curNode = null;
             SqlFsErrCode.setCurrentError(FsErr.ChildNotFound);
             break;
          }
       }
       

       return (SqlDir)curNode;
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
     *  @param [in] relative filePath -- e.g. "path/to/file"
     */
    private SqlFile __getFile(String filePath)
    {
       if (SqlFsFunc.isNullOrEmpty(filePath)) {
    	  SqlFsErrCode.setCurrentError(FsErr.EmptyString); 
          return null;
       }

       if (filePath.startsWith(SqlFsConst.STRPATHSEP) ||
           filePath.endsWith(SqlFsConst.STRPATHSEP)) {     // must *NOT* start or end with '/'
    	  SqlFsErrCode.setCurrentError(FsErr.MustNotStartOrEndWithPathSeparator);
          return null;
       }

       String[] dirSeg = filePath.split(SqlFsConst.STRPATHSEP);
       if (dirSeg == null || dirSeg.length <= 0) {
    	  SqlFsErrCode.setCurrentError(FsErr.SplitPathErr); 
          return null;
       }

       SqlFsNode curNode = this;

       // start looping to target file
       for (int i = 0; i < dirSeg.length; ++i) {
          if (SqlFsFunc.isNullOrEmpty(dirSeg[i]))  // to prevent empty space between separator
             continue;

          curNode = ((SqlDir)curNode).__getChild(dirSeg[i]);
          if (i == dirSeg.length - 1) {
             if (curNode == null || curNode.isDir()) {  // last one must be a file
                curNode = null;
                SqlFsErrCode.setCurrentError(FsErr.ChildNotFound);
                break;
             }
          }
          else {
           
             if (curNode == null || !curNode.isDir()) { // others' should be dir
                curNode = null;
                SqlFsErrCode.setCurrentError(FsErr.NotDirInPath);
                break;
             }
          }
       }
       
       return (SqlFile)curNode;
    }
}
