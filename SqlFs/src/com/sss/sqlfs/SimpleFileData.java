package com.sss.sqlfs;

import java.util.ArrayList;

import com.sss.sqlfs.helper.SqlStr;
import android.content.ContentValues;
import android.database.Cursor;

/**
 *  An implementation of IFileData which saves text data or raw binary data.  
 */
public class SimpleFileData extends IFileData
{
	//! fields in addition to dID
    protected enum FILEDATABLOCK 
    {
       dID, // just a place holder
       dFileType,
       dTextData,
       dRawBinData
    };
    
    protected enum FILETYPE
    {
       fText,  ///< text 
       fBin    ///< raw binary
    };
    
    private static final String[][] colDataBlock = new String[][] {
                       new String[]{IFileData.IDCOL, IFileData.IDCOLTYPE}, // first column from IFileData
                       new String[]{FILEDATABLOCK.dFileType.toString(), "integer"},
                       new String[]{FILEDATABLOCK.dTextData.toString(), "text"},
                       new String[]{FILEDATABLOCK.dRawBinData.toString(), "blob"}
                                                                  };
    
    private byte[] rawBinData;
    private String textData;
    
    public SimpleFileData()
    {
    }
    
    public byte[] getRawBinData()
    {
       return rawBinData;
    }
    
    public void setRawBinData(byte[] data)
    {
       this.rawBinData = data;
       this.textData = null;
    }
    
    public String getText()
    {
       return textData;
    }
    
    public void setTextData(String data)
    {
       this.textData = data;
       this.rawBinData = null;
    }
    
    public boolean isTextFile()
    {
       return (this.textData != null);
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
	   int fileType = c.getInt(FILEDATABLOCK.dFileType.ordinal());
	   if (fileType == FILETYPE.fBin.ordinal()) {
          this.rawBinData = c.getBlob(FILEDATABLOCK.dRawBinData.ordinal());
          this.textData = null; // clear
	   }
	   else if (fileType == FILETYPE.fText.ordinal()) {
		  this.textData = c.getString(FILEDATABLOCK.dTextData.ordinal());
		  this.rawBinData = null; // clear
	   }
	}

	/**
     *  Return a ContentValues to be inserted or updated to DB
     */
	@Override
	protected ContentValues __saveData() 
	{
		ArrayList<Object> colsAndValues = new ArrayList<Object>(2);
		FILETYPE fType = FILETYPE.fBin;
		if (this.rawBinData != null) {
		   colsAndValues.add(FILEDATABLOCK.dRawBinData.toString()); 
		   colsAndValues.add(this.rawBinData);
		   colsAndValues.add(FILEDATABLOCK.dTextData.toString()); // clear text column 
		   colsAndValues.add("");
		}
		else if (this.textData != null) {
		   colsAndValues.add(FILEDATABLOCK.dTextData.toString()); 
		   colsAndValues.add(this.textData);
		   colsAndValues.add(FILEDATABLOCK.dRawBinData.toString()); // clear rawbin column
		   colsAndValues.add(null);
		   fType = FILETYPE.fText;
		}
		
		// file type
		colsAndValues.add(FILEDATABLOCK.dFileType.toString()); 
		colsAndValues.add(fType.ordinal());
		   
		return SqlStr.genContentValues(colsAndValues);
	}
	
	/**
	 *  Return the number of bytes used up by rawBin or text data 
	 */
	@Override
	public int getDataSizeInByte()
	{
		if (this.rawBinData != null) {
		   return this.rawBinData.length;
		}
		else if (this.textData != null) {
		   return this.textData.length() * 2; // each character is 2 bytes
		}
		
		return 0;
	}
}
