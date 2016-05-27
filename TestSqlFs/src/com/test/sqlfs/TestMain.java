package com.test.sqlfs;

import java.util.HashMap;
import java.util.ArrayList;
import java.io.File;
import com.sss.consolehelper.CmdApp;
import com.sss.sqlfs.FsID;
import com.sss.sqlfs.*;
import com.sss.sqlfs.SqlFsErrCode.FsErr;


public class TestMain 
{
   private static CmdApp cmdApp;
   private static final String TESTDB = "/sdcard/consoleapps/hello.db";
   private static final long delayMSec = 0;
   
   public static void __assert(boolean cmpRet)
   {
	  if (!cmpRet)
		  throw new AssertionError("assertion failed");
   }
   
   public static void __assertEquErrCode(FsErr e1, FsErr e2)
   {
	  if (e1 != e2) {
		  cmdApp.stdOut.println("e1 = " + e1.name() + ", e2 = " + e2.name());
		  throw new AssertionError("assertion failed");
	  }
   }
   
   public static void __assertNotEquErrCode(FsErr e1, FsErr e2)
   {
	  if (e1 == e2) {
		  cmdApp.stdOut.println("e1 = " + e1.name() + ", e2 = " + e2.name());
		  throw new AssertionError("assertion failed");
	  }
   }
   
   public static String getCallerMethodName()
   {
	   StackTraceElement[] sEle = Thread.currentThread().getStackTrace();
	   return sEle[3].getMethodName();
   }
   
   public static void __sleep(long msec)
   {
	   try { Thread.sleep(msec); } catch (Exception e) { }
   }
   
   ///////////////////////////////////////////////////////////////////////////
   
   static void testAddDir()
   {
      SqlFs fs = SqlFs.create(TESTDB, new UrlFileData(), cmdApp.appInst);

      SqlDir rootDir = fs.getRootDir();
      __assert(rootDir != null);
      __assert(rootDir.getChildCount() == 0);
      __assert(rootDir.addDir("dir1") != null);
      __assert(rootDir.getChildCount() == 1);
      __assertEquErrCode(SqlFsErrCode.getLastError(), FsErr.OK);
      
      SqlDir dir2 = null;
      __assert((dir2 = rootDir.addDir("dir2")) != null);
      __assert(rootDir.getChildCount() == 2);           
      __assert(dir2.addDir("subdir2") != null);
      __assertEquErrCode(SqlFsErrCode.getLastError(), FsErr.OK);
      
      __assert(rootDir.addDir("dir3") != null);
      SqlDir dir3 = (SqlDir)rootDir.getChild("dir3");
      __assert(dir3.addDir("subdir3") != null);
      __assertEquErrCode(SqlFsErrCode.getLastError(), FsErr.OK);

      SqlDir subdir2 = (SqlDir)dir2.getChild("subdir2");
      __assert(subdir2.addDir("__subSubdir2") != null);
      __assertEquErrCode(SqlFsErrCode.getLastError(), FsErr.OK);
      
      // dir with same name
      __assert(rootDir.addDir("dir2") == null);
      __assertNotEquErrCode(SqlFsErrCode.getLastError(), FsErr.OK);
      __assert(rootDir.addDir("dir1") == null);
      __assertNotEquErrCode(SqlFsErrCode.getLastError(), FsErr.OK);

      // get info
      __assert(fs.getInfo(SqlFs.FSINFOFIELDS.version.toString()) != null);
      __assert(fs.getInfo(SqlFs.FSINFOFIELDS.createTimeUtc.toString()) != null);
      __assert(fs.getInfo(SqlFs.FSINFOFIELDS.fsLabel.toString()) != null);
      __assertEquErrCode(SqlFsErrCode.getLastError(), FsErr.OK);

      fs.close();

   }
   
