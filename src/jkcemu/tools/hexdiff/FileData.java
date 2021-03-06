/*
 * (c) 2008-2017 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Daten/Inhalt einer Datei
 */

package jkcemu.tools.hexdiff;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;


public class FileData implements Closeable
{
  private File        file;
  private InputStream in;


  public FileData( File file ) throws IOException
  {
    if( !file.exists() ) {
      throw new IOException( file.getPath() + ":\nDatei nicht gefunden" );
    }
    if( !file.isFile() ) {
      throw new IOException( file.getPath() + ":\nKeine regul\u00E4re Datei" );
    }
    if( !file.canRead() ) {
      throw new IOException( file.getPath() + ":\nDatei nicht lesbar" );
    }
    this.file = file;
    this.in   = null;
  }


  public File getFile()
  {
    return this.file;
  }


  public int read() throws IOException
  {
    if( this.in == null ) {
      this.in = new BufferedInputStream( new FileInputStream( this.file ) );
    }
    return this.in.read();
  }


	/* --- Closeable --- */

  @Override
  public void close() throws IOException
  {
    if( this.in != null ) {
      this.in.close();
      this.in = null;
    }
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public String toString()
  {
    return this.file.getPath();
  }
}
