/*
 * (c) 2009-2019 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Emulation einer Diskette basierend auf einer strukturlosen Abbilddatei
 */

package jkcemu.disk;

import java.awt.Frame;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.Properties;
import jkcemu.base.DeviceIO;
import jkcemu.base.EmuUtil;
import jkcemu.file.FileUtil;


public class PlainDisk extends AbstractFloppyDisk
{
  public static final String PROP_DRIVE = "drive";

  private String                      fileName;
  private FileLock                    fileLock;
  private DeviceIO.RandomAccessDevice rad;
  private RandomAccessFile            raf;
  private byte[]                      diskBytes;
  private boolean                     readOnly;
  private boolean                     appendable;
  private int                         sectorSizeCode;


  public static PlainDisk createForDrive(
				Frame                       owner,
				String                      driveFileName,
				DeviceIO.RandomAccessDevice rad,
				boolean                     readOnly,
				FloppyDiskFormat            fmt )
  {
    return new PlainDisk(
			owner,
			fmt.getCylinders(),
			fmt.getSides(),
			fmt.getSectorsPerTrack(),
			fmt.getSectorSize(),
			0,			// kein Interleave
			driveFileName,
			rad,
			null,
			null,
			null,
			readOnly,
			false );
  }


  public static PlainDisk createForByteArray(
			Frame            owner,
			String           fileName,
			byte[]           fileBytes,
			FloppyDiskFormat fmt,
			int              interleave ) throws IOException
  {
    PlainDisk rv = null;
    if( fileBytes != null ) {
      rv = new PlainDisk(
			owner,
			fmt.getCylinders(),
			fmt.getSides(),
			fmt.getSectorsPerTrack(),
			fmt.getSectorSize(),
			interleave,
			fileName,
			null,
			null,
			null,
			fileBytes,
			true,
			false );
    }
    return rv;
  }


  public static PlainDisk createForByteArray(
			Frame            owner,
			String           fileName,
			byte[]           fileBytes,
			FloppyDiskFormat fmt ) throws IOException
  {
    return createForByteArray( owner, fileName, fileBytes, fmt, 0 );
  }


  public static PlainDisk createForFile(
				Frame            owner,
				String           driveFileName,
				RandomAccessFile raf,
				boolean          readOnly,
				FloppyDiskFormat fmt )
  {
    return new PlainDisk(
			owner,
			fmt.getCylinders(),
			fmt.getSides(),
			fmt.getSectorsPerTrack(),
			fmt.getSectorSize(),
			0,			// kein Interleave
			driveFileName,
			null,
			raf,
			null,
			null,
			readOnly,
			false );
  }