   static void __testAddFile(SqlDir dir, String fileName, int attr, String url, boolean expResult)
   {
      SqlFile hf = dir.addFile(fileName);
      __assert((hf != null) == expResult);
      if (!expResult)
         return;

      __assertEquErrCode(SqlFsErrCode.getLastError(), FsErr.OK);
      
      UrlFileData urlF = new UrlFileData();
      urlF.setAttr(attr);
      urlF.setUrl(url);
      __assert(hf.saveFileData(urlF) == expResult);
      __assertEquErrCode(SqlFsErrCode.getLastError(), FsErr.OK);
   }
   
   static void testAddFile()
   {
      SqlFs fs = SqlFs.create(TESTDB, new UrlFileData(), cmdApp.appInst);
      SqlDir rootDir = fs.getRootDir();
      SqlDir dir2 = (SqlDir)rootDir.getChild("dir2");

      // add file to root Dir
      __testAddFile(rootDir, "Hello World", 22, "http://abc.com", true);
      __testAddFile(rootDir, "What", 43, "http://what.com", true);
      
      // add file to dir2
      __testAddFile(dir2, "明天更好", 999, "http://www.google.com", true);
      __testAddFile(dir2, "射鵰英雄傳", 10, "http://www.shendiao.com", true);

      __testAddFile(dir2, ">?射鵰英雄傳", 10, "http://kkk", false);
      __testAddFile(dir2, "    ", 10, "http://www", false);
      

      fs.close();
   }
   
   static void testUpdateFile()
   {
      SqlFs fs = SqlFs.create(TESTDB, new UrlFileData(), cmdApp.appInst);
      SqlDir rootDir = fs.getRootDir();
      SqlFile f = (SqlFile)rootDir.getChild("Hello World");
      UrlFileData fData = new UrlFileData();
      fData.setAttr(10);
      fData.setUrl("qwerty.com");
      __assert(f.saveFileData(fData));

      f = (SqlFile)rootDir.getChild("What");
      fData.setAttr(111);
      fData.setUrl("file://repos.org");
      __assert(f.saveFileData(fData));
      
      // read back to see if they are correct 
      f = (SqlFile)rootDir.getChild("Hello World");
      __assert(f.getFileData(fData));
      __assert(fData.getAttr() == 10);
      __assert(fData.getUrl().equals("qwerty.com"));
      
      f = (SqlFile)rootDir.getChild("What");
      __assert(f.getFileData(fData));
      __assert(fData.getAttr() == 111);
      __assert(fData.getUrl().equals("file://repos.org"));

      fs.close();
   }
   
   static void testRename()
   {
      SqlFs fs = SqlFs.create(TESTDB, new UrlFileData(), cmdApp.appInst);
      SqlDir rootDir = fs.getRootDir();           
      
      // this should fail
      __assert(rootDir.addDir("dir2") == null);
      SqlDir dir2 = (SqlDir)rootDir.getChild("dir2");
      // same name
      __assert(! dir2.rename("dir1"));
      __assert(dir2.rename("newDir"));
      // make dir2 again
      __assert(rootDir.addDir("dir2") != null);

      SqlFile f = (SqlFile)rootDir.getChild("Hello World");
      __assert(! f.rename("What"));
      __assert(! f.rename("What |"));
      __assert(! f.rename("What ?"));
      __assert(! f.rename("What <"));
      __assert(! f.rename(">What "));
      __assert(! f.rename("/What "));
      __assert(! f.rename("\\What"));
      __assert(! f.rename(".."));
      __assert(! f.rename("."));
      __assert(! f.rename("    \t \r \n "));
      __assert(f.rename("   886HelloWorld   "));

      fs.close();
   }
   
   static void testDeleteDir()
   {
	  SqlFs fs = SqlFs.create(TESTDB, new UrlFileData(), cmdApp.appInst);

      SqlDir rootDir = fs.getRootDir();
      SqlDir dir2 = (SqlDir)rootDir.getChild("dir2");
      SqlDir dir1 = (SqlDir)rootDir.getChild("dir1");
      __assert(dir2.delete());
      // after delete, the following should fail
      __assert(dir2.addDir("qwerty") == null);
      __assert(dir2.addFile("123") == null);
      __assert(!dir2.delete());
      __assert(dir2.getChild("subdir2") == null);
      __assert(dir2.getChildList() == null);
      __assert(dir2.getDir("..") == null);
      __assert(dir2.getFile("../What") == null);
      __assert(dir2.getFiles() == null);
      __assert(dir2.getSubDirs() == null);
      __assert(!dir2.isAlreadyExist("subdir2"));
      __assert(!dir2.isAncestor(rootDir));
      __assert(!dir2.move("/dir1"));
      __assert(!dir2.move(dir1));
      __assert(!dir2.rename("345"));

      ArrayList<SqlDir> dirList = rootDir.getSubDirs();
      __assert(dirList != null && dirList.size() == 2);

      fs.close();
   }
   
   static void testDeleteRootDir()
   {
	  SqlFs fs = SqlFs.create(TESTDB, new UrlFileData(), cmdApp.appInst);

      SqlDir rootDir = fs.getRootDir();
      __assert(rootDir.delete());
      ArrayList<SqlDir> dirList = rootDir.getSubDirs();
      __assert(dirList == null);
      ArrayList<SqlFile> fileList = rootDir.getFiles();
      __assert(fileList == null);

      fs.close();
   }
   
   static void testDeleteFile()
   {
	  SqlFs fs = SqlFs.create(TESTDB, new UrlFileData(), cmdApp.appInst);

      SqlDir rootDir = fs.getRootDir();
      SqlFile f = (SqlFile)rootDir.getChild("What");
      SqlDir dir1 = (SqlDir)rootDir.getChild("dir1");
      __assert(f.delete());
      // the following should fail after delete
      __assert( ! f.delete());
      UrlFileData fData = new UrlFileData();
      __assert( ! f.getFileData(fData));
      __assert( ! f.saveFileData(fData));
      __assert( ! f.isAncestor(rootDir));
      __assert( ! f.move("/dir1"));
      __assert( ! f.move(dir1));
      __assert( ! f.rename("345"));

      ArrayList<SqlFile> fileList = rootDir.getFiles();
      __assert(fileList != null && fileList.size() == 1);

      fs.close();
   }
   
   static void testGetDirs()
   {
      SqlFs fs = SqlFs.create(TESTDB, new UrlFileData(), cmdApp.appInst);

      SqlDir rootDir = fs.getRootDir();
      ArrayList<SqlDir> dirList = rootDir.getSubDirs();
      __assert(dirList != null);
      __assert(dirList.size() == 3);
      __assert(dirList.get(0).getName().equals("dir1"));
      __assert(dirList.get(1).getName().equals("dir2"));
      __assert(dirList.get(2).getName().equals("dir3"));
      
      fs.close();
   }
   
   static void testGetFiles()
   {
      SqlFs fs = SqlFs.create(TESTDB, new UrlFileData(), cmdApp.appInst);

      SqlDir rootDir = fs.getRootDir();
      ArrayList<SqlFile> fileList = rootDir.getFiles();
      __assert(fileList != null);
      __assert(fileList.size() == 2);
      __assert(fileList.get(0).getName().equals("Hello World"));
      __assert(fileList.get(1).getName().equals("What"));

      fs.close();
   }
   
   static void testGetSingleDirFiles()
   {

	  SqlFs fs = SqlFs.create(TESTDB, new UrlFileData(), cmdApp.appInst);
      __assert(fs.getDir("/dir2/subdir2") != null);
      __assert(fs.getDir("///dir2///subdir2///") != null);
      __assert(fs.getFile("/dir2/明天更好") != null);
      __assert(fs.getFile("///dir2////明天更好") != null);
      __assert(fs.getDir("/dir2/明天更好") == null);
      __assert(fs.getDir("/dir2/123") == null);
      __assert(fs.getDir("dir2/123") == null);
      __assert(fs.exists("/dir3/subdir3"));
      __assert(!fs.exists("/dir4"));
      __assert(fs.getDir("/") != null);
      __assert(fs.getDir("..") == null);
      __assert(fs.getFile("/") == null);
      __assert(fs.getFile("..") == null);
      __assert(fs.getFsNode("/dir2/subdir2") != null);
      __assert(fs.getFsNode("/dir1/subdir1") == null);
      __assert(fs.getFsNode("/What") != null);
      __assert(fs.getFsNode("/What/abc") == null);
      __assert(fs.getFsNode("/Hello World/") != null);
      __assert(fs.getFsNode("/") != null);
     

      SqlDir rootDir = fs.getRootDir();
      __assert(rootDir.getDir("../dir1") != null);
      SqlDir subdir3 = rootDir.getDir("dir3/subdir3");
      __assert(subdir3 != null);
      SqlDir subdir2 = subdir3.getDir("../../dir2/subdir2");
      __assert(subdir2 != null);

      fs.close();
   }
   
   static void testMove()
   {
	  SqlFs fs = SqlFs.create(TESTDB, new UrlFileData(), cmdApp.appInst);
      SqlDir rootDir = fs.getRootDir();
      SqlDir subdir3 = rootDir.getDir("dir3/subdir3");
      __assert(subdir3.move("../dir1"));

      SqlDir subdir2 = rootDir.getDir("dir2/subdir2");
      __assert(subdir2.move("../dir1"));

      SqlFile f1 = rootDir.getFile("dir2/明天更好");
      __assert(f1.move("../dir1"));

      SqlDir subSubDir2 = subdir2.getDir("__subSubdir2");
      __assert( ! subdir2.move(subSubDir2));

      __assert(subSubDir2.move("/"));
      __assert(subSubDir2.move("/dir3"));
      __assert( ! subSubDir2.move("../dir3"));
      __assert(subSubDir2.move("../dir1"));
      __assert( ! subSubDir2.move("."));
      __assert(subSubDir2.move(".."));
      __assert( ! rootDir.move("/dir3"));

      __assert(f1.rename("射鵰英雄傳"));
      SqlFile f2 = rootDir.getFile("dir2/射鵰英雄傳");
      __assert( ! f2.move("../dir1"));
      fs.close();
   }

   
   ////////////////////////////////////////////////////////////////////////
   
   
   
   public static void deleteFile(String pathToFile)
   {
	   File f = new File(pathToFile);
	   f.delete();
   }
   
   private static void ct_testAddDirFile()
   {
	   cmdApp.stdOut.println("Running " + getCallerMethodName());
	   deleteFile(TESTDB);
	   
	   testAddDir();
	   testAddFile();
	   
	   __sleep(delayMSec);
   }
   
   private static void ct_testUpdateFile()
   {
	   cmdApp.stdOut.println("Running " + getCallerMethodName());
	   deleteFile(TESTDB);
	   
	   testAddDir();
	   testAddFile();
	   testUpdateFile();
	   
	   __sleep(delayMSec);
   }
   
   static void ct_testGetDirFile()
   {
	   cmdApp.stdOut.println("Running " + getCallerMethodName());
	   deleteFile(TESTDB);
	   
	   testAddDir();
	   testAddFile();
	   testGetDirs();
       testGetFiles();
       testGetSingleDirFiles();

       __sleep(delayMSec);
   }     
   
   static void ct_testRename()
   {
	   cmdApp.stdOut.println("Running " + getCallerMethodName());
	   deleteFile(TESTDB);
	   
       testAddDir();
       testAddFile();
       testRename();

       __sleep(delayMSec);
   }
   
   static void ct_testDeleteDirFile()
   {
	   cmdApp.stdOut.println("Running " + getCallerMethodName());
	   deleteFile(TESTDB);
	   
       testAddDir();
       testAddFile();
       testDeleteFile();
       testDeleteDir();
       testDeleteRootDir();

       __sleep(delayMSec);
   }
   
   static void ct_testMoveDirFile()
   {
	  cmdApp.stdOut.println("Running " + getCallerMethodName());
	  deleteFile(TESTDB);

      testAddDir();
      testAddFile();
      testMove();

      __sleep(delayMSec);
   }
   
   private static final String SIMPLEFILEDB = "/sdcard/consoleapps/simplefile.db";
   
