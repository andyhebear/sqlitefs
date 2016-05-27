using System;
using System.Collections.Generic;
using System.Text;

namespace com.sss.sqlfs.helper
{

	using ContentValues = android.content.ContentValues;

	public class SqlStr
	{
		private SqlStr()
		{
		}

		/// <summary>
		///  A class to wrap around a simple condition statement like
		///      --  x = 5
		///      --  y = 'earth'
		///      --  z > 90
		/// </summary>
		public class SqlSimpCond
		{
		   internal string name; ///< left to op string
		   internal string op; ///< operation string, e.g. "=", "<", ">"
		   internal object value; ///< right to op string (a string or an integer)

		   public SqlSimpCond(string name, string op, object value)
		   {
			  this.name = name;
			  this.op = op;
			  this.value = value;
		   }

		   public virtual void genString(StringBuilder sb)
		   {
			  sb.Append(this.name);
			  sb.Append(' ');
			  sb.Append(this.op);
			  sb.Append(' ');
			  SqlStr.appendValueType(sb, this.value);
		   }
		}

		/// <summary>
		///  A class to wrap around statement with brackets like
		///    --  (x = 'apple' or y > 9) and u = 11
		///    --  (x = 'apple' or y > 9) and (u = 11 or a = 'haha')
		/// </summary>
		public class SqlCondClause
		{
		   internal object cond1; ///< a SqlCondClause or a SqlSimpCond
		   internal string op; ///< operation string, e.g. "=", "<", ">"
		   internal object cond2; ///< a SqlCondClause or a SqlSimpCond

		   public SqlCondClause(object cond1, string op, object cond2)
		   {
			  this.cond1 = cond1;
			  this.op = op;
			  this.cond2 = cond2;
		   }

		   public virtual void genString(StringBuilder sb)
		   {
			  sb.Append("(");
			  processCond(sb, this.cond1);
			  sb.Append(' ');
			  sb.Append(op);
			  sb.Append(' ');
			  processCond(sb, this.cond2);
			  sb.Append(")");
		   }

		   internal virtual void processCond(StringBuilder sb, object cond)
		   {
			  if (cond is SqlCondClause)
			  {
				 ((SqlCondClause)cond).genString(sb);
			  }
			  else if (cond is SqlSimpCond)
			  {
				 ((SqlSimpCond)cond).genString(sb);
			  }
		   }
		}



		/// <summary>
		///  Print value to StringBuilder base on data type 
		/// </summary>
		private static void appendValueType(StringBuilder sb, object val)
		{
		   if (val == null)
		   {
			  sb.Append("null");
		   }
		   else if (val is string)
		   {
			  /*
			   *  Use a single quote to wrap around a string type.
			   *  At the same, append one more "'" after each single quote
			   *  so as to "escape" this character.
			   */
			  string newS = ((string)val).Replace("'", "''");
			  sb.Append("'");
			  sb.Append(newS);
			  sb.Append("'");
		   }
		   else if (val is int?)
		   {
			  sb.Append((int?)val);
		   }
		   else if (val is long?)
		   {
			   sb.Append((long?)val);
		   }
		   else if (val is FsID)
		   {
			   sb.Append(((FsID)val).Val);
		   }
		   else if (val is DateTime)
		   {
			  DateTime dt = (DateTime)val;
			  string calString = android.text.format.DateFormat.format("'yyyy-MM-dd kk:mm:ss'", dt).ToString();
			  sb.Append(calString);
		   }

		}



		/// <summary>
		///  Generate a "Create table" SQL query
		/// </summary>
		///  @param [in] tabName -- table name </param>
		///  @param [in] cols -- column name (each element is a String[2]) </param>
		public static string genCreateTable(string tabName, string[][] cols)
		{
			StringBuilder sb = new StringBuilder(256);

			sb.Append("CREATE TABLE ").Append(tabName).Append(" (");
			foreach (string[] sCol in cols)
			{
			   sb.Append(sCol[0]).Append(" ").Append(sCol[1]).Append(",");
			}
			sb.Remove(sb.Length - 1, 1); // remove the last comma
			sb.Append(")");

			return sb.ToString();
		}

		/// <summary>
		///  Generate a "Drop table" SQL query
		/// </summary>
		public static string genDropTable(string tabName)
		{
		   // Adding "IF EXISTS" will not generate error if table does not exists
		   return "DROP TABLE IF EXISTS " + tabName;
		}

		/// <summary>
		///  Generate a "WHERE" clause
		/// </summary>
		///  @param [in] values -- each element is a SqlCondClause or a SqlSimpCond or a String </param>
		public static string genWhere(params object[] values)
		{
			List<object> objList = new List<object>();

			foreach (object val in values)
			{
				objList.Add(val);
			}

			return genWhere(objList);
		}

		public static string genWhere(List<object> values)
		{
			StringBuilder sb = new StringBuilder(256);

			foreach (object val in values)
			{
			   if (val is SqlCondClause)
			   {
				  ((SqlCondClause)val).genString(sb);
			   }
			   else if (val is SqlSimpCond)
			   {
				  ((SqlSimpCond)val).genString(sb);
			   }
			   else if (val is string)
			   {
				  // it is a single op string or other specifiers
				  sb.Append(' ');
				  sb.Append((string)val);
				  sb.Append(' ');
			   }
			}

			return sb.ToString();
		}

		/// <summary>
		///  Get content values ready for insert/update
		/// </summary>
		public static ContentValues genContentValues(List<object> colsAndValues)
		{
			ContentValues contVals = new ContentValues();

			for (int i = 0; i < colsAndValues.Count; i += 2)
			{
				object val = colsAndValues[i + 1];
				if (val == null)
				{
					contVals.putNull((string)colsAndValues[i]);
				}
				else if (val is string)
				{
					contVals.put((string)colsAndValues[i], (string)val);
				}
				else if (val is int?)
				{
					contVals.put((string)colsAndValues[i], (int?)val);
				}
				else if (val is long?)
				{
					contVals.put((string)colsAndValues[i], (long?)val);
				}
				else if (val is sbyte[])
				{
					contVals.put((string)colsAndValues[i], (sbyte[])val);
				}
				else if (val is FsID)
				{
					contVals.put((string)colsAndValues[i], ((FsID)val).Val);
				}
			}

			return contVals;
		}
	}

}