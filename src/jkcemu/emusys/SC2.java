/*
 * (c) 2009-2021 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Emulation des Schach-Computers SC2
 */

package jkcemu.emusys;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Properties;
import jkcemu.audio.AbstractSoundDevice;
import jkcemu.base.EmuSys;
import jkcemu.base.EmuThread;
import jkcemu.base.EmuUtil;
import jkcemu.emusys.etc.SC2KeyboardFld;
import jkcemu.etc.CPUSynchronSoundDevice;
import z80emu.Z80CPU;
import z80emu.Z80InterruptSource;
import z80emu.Z80MaxSpeedListener;
import z80emu.Z80PIO;


public class SC2 extends EmuSys implements Z80MaxSpeedListener
{
  public static final String SYSNAME     = "SC2";
  public static final String PROP_PREFIX = "jkcemu.sc2.";

  private static byte[] rom0000 = null;
  private static byte[] rom2000 = null;

  private byte[]                 ram;
  private int[]                  digitStatus;
  private int[]                  digitValues;
  private int[]                  keyboardMatrix;
  private SC2KeyboardFld         keyboardFld;
  private int                    ledChessStatus;
  private int                    ledMateStatus;
  private boolean                ledChessValue;
  private boolean                ledMateValue;
  private long                   curDisplayTStates;
  private long                   displayTStates;
  private CPUSynchronSoundDevice loudspeaker;
  private Z80PIO                 pio;


  public SC2( EmuThread emuThread, Properties props )
  {
    super( emuThread, props, PROP_PREFIX );
    if( rom0000 == null ) {
      rom0000 = readResource( "/rom/sc2/sc2_0000.bin" );
    }
    if( rom2000 == null ) {
      rom2000 = readResource( "/rom/sc2/sc2_2000.bin" );
    }
    this.ram            = new byte[ 0x0400 ];
    this.keyboardFld    = null;
    this.keyboardMatrix = new int[ 4 ];
    this.digitStatus    = new int[ 4 ];
    this.digitValues    = new int[ 4 ];
    this.loudspeaker    = new CPUSynchronSoundDevice( "Lautsprecher" );

    Z80CPU cpu = emuThread.getZ80CPU();
    this.pio   = new Z80PIO( "PIO" );
    cpu.setInterruptSources( this.pio );
    cpu.addMaxSpeedListener( this );
    cpu.addTStatesListener( this );

    z80MaxSpeedChanged( cpu );
  }


  public static int getDefaultSpeedKHz()
  {
    return 2458;		// eigentlich 2,4576 MHz
  }


  public boolean getLEDChessValue()
  {
    return this.ledChessValue;
  }


  public boolean getLEDMateValue()
  {
    return this.ledMateValue;
  }


  public void updKeyboardMatrix( int[] kbMatrix )
  {
    synchronized( this.keyboardMatrix ) {
      int n = Math.min( this.keyboardMatrix.length, kbMatrix.length );
      int i = 0;
      while( i < n ) {
	this.keyboardMatrix[ i ] = kbMatrix[ i ];
	i++;
      }
      while( i < this.keyboardMatrix.length ) {
	this.keyboardMatrix[ i ] = 0;
	i++;
      }
    }
  }


	/* --- Z80MaxSpeedListener --- */