   static void checkBinFileData(SqlFile file, byte[] chkDataBin)
   {
	   SimpleFileData fdRetrieve = new SimpleFileData();
	   file.getFileData(fdRetrieve);
	   __assert(!fdRetrieve.isTextFile());
	   byte[] dataRetrieve = fdRetrieve.getRawBinData();
	   __assert(dataRetrieve.length == chkDataBin.length);
	   for (int i = 0 ; i < chkDataBin.length; ++i) {
	      __assert(dataRetrieve[i] == chkDataBin[i]);
	   }
   }
   
   static void checkTextFileData(SqlFile file, String chkStr)
   {
	   SimpleFileData fdRetrieve = new SimpleFileData();
	   file.getFileData(fdRetrieve);
	   __assert(fdRetrieve.isTextFile());
	   String dataRetStr = fdRetrieve.getText();
	   __assert(dataRetStr.length() == chkStr.length());
	   __assert(dataRetStr.equals(chkStr));
   }
   
   static void ct_testSimpleFile()
   {
	  cmdApp.stdOut.println("Running " + getCallerMethodName());
	  deleteFile(SIMPLEFILEDB);
	  SqlFs fs = SqlFs.create(SIMPLEFILEDB, cmdApp.appInst);
	  // add dirs
      SqlDir rootDir = fs.getRootDir();
      SqlDir helloDir = rootDir.addDir("hello");
      rootDir.addDir("dir2");
      rootDir.addDir("dir3");
      
      // add bin file under root dir
      SqlFile file = rootDir.addFile("yes.bin");
      SimpleFileData fd = new SimpleFileData();
      byte[] dataBin = new byte[]{0x34, 0x12, 0x09, 0x11, 0x08}; 
      fd.setRawBinData(dataBin);
      file.saveFileData(fd);
      __assert(file.getFileSize() == 5);
      
      // retrieve saved data
      checkBinFileData(file, dataBin);
      
      // add text file
      file = rootDir.addFile("mytext.txt");
      String saveStr = "In a device that does not display text, a simple program to produce a signal, such as turning on an LED, is often substituted for Hello world as the introductory program."; 
      fd.setTextData(saveStr);
      file.saveFileData(fd);
      __assert(file.getFileSize() == saveStr.length() * 2);
      
      // retrieve saved data
      checkTextFileData(file, saveStr);
      
      // modify file from bin to text
      file = rootDir.getFile("yes.bin");
      fd.setTextData(saveStr);
      file.saveFileData(fd);
      checkTextFileData(file, saveStr);
      
      // modify file from text to bin
      file = rootDir.getFile("mytext.txt");
      fd.setRawBinData(dataBin);
      file.saveFileData(fd);
      checkBinFileData(file, dataBin);
      
      rootDir.getFile("yes.bin").delete();
      rootDir.getFile("mytext.txt").delete();
      fs.close();
   }
/*   
   private static void __testFsID()
   {
	   ArrayList<FsID> childList = new ArrayList<FsID>(3);
	   
	   childList.add(FsID.toFsID(3));
	   childList.add(FsID.toFsID(4));
	   childList.add(FsID.toFsID(5));
	   cmdApp.stdOut.println("childList size = " + childList.size());
	   
	   
	   FsID temp = FsID.toFsID(3);
	   childList.remove(temp);
	   cmdApp.stdOut.println("childList size = " + childList.size());
	   
	   temp = FsID.toFsID(5);
	   childList.remove(temp);
	   cmdApp.stdOut.println("childList size = " + childList.size());
   }
*/
   
   public static void main(HashMap<Integer, Object> args)
   {
	   cmdApp = new CmdApp(args);
	   cmdApp.stdOut.println("IDSize = " + FsID.getIDSize());
	   
	   ct_testAddDirFile();
	   ct_testUpdateFile();
	   ct_testGetDirFile();
	   ct_testRename();
	   ct_testDeleteDirFile();
	   ct_testMoveDirFile();
	   
	   ct_testSimpleFile();
	   
	   TestMultiReadWrite.testReadWrite(cmdApp);
	   //__testFsID();
	   //TestMultiReadWrite.checkFilesCount(cmdApp);
   }
}
