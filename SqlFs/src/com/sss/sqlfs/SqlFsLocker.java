package com.sss.sqlfs;

import java.util.concurrent.locks.ReentrantLock;
import java.util.HashMap;

import com.sss.sqlfs.helper.*;

class SqlFsLocker implements IDisposable
{
   private static HashMap<String, SqlFsLocker> lockerTable = null;
   private static HashMap<String, Integer> refCountTable = null;
   
   /**
    * Get/create a named lock based on 'dbFullPath'
    * Same 'dbFullPath' will get the same lock
    * 
    */
   final static synchronized SqlFsLocker getFsLocker(String dbFullPath)
   {
	  if (lockerTable == null)
		  lockerTable = new HashMap<String, SqlFsLocker>();
	  
	  if (refCountTable == null)
		  refCountTable = new HashMap<String, Integer>();
	  
	  String tableKey = SqlFsFunc.genHash(dbFullPath); 
	  SqlFsLocker locker = lockerTable.get(tableKey);
	  if (locker == null) {
		  locker = new SqlFsLocker(tableKey);
		  lockerTable.put(tableKey, locker);
		  refCountTable.put(tableKey, 1); // reference count 1
	  }
	  else {
		  // increase reference count by 1
		  Integer count = refCountTable.get(tableKey);
		  refCountTable.put(tableKey, count.intValue() + 1);
	  }
	  
	  return locker;
   }
   
   private String tableKey;
   private ReentrantLock lock;
   
   private SqlFsLocker(String tableKey) 
   { 
	  this.tableKey = tableKey;
	  lock = new ReentrantLock();   
   }
   
   private String getTableKey()
   {
	  return tableKey;
   }
	
   void getFsLock()
   {
	  lock.lock();
   }
   
   public void dispose()
   {
	  lock.unlock();
   }
   
   private void close()
   {
	  lock = null;
   }
   
   /**
    * Don't call any of the above when this one is called
    */
   final static synchronized void close(SqlFsLocker locker)
   {
	  if (lockerTable == null)
		  return;
	  
	  String tableKey = locker.getTableKey();
	  Integer count = refCountTable.get(tableKey);
	  if (count == null)
		  return;
	  
	  int newCount = count.intValue() - 1;
	  if (newCount == 0) {
		 // remove all references
	     lockerTable.remove(tableKey);
	     refCountTable.remove(tableKey);
	     locker.close();  
	  }
	  else {
		 // decrease reference count by 1
		 refCountTable.put(tableKey, newCount);
	  }
   }
}
