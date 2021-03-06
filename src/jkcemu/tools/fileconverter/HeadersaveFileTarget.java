/*
 * (c) 2011-2019 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Konvertierung in eine Headersave-Datei
 */

package jkcemu.tools.fileconverter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import javax.swing.JComboBox;
import jkcemu.base.EmuUtil;
import jkcemu.base.UserInputException;
import jkcemu.file.FileUtil;


public class HeadersaveFileTarget extends AbstractConvertTarget
{
  private byte[] dataBytes;
  private int    offs;
  private int    len;


  public HeadersaveFileTarget(
		FileConvertFrm fileConvertFrm,
		byte[]         dataBytes,
		int            offs,
		int            len )
  {
    super( fileConvertFrm, "Headersave-Datei (*.z80)" );
    this.dataBytes = dataBytes;
    this.offs      = offs;
    this.len       = len;
  }


  public static void setFileTypesTo(
				JComboBox<String> combo,
				FileConvertFrm    fileConvertFrm )
  {
    combo.removeAllItems();
    FileUtil.addHeadersaveFileTypeItemsTo( combo );
    combo.setEnabled( true );
    int fileType = fileConvertFrm.getOrgFileTypeChar();
    if( (fileType > 0x20) && (fileType < 0x7F) ) {
      if( !FileUtil.setSelectedHeadersaveFileTypeItem( combo, fileType ) ) {
	combo.setSelectedItem( Character.toString( (char) fileType ) );
      }
    } else {
      if( fileConvertFrm.getOrgIsBasicPrg() ) {
	FileUtil.setSelectedHeadersaveFileTypeItem( combo, 'B' );
      } else if( fileConvertFrm.getOrgStartAddr() >= 0 ) {
	FileUtil.setSelectedHeadersaveFileTypeItem( combo, 'C' );
      } else {
	FileUtil.setSelectedHeadersaveFileTypeItem( combo, 'M' );
      }
    }
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public javax.swing.filechooser.FileFilter getFileFilter()
  {
    return FileUtil.getHeadersaveFileFilter();
  }


  @Override
  public int getMaxFileDescLength()
  {
    return 16;
  }


  @Override
  public File getSuggestedOutFile( File srcFile )
  {
    return FileUtil.replaceExtension( srcFile, ".z80" );
  }


  @Override
  public String save( File file ) throws IOException, UserInputException
  {
    checkFileExtension( file, ".z80" );
    int          begAddr   = this.fileConvertFrm.getBegAddr( true );
    int          endAddr   = begAddr + this.len - 1;
    int          startAddr = this.fileConvertFrm.getStartAddr( false );
    int          fileType  = this.fileConvertFrm.getFileTypeChar( true );
    String       fileDesc  = this.fileConvertFrm.getFileDesc( true );
    OutputStream out       = null;
    try {
      out = new FileOutputStream( file );
      out.write( begAddr );
      out.write( begAddr >> 8 );
      out.write( endAddr );
      out.write( endAddr >> 8 );
      if( startAddr >= 0 ) {
	out.write( startAddr );
	out.write( startAddr >> 8 );
      } else {
	out.write( 0 );
	out.write( 0 );
      }
      out.write( 'J' );
      out.write( 'K' );
      out.write( 'C' );
      out.write( 'E' );
      out.write( 'M' );
      out.write( 'U' );
      out.write( fileType );
      out.write( 0xD3 );
      out.write( 0xD3 );
      out.write( 0xD3 );
      int n = 16;
      if( fileDesc != null ) {
	int p   = 0;
	int len = fileDesc.length();
	while( (n > 0) && (p < len) ) {
	  out.write( fileDesc.charAt( p++ ) & 0xFF );
	  --n;
	}
      }
      while( n > 0 ) {
	out.write( 0x20 );
	--n;
      }
      n = Math.min( this.dataBytes.length - this.offs, this.len );
      out.write( this.dataBytes, this.offs, n );
      n = n % 0x20;
      if( n > 0 ) {
	for( int i = n; i < 0x20; i++ ) {
	  out.write( 0 );
	}
      }
      out.close();
      out = null;
    }
    finally {
      EmuUtil.closeSilently( out );
    }
    return null;
  }


  @Override
  public void setFileTypesTo( JComboBox<String> combo )
  {
    setFileTypesTo( combo, this.fileConvertFrm );
  }


  @Override
  public boolean usesBegAddr()
  {
    return true;
  }


  @Override
  public boolean usesStartAddr( int fileType )
  {
    return (fileType == 'C') || (fileType == 'M');
  }
}
