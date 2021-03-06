/*
 * (c) 2010-2018 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Berechnung einer Pruefsumme bzw. eines Hashwertes
 */

package jkcemu.etc;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.Arrays;
import java.util.Set;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.Checksum;


public class CksCalculator
{
  public static class Add8 implements Checksum
  {
    private int cks;

    public Add8()
    {
      reset();
    }

    @Override
    public long getValue()
    {
      return this.cks & 0xFFFFL;
    }

    @Override
    public void reset()
    {
      this.cks = 0;
    }

    @Override
    public void update( byte[] a, int offs, int len )
    {
      while( len > 0 ) {
	update( a[ offs++ ] );
	--len;
      }
    }

    @Override
    public void update( int b )
    {
      this.cks = (this.cks + (b & 0xFF)) & 0xFFFF;
    }
  };


  public static class Add16BE implements Checksum
  {
    private int cks;
    private int buf;

    public Add16BE()
    {
      reset();
    }

    @Override
    public long getValue()
    {
      int cks = this.cks;
      if( this.buf >= 0 ) {
	cks += ((this.buf << 8) & 0xFF00);
      }
      return cks & 0xFFFFL;
    }

    @Override
    public void reset()
    {
      this.cks = 0;
      this.buf = -1;
    }

    @Override
    public void update( byte[] a, int offs, int len )
    {
      while( len > 0 ) {
	update( a[ offs++ ] );
	--len;
      }
    }

    @Override
    public void update( int b )
    {
      b &= 0xFF;
      if( this.buf >= 0 ) {
	this.cks += ((this.buf << 8) & 0xFF00) | b;
	this.cks &= 0xFFFF;
	this.buf = -1;
      } else {
	this.buf = b;
      }
    }
  };


  public static class Add16LE implements Checksum
  {
    private int cks;
    private int buf;

    public Add16LE()
    {
      reset();
    }

    @Override
    public long getValue()
    {
      int cks = this.cks;
      if( this.buf >= 0 ) {
	cks += this.buf;
      }
      return cks & 0xFFFFL;
    }

    @Override
    public void reset()
    {
      this.cks = 0;
      this.buf = -1;
    }

    @Override
    public void update( byte[] a, int offs, int len )
    {
      while( len > 0 ) {
	update( a[ offs++ ] );
	--len;
      }
    }

    @Override
    public void update( int b )
    {
      if( this.buf >= 0 ) {
	this.cks += ((b << 8) & 0xFF00) | this.buf;
	this.cks &= 0xFFFF;
	this.buf = -1;
      } else {
	this.buf = b & 0xFF;
      }
    }
  };


  private static final String CKS_ADD8    = "Summe der Bytes";
  private static final String CKS_ADD16LE =
			"Summe der 16-Bit-Worte (Little Endian)";
  private static final String CKS_ADD16BE =
			"Summe der 16-Bit-Worte (Big Endian)";
  private static final String CKS_ADLER32    = "Adler-32";
  private static final String CKS_CRC16CCITT = "CRC-CCITT (CRC-16 HDLC)";
  private static final String CKS_CRC32      = "CRC-32";

  private static final String[] sumAlgorithms = {
					CKS_ADD8,
					CKS_ADD16LE,
					CKS_ADD16BE,
					CKS_CRC16CCITT,
					CKS_CRC32,
					CKS_ADLER32 };

  private static volatile String[] algorithms = null;

  private String        algorithm;
  private String        value;
  private Checksum      checksum;
  private MessageDigest digest;


  public CksCalculator( String algorithm ) throws NoSuchAlgorithmException
  {
    this.algorithm = algorithm;
    this.value     = null;
    this.checksum  = null;
    this.digest    = null;
    if( algorithm.equals( CKS_ADD8 ) ) {
      this.checksum = new Add8();
    } else if( algorithm.equals( CKS_ADD16LE ) ) {
      this.checksum = new Add16LE();
    } else if( algorithm.equals( CKS_ADD16BE ) ) {
      this.checksum = new Add16BE();
    } else if( algorithm.equals( CKS_ADLER32 ) ) {
      this.checksum = new Adler32();
    } else if( algorithm.equals( CKS_CRC16CCITT ) ) {
      this.checksum = CRC16.createCRC16CCITT();
    } else if( algorithm.equals( CKS_CRC32 ) ) {
      this.checksum = new CRC32();
    } else {
      this.digest = MessageDigest.getInstance( algorithm );
    }
  }


  public String getAlgorithm()
  {
    return this.algorithm;
  }


  public static String[] getAvailableAlgorithms()
  {
    if( algorithms == null ) {
      String[] mds = null;
      try {
	Set<String> mdSet = Security.getAlgorithms(
				MessageDigest.class.getSimpleName() );
	if( mdSet != null ) {
	  int size = mdSet.size();
	  if( size > 0 ) {
	    mds = mdSet.toArray( new String[ size ] );
	    Arrays.sort( mds );
	  }
	}
      }
      catch( Exception ex ) {}
      if( mds != null ) {
	String[] a = new String[ sumAlgorithms.length + mds.length ];
	System.arraycopy( sumAlgorithms, 0, a, 0, sumAlgorithms.length );
	System.arraycopy( mds, 0, a, sumAlgorithms.length, mds.length );
	algorithms = a;
      } else {
	algorithms = sumAlgorithms;
      }
    }
    return algorithms;
  }


  public String getValue()
  {
    if( this.value == null ) {
      if( this.checksum != null ) {
	if( this.checksum instanceof CRC16 ) {
	  this.value = String.format( "%04X", this.checksum.getValue() );
	} else {
	  this.value = String.format( "%08X", this.checksum.getValue() );
	}
      } else if( this.digest != null ) {
	byte[] result = this.digest.digest();
	if( result != null ) {
	  StringBuilder buf = new StringBuilder( 2 * result.length );
	  for( int i = 0; i < result.length; i++ ) {
	    buf.append( String.format( "%02X", ((int) result[ i ] & 0xFF) ) );
	  }
	  this.value = buf.toString();
	}
      }
    }
    return this.value;
  }


  public void reset()
  {
    this.value = null;
    if( this.checksum != null ) {
      this.checksum.reset();
    } else if( this.digest != null ) {
      this.digest.reset();
    }
  }


  public void update( int b )
  {
    if( this.checksum != null ) {
      this.checksum.update( b );
    } else if( this.digest != null ) {
      this.digest.update( (byte) b );
    }
  }
}
