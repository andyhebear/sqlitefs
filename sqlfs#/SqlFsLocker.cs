using System.Collections.Generic;

namespace com.sss.sqlfs
{


	using com.sss.sqlfs.helper;

	internal class SqlFsLocker : IDisposable
	{
	   private static Dictionary<string, SqlFsLocker> lockerTable = null;
	   private static Dictionary<string, int?> refCountTable = null;

	   /// <summary>
	   /// Get/create a named lock based on 'dbFullPath'
	   /// Same 'dbFullPath' will get the same lock
	   /// 
	   /// </summary>
	   internal static SqlFsLocker getFsLocker(string dbFullPath)
	   {
		   lock (typeof(SqlFsLocker))
		   {
			  if (lockerTable == null)
			  {
				  lockerTable = new Dictionary<string, SqlFsLocker>();
			  }
        
			  if (refCountTable == null)
			  {
				  refCountTable = new Dictionary<string, int?>();
			  }
        
			  string tableKey = SqlFsFunc.genHash(dbFullPath);
			  SqlFsLocker locker = lockerTable[tableKey];
			  if (locker == null)
			  {
				  locker = new SqlFsLocker(tableKey);
				  lockerTable[tableKey] = locker;
				  refCountTable[tableKey] = 1; // reference count 1
			  }
			  else
			  {
				  // increase reference count by 1
				  int? count = refCountTable[tableKey];
				  refCountTable[tableKey] = (int)count + 1;
			  }
        
			  return locker;
		   }
	   }

	   private string tableKey;
	   private ReentrantLock @lock;

	   private SqlFsLocker(string tableKey)
	   {
		  this.tableKey = tableKey;
		  @lock = new ReentrantLock();
	   }

	   private string TableKey
	   {
		   get
		   {
			  return tableKey;
		   }
	   }

	   internal virtual void getFsLock()
	   {
		  @lock.@lock();
	   }

	   public virtual void dispose()
	   {
		  @lock.unlock();
	   }

	   private void close()
	   {
		  @lock = null;
	   }

	   /// <summary>
	   /// Don't call any of the above when this one is called
	   /// </summary>
	   internal static void close(SqlFsLocker locker)
	   {
		   lock (typeof(SqlFsLocker))
		   {
			  if (lockerTable == null)
			  {
				  return;
			  }
        
			  string tableKey = locker.TableKey;
			  int? count = refCountTable[tableKey];
			  if (count == null)
			  {
				  return;
			  }
        
			  int newCount = (int)count - 1;
			  if (newCount == 0)
			  {
				 // remove all references
				 lockerTable.Remove(tableKey);
				 refCountTable.Remove(tableKey);
				 locker.close();
			  }
			  else
			  {
				 // decrease reference count by 1
				 refCountTable[tableKey] = newCount;
			  }
		   }
	   }
	}

}