/*
 * (c) 2011-2017 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Konvertierung in eine Sound-Datei im Z1013-Format
 */

package jkcemu.tools.fileconverter;

import java.io.File;
import java.io.IOException;
import javax.swing.JComboBox;
import jkcemu.audio.AudioFile;
import jkcemu.audio.PCMDataSource;
import jkcemu.base.UserInputException;
import jkcemu.emusys.z1013.Z1013AudioCreator;


public class Z1013AudioFileTarget extends AbstractConvertTarget
{
  private byte[]  buf;
  private int     offs;
  private int     len;
  private boolean headersave;


  public Z1013AudioFileTarget(
		FileConvertFrm fileConvertFrm,
		byte[]         buf,
		int            offs,
		int            len,
		boolean        headersave )
  {
    super( fileConvertFrm, createInfoText( headersave ) );
    this.buf        = buf;
    this.offs       = offs;
    this.len        = len;
    this.headersave = headersave;
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public boolean canPlay()
  {
    return true;
  }


  @Override
  public PCMDataSource createPCMDataSource()
				throws IOException, UserInputException
  {
    byte[]  buf  = this.buf;
    int     offs = this.offs;
    int     len  = Math.min( this.len, this.buf.length - this.offs );
    boolean hs   = false;
    if( this.headersave ) {
      int begAddr   = this.fileConvertFrm.getBegAddr( true );
      int endAddr   = begAddr + len - 1;
      int startAddr = this.fileConvertFrm.getBegAddr( false );
      if( startAddr < 0 ) {
	startAddr = 0;
      }
      int fileType = this.fileConvertFrm.getFileTypeChar( true );
      if( fileType < 0 ) {
	fileType = 0x20;
      }
      String s   = this.fileConvertFrm.getFileDesc( true );
      buf        = new byte[ 32 + len ];
      buf[ 0 ]   = (byte) begAddr;
      buf[ 1 ]   = (byte) (begAddr >> 8);
      buf[ 2 ]   = (byte) endAddr;
      buf[ 3 ]   = (byte) (endAddr >> 8);
      buf[ 4 ]   = (byte) startAddr;
      buf[ 5 ]   = (byte) (startAddr >> 8);
      buf[ 6 ]   = (byte) 'J';
      buf[ 7 ]   = (byte) 'K';
      buf[ 8 ]   = (byte) 'C';
      buf[ 9 ]   = (byte) 'E';
      buf[ 10 ]  = (byte) 'M';
      buf[ 11 ]  = (byte) 'U';
      buf[ 12 ]  = (byte) fileType;
      buf[ 13 ]  = (byte) 0xD3;
      buf[ 14 ]  = (byte) 0xD3;
      buf[ 15 ]  = (byte) 0xD3;
      int p = 16;
      if( s != null ) {
	int l = s.length();
	int i = 0;
	while( (p < 32) && (i < l) ) {
	  buf[ p++ ] = (byte) s.charAt( i++ );
	}
      }
      while( p < 32 ) {
	buf[ p++ ] = (byte) 0x20;
      }
      System.arraycopy( this.buf, this.offs, buf, p, len );
      offs = 0;
      len  = buf.length;
      hs   = true;
    }
    return new Z1013AudioCreator( hs, buf, offs, len ).newReader();
  }


  @Override
  public javax.swing.filechooser.FileFilter getFileFilter()
  {
    return AudioFile.getFileFilter();
  }


  @Override
  public int getMaxFileDescLength()
  {
    return this.headersave ? 16 : 0;
  }


  @Override
  public int getMaxFileTypeLength()
  {
    return this.headersave ? 1 : 0;
  }


  @Override
  public File getSuggestedOutFile( File srcFile )
  {
    return replaceExtensionToAudioFile( srcFile );
  }


  @Override
  public String save( File file ) throws IOException, UserInputException
  {
    saveAudioFile( file, createPCMDataSource() );
    return null;
  }


  @Override
  public void setFileTypesTo( JComboBox<String> combo )
  {
    if( this.headersave ) {
      HeadersaveFileTarget.setFileTypesTo( combo, this.fileConvertFrm );
    } else {
      super.setFileTypesTo( combo );
    }
  }


  @Override
  public boolean usesBegAddr()
  {
    return this.headersave;
  }


  @Override
  public boolean usesStartAddr( int fileType )
  {
    return (fileType == 'C') || (fileType == 'M');
  }


	/* --- private Methoden --- */

  private static String createInfoText( boolean headersave )
  {
    StringBuilder buf = new StringBuilder( 128 );
    buf.append( "Sound-Datei im Z1013-" );
    if( headersave ) {
      buf.append( "Headersave-" );
    }
    buf.append( "Format (" );
    buf.append( AudioFile.getFileExtensionText() );
    buf.append( ')' );
    return buf.toString();
  }
}
