package com.sss.sqlfs;

import java.util.ArrayList; 

import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;

import com.sss.sqlfs.SqlFs.FSBLOCK;
import com.sss.sqlfs.SqlFsErrCode.FsErr;
import com.sss.sqlfs.helper.*;

/**
 *  a class for file
 */
public class SqlFile extends SqlFsNode 
{
	static SqlFile getFile(SQLiteDatabase db, SqlFsLocker fsLocker, FsID id)
    {
       SqlFile f = new SqlFile(db, fsLocker, id);
       return f;
    }

    static SqlFile getFile(SQLiteDatabase db, SqlFsLocker fsLocker, Cursor c)
    {
       FsID id = SqlFsFunc.getID(c, SqlFs.FSBLOCK.fsID.ordinal());
       return SqlFile.getFile(db, fsLocker, id);
    }
    
    /**
     *  Create an empty file
     *
     *  @return new ID for the inserted dir
     */
    static FsID addFile(SQLiteDatabase db, String fileName, FsID parentID)
    {
       return SqlFsNode.addFsNode(db, SqlFsConst.FSTYPE.FILE, fileName, parentID);
    }
    
	private SqlFile() { }

    private SqlFile(SQLiteDatabase db, SqlFsLocker fsLocker, FsID id)
    {
    	super(db, fsLocker, id);
    }

    @Override
    public boolean isDir()
    {
       return false; 
    }
    
    FsID getDataBlockID()
    {
       ArrayList<FsID> dbID = (ArrayList<FsID>)getField(SqlFs.FSBLOCK.fsChild);
       if (dbID != null && !dbID.isEmpty())
          return dbID.get(0);
       return SqlFsConst.INVALIDID;
    }

    void setDataBlockID(FsID value) 
    {
       ArrayList<FsID> dbID = new ArrayList<FsID>(1);
       dbID.add(value);
       setField(SqlFs.FSBLOCK.fsChild, dbID);
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
    
    private boolean __delete()
    {
       boolean isOK = false;

       do {
          // delete itself from parent
          SqlDir parent = this.getParent();
          if (parent == null) {
        	 SqlFsErrCode.setCurrentError(FsErr.NoParent);
             break;
          }

          if (!parent.updateChildList(SqlFsConst.FSOP.DEL, this.getID(), FsID.toFsID(0)))
        	 break;

          // delete entry in data block table
          FsID dataBlockID = this.getDataBlockID();
          if (dataBlockID.compare(SqlFsConst.INVALIDID) <= 0) {
//SqlFsLog.debug("+++ dataBlockID = " + dataBlockID.getVal());
        	 SqlFsErrCode.setCurrentError(FsErr.DataBlockIDNotValid);
             break;
          }

          if (!SqlFs.deleteEntryByID(db, IFileData.DTABLENAME, IFileData.IDCOL, dataBlockID)) {
        	 SqlFsErrCode.setCurrentError(FsErr.CannotDeleteDataBlockEntry);
             break;
          }

          // delete its own entry
          if (!SqlFs.deleteEntryByID(db, SqlFs.DBNAMES.FsBlock.toString(), 
        		                     SqlFs.FSBLOCK.fsID.toString(), this.getID())) {
        	 SqlFsErrCode.setCurrentError(FsErr.CannotDeleteFsEntry);
             break;
          }

          isOK = true;

       } while(false);

       return isOK;
    }

    public boolean getFileData(IFileData fileData)
    {
    	SqlFsErrCode.setCurrentError(FsErr.OK);
    	
    	fsLocker.getFsLock();
        try {
           return __getFileData(fileData);
        }
        finally {
      	   fsLocker.dispose();
        }
    }

    /**
     *  Retrieve data from data block table
     */
    private boolean __getFileData(IFileData fileData)
    {
       FsID dataBlockID = this.getDataBlockID();
       if (dataBlockID.compare(SqlFsConst.INVALIDID) <= 0) {
    	  SqlFsErrCode.setCurrentError(FsErr.DataBlockIDNotValid);
          return false;
       }
       
       return fileData.getData(db, dataBlockID);
    }
    
    public boolean saveFileData(IFileData fileData)
    {
    	SqlFsErrCode.setCurrentError(FsErr.OK);
    	
    	boolean isOK = false;
    	fsLocker.getFsLock();
        SqlFsTransaction fsTran = new SqlFsTransaction(db);
        try {
           isOK = __saveFileData(fileData);
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
     *  Save data to data block table
     */
    private boolean __saveFileData(IFileData fileData)
    {
       boolean isOK = false;
       
       do {
           FsID dataBlockID = this.getDataBlockID();        
           if (dataBlockID.compare(SqlFsConst.INVALIDID) == 0) {  // new file without data will be -1
        	  SqlFsErrCode.setCurrentError(FsErr.DataBlockIDNotValid);
              break;
           }

           FsID newDataBlockID = fileData.saveData(db, dataBlockID);
           if (newDataBlockID.compare(SqlFsConst.INVALIDID) <= 0) {
        	  SqlFsErrCode.setCurrentError(FsErr.SaveFileDataErr);
              break;
           }
           
           // update file size
           this.setField(FSBLOCK.fsFileSize, fileData.getDataSizeInByte());

           // update itself (data block table) so that last mod time can be updated
           this.setDataBlockID(newDataBlockID); 

           isOK = true;
       } while (false);

       return isOK;
    }
}
