package com.test.sqlfs;

import java.util.ArrayList;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.sss.sqlfs.FsID;
import com.sss.sqlfs.IFileData;
import com.sss.sqlfs.SqlFs;
import com.sss.sqlfs.SqlFsErrCode;
import com.sss.sqlfs.SqlFsNode;
import com.sss.sqlfs.SqlFsConst;
import com.sss.sqlfs.SqlFsErrCode.FsErr;
import com.sss.sqlfs.helper.SqlStr;

public class UrlFileData extends IFileData
{
	//! fields in addition to dID
    protected enum URLDATABLOCK 
    {
       dAttr, 
       dUrl
    };

    
    ///////// data of this class //////////

    private static final String[][] colDataBlock = new String[][] {
                       new String[]{IFileData.IDCOL, IFileData.IDCOLTYPE}, // first column from IFileData
                       new String[]{URLDATABLOCK.dAttr.toString(), "integer"},
                       new String[]{URLDATABLOCK.dUrl.toString(), "varchar(512)"}
                                                                  };

    private static final String DEFURL = "*** blank URL ***";

    private int attr;
    private String url;
    
    public UrlFileData()
    {
       this.attr = -1;
       this.url = DEFURL;
    }
    
    public int getAttr()
    {
       return attr;
    }
    
    public void setAttr(int attr)
    {
       this.attr = attr;
    }
    
    public String getUrl()
    {
       return url;
    }
    
    public void setUrl(String url)
    {
       this.url = url;
    }
    
    /**
     *  Return DataBlock table schema
     */
    @Override
	public String[][] getColSchema() 
	{
		return colDataBlock;
	}
	
    /**
     *  Get data from cursor 
     */
	@Override
	protected void __getData(Cursor c)
	{
       this.attr = c.getInt(1);
       this.url = c.getString(2);
	}

	/**
     *  Return a ContentValues to be inserted or updated to DB
     */
	@Override
	protected ContentValues __saveData() 
	{
		ArrayList<Object> colsAndValues = new ArrayList<Object>(4);
		colsAndValues.add(URLDATABLOCK.dAttr.toString()); colsAndValues.add(this.attr);
		colsAndValues.add(URLDATABLOCK.dUrl.toString()); colsAndValues.add(this.url);

		return SqlStr.genContentValues(colsAndValues);
	}

	@Override
	public int getDataSizeInByte()
	{
		if (this.url != null) {
		   return this.url.length() * 2;
		}
		
		return 0;
	}

}
