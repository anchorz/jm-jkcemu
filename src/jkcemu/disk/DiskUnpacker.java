/*
 * (c) 2009-2020 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Entpacker fuer eine CP/M-Diskette
 */

package jkcemu.disk;

import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.Window;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import jkcemu.base.AbstractThreadDlg;
import jkcemu.base.BaseDlg;
import jkcemu.base.EmuUtil;
import jkcemu.file.FileTimesData;
import jkcemu.file.FileUtil;


public class DiskUnpacker extends AbstractThreadDlg
{
  private AbstractFloppyDisk disk;
  private String             diskDesc;
  private File               outDir;
  private int                sysTracks;
  private int                sides;
  private int                sectorsPerTrack;
  private int                sectorOffset;
  private int                sectorSize;
  private int                blockSize;
  private int                maxBlocksPerEntry;
  private int                sectPerBlock;
  private boolean            blockNum16Bit;
  private boolean            applyReadOnly;
  private boolean            forceLowerCase;
  private boolean            fileErr;
  private boolean            blockNumErr;


  public static void unpackDisk(
		Window             owner,
		AbstractFloppyDisk disk,
		String             diskDesc,
		File               outDir,
		int                sysTracks,
		int                blockSize,
		boolean            blockNum16Bit,
		boolean            applyReadOnly,
		boolean            forceLowerCase ) throws IOException
  {
    Dialog dlg = new DiskUnpacker(
				owner,
				disk,
				diskDesc,
				outDir,
				sysTracks,
				blockSize,
				blockNum16Bit,
				applyReadOnly,
				forceLowerCase );
    dlg.setTitle( diskDesc + " entpacken" );
    dlg.setVisible( true );
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  protected void doProgress()
  {
    if( (this.sides < 1) || (this.sides > 2)
	|| (this.sectorsPerTrack < 1)
	|| (this.sectorSize < 256)
	|| (this.blockSize < 1024)
	|| (this.sectPerBlock < 1)
	|| (this.maxBlocksPerEntry < 1) )
    {
      StringBuilder buf = new StringBuilder( 256 );
      buf.append( "Ung\u00FCltiger Formatangaben:\n"
		+ "\nSeiten:                  " );
      buf.append( this.sides );
      buf.append( "\nSektoren pro Spur:       " );
      buf.append( this.sectorsPerTrack );
      buf.append( "\nSektorgr\u00F6\u00DFe:             " );
      buf.append( this.sectorSize );
      buf.append( "\nBlockgr\u00F6\u00DFe:              " );
      buf.append( this.blockSize );
      buf.append( "\nSektoren pro Block:      " );
      buf.append( this.sectPerBlock );
      buf.append( "\nMax. Bl\u00F6cke pro Eintrag: " );
      buf.append( this.maxBlocksPerEntry );
      buf.append( '\n' );
      appendToLog( buf.toString() );
      incErrorCount();
    } else {

      // Systemspuren
      if( this.sysTracks > 0 ) {
	String       fName = "@boot.sys";
	File         file  = prepareOutFile( this.outDir, fName );
	OutputStream out   = null;
	boolean      err   = false;
	this.outDir.mkdirs();
	appendToLog( fName + "\n" );
	try {
	  out = new FileOutputStream( file );

	  int sectorsPerTrack = this.disk.getSectorsPerTrack();
	  for( int cyl = 0; cyl < this.sysTracks; cyl++ ) {
	    for( int head = 0; head < this.disk.getSides(); head++ ) {
	      for( int sectIdx = 0; sectIdx < sectorsPerTrack; sectIdx++ ) {
		SectorData sector = this.disk.getSectorByID(
					cyl,
					head,
					cyl,
					head,
					sectIdx + 1 + this.sectorOffset,
					-1 );
		if( sector != null ) {
		  if( sector.checkError() ) {
		    err = true;
		  }
		  sector.writeTo( out, -1 );
		}
	      }
	    }
	  }
	  out.close();
	  out = null;
	}
	catch( IOException ex ) {
	  appendErrorToLog( ex );
	  incErrorCount();
	  err = true;
	}
	finally {
	  EmuUtil.closeSilently( out );
	  if( err ) {
	    fName = "@boot.sys.error";
	    if( file.renameTo( new File( outDir, "@boot.sys.error" ) ) ) {
	      appendToLog( "  Datei in " + fName + " umbenannt\n" );
	      disableAutoClose();
	    }
	  }
	}
      }

      // Directory-Bloecke lesen
      java.util.List<byte[]> dirBlocks = new ArrayList<>();
      int                    blkIdx    = 0;
      for(;;) {
	byte[] blkBuf = readLogicalBlock( blkIdx++ );
	if( blkBuf != null ) {
	  if( DiskUtil.isFilledDir( blkBuf ) ) {
	    dirBlocks.add( blkBuf );
	    continue;
	  }
	}
	break;
      }

      // Directory durchgehen
      byte[] timeBytes = null;
      if( dirBlocks.isEmpty() ) {
	appendToLog( this.diskDesc + " ist leer.\n" );
	disableAutoClose();
      } else {
	int extentsPerDirEntry = DiskUtil.getExtentsPerDirEntry(
							this.blockSize,
							this.blockNum16Bit );
	int     dirBlkOffs = 0;
	boolean firstEntry = true;
	this.outDir.mkdirs();
	for( byte[] dirBlockBuf : dirBlocks ) {
	  if( dirBlockBuf != null ) {
	    int entryPos = 0;
	    while( (entryPos + 31) < dirBlockBuf.length ) {
	      /*
	       * Gueltige erste Directory-Eintraege suchen,
	       * Das 1. Bye wird dabei mit ausgeblendetem Bit 4
	       * (Passwort-Bit) auswertet.
	       */
	      int b0        = (int) dirBlockBuf[ entryPos ] & 0xEF;
	      int extentNum = DiskUtil.getExtentNumByEntryPos(
							dirBlockBuf,
							entryPos );
	      if( (b0 >= 0) && (b0 <= 0x1F)
		  && (extentNum >= 0) && (extentNum < extentsPerDirEntry) )
	      {
		String fileName = getFileName( dirBlockBuf, entryPos + 1 );
		if( fileName != null ) {
		  this.fileErr = false;

		  boolean readOnly = false;
		  if( this.applyReadOnly ) {
		    readOnly = (((int) dirBlockBuf[ entryPos + 9 ]
							& 0x80) != 0);
		  }

		  File outDir = this.outDir;
		  if( b0 > 0 ) {
		    String subDir = String.valueOf( b0 );
		    outDir        = new File( this.outDir, subDir );
		    outDir.mkdirs();
		    appendToLog(
			subDir + File.separatorChar + fileName + "\n" );
		  } else {
		    appendToLog( fileName + "\n" );
		  }
		  File         file = prepareOutFile( outDir, fileName );
		  OutputStream out  = null;
		  try {
		    if( firstEntry
			&& fileName.equalsIgnoreCase(
					DateStamper.FILENAME ) )
		    {
		      int blocks = (int) dirBlockBuf[ entryPos + 15 ] & 0xFF;
		      out = new ByteArrayOutputStream(
					blocks > 0 ? (blocks * 128) : 32 );
		    } else {
		      out = new FileOutputStream( file );
		    }
		    writeEntryContentTo( out, dirBlockBuf, entryPos, 0 );
		    int baseExtentNum = extentsPerDirEntry;
		    while( writeExtentContentTo(
					out,
					dirBlocks,
					dirBlockBuf,
					entryPos,
					baseExtentNum,
					extentsPerDirEntry ) )
		    {
		      baseExtentNum += extentsPerDirEntry;
		    }
		    out.close();
		    if( out instanceof ByteArrayOutputStream ) {
		      timeBytes = ((ByteArrayOutputStream) out).toByteArray();
		      if( timeBytes != null ) {
			out = new FileOutputStream( file );
			out.write( timeBytes );
			out.close();
		      }
		    }
		    out = null;
		  }
		  catch( IOException ex ) {
		    appendErrorToLog( ex );
		    incErrorCount();
		    this.fileErr = true;
		  }
		  finally {
		    EmuUtil.closeSilently( out );
		  }

		  // Zeitstempel setzen
		  if( timeBytes != null ) {
		    int p = (dirBlkOffs + entryPos) / 2;
		    if( (p + 15) < timeBytes.length ) {
		      FileTimesData.createOf( file ).setTimesInMillis(
				DateStamper.getMillis( timeBytes, p ),
				DateStamper.getMillis( timeBytes, p + 5 ),
				DateStamper.getMillis( timeBytes, p + 10 ) );
		    }
		  }
		  // Bei Fehler Datei umbenennen
		  if( this.fileErr ) {
		    String fName2 = fileName + ".error";
		    if( file.renameTo( new File( this.outDir, fName2 ) ) ) {
		      appendToLog( "  Umbenannt in " );
		      appendToLog( fName2 );
		      appendToLog( "\n" );
		      disableAutoClose();
		    }
		  } else {
		    if( readOnly ) {
		      FileUtil.setFileWritable( file, false );
		    }
		  }
		}
	      }
	      firstEntry = false;
	      entryPos += 32;
	    }
	  }
	  dirBlkOffs += this.blockSize;
	}
      }

      // Fertig-Meldung
      final Window owner = getOwner();
      if( owner != null ) {
	final boolean error = (this.errorCount > 0);

	StringBuilder buf = new StringBuilder();
	if( error ) {
	  buf.append( "Beim Entpacken traten Fehler auf!" );
	  if( this.blockNumErr ) {
	    buf.append( "\n\nWahrscheinlich ist die Blockgr\u00F6\u00DFe"
		+ " zu gro\u00DF oder das Blocknummernformat (8/16 Bit)"
		+ " falsch eingestellt.\n"
		+ "Wenn dem so ist, dann enthalten auch die ohne"
		+ " Fehlermeldung entpackten Dateien falsche Daten!" );
	  }
	} else {
	  buf.append( "Fertig!" );
	}
	if( timeBytes != null ) {
	  buf.append( "\n\nDateStamper erkannt:\n"
			+ "Die Zeitstempel der entpackten Dateien"
			+ " wurden auf die in der Datei\n"
			+ DateStamper.FILENAME
			+ " enthaltenen Werte gesetzt." );
	}
	final String msg = buf.toString();
	EventQueue.invokeLater(
		new Runnable()
		{
		  public void run()
		  {
		    if( error ) {
		      BaseDlg.showWarningDlg( owner, msg );
		    } else {
		      BaseDlg.showInfoDlg( owner, msg, "Entpacken" );
		    }
		  }
		} );
      }
    }
    this.disk.closeSilently();
  }


	/* --- private Konstruktoren und Methoden --- */

  private DiskUnpacker(
		Window             owner,
		AbstractFloppyDisk disk,
		String             diskDesc,
		File               outDir,
		int                sysTracks,
		int                blockSize,
		boolean            blockNum16Bit,
		boolean            applyReadOnly,
		boolean            forceLowerCase ) throws IOException
  {
    super( owner, "JKCEMU disk unpacker", false );
    this.disk              = disk;
    this.diskDesc          = diskDesc;
    this.outDir            = outDir;
    this.sysTracks         = sysTracks;
    this.sides             = disk.getSides();
    this.sectorsPerTrack   = disk.getSectorsPerTrack();
    this.sectorOffset      = disk.getSectorOffset();
    this.sectorSize        = disk.getSectorSize();
    this.blockSize         = blockSize;
    this.sectPerBlock      = (sectorSize > 0 ? (blockSize / sectorSize) : 0);
    this.maxBlocksPerEntry = (blockNum16Bit ? 8 : 16);
    this.blockNum16Bit     = blockNum16Bit;
    this.applyReadOnly     = applyReadOnly;
    this.forceLowerCase    = forceLowerCase;
    this.fileErr           = false;
    this.blockNumErr       = false;
  }


  private String getFileName( byte[] blockBuf, int pos )
  {
    String rv = getFileNamePortion( blockBuf, pos, 8 );
    if( rv != null ) {
      String ext = getFileNamePortion( blockBuf, pos + 8, 3 );
      if( ext != null ) {
	rv = rv + "." + ext;
      }
    }
    return rv;
  }


  private String getFileNamePortion( byte[] blockBuf, int pos, int len )
  {
    boolean abort = false;
    char[]  buf   = new char[ len ];
    int     idx   = 0;
    int     nSp   = 0;
    for( int i = 0; i < len; i++ ) {
      int b = (int) blockBuf[ pos++ ] & 0x7F;
      if( b == 0x20 ) {
	nSp++;
      }
      else if( (b > 0x20) && (b < 0x7F) ) {
	while( nSp > 0 ) {
	  buf[ idx++ ] = '_';
	  --nSp;
	}
	char ch = (char) b;
	if( this.forceLowerCase ) {
	  ch = Character.toLowerCase( ch );
	}
	if( DiskUtil.isValidCPMFileNameChar( ch ) ) {
	  buf[ idx++ ] = ch;
	} else {
	  buf[ idx++ ] = '_';
	}
      } else {
	abort = true;
	break;
      }
    }
    return !abort && (idx > 0) ? new String( buf, 0, idx ) : null;
  }


  /*
   * Lesen eines Block
   *
   * Block 0 ist der erste hinter den Systemspuren
   */
  private byte[] readLogicalBlock( int blockIdx )
  {
    byte[] buf     = new byte[ this.blockSize ];
    int    nRemain = buf.length;
    for( int i = 0; i < this.sectPerBlock; i++ ) {
      int nRead      = 0;
      int absSectIdx = (this.sides * this.sysTracks * this.sectorsPerTrack)
				+ ((this.sectPerBlock * blockIdx) + i);
      int cyl     = absSectIdx / this.sectorsPerTrack / this.sides;
      int head    = 0;
      int sectIdx = absSectIdx - (cyl * this.sides * this.sectorsPerTrack);
      if( sectIdx >= this.sectorsPerTrack ) {
	head++;
	sectIdx -= this.sectorsPerTrack;
      }
      SectorData sector = this.disk.getSectorByID(
					cyl,
					head,
					cyl,
					head,
					sectIdx + 1 + this.sectorOffset,
					-1 );
      if( sector != null ) {
	nRead = sector.read( buf, i * sectorSize, sectorSize );
	nRemain -= nRead;
      }
      if( nRead <= 0 ) {
	break;
      }
    }
    return nRemain == 0 ? buf : null;
  }


  private void writeEntryContentTo(
			OutputStream out,
			byte[]       dirBlockBuf,
			int          entryPos,
			int          baseExtentNum ) throws IOException
  {
    int extentNum = DiskUtil.getExtentNumByEntryPos( dirBlockBuf, entryPos );
    int nBytes    = ((int) dirBlockBuf[ entryPos + 15 ] & 0xFF) * 128;
    if( extentNum > baseExtentNum ) {
      nBytes += (0x4000 * (extentNum - baseExtentNum));
    }
    int nBlocks = (nBytes + this.blockSize - 1) / this.blockSize;
    if( nBlocks > this.maxBlocksPerEntry ) {
      this.blockNumErr = true;
      appendErrorToLog(
	String.format(
		"Ung\u00FCltiger Gr\u00F6\u00DFeneintrag in Extent %d",
		extentNum,
		this.blockNum16Bit ? 16 : 8 ) );
      incErrorCount();
      this.fileErr = true;
      nBlocks      = this.maxBlocksPerEntry;
    }
    int pos = entryPos + 16;
    for( int i = 0; (nBytes > 0) && (i < nBlocks); i++ ) {
      int blockIdx = 0;
      if( this.blockNum16Bit ) {
	blockIdx = EmuUtil.getWord( dirBlockBuf, pos );
	pos += 2;
      } else {
	blockIdx = (int) dirBlockBuf[ pos++ ] & 0xFF;
      }
      if( blockIdx == 0 ) {
	this.blockNumErr = true;
	throw new IOException(
		String.format(
			"Ung\u00FCltige Blocknummer 0 in Extent %d",
			extentNum ) );
      }
      for( int k = 0; k < this.sectPerBlock; k++ ) {
	int absSectIdx = (this.sides * this.sysTracks * this.sectorsPerTrack)
				+ ((this.sectPerBlock * blockIdx) + k);
	int cyl     = absSectIdx / this.sectorsPerTrack / this.sides;
	int head    = 0;
	int sectIdx = absSectIdx - (cyl * this.sides * this.sectorsPerTrack);
	if( sectIdx >= this.sectorsPerTrack ) {
	  head++;
	  sectIdx -= this.sectorsPerTrack;
	}
	SectorData sector = this.disk.getSectorByID(
					cyl,
					head,
					cyl,
					head,
					sectIdx + 1 + this.sectorOffset,
					-1 );
	if( sector != null ) {
	  if( sector.checkError() ) {
	    appendErrorToLog(
		String.format(
			"Sektor [H=%d,C=%d,R=%d] im Block %02Xh fehlerhaft",
			head,
			cyl,
			sectIdx + 1,
			blockIdx ) );
	    incErrorCount();
	    this.fileErr = true;
	  }
	  nBytes -= sector.writeTo( out, nBytes );
	} else {
	  this.blockNumErr = true;
	  throw new IOException(
		String.format(
			"Sektor [H=%d,C=%d,R=%d] im Block %02Xh"
						+ " nicht gefunden",
			head,
			cyl,
			sectIdx + 1,
			blockIdx ) );
	}
      }
    }
  }


  private boolean writeExtentContentTo(
			OutputStream           out,
			java.util.List<byte[]> dirBlocks,
			byte[]                 baseBlockBuf,
			int                    baseEntryPos,
			int                    baseExtentNum,
			int                    extentsPerDirEntry )
							throws IOException
  {
    boolean rv = false;
    for( byte[] dirBlockBuf : dirBlocks ) {
      if( dirBlockBuf != null ) {
	int entryPos = 0;
	while( (entryPos + 31) < dirBlockBuf.length ) {
	  if( (dirBlockBuf != baseBlockBuf) || (entryPos != baseEntryPos) ) {
	    int extentNum = DiskUtil.getExtentNumByEntryPos(
							dirBlockBuf,
							entryPos );
	    if( (extentNum >= baseExtentNum)
		&& (extentNum < (baseExtentNum + extentsPerDirEntry)) )
	    {
	      rv = true;
	      for( int i = 0; i < 12; i++ ) {
		if( dirBlockBuf[ entryPos + i ]
			!= baseBlockBuf[ baseEntryPos + i ] )
		{
		  rv = false;
		  break;
		}
	      }
	    }
	    if( rv ) {
	      writeEntryContentTo(
				out,
				dirBlockBuf,
				entryPos,
				baseExtentNum );
	      break;
	    }
	  }
	  entryPos += 32;
	}
      }
      if( rv ) {
	break;
      }
    }
    return rv;
  }
}
