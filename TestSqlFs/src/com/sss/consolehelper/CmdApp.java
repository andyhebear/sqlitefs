package com.sss.consolehelper;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.PrintStream;
import android.app.Application;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;

/**
 *  A class to wrap around arguments passed from "main"
 */
public class CmdApp
{
   public Application appInst;  // application instance
   public String[] argv;   // command line arguments (no "apk" file itself, may be null)
   public PrintStream stdOut;
   private InputStream stdIn;   // private, better use stdInReader
   private BufferedReader stdInReader;  // private

   private HashMap<Integer, Object> args; // keep a reference to the arguments 
   

   private CmdApp() { }

   public CmdApp(HashMap<Integer, Object> args)
   {
      appInst = (Application)args.get(0); 
      argv = (String[])args.get(1); 
      stdIn = (InputStream)args.get(2); 
      stdOut = (PrintStream)args.get(3); 
      String streamEncoding = (String)args.get(4); 
      this.args = args;
      
      try {
         // create a convenient line reader
         stdInReader = new BufferedReader(new InputStreamReader(stdIn, streamEncoding));
      }
      catch (UnsupportedEncodingException e) {
      }
   }

   /**
    *  Test whether console request this program to end
    */
   public boolean requestToEnd()
   {
      Boolean reqEnd = (Boolean)args.get(0xaeaeaeae);
      if (reqEnd != null)
         return reqEnd;

      return false;
   }
   
   /**
    *  In case user want to kill this app by interrupting the running thread
    */
   private void killAppUnconditionally()
   {
       if (requestToEnd())
	      throw new RuntimeException("Oops! I am dead !!");
   }

   /**
    *  method to wrap around BufferedReader.readLine
    */
   public String readln()
   {
      try {
         return stdInReader.readLine();
      }
      catch(IOException e) { killAppUnconditionally(); }

      return null;
   }

   /**
    *  method to wrap around BufferedReader.read
    */
   public int read(char[] buffer, int offset, int length)
   {
      try {
         return stdInReader.read(buffer, offset, length);
      }
      catch(IOException e) { killAppUnconditionally(); }

      return 0;
   }
   
   /**
    *  method to wrap around BufferedReader.read
    */
   public int read()
   {
      try {
         return stdInReader.read();
      }
      catch(IOException e) { killAppUnconditionally(); }

      return -1;
   }

   /**
    *  method to wrap around BufferedReader.ready
    */
   public boolean ready()
   {
      try {
         return stdInReader.ready();
      }
      catch(IOException e) { killAppUnconditionally(); }

      return false;
   }
}
