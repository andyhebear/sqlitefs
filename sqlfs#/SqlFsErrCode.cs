namespace com.sss.sqlfs
{

	public class SqlFsErrCode
	{
	   public enum FsErr
	   {
		  OK,
		  EmptyString,
		  GetFieldError,
		  SetFieldError,
		  AddFsNodeError,
		  GetLastInsertIDError,
		  InvalidChars,
		  CannotRenameRoot,
		  NoParent,
		  NameAlreadyExists,
		  NoEntryByName,
		  DestDirNotFound,
		  MustUseAbsolutePath,
		  MustUseRelativePath,
		  MustNotStartOrEndWithPathSeparator,
		  SplitPathErr,
		  ChildNotFound,
		  CannotMoveRoot,
		  CannotMoveToSelf,
		  CannotMoveToSubdir,
		  ChildListNotUpdated,
		  NoNewIDForNewFsNode,
		  DeleteFsEntryError,
		  NotDirInPath,
		  DataBlockIDNotValid,
		  CannotDeleteFsEntry,
		  CannotDeleteDataBlockEntry,
		  GetFileDataErr,
		  SaveFileDataErr,
		  CannotOpenDB,
		  GetFsInfoErr,
		  WriteFsInfoErr,
		  CannotAccessRoot,
	   }

	   private static ThreadLocal<FsErr> threadLocalFsErr = new ThreadLocalAnonymousInnerClassHelper();

	   private class ThreadLocalAnonymousInnerClassHelper : ThreadLocal<FsErr>
	   {
		   public ThreadLocalAnonymousInnerClassHelper()
		   {
		   }

		   protected internal virtual FsErr initialValue()
		   {
			   return FsErr.OK;
		   }
	   }

	   /// <summary>
	   /// Set error
	   /// </summary>
	   internal static FsErr CurrentError
	   {
		   set
		   {
			   threadLocalFsErr.set(value);
		   }
	   }

	   /// <summary>
	   /// Get error
	   /// </summary>
	   public static FsErr LastError
	   {
		   get
		   {
			   return threadLocalFsErr.get();
		   }
	   }

	   internal static void unset()
	   {
		   threadLocalFsErr.remove();
	   }
	}

}