  public static String export(
			AbstractFloppyDisk disk,
			File               file ) throws IOException
  {
    StringBuilder msgBuf  = null;
    IOException   ioEx    = null;
    OutputStream  out     = null;
    boolean       created = false;
    try {
      out     = new FileOutputStream( file );
      created = true;

      boolean dataDeleted     = false;
      int     cyls            = disk.getCylinders();
      int     sides           = disk.getSides();
      int     sectorsPerTrack = disk.getSectorsPerTrack();
      int     sectorSize      = disk.getSectorSize();
      for( int cyl = 0; cyl < cyls; cyl++ ) {
	for( int head = 0; head < sides; head++ ) {
	  int cylSectors = disk.getSectorsOfTrack( cyl, head );
	  if( cylSectors != sectorsPerTrack ) {
	    if( msgBuf == null ) {
	      msgBuf = new StringBuilder( 1024 );
	    }
	    msgBuf.append(
		String.format(
			"Spur %d, Seite %d: %d anstelle von %d Sektoren"
				+ " vorhanden",
			cyl,
			head + 1,
			cylSectors,
			sectorsPerTrack ) );
	  }
	  for( int i = 0; i < sectorsPerTrack; i++ ) {
	    SectorData sector = disk.getSectorByID(
						cyl,
						head,
						cyl,
						head,
						i + 1,
						-1 );
	    if( sector == null ) {
	      throw new IOException(
		String.format(
			"Seite %d, Spur %d: Sektor %d nicht gefunden"
				+ "\n\nDas Format unterst\u00FCtzt"
				+ " keine freie Sektoranordnung.",
			head + 1,
			cyl,
			i + 1  ) );
	    }
	    if( sector.checkError()
		|| sector.getDataDeleted()
		|| sector.hasBogusID() )
	    {
	      if( msgBuf == null ) {
		msgBuf = new StringBuilder( 1024 );
	      }
	      msgBuf.append(
			String.format(
				"Seite %d, Spur %d, Sektor %d:",
				head + 1,
				cyl,
				sector.getSectorNum() ) );
	      boolean appended = false;
	      if( sector.hasBogusID() ) {
		msgBuf.append( " Sektor-ID generiert" );
		appended = true;
	      }
	      if( sector.checkError() ) {
		if( appended ) {
		  msgBuf.append( ',' );
		}
		msgBuf.append( " CRC-Fehler" );
		appended = true;
	      }
	      if( sector.getDataDeleted() ) {
		if( appended ) {
		  msgBuf.append( ',' );
		}
		msgBuf.append( " Daten als gel\u00F6scht markiert" );
		appended    = true;
		dataDeleted = true;
	      }
	      msgBuf.append( '\n' );
	    }
	    if( sector.getDataDeleted() ) {
	      dataDeleted = true;
	      if( msgBuf == null ) {
		msgBuf = new StringBuilder( 1024 );
	      }
	      msgBuf.append(
			String.format(
				"Seite %d, Spur %d: Sektor %d ist als"
					+ " gel\u00F6scht markiert\n",
				head + 1,
				cyl,
				sector.getSectorNum() ) );
	    }
	    if( sector.getDataLength() > sectorSize ) {
	      throw new IOException(
		String.format(
			"Seite %d, Spur %d: Sektor %d ist zu gro\u00DF."
				+ "\n\nDas Format unterst\u00FCtzt"
				+ " keine \u00FCbergro\u00DFen Sektoren.",
			head + 1,
			cyl,
			sector.getSectorNum() ) );
	    }
	    int n = sector.writeTo( out, sectorSize );
	    while( n < sectorSize ) {
	      out.write( 0 );
	      n++;
	    }
	  }
	}
      }
      out.close();
      out = null;

      if( msgBuf != null ) {
	msgBuf.append( "\nDie angezeigten Informationen k\u00F6nnen"
		+ " in einer einfachen Abbilddatei nicht gespeichert werden\n"
		+ "und sind deshalb in der erzeugten Datei"
		+ " nicht mehr enthalten.\n" );
	if( dataDeleted ) {
	  msgBuf.append( "\nSektoren mit gel\u00F6schten Daten werden"
		+ " in einfachen Abbilddateien nicht unterst\u00FCtzt\n"
		+ "und sind deshalb als normale Sektoren enthalten.\n" );
	}
      }
    }
    catch( IOException ex ) {
      ioEx = ex;
    }
    finally {
      EmuUtil.closeSilently( out );
    }
    if( ioEx != null ) {
      if( created ) {
	file.delete();
      }
      throw ioEx;
    }
    return msgBuf != null ? msgBuf.toString() : null;
  }


  public static PlainDisk newFile( Frame owner, File file ) throws IOException
  {
    PlainDisk        rv  = null;
    FileLock         fl  = null;
    RandomAccessFile raf = null;
    try {
      raf = new RandomAccessFile( file, "rw" );
      fl  = FileUtil.lockFile( file, raf );
      raf.setLength( 0 );
      rv = new PlainDisk(
			owner,
			0,
			0,
			0,
			0,
			0,			// kein Interleave
			file.getPath(),
			null,
			raf,
			fl,
			null,
			false,
			true );
    }
    finally {
      if( rv == null ) {
        FileUtil.releaseSilent( fl );
        EmuUtil.closeSilently( raf );
      }
    }
    return rv;
  }


