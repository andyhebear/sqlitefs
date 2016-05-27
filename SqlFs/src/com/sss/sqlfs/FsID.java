package com.sss.sqlfs;

import android.util.Log;

/**
 * Class to represent an ID in the file system. May be 32bit or 64bit
 */
public class FsID 
{
	// set to true if use long (64bit) ID
	private static final boolean useLongID = false;
	private static final int INT32SIZE = 4;
    private static final int INT64SIZE = 8;
    
    public static int getIDSize() { return useLongID ? INT64SIZE : INT32SIZE; }
    public static boolean isLongID() { return useLongID; }
    

    private IFsID _fsID;
    
    private FsID() { }
    
    
    FsID(int id)
    {
    	_fsID = useLongID ? new FsID64(id) : new FsID32(id); 
    }
    
    FsID(long id)
    {
    	_fsID = useLongID ? new FsID64(id) : new FsID32((int)(id & 0xffffffff)); 
    }
    
    public int compare(FsID value)
    {
       return _fsID.compare(value._fsID);
    }
    
    public boolean equals(FsID value)
    {
       return _fsID.equals(value._fsID);
    }
    
    @Override
    public boolean equals(Object obj)
    {
       if (obj instanceof FsID) {
    	   return this.getVal() == ((FsID)obj).getVal();
       }
       
       return false;
    }
    
    @Override
    public String toString()
    {
       return Long.toString(getVal());
    }
    
    /**
     *  Get underlying ID value
     */
    public long getVal()
    {
       return useLongID ? ((FsID64)_fsID).id : ((FsID32)_fsID).id;
    }
    
    /**
     *  Convert from int to FsID
     */
    public static FsID toFsID(int id)
    {
       return new FsID(id);
    }
    
    /**
     *  Convert from long to IntID
     */
    public static FsID toFsID(long id)
    {
       return new FsID(id);
    }
    
    //////////////////////// inner interface and class /////////////////////
    
    private interface IFsID
    {
       public int compare(IFsID aID);
       public boolean equals(IFsID aID);
    }
    
    /**
     * 64bit FsID
     */
    private class FsID64 implements IFsID
    {
       private long id;
       
       public FsID64(int id)
       {
    	  this.id = id;
       }
       
       public FsID64(long id)
       {
    	  this.id = id; 
       }
       
       public int compare(IFsID aID)
       {
    	   if (! (aID instanceof FsID64)) 
    		   return -1;
    	   
    	   long longID = ((FsID64)aID).id;
    	   if (this.id > longID) return 1;
    	   if (this.id < longID) return -1;
    	   return 0;
       }
       
       public boolean equals(IFsID aID)
       {
    	  if (! (aID instanceof FsID64)) return false;
    	  return this.id == ((FsID64)aID).id;
       }
    }
    
    /**
     * 32bit FsID
     */
    private class FsID32 implements IFsID
    {
       private int id;
       
       public FsID32(int id)
       {
    	  this.id = id;
       }
       
       public int compare(IFsID aID)
       {
    	   if (! (aID instanceof FsID32)) 
    		   return -1;
    	   
    	   int intID = ((FsID32)aID).id;
    	   if (this.id > intID) return 1;
    	   if (this.id < intID) return -1;
    	   return 0;
       }
       
       public boolean equals(IFsID aID)
       {
    	  if (! (aID instanceof FsID32)) return false;
    	  return this.id == ((FsID32)aID).id;
       }
    }
}
