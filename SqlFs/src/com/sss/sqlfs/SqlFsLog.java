package com.sss.sqlfs;

import android.util.Log;

/**
 *  A class for logging
 */
class SqlFsLog 
{
    private SqlFsLog() { }
    
	static void debug(Exception e) 
    {
       Log.d("SqlFsLog", e.getMessage());
    }

    static void debug(String eMsg) 
    {
    	Log.d("SqlFsLog", eMsg);
    }
}
