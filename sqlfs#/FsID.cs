using System;

namespace com.sss.sqlfs
{

	using Log = android.util.Log;

	/// <summary>
	/// Class to represent an ID in the file system. May be 32bit or 64bit
	/// </summary>
	public class FsID
	{
		// set to true if use long (64bit) ID
		private const bool useLongID = false;
		private const int INT32SIZE = 4;
		private const int INT64SIZE = 8;

		public static int IDSize
		{
			get
			{
				return useLongID ? INT64SIZE : INT32SIZE;
			}
		}
		public static bool LongID
		{
			get
			{
				return useLongID;
			}
		}


		private IFsID _fsID;

		private FsID()
		{
		}


		internal FsID(int id)
		{
			_fsID = useLongID ? new FsID64(this, id) : new FsID32(this, id);
		}

		internal FsID(long id)
		{
			_fsID = useLongID ? new FsID64(this, id) : new FsID32(this, unchecked((int)(id & 0xffffffff)));
		}

		public virtual int compare(FsID value)
		{
		   return _fsID.compare(value._fsID);
		}

		public virtual bool Equals(FsID value)
		{
		   return _fsID.Equals(value._fsID);
		}

		public override bool Equals(object obj)
		{
		   if (obj is FsID)
		   {
			   return this.Val == ((FsID)obj).Val;
		   }

		   return false;
		}

		public override string ToString()
		{
		   return Convert.ToString(Val);
		}

		/// <summary>
		///  Get underlying ID value
		/// </summary>
		public virtual long Val
		{
			get
			{
			   return useLongID ? ((FsID64)_fsID).id : ((FsID32)_fsID).id;
			}
		}

		/// <summary>
		///  Convert from int to FsID
		/// </summary>
		public static FsID toFsID(int id)
		{
		   return new FsID(id);
		}

		/// <summary>
		///  Convert from long to IntID
		/// </summary>
		public static FsID toFsID(long id)
		{
		   return new FsID(id);
		}

		//////////////////////// inner interface and class /////////////////////

		private interface IFsID
		{
		   int compare(IFsID aID);
		   bool Equals(IFsID aID);
		}

		/// <summary>
		/// 64bit FsID
		/// </summary>
		private class FsID64 : IFsID
		{
			private readonly FsID outerInstance;

		   internal long id;

		   public FsID64(FsID outerInstance, int id)
		   {
			   this.outerInstance = outerInstance;
			  this.id = id;
		   }

		   public FsID64(FsID outerInstance, long id)
		   {
			   this.outerInstance = outerInstance;
			  this.id = id;
		   }

		   public virtual int compare(IFsID aID)
		   {
			   if (!(aID is FsID64))
			   {
				   return -1;
			   }

			   long longID = ((FsID64)aID).id;
			   if (this.id > longID)
			   {
				   return 1;
			   }
			   if (this.id < longID)
			   {
				   return -1;
			   }
			   return 0;
		   }

		   public virtual bool Equals(IFsID aID)
		   {
			  if (!(aID is FsID64))
			  {
				  return false;
			  }
			  return this.id == ((FsID64)aID).id;
		   }
		}

		/// <summary>
		/// 32bit FsID
		/// </summary>
		private class FsID32 : IFsID
		{
			private readonly FsID outerInstance;

		   internal int id;

		   public FsID32(FsID outerInstance, int id)
		   {
			   this.outerInstance = outerInstance;
			  this.id = id;
		   }

		   public virtual int compare(IFsID aID)
		   {
			   if (!(aID is FsID32))
			   {
				   return -1;
			   }

			   int intID = ((FsID32)aID).id;
			   if (this.id > intID)
			   {
				   return 1;
			   }
			   if (this.id < intID)
			   {
				   return -1;
			   }
			   return 0;
		   }

		   public virtual bool Equals(IFsID aID)
		   {
			  if (!(aID is FsID32))
			  {
				  return false;
			  }
			  return this.id == ((FsID32)aID).id;
		   }
		}
	}

}