  @Override
  public void z80MaxSpeedChanged( Z80CPU cpu )
  {
    this.loudspeaker.z80MaxSpeedChanged( cpu );
    this.displayTStates = cpu.getMaxSpeedKHz() * 50;
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public boolean canApplySettings( Properties props )
  {
    return EmuUtil.getProperty(
			props,
			EmuThread.PROP_SYSNAME ).equals( SYSNAME );
  }


  @Override
  public SC2KeyboardFld createKeyboardFld()
  {
    this.keyboardFld = new SC2KeyboardFld( this );
    return this.keyboardFld;
  }


  @Override
  public void die()
  {
    Z80CPU cpu = this.emuThread.getZ80CPU();
    cpu.removeTStatesListener( this );
    cpu.removeMaxSpeedListener( this );
    cpu.setInterruptSources( (Z80InterruptSource[]) null );

    this.loudspeaker.fireStop();
    super.die();
  }


  @Override
  public Chessman getChessman( int row, int col )
  {
    Chessman rv = null;
    if( (row >= 0) && (row < 8) && (col >= 0) && (col < 8) ) {
      switch( getMemByte( 0x1000 + (row * 16) + col, false ) ) {
	case 1:
	  rv = Chessman.WHITE_PAWN;
	  break;

	case 2:
	  rv = Chessman.WHITE_KNIGHT;
	  break;

	case 3:
	  rv = Chessman.WHITE_BISHOP;
	  break;

	case 4:
	  rv = Chessman.WHITE_ROOK;
	  break;

	case 5:
	  rv = Chessman.WHITE_QUEEN;
	  break;

	case 6:
	  rv = Chessman.WHITE_KING;
	  break;

	case 0xFF:
	  rv = Chessman.BLACK_PAWN;
	  break;

	case 0xFE:
	  rv = Chessman.BLACK_KNIGHT;
	  break;

	case 0xFD:
	  rv = Chessman.BLACK_BISHOP;
	  break;

	case 0xFC:
	  rv = Chessman.BLACK_ROOK;
	  break;

	case 0xFB:
	  rv = Chessman.BLACK_QUEEN;
	  break;

	case 0xFA:
	  rv = Chessman.BLACK_KING;
	  break;
      }
    }
    return rv;
  }


  @Override
  public Color getColor( int colorIdx )
  {
    Color color = Color.BLACK;
    switch( colorIdx ) {
      case 1:
	color = this.colorGreenDark;
	break;

      case 2:
	color = this.colorGreenLight;
	break;
    }
    return color;
  }


  @Override
  public int getColorCount()
  {
    return 3;
  }


  @Override
  public String getHelpPage()
  {
    return "/help/sc2.htm";
  }


  @Override
  public int getMemByte( int addr, boolean m1 )
  {
    int rv = 0xFF;

    addr &= 0x3FFF;		// A14 und A15 ignorieren
    if( (addr < 0x1000) && (rom0000 != null) ) {
      if( addr < rom0000.length ) {
	rv = (int) rom0000[ addr ] & 0xFF;
      }
    }
    else if( (addr >= 0x1000) && (addr < 0x2000) ) {
      int idx = addr - 0x1000;
      if( idx < this.ram.length ) {
	rv = (int) this.ram[ idx ] & 0xFF;
      }
    }
    else if( (addr >= 0x2000) && (addr < 0x3C00) && (rom2000 != null) ) {
      int idx = addr - 0x2000;
      if( idx < rom2000.length ) {
	rv = (int) rom2000[ idx ] & 0xFF;
      }
    }
    return rv;
  }


  @Override
  public int getScreenHeight()
  {
    return 110;
  }


  @Override
  public int getScreenWidth()
  {
    return 70 + (this.digitValues.length * 50);
  }


  @Override
  public AbstractSoundDevice[] getSoundDevices()
  {
    return new AbstractSoundDevice[] { this.loudspeaker };
  }


  @Override
  public String getTitle()
  {
    return SYSNAME;
  }


  @Override
  public boolean keyPressed(
			int     keyCode,
			boolean ctrlDown,
			boolean shiftDown )
  {
    boolean rv = false;
    if( keyCode == KeyEvent.VK_BACK_SPACE ) {
      synchronized( this.keyboardMatrix ) {
	this.keyboardMatrix[ 0 ] |= 0x40;
        rv = true;
      }
    }
    else if( keyCode == KeyEvent.VK_ENTER ) {
      synchronized( this.keyboardMatrix ) {
	this.keyboardMatrix[ 0 ] |= 0x80;
        rv = true;
      }
    }
    else if( keyCode == KeyEvent.VK_ESCAPE ) {
      this.emuThread.fireReset( false );
      rv = true;
    }
    if( rv ) {
      updKeyboardFld();
    }
    return rv;
  }


  @Override
  public void keyReleased()
  {
    synchronized( this.keyboardMatrix ) {
      Arrays.fill( this.keyboardMatrix, 0 );
    }
    updKeyboardFld();
  }


  @Override
  public boolean keyTyped( char keyChar )
  {
    boolean rv = false;
    synchronized( this.keyboardMatrix ) {
      switch( Character.toUpperCase( keyChar ) ) {
	case '1':
	case 'A':
	  this.keyboardMatrix[ 1 ] |= 0x10;
	  rv = true;
	  break;

	case '2':
	case 'B':
	  this.keyboardMatrix[ 1 ] |= 0x20;
	  rv = true;
	  break;

	case '3':
	case 'C':
	  this.keyboardMatrix[ 1 ] |= 0x40;
	  rv = true;
	  break;

	case '4':
	case 'D':
	  this.keyboardMatrix[ 1 ] |= 0x80;
	  rv = true;
	  break;

	case '5':
	case 'E':
	  this.keyboardMatrix[ 2 ] |= 0x10;
	  rv = true;
	  break;

	case '6':
	case 'F':
	  this.keyboardMatrix[ 2 ] |= 0x20;
	  rv = true;
	  break;

	case '7':
	case 'G':
	  this.keyboardMatrix[ 2 ] |= 0x40;
	  rv = true;
	  break;

	case '8':
	case 'H':
	  this.keyboardMatrix[ 2 ] |= 0x80;
	  rv = true;
	  break;

	case 'K':
	case '+':
	  this.keyboardMatrix[ 3 ] |= 0x10;
	  rv = true;
	  break;

	case 'L':
	  this.keyboardMatrix[ 0 ] |= 0x40;
	  rv = true;
	  break;

	case 'P':
	  this.keyboardMatrix[ 3 ] |= 0x80;
	  rv = true;
	  break;

	case 'Q':
	  this.keyboardMatrix[ 0 ] |= 0x80;
	  rv = true;
	  break;

	case 'S':
	case 'W':
	  this.keyboardMatrix[ 3 ] |= 0x20;
	  rv = true;
	  break;

	case 'T':
	  this.keyboardMatrix[ 0 ] |= 0x20;
	  rv = true;
	  break;
      }
    }
    if( rv ) {
      updKeyboardFld();
    }
    return rv;
  }


  @Override
  public boolean paintScreen( Graphics g, int x, int y, int screenScale )
  {
    synchronized( this.digitValues ) {

      // LED Schach
      g.setFont( new Font( Font.SANS_SERIF, Font.BOLD, 18 * screenScale ) );
      g.setColor( this.ledChessValue ?
				this.colorGreenLight
				: this.colorGreenDark );
      g.drawString( "Schach", x, y + (110 * screenScale) );

      // LED Matt
      int          xMate = 0;
      final String s     = "Matt";
      FontMetrics  fm    = g.getFontMetrics();
      if( fm != null ) {
	xMate = x + (getScreenWidth() * screenScale) - fm.stringWidth( s );
      } else {
	xMate = x + (getScreenWidth() / 2 * screenScale);
      }
      g.setColor( this.ledMateValue ?
				this.colorGreenLight
				: this.colorGreenDark );
      g.drawString( s, xMate, y + (110 * screenScale) );

      // 7-Segment-Anzeige
      for( int i = 0; i < this.digitValues.length; i++ ) {
	paint7SegDigit(
		g,
		x,
		y,
		this.digitValues[ i ],
		this.colorGreenDark,
		this.colorGreenLight,
		screenScale );
	x += ((i == 1 ? 90 : 65) * screenScale);
      }
    }
    return true;
  }


  @Override
  public int readIOByte( int port, int tStates )
  {
    int rv = 0xFF;
    if( (port & 0x08) == 0 ) {
      switch( port & 0x03 ) {
	case 0:
          rv = this.pio.readDataA();
          break;

        case 1:
	  {
	    int v = this.pio.fetchOutValuePortB( 0x0F ) & 0x0F;
	    synchronized( this.keyboardMatrix ) {
	      int m = 0x01;
	      for( int i = 0; i < this.keyboardMatrix.length; i++ ) {
		if( (v & m) != 0 ) {
		  v |= (this.keyboardMatrix[ i ] & 0xF0);
		}
		m <<= 1;
	      }
	    }
	    this.pio.putInValuePortB( v, 0xF0 );
	  }
          rv = this.pio.readDataB();
          break;
      }
    }
    return rv;
  }


  @Override
  public int readMemByte( int addr, boolean m1 )
  {
    addr &= 0x3FFF;
    updSoundPhase( addr );
    return getMemByte( addr, m1);
  }


  @Override
  public void reset( boolean powerOn, Properties props )
  {
    super.reset( powerOn, props );
    if( powerOn ) {
      initSRAM( this.ram, props );
    }
    synchronized( this.digitValues ) {
      Arrays.fill( this.digitStatus, 0 );
      Arrays.fill( this.digitValues, 0 );
    }
    this.ledChessValue      = false;
    this.ledMateValue       = false;
    this.ledChessStatus     = 0;
    this.ledMateStatus      = 0;
    this.curDisplayTStates  = 0;
    this.loudspeaker.reset();
    this.pio.reset( powerOn );
  }


  @Override
  public boolean setMemByte( int addr, int value )
  {
    boolean rv = false;

    addr &= 0x3FFF;		// A14 und A15 ignorieren
    if( (addr >= 0x1000) && (addr < 0x3C00) ) {
      int idx = addr - 0x1000;
      if( idx < this.ram.length ) {
	this.ram[ idx ] = (byte) value;
	if( (idx < 0x78) && ((idx % 16) < 8) ) {
	  this.screenFrm.setChessboardDirty( true );
	}
	rv = true;
      }
    }
    return rv;
  }


  @Override
  public boolean supportsChessboard()
  {
    return true;
  }


  @Override
  public boolean supportsKeyboardFld()
  {
    return true;
  }


  @Override
  public void writeIOByte( int port, int value, int tStates )
  {
    if( (port & 0x08) == 0 ) {
      switch( port & 0x03 ) {
	case 0:
          this.pio.writeDataA( value );
	  updDisplay();
          break;

        case 1:
          this.pio.writeDataB( value );
	  updDisplay();
          break;

        case 2:
          this.pio.writeControlA( value );
          break;

        case 3:
          this.pio.writeControlB( value );
          break;
      }
    }
  }


  @Override
  public void writeMemByte( int addr, int value )
  {
    addr &= 0x3FFF;
    updSoundPhase( addr );
    setMemByte( addr, value );
  }


  @Override
  public void z80TStatesProcessed( Z80CPU cpu, int tStates )
  {
    super.z80TStatesProcessed( cpu, tStates );
    this.loudspeaker.z80TStatesProcessed( cpu, tStates );

    // Anzeige
    if( this.displayTStates > 0 ) {
      this.curDisplayTStates += tStates;
      if( this.curDisplayTStates > this.displayTStates ) {
	boolean displayDirty = false;
	boolean ledDirty     = false;
	synchronized( this.digitValues ) {
	  for( int i = 0; i < this.digitValues.length; i++ ) {
	    int status = this.digitStatus[ i ];
	    if( status < 4 ) {
	      if( status > 0 ) {
		--this.digitStatus[ i ];
	      } else {
		if( this.digitValues[ i ] != 0 ) {
		  this.digitValues[ i ] = 0;
		  displayDirty = true;
		}
	      }
	    }
	  }
	  if( this.ledChessStatus < 4 ) {
	    if( this.ledChessStatus > 0 ) {
	      --this.ledChessStatus;
	    } else {
	      if( this.ledChessValue ) {
		this.ledChessValue = false;
		ledDirty = true;
	      }
	    }
	  }
	  if( this.ledMateStatus < 4 ) {
	    if( this.ledMateStatus > 0 ) {
	      --this.ledMateStatus;
	    } else {
	      if( this.ledMateValue ) {
		this.ledMateValue = false;
		ledDirty = true;
	      }
	    }
	  }
	}
	if( displayDirty || ledDirty ) {
	  this.screenFrm.setScreenDirty( true );
	}
	if( ledDirty && (this.keyboardFld != null) ) {
	  this.keyboardFld.repaint();
	}
	this.curDisplayTStates = 0;
      }
    }
  }


	/* --- private Methoden --- */

  /*
   * Eingang: H-Aktiv
   *   Bit: 0 -> G
   *   Bit: 1 -> F
   *   Bit: 2 -> E
   *   Bit: 3 -> D
   *   Bit: 4 -> C
   *   Bit: 5 -> B
   *   Bit: 6 -> A
   *   Bit: 7 -> LED, in 7-Segment-Anzeige nicht verwendet
   *
   * Ausgang: H-Aktiv
   *   Bit: 0 -> A
   *   Bit: 1 -> B
   *   Bit: 2 -> C
   *   Bit: 3 -> D
   *   Bit: 4 -> E
   *   Bit: 5 -> F
   *   Bit: 6 -> G
   */
  private int toDigitValue( int value )
  {
    int rv = value & 0x08;	// D stimmt ueberein
    if( (value & 0x01) != 0 ) {
      rv |= 0x40;
    }
    if( (value & 0x02) != 0 ) {
      rv |= 0x20;
    }
    if( (value & 0x04) != 0 ) {
      rv |= 0x10;
    }
    if( (value & 0x10) != 0 ) {
      rv |= 0x04;
    }
    if( (value & 0x20) != 0 ) {
      rv |= 0x02;
    }
    if( (value & 0x40) != 0 ) {
      rv |= 0x01;
    }
    return rv;
  }


  private void updDisplay()
  {
    int     portAValue   = this.pio.fetchOutValuePortA( 0x00 );
    int     digitValue   = toDigitValue( portAValue & 0x7F );
    int     colValue     = this.pio.fetchOutValuePortB( 0x0F );
    boolean ledValue     = ((portAValue & 0x80) != 0);
    boolean displayDirty = false;
    boolean ledDirty     = false;
    synchronized( this.digitValues ) {
      int m = 0x01;
      for( int i = 0; i < this.digitValues.length; i++ ) {
	if( (colValue & m) == 0 ) {
	  if( digitValue != 0 ) {
	    if( digitValue != this.digitValues[ i ] ) {
	      this.digitValues[ i ] = digitValue;
	      displayDirty          = true;
	    }
	    this.digitStatus[ i ] = 4;
	  } else {
	    if( this.digitStatus[ i ] > 3 ) {
	      this.digitStatus[ i ] = 3;
	    }
	  }
	}
	m <<= 1;
      }
      if( ledValue && ((colValue & 0x01) == 0) ) {
	if( ledValue != this.ledChessValue ) {
	  this.ledChessValue = ledValue;
	  ledDirty           = true;
	}
	this.ledChessStatus = 4;
      } else {
	if( this.ledChessStatus > 3 ) {
	  this.ledChessStatus = 3;
	}
      }
      if( ledValue && ((colValue & 0x02) == 0) ) {
	if( ledValue != this.ledMateValue ) {
	  this.ledMateValue = ledValue;
	  ledDirty          = true;
	}
	this.ledMateStatus = 4;
      } else {
	if( this.ledMateStatus > 3 ) {
	  this.ledMateStatus = 3;
	}
      }
    }
    if( displayDirty || ledDirty ) {
      this.screenFrm.setScreenDirty( true );
    }
    if( ledDirty && (this.keyboardFld != null) ) {
      this.keyboardFld.repaint();
    }
  }


  private void updKeyboardFld()
  {
    if( this.keyboardFld != null )
      this.keyboardFld.updKeySelection( this.keyboardMatrix );
  }


  private void updSoundPhase( int addr )
  {
    this.loudspeaker.setCurPhase( addr >= 0x3C00 );
  }
}
