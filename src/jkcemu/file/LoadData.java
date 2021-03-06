/*
 * (c) 2008-2017 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Daten, die in den Arbeitsspeicher geladen
 * und dort ggf. gestartet werden koennen
 */

package jkcemu.file;

import java.io.IOException;
import jkcemu.base.EmuMemView;
import jkcemu.base.EmuThread;
import jkcemu.base.EmuSys;


public class LoadData implements EmuMemView
{
  private byte[]     data;
  private int        offset;
  private int        len;
  private int        begAddr;
  private int        startAddr;
  private int        fileType;
  private FileFormat fileFmt;
  private String     infoMsg;


  public LoadData(
		byte[]     data,
		int        offset,
		int        len,
		int        begAddr,
		int        startAddr,
		FileFormat fileFmt ) throws IOException
  {
    if( (data == null) || (offset < 0) ) {
      throwLoadError();
    }
    if( (offset + len) > data.length ) {
      len = data.length - offset;
    }
    if( len <= 0 ) {
      throwLoadError();
    }
    this.data      = data;
    this.offset    = offset;
    this.len       = len;
    this.begAddr   = begAddr;
    this.startAddr = startAddr;
    this.fileFmt   = fileFmt;
    this.fileType  = -1;
    this.infoMsg   = null;
  }


  public int getAbsoluteByte( int idx )
  {
    int rv = -1;
    if( this.data != null ) {
      if( (idx >= 0)
	  && (idx < this.data.length)
	  && (idx < this.offset + this.len) )
      {
	rv = this.data[ idx ] & 0xFF;
      }
    }
    return rv;
  }


  public byte[] getByteArray()
  {
    return this.data;
  }


  public int getBegAddr()
  {
    return this.begAddr;
  }


  public int getEndAddr()
  {
    return this.begAddr + this.len - 1;
  }


  public int getOffset()
  {
    return this.offset;
  }


  public int getLength()
  {
    return this.len;
  }


  public int getFileType()
  {
    return this.fileType;
  }


  public String getInfoMsg()
  {
    return this.infoMsg;
  }


  public int getStartAddr()
  {
    return this.startAddr;
  }


  public void loadIntoMemory(
			EmuThread     emuThread,
			StringBuilder rvStatusMsg )
  {
    if( (emuThread != null) && (this.data != null) ) {
      EmuSys emuSys = emuThread.getEmuSys();
      if( emuSys != null ) {
	emuSys.loadIntoMem(
			this.begAddr,
			this.data,
			this.offset,
			this.len,
			this.fileFmt,
			this.fileType,
			rvStatusMsg );
      }
    }
  }


  public void relocateKCBasicProgram( int begAddr ) throws IOException
  {
    int diff = begAddr - this.begAddr;
    if( diff != 0 ) {
      int len = Math.min( this.len, this.data.length - this.offset );
      if( len > 0 ) {
	byte[] buf = new byte[ len ];
	System.arraycopy( this.data, this.offset, buf, 0, len );

	int curAddr = this.begAddr;
	do {
	  int nextAddr = -1;
	  int idx      = curAddr - this.begAddr;
	  if( (idx >= 0) && (idx + 1 < buf.length) && (idx + 1 < len) ) {
	    nextAddr = (((int) buf[ idx + 1 ] & 0xFF) << 8)
				  | ((int) buf[ idx ] & 0xFF);
	    if( nextAddr > curAddr ) {
	      int changedAddr = nextAddr + diff;
	      buf[ idx ]      = (byte) (changedAddr & 0xFF);
	      buf[ idx + 1 ]  = (byte) ((changedAddr >> 8) & 0xFF);
	    }
	  }
	  if( (nextAddr > 0) && (nextAddr <= curAddr) ) {
	    throw new IOException( "Das KC-BASIC-Programm kann nicht auf die"
			  + " gew\u00FCnschte Adresse reloziert werden." );
	  }
	  curAddr = nextAddr;
	} while( curAddr > 0 );

	this.begAddr = begAddr;
	this.offset  = 0;
	this.data    = buf;
      }
    }
  }


  public void setBegAddr( int addr )
  {
    this.begAddr = addr;
  }


  public void setFileType( int fileType )
  {
    this.fileType = fileType;
  }


  public void setInfoMsg( String infoMsg )
  {
    this.infoMsg = infoMsg;
  }


  public void setLength( int len )
  {
    if( len < 0 ) {
      len = 0;
    }
    if( len < this.len ) {
      this.len = len;
    }
  }


  public void setStartAddr( int addr )
  {
    this.startAddr = addr;
  }


	/* --- EmuMemView --- */

  @Override
  public int getBasicMemByte( int addr )
  {
    return getAbsoluteByte( this.offset + addr - this.begAddr );
  }


	/* --- Z80MemView --- */

  @Override
  public int getMemByte( int addr, boolean m1 )
  {
    return getAbsoluteByte( this.offset + addr - this.begAddr );
  }


  @Override
  public int getMemWord( int addr )
  {
    return (getMemByte( addr + 1, false ) << 8) | getMemByte( addr, false );
  }


	/* --- private Methoden --- */

  private static void throwLoadError() throws IOException
  {
    throw new IOException( "Datei kann nicht geladen werden." );
  }
}