  public static PlainDisk openFile(
				Frame            owner,
				File             file,
				boolean          readOnly,
				FloppyDiskFormat fmt ) throws IOException
  {
    PlainDisk        rv  = null;
    FileLock         fl  = null;
    RandomAccessFile raf = null;
    try {
      raf = new RandomAccessFile( file, readOnly ? "r" : "rw" );
      if( !readOnly ) {
	fl = FileUtil.lockFile( file, raf );
      }
      rv = new PlainDisk(
			owner,
			fmt.getCylinders(),
			fmt.getSides(),
			fmt.getSectorsPerTrack(),
			fmt.getSectorSize(),
			0,			// kein Interleave
			file.getPath(),
			null,
			raf,
			fl,
			null,
			readOnly,
			!readOnly );
    }
    finally {
      if( rv == null ) {
        FileUtil.releaseSilent( fl );
        EmuUtil.closeSilently( raf );
      }
    }
    return rv;
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public synchronized void closeSilently()
  {
    FileUtil.releaseSilent( this.fileLock );
    EmuUtil.closeSilently( this.raf );
    EmuUtil.closeSilently( this.rad );
  }


  @Override
  public boolean formatTrack(
			int        physCyl,
			int        physHead,
			SectorID[] sectorIDs,
			byte[]     dataBuf )
  {
    boolean rv = false;
    if( !this.readOnly
	&& ((this.rad != null) || (this.raf != null))
	&& (sectorIDs != null)
	&& (dataBuf != null) )
    {
      int oldSectorSize = getSectorSize();
      if( (sectorIDs.length > 0)
	  && ((oldSectorSize == 0) || (oldSectorSize == dataBuf.length))
	  && this.appendable )
      {
	rv = true;
	try {
	  for( int i = 0; i < sectorIDs.length; i++ ) {
	    int  sectorIdx = sectorIDs[ i ].getSectorNum() - 1;
	    long filePos   = calcFilePos( physCyl, physHead, sectorIdx );
	    if( filePos >= 0 ) {
	      if( this.rad != null ) {
		this.rad.seek( filePos );
		this.rad.write( dataBuf, 0, dataBuf.length );
	      } else {
		this.raf.seek( filePos );
		this.raf.write( dataBuf );
	      }
	      if( physCyl >= getCylinders() ) {
		setCylinders( physCyl + 1 );
	      }
	      int sides = ((physHead & 0x01) != 0 ? 2 : 1);
	      if( sides > getSides() ) {
		setSides( sides );
	      }
	      if( sectorIDs.length > getSectorsPerTrack() ) {
		setSectorsPerTrack( sectorIDs.length );
	      }
	      if( getSectorSize() == 0 ) {
		setSectorSize( dataBuf.length );
		this.sectorSizeCode = sectorIDs[ 0 ].getSizeCode();
	      }
	    } else {
	      rv = false;
	      break;
	    }
	  }
	}
	catch( IOException ex ) {
	  rv = false;
	  fireShowError( "Anh\u00E4ngen von Sektoren fehlgeschlagen", ex );
	}
      } else {
	rv = super.formatTrack( physCyl, physHead, sectorIDs, dataBuf );
      }
    }
    return rv;
  }


  @Override
  public String getFileFormatText()
  {
    return "Einfache Abbilddatei";
  }


  @Override
  public synchronized SectorData getSectorByIndex(
					int physCyl,
					int physHead,
					int sectorIdx )
  {
    return getSectorByIndexInternal(
				physCyl,
				physHead,
				sectorIndexToInterleave( sectorIdx ) );
  }


  @Override
  public SectorData getSectorByID(
				int physCyl,
				int physHead,
				int cyl,
				int head,
				int sectorNum,
				int sizeCode )
  {
    SectorData rv = getSectorByIndexInternal(
					physCyl,
					physHead,
					sectorNum - 1 );
    if( rv != null ) {
      if( (rv.getCylinder() != cyl)
	  || (rv.getHead() != head)
	  || (rv.getSectorNum() != sectorNum)
	  || ((sizeCode >= 0) && (rv.getSizeCode() != sizeCode)) )
      {
	rv = null;
      }
    }
    return rv;
  }


  @Override
  public int getSectorOffset()
  {
    return 0;
  }


  @Override
  public boolean isReadOnly()
  {
    return this.readOnly;
  }


  @Override
  public void putSettingsTo( Properties props, String prefix )
  {
    super.putSettingsTo( props, prefix );
    if( (props != null) && (fileName != null) ) {
      if( this.rad != null ) {
	props.setProperty( prefix + PROP_DRIVE, this.fileName );
      } else {
	props.setProperty( prefix + PROP_FILE, this.fileName );
      }
    }
  }


  @Override
  public boolean writeSector(
			int        physCyl,
			int        physHead,
			SectorData sector,
			byte[]     dataBuf,
			int        dataLen,
			boolean    dataDeleted )
  {
    boolean rv = false;
    if( !this.readOnly
	&& ((this.rad != null) || (this.raf != null))
	&& (sector != null)
	&& (dataBuf != null)
	&& !dataDeleted )
    {
      if( (sector.getDisk() == this) && (dataLen == getSectorSize()) ) {
	int  sectorIdx = sector.getIndexOnCylinder();
	long filePos   = calcFilePos( physCyl, physHead, sectorIdx );
	if( filePos == sector.getFilePos() ) {
	  try {
	    if( this.rad != null ) {
	      this.rad.seek( filePos );
	      this.rad.write( dataBuf, 0, dataLen );
	    } else {
	      this.raf.seek( filePos );
	      this.raf.write( dataBuf, 0, dataLen );
	    }
	    rv = true;
	  }
	  catch( IOException ex ) {
	    fireShowWriteError(
			physCyl,
			physHead,
			sector.getSectorNum(),
			ex );
	    sector.setError( true );
	  }
	}
      }
    }
    return rv;
  }


	/* --- private Methoden --- */

  private PlainDisk(
		Frame                       owner,
		int                         cyls,
		int                         sides,
		int                         sectorsPerTrack,
		int                         sectorSize,
		int                         interleave,
		String                      fileName,
		DeviceIO.RandomAccessDevice rad,
		RandomAccessFile            raf,
		FileLock                    fileLock,
		byte[]                      diskBytes,
		boolean                     readOnly,
		boolean                     appendable )
  {
    super( owner, cyls, sides, sectorsPerTrack, sectorSize, interleave );
    this.fileName       = fileName;
    this.rad            = rad;
    this.raf            = raf;
    this.fileLock       = fileLock;
    this.diskBytes      = diskBytes;
    this.readOnly       = readOnly;
    this.appendable     = appendable;
    this.sectorSizeCode = SectorData.getSizeCodeBySize( sectorSize );
  }


  private long calcFilePos( int cyl, int head, int sectorIdx )
  {
    head &= 0x01;

    long rv         = -1;
    int  sectorSize = getSectorSize();
    if( (cyl >= 0) && (sectorIdx >= 0) ) {
      int cyls            = getCylinders();
      int sides           = getSides();
      int sectorsPerTrack = getSectorsPerTrack();
      if( (cyl < cyls)
	  && (head < sides)
	  && (sectorIdx < sectorsPerTrack)
	  && (sectorSize > 0) )
      {
	int nSkipSectors = sides * sectorsPerTrack * cyl;
	if( head > 0 ) {
	  nSkipSectors += sectorsPerTrack;
	}
	nSkipSectors += sectorIdx;
	rv = (long) nSkipSectors * (long) sectorSize;
      } else {
	/*
	 * Waehrend des Formatierens sind die Formatinformationen
	 * noch unvollstaendig.
	 * Deshalb wird hier sichergestellt,
	 * dass die Positionsberechnung funktioniert,
	 * wenn aufsteigend formatiert wird.
	 */
	if( (cyl == 0) && (cyls <= 1) ) {
	  if( (head == 0) && (sectorIdx == 0) ) {
	    rv = 0L;
	  } else {
	    if( sectorSize > 0 ) {
	      if( head == 0 ) {
		rv = sectorIdx * sectorSize;
	      }
	      else if( (head == 1) && (sectorsPerTrack > 0) ) {
		rv = (sectorsPerTrack + sectorIdx) * sectorSize;
	      }
	    }
	  }
	}
      }
    }
    return rv;
  }


  private synchronized SectorData getSectorByIndexInternal(
							int physCyl,
							int physHead,
							int sectorIdx )
  {
    SectorData rv         = null;
    int        sectorSize = getSectorSize();
    long       filePos    = calcFilePos( physCyl, physHead, sectorIdx );
    if( (sectorSize > 0) && (filePos >= 0) ) {
      if( this.diskBytes != null ) {
	if( filePos <= Integer.MAX_VALUE ) {
	  rv = new SectorData(
			sectorIdx,
			physCyl,
			physHead,
			sectorIdx + 1,
			this.sectorSizeCode,
			this.diskBytes,
			(int) filePos,
			sectorSize );
	}
      }
      else if( (this.rad != null) || (this.raf != null) ) {
	try {
	  byte[] buf = new byte[ sectorSize ];
	  int    len = -1;
	  if( this.rad != null ) {
	    this.rad.seek( filePos );
	    len = this.rad.read( buf, 0, buf.length );
	  } else {
	    this.raf.seek( filePos );
	    len = this.raf.read( buf );
	  }
	  if( len > 0 ) {
	    // falls nicht vollstaendig gelesen wurde
	    while( len < buf.length ) {
	      int n = -1;
	      if( this.rad != null ) {
		n = this.rad.read( buf, len, buf.length - len );
	      } else {
		n = this.raf.read( buf, len, buf.length - len );
	      }
	      if( n > 0 ) {
		len += n;
	      } else {
		break;
	      }
	    }
	  }
	  if( len > 0 ) {
	    rv = new SectorData(
				sectorIdx,
				physCyl,
				physHead,
				sectorIdx + 1,
				this.sectorSizeCode,
				buf,
				0,
				len );
	  }
	}
	catch( IOException ex ) {
	  fireShowReadError( physCyl, physHead, sectorIdx + 1, ex );
	  rv = new SectorData(
			sectorIdx,
			physCyl,
			physHead,
			sectorIdx + 1,
			this.sectorSizeCode,
			null,
			0,
			0 );
	  rv.setError( true );
	}
      }
      if( rv != null ) {
	rv.setDisk( this );
	rv.setFilePos( filePos );
	rv.setFilePortionLen( sectorSize );
      }
    }
    return rv;
  }
}
