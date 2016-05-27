package com.sss.sqlfs;

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
   };
   
   private static ThreadLocal<FsErr> threadLocalFsErr = new ThreadLocal<FsErr>() 
   {
       protected FsErr initialValue() {
           return FsErr.OK;
       }
   };
   
   /**
    * Set error
    */
   static void setCurrentError(FsErr err)
   {
	   threadLocalFsErr.set(err);
   }
   
   /**
    * Get error
    */
   public static FsErr getLastError()
   {
	   return threadLocalFsErr.get();
   }
   
   static void unset()
   {
	   threadLocalFsErr.remove();
   }
}
