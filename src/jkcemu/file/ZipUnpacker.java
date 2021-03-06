/*
 * (c) 2008-2020 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * ZIP-Entpacker
 */

package jkcemu.file;

import java.awt.Dialog;
import java.awt.Window;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import jkcemu.base.AbstractThreadDlg;
import jkcemu.base.EmuUtil;


public class ZipUnpacker extends AbstractThreadDlg
{
  private File srcFile;
  private File outDir;


  public static void unpackFile( Window owner, File srcFile, File outDir )
  {
    Dialog dlg = new ZipUnpacker( owner, srcFile, outDir );
    dlg.setTitle( "ZIP-Datei entpacken" );
    dlg.setVisible( true );
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  protected void doProgress()
  {
    boolean dirExists = this.outDir.exists();
    this.outDir.mkdirs();
    InputStream    in    = null;
    ZipInputStream inZip = null;
    try {
      in    = openInputFile( this.srcFile, null );
      inZip = new ZipInputStream( new BufferedInputStream( in ) );
      ZipEntry entry = inZip.getNextEntry();
      while( !this.cancelled && (entry != null) ) {
	String entryName = entry.getName();
	if( entryName != null ) {
	  File outFile = null;
	  int  len     = entryName.length();
	  int  pos     = 0;
	  while( pos < len ) {
	    String elem   = null;
	    int    delim1 = entryName.indexOf( '/', pos );
	    int    delim2 = entryName.indexOf( '\\', pos );
	    if( (delim1 < 0) || ((delim2 >= 0) && (delim2 < delim1)) ) {
	      delim1 = delim2;
	    }
	    if( delim1 >= pos ) {
	      elem = entryName.substring( pos, delim1 );
	      pos  = delim1 + 1;
	    } else {
	      elem = entryName.substring( pos );
	      pos  = len;
	    }
	    if( elem != null ) {
	      if( !elem.isEmpty() ) {
		if( outFile != null ) {
		  outFile = prepareOutFile( outFile, elem );
		} else {
		  outFile = prepareOutFile( this.outDir, elem );
		}
	      }
	    }
	  }
	  if( outFile != null ) {
	    appendToLog( outFile.getPath() + "\n" );
	    long millis = entry.getTime();
	    if( entry.isDirectory()
		|| entryName.endsWith( "/" )
		|| entryName.endsWith( "\\" ) )
	    {
	      outFile.mkdirs();
	      if( !outFile.exists() ) {
		throw new IOException(
				"Verzeichnis kann nicht angelegt werden" );
	      }
	    } else {
	      File parent = outFile.getParentFile();
	      if( parent != null ) {
		parent.mkdirs();
	      }
	      boolean      failed = false;
	      OutputStream out    = null;
	      try {
		long  chkSum = entry.getCrc();
		CRC32 crc32  = null;
		if( chkSum >= 0 ) {
		  crc32 = new CRC32();
		}
		out = new BufferedOutputStream(
				new FileOutputStream( outFile ) );

		int b = inZip.read();
		while( !this.cancelled && (b != -1) ) {
		  if( crc32 != null ) {
		    crc32.update( b );
		  }
		  out.write( b );
		  b = inZip.read();
		}
		out.close();
		out = null;

		if( crc32 != null ) {
		  if( crc32.getValue() != chkSum ) {
		    appendErrorToLog( "CRC32-Pr\u00FCfsumme differiert" );
		    incErrorCount();
		  }
		}
	      }
	      catch( IOException ex ) {
		appendErrorToLog( ex );
		incErrorCount();
		failed = true;
	      }
	      finally {
		EmuUtil.closeSilently( out );
	      }
	      if( failed ) {
		outFile.delete();
	      } else {
		if( millis > 0 ) {
		  outFile.setLastModified( millis );
		}
	      }
	    }
	  }
	}
	inZip.closeEntry();
	entry = inZip.getNextEntry();
      }
    }
    catch( Exception ex ) {
      StringBuilder buf = new StringBuilder( 256 );
      buf.append( "\nFehler beim Lesen der Datei " );
      buf.append( this.srcFile.getPath() );
      String errMsg = ex.getMessage();
      if( errMsg != null ) {
        if( !errMsg.isEmpty() ) {
          buf.append( ":\n" );
          buf.append( errMsg );
        }
      }
      buf.append( '\n' );
      appendToLog( buf.toString() );
      incErrorCount();
    }
    finally {
      if( inZip != null ) {
	EmuUtil.closeSilently( inZip );
      } else {
	EmuUtil.closeSilently( in );
      }
    }
  }


	/* --- private Konstruktoren --- */

  private ZipUnpacker( Window owner, File srcFile, File outDir )
  {
    super( owner, "JKCEMU zip unpacker", true );
    this.srcFile = srcFile;
    this.outDir  = outDir;
  }
}
