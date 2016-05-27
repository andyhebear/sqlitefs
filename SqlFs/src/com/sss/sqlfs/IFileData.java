package com.sss.sqlfs;

import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;
import android.util.Log;
import android.content.ContentValues;

import com.sss.sqlfs.SqlFsErrCode.FsErr;
import com.sss.sqlfs.helper.SqlStr;

/**
 *  abstract class of file data
 */
public abstract class IFileData 
{
	//! data table name
    public static final String DTABLENAME = "DataBlock";
    //! required ID column name
    public static final String IDCOL = "dID";
    //! ID column type
    public static final String IDCOLTYPE = "integer primary key autoincrement";

    /**
     *  For derived class to return DataBlock table schema
     */
    public abstract String[][] getColSchema();

    protected FsID getLastInsertID(SQLiteDatabase db)
    {
       return SqlFs.getLastInsertID(db);
    }
    
    /**
     *  Called by SqlFile.GetFileData
     */
    boolean getData(SQLiteDatabase db, FsID dataBlockID)
    {
       if (! __getData(db, dataBlockID)) {
    	  SqlFsErrCode.setCurrentError(FsErr.GetFileDataErr);
          return false;  
       }
       
       return true;
    }

    /**
     *  Get data from data block table
     *
     *  @param [in] dataBlockID -- the data block ID
     *
     *  @return true if OK
     *  @return false if failed
     */
    private boolean __getData(SQLiteDatabase db, FsID dataBlockID)
    {
    	String where = SqlStr.genWhere(new SqlStr.SqlSimpCond(IFileData.IDCOL, "=", dataBlockID));
 	    Cursor c = null;
 	       
        try {
     	   
     	  c = db.query(IFileData.DTABLENAME.toString(), null, 
                        where, null, null, null, null);
     	   
     	  if (c.moveToFirst()) {
     		 __getData(c);
     	  }
        } 
        catch (Exception e) {
          Log.d("IFileData.__getData", e.getMessage());
          return false;
        }
        finally {
        	  if (c != null) c.close();
        }
 	
        return true;
    }
    
    /**
     * Overridden by derived class to get data from cursor 
     * 
     * @param c -- cursor queried
     */
    protected abstract void __getData(Cursor c);

    /**
     *  Called by SqlFile.SaveFileData
     */
    FsID saveData(SQLiteDatabase db, FsID dataBlockID)
    {
       return __saveData(db, dataBlockID);
    }

    /**
     * 
     *  Save data to data block table
     *
     *  @param [in] dataBlockID -- the data block ID if exist. Pass 0 if not exist
     *
     *  @return dataBlockID if OK
     *  @return 0 if failed
     */
    private FsID __saveData(SQLiteDatabase db, FsID dataBlockID)
    {
    	
    	ContentValues contValues = __saveData();
    	
        try {
			
			if (dataBlockID.compare(SqlFsConst.INVALIDID) <= 0) {
						
			   // save new data
			   if (db.insert(IFileData.DTABLENAME.toString(), null, contValues) < 0)
				  dataBlockID = SqlFsConst.INVALIDID;
			   else
		          dataBlockID = getLastInsertID(db);
			}
			else {
				
			   // update data
			   String where = SqlStr.genWhere(new SqlStr.SqlSimpCond(IFileData.IDCOL, "=", dataBlockID));
		       if (db.update(IFileData.DTABLENAME.toString(), contValues, where, null) == 0)
		          dataBlockID = SqlFsConst.INVALIDID;
			}
		}
	    catch (Exception e) {
	       Log.d("IFileData.__saveData", e.getMessage());
	       dataBlockID = SqlFsConst.INVALIDID;
	    }
		
		return dataBlockID;
    }
    
    /**
     * Overridden by derived class to return a ContentValues 
     * to be inserted or updated to DB
     * 
     * @return
     */
	protected abstract ContentValues __saveData();
	
	/**
     * Overridden by derived class to return the number of bytes occupied by the underlying 
     * file data
     */
	public abstract int getDataSizeInByte();
}
