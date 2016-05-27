package com.sss.sqlfs.helper;

import com.sss.sqlfs.FsID;
import java.util.Calendar;
import java.util.ArrayList;
import android.content.ContentValues;

public class SqlStr 
{
	private SqlStr() { }
	
	/**
	 *  A class to wrap around a simple condition statement like
	 *      --  x = 5
	 *      --  y = 'earth'
	 *      --  z > 90
	 */
	public static class SqlSimpCond
    {
       private String name;    ///< left to op string
       private String op;      ///< operation string, e.g. "=", "<", ">"
       private Object value;   ///< right to op string (a string or an integer)
								  
       public SqlSimpCond(String name, String op, Object value)
       {
          this.name = name;
          this.op = op;
          this.value = value;
       }

       public void genString(StringBuilder sb)
       {
    	  sb.append(this.name); sb.append(' ');
          sb.append(this.op); sb.append(' ');
          SqlStr.appendValueType(sb, this.value);
       }
    }
	
	/**
	 *  A class to wrap around statement with brackets like
	 *    --  (x = 'apple' or y > 9) and u = 11
	 *    --  (x = 'apple' or y > 9) and (u = 11 or a = 'haha')
	 */
	public static class SqlCondClause
    {
       private Object cond1;   ///< a SqlCondClause or a SqlSimpCond
       private String op;      ///< operation string, e.g. "=", "<", ">"
       private Object cond2;   ///< a SqlCondClause or a SqlSimpCond

       public SqlCondClause(Object cond1, String op, Object cond2)
       {
          this.cond1 = cond1;
          this.op = op;
          this.cond2 = cond2;
       }

       public void genString(StringBuilder sb)
       {
          sb.append("(");
          processCond(sb, this.cond1);
          sb.append(' '); sb.append(op); sb.append(' '); 
          processCond(sb, this.cond2);
          sb.append(")");
       }

       private void processCond(StringBuilder sb, Object cond)
       {
          if (cond instanceof SqlCondClause) {
             ((SqlCondClause)cond).genString(sb);
          }
          else if (cond instanceof SqlSimpCond) {
             ((SqlSimpCond)cond).genString(sb);
          }
       }
    }
	
	
	
	/**
	 *  Print value to StringBuilder base on data type 
	 */
    private static void appendValueType(StringBuilder sb, Object val)
    {
       if (val == null) {
          sb.append("null");
       }
       else if (val instanceof String) {
          /*
		   *  Use a single quote to wrap around a string type.
		   *  At the same, append one more "'" after each single quote
		   *  so as to "escape" this character.
           */
          String newS = ((String)val).replace("'", "''");
          sb.append("'");
          sb.append(newS);
          sb.append("'");
       }
       else if (val instanceof Integer) {
          sb.append((Integer)val);
       }
       else if (val instanceof Long) {
           sb.append((Long)val);
       }
       else if (val instanceof FsID) {
           sb.append(((FsID)val).getVal());
       }
       else if (val instanceof Calendar) {
    	  Calendar dt = (Calendar)val;
    	  String calString = android.text.format.DateFormat.format("'yyyy-MM-dd kk:mm:ss'", dt).toString();
          sb.append(calString);
       }
       
    }

	
	
	/**
     *  Generate a "Create table" SQL query
     * 
     *  @param [in] tabName -- table name
     *  @param [in] cols -- column name (each element is a String[2])
     */
	public static String genCreateTable(String tabName, String[][] cols)
	{
		StringBuilder sb = new StringBuilder(256);

        sb.append("CREATE TABLE ").append(tabName).append(" ("); 
        for (String[] sCol : cols) {
           sb.append(sCol[0]).append(" ").append(sCol[1]).append(",");
        }
        sb.deleteCharAt(sb.length() - 1); // remove the last comma
        sb.append(")"); 

        return sb.toString();
	}
	
	/**
	 *  Generate a "Drop table" SQL query
	 */
    public static String genDropTable(String tabName)
    {
       // Adding "IF EXISTS" will not generate error if table does not exists
       return "DROP TABLE IF EXISTS " + tabName;
    }
    
    /**
	 *  Generate a "WHERE" clause
	 * 
	 *  @param [in] values -- each element is a SqlCondClause or a SqlSimpCond or a String
	 */
    public static String genWhere(Object... values)
    {
    	ArrayList<Object> objList = new ArrayList<Object>(); 

        for(Object val : values) {
        	objList.add(val);
        }

        return genWhere(objList);
    }
    
    public static String genWhere(ArrayList<Object> values)
    {
    	StringBuilder sb = new StringBuilder(256);

        for(Object val : values) {
           if (val instanceof SqlCondClause) {
              ((SqlCondClause)val).genString(sb);
           }
           else if (val instanceof SqlSimpCond) {
              ((SqlSimpCond)val).genString(sb);
           }
           else if (val instanceof String) {
              // it is a single op string or other specifiers
        	  sb.append(' ');
        	  sb.append((String)val);
              sb.append(' ');
           }
        }

        return sb.toString();
    }
    
    /**
     *  Get content values ready for insert/update
     */
    public static ContentValues genContentValues(ArrayList<Object> colsAndValues)
    {
    	ContentValues contVals = new ContentValues();
    	
    	for (int i = 0; i < colsAndValues.size(); i += 2) {
    		Object val = colsAndValues.get(i + 1);
    		if (val == null) {
    			contVals.putNull((String)colsAndValues.get(i));
    		}
    		else if (val instanceof String) {
    			contVals.put((String)colsAndValues.get(i), (String)val);
    		}
    		else if (val instanceof Integer) {
    			contVals.put((String)colsAndValues.get(i), (Integer)val);
    		}
            else if (val instanceof Long) {
            	contVals.put((String)colsAndValues.get(i), (Long)val);
    		}
            else if (val instanceof byte[]) {
            	contVals.put((String)colsAndValues.get(i), (byte[])val);
    		}
            else if (val instanceof FsID) {
            	contVals.put((String)colsAndValues.get(i), ((FsID)val).getVal());
            }
    	}
    	
    	return contVals;
    }
}
