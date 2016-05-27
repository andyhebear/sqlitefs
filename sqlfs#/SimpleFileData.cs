using System.Collections.Generic;

namespace com.sss.sqlfs
{

	using SqlStr = com.sss.sqlfs.helper.SqlStr;
	using ContentValues = android.content.ContentValues;
	using Cursor = android.database.Cursor;

	/// <summary>
	///  An implementation of IFileData which saves text data or raw binary data.  
	/// </summary>
	public class SimpleFileData : IFileData
	{
		//! fields in addition to dID
		protected internal enum FILEDATABLOCK
		{
		   dID, // just a place holder
		   dFileType,
		   dTextData,
		   dRawBinData
		}

		protected internal enum FILETYPE
		{
		   fText, ///< text
		   fBin ///< raw binary
		}

		private static readonly string[][] colDataBlock = new string[][] {new string[]{IFileData.IDCOL, IFileData.IDCOLTYPE}, new string[]{FILEDATABLOCK.dFileType.ToString(), "integer"}, new string[]{FILEDATABLOCK.dTextData.ToString(), "text"}, new string[]{FILEDATABLOCK.dRawBinData.ToString(), "blob"}};

		private sbyte[] rawBinData;
		private string textData;

		public SimpleFileData()
		{
		}

		public virtual sbyte[] RawBinData
		{
			get
			{
			   return rawBinData;
			}
			set
			{
			   this.rawBinData = value;
			   this.textData = null;
			}
		}


		public virtual string Text
		{
			get
			{
			   return textData;
			}
		}

		public virtual string TextData
		{
			set
			{
			   this.textData = value;
			   this.rawBinData = null;
			}
		}

		public virtual bool TextFile
		{
			get
			{
			   return (this.textData != null);
			}
		}

		/// <summary>
		///  Return DataBlock table schema
		/// </summary>
		public override string[][] ColSchema
		{
			get
			{
				return colDataBlock;
			}
		}

		/// <summary>
		///  Get data from cursor 
		/// </summary>
		protected internal override void __getData(Cursor c)
		{
		   int fileType = c.getInt(FILEDATABLOCK.dFileType.ordinal());
		   if (fileType == FILETYPE.fBin.ordinal())
		   {
			  this.rawBinData = c.getBlob(FILEDATABLOCK.dRawBinData.ordinal());
			  this.textData = null; // clear
		   }
		   else if (fileType == FILETYPE.fText.ordinal())
		   {
			  this.textData = c.getString(FILEDATABLOCK.dTextData.ordinal());
			  this.rawBinData = null; // clear
		   }
		}

		/// <summary>
		///  Return a ContentValues to be inserted or updated to DB
		/// </summary>
		protected internal override ContentValues __saveData()
		{
			List<object> colsAndValues = new List<object>(2);
			FILETYPE fType = FILETYPE.fBin;
			if (this.rawBinData != null)
			{
			   colsAndValues.Add(FILEDATABLOCK.dRawBinData.ToString());
			   colsAndValues.Add(this.rawBinData);
			   colsAndValues.Add(FILEDATABLOCK.dTextData.ToString()); // clear text column
			   colsAndValues.Add("");
			}
			else if (this.textData != null)
			{
			   colsAndValues.Add(FILEDATABLOCK.dTextData.ToString());
			   colsAndValues.Add(this.textData);
			   colsAndValues.Add(FILEDATABLOCK.dRawBinData.ToString()); // clear rawbin column
			   colsAndValues.Add(null);
			   fType = FILETYPE.fText;
			}

			// file type
			colsAndValues.Add(FILEDATABLOCK.dFileType.ToString());
			colsAndValues.Add(fType.ordinal());

			return SqlStr.genContentValues(colsAndValues);
		}

		/// <summary>
		///  Return the number of bytes used up by rawBin or text data 
		/// </summary>
		public override int DataSizeInByte
		{
			get
			{
				if (this.rawBinData != null)
				{
				   return this.rawBinData.Length;
				}
				else if (this.textData != null)
				{
				   return this.textData.Length * 2; // each character is 2 bytes
				}
    
				return 0;
			}
		}
	}

}