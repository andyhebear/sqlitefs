package com.test.sqlfs;

import com.sss.consolehelper.CmdApp;
import com.sss.sqlfs.SimpleFileData;
import com.sss.sqlfs.SqlFile;
import com.sss.sqlfs.SqlDir;
import com.sss.sqlfs.SqlFs;
import com.sss.sqlfs.SqlFsErrCode;

public class TestMultiReadWrite implements Runnable
{
   private static final String READWRITEFILEDB = "/sdcard/consoleapps/readwritefile.db";
   private static CmdApp cmdApp;
   private static boolean isDeleteFilesInThread = false;
   	
   public static void testReadWrite(CmdApp cmdApp)
   {
      try {
    	 TestMultiReadWrite.cmdApp = cmdApp;
    	 startCreateDeleteFiles(true);
    	 checkFilesCount(cmdApp);
      }
      catch(Exception e) {
    	  cmdApp.stdOut.println("Error: " + e.getMessage());
      }
   
   }
   
   public static void checkFilesCount(CmdApp cmdApp)
   {
	   
	  SqlFs fs = SqlFs.create(READWRITEFILEDB, cmdApp.appInst);
	  // get dirs
	  SqlDir rootDir = fs.getRootDir();
	  SqlDir subDir = rootDir.getDir("subDir");
	  
	  int childCount = subDir.getChildCount();
	  cmdApp.stdOut.println("subDirChildCount = " + childCount);
	  int rootChildCount = rootDir.getChildCount();
	  cmdApp.stdOut.println("rootChildCount = " + rootChildCount);
	  TestMain.__assert(childCount == 0);
	  TestMain.__assert(subDir.getFiles() == null);
	  TestMain.__assert(rootDir.getFiles() == null);
	  TestMain.__assert(rootDir.getSubDirs().size() == 1);
	  
	  fs.close();
   }
   
   private static void startCreateDeleteFiles(boolean isCreateDeleteThread) throws InterruptedException
   {
      TestMultiReadWrite.isDeleteFilesInThread = isCreateDeleteThread;
	  
	  if (!isCreateDeleteThread) {
	     startCreateFiles();
  	     startDeleteFilesThread(isCreateDeleteThread);
	  }
	  else {
	  	 Thread delThread = startDeleteFilesThread(isCreateDeleteThread);
	  	 startCreateFiles();
	  	 delThread.join();
	  }
  	 
   }
      
   private static Thread startDeleteFilesThread(boolean isCreateDeleteThread)
   {
	  TestMultiReadWrite inst = new TestMultiReadWrite();
	  if (isCreateDeleteThread) {
 	     Thread thrd = new Thread(inst);
 	     thrd.start();
 	     return thrd;
	  }
	  
	  inst.run();
	  return null;
   }
   
   private static void createBinFile(SqlDir dir, String fileName)
   {
	   SqlFile file = dir.addFile(fileName);
	   SimpleFileData fd = new SimpleFileData();
	   byte[] dataBin = new byte[]{0x34, 0x12, 0x09, 0x11, 0x08}; 
	   fd.setRawBinData(dataBin);
	   TestMain.__assert(file.saveFileData(fd));
	   //TestMain.__assert(file.getFileSize() == dataBin.length);
   }
   
   private static void createTextFile(SqlDir dir, String fileName)
   {
	   SqlFile file = dir.addFile(fileName);
	   SimpleFileData fd = new SimpleFileData();
	   String saveStr = "a simple program to produce a signal, such as turning on an LED, is often substituted for Hello world as the introductory program."; 
	   fd.setTextData(saveStr);
	   TestMain.__assert(file.saveFileData(fd));
	   //TestMain.__assert(file.getFileSize() == saveStr.length() * 2);
   }
   
   private static boolean isCreateBin(int i)
   {
	   return (i % 2 == 0); 
   }
   
   private static boolean isWriteInSubDir(int i)
   {
	   return ((i + 1) % 2 == 0);
   }
   
   private static String getFileName(int i)
   {
	   if (isCreateBin(i))
		   return "myBin_" + i + ".bin";
	   
	   return "myText_" + i + ".txt";
   }
   
   private static final int NUMFILES = 500;

   private static void startCreateFiles()
   {
	  TestMain.deleteFile(READWRITEFILEDB);
	  
	  SqlFs fs = SqlFs.create(READWRITEFILEDB, cmdApp.appInst);
	  // add dirs
      SqlDir rootDir = fs.getRootDir();
      SqlDir subDir = rootDir.addDir("subDir");
      
      // write files
	  int i = 0;
	  while (i < NUMFILES) {
		 SqlDir d = isWriteInSubDir(i) ? subDir : rootDir;
		 String fName = getFileName(i);
		 
		 cmdApp.stdOut.println("Writing \"" + fName + "\" in " + d.getName());
		 
		 if (isCreateBin(i))
			 createBinFile(d, fName);
		 else 
			 createTextFile(d, fName);
		 
	     ++i;
	  }
	  
	  fs.close();
   }
   
   private void deleteFileFunc()
   {
	      // thread to delete files
		  if (TestMultiReadWrite.isDeleteFilesInThread)
		     TestMain.__sleep(4 * 1000);
		  
		  SqlFs fs = SqlFs.create(READWRITEFILEDB, cmdApp.appInst);
		  // get dirs
	      SqlDir rootDir = fs.getRootDir();
	      SqlDir subDir = rootDir.getDir("subDir");
	      
	      // delete files
	      int i = 0;
	      int cannotDeleteCount = 0;
		  while (i < NUMFILES) {
			 SqlDir d = isWriteInSubDir(i) ? subDir : rootDir;
			 String fName = getFileName(i);
			 
			 cmdApp.stdOut.println("Deleting \"" + fName + "\" in " + d.getName());
			 
			 SqlFile f = d.getFile(fName);
			 if (f == null) {
				cmdApp.stdOut.println("### \"" + fName + "\" not found in " + d.getName());
				TestMain.__sleep(8000);
			 }
			 else if (f.getFileSize() <= 0) {
				cmdApp.stdOut.println("@@@ \"" + fName + "\" is zero size in " + d.getName());
				TestMain.__sleep(8000);
			 }
			 else {
				
			    //TestMain.__assert(f.delete());
			    if (!f.delete()) {
			    	cmdApp.stdOut.println(">>> Delete error -- " + SqlFsErrCode.getLastError().name());
			    	++cannotDeleteCount;
			    }
			    else {
			       if (TestMultiReadWrite.isDeleteFilesInThread) {
			          //if ((i % 10) == 0)
				      //   TestMain.__sleep(1000);
			       }
			 
		           ++i;
		           cannotDeleteCount = 0;
			    }
			    
			    if (cannotDeleteCount > 10) {
			    	cmdApp.stdOut.println(">>> Fail too many times upon delete.");
			    	break;
			    }
			 }
		  }
		  
		  fs.close();
   }
   
   @Override
   public void run() 
   {
      try {
    	  deleteFileFunc();
      }
	  catch(Exception e) {
		  cmdApp.stdOut.println("deleteFileFunc Error: " + e.getMessage());
	  }
   }
}
