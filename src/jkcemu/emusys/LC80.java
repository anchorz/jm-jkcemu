/*
 * (c) 2009-2016 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Emulation des LC80
 */

package jkcemu.emusys;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.lang.*;
import java.util.Arrays;
import java.util.Properties;
import jkcemu.base.AbstractKeyboardFld;
import jkcemu.base.EmuSys;
import jkcemu.base.EmuThread;
import jkcemu.base.EmuUtil;
import jkcemu.emusys.lc80.LC80KeyboardFld;
import jkcemu.text.TextUtil;
import z80emu.Z80CPU;
import z80emu.Z80CTC;
import z80emu.Z80HaltStateListener;
import z80emu.Z80InterruptSource;
import z80emu.Z80PCListener;
import z80emu.Z80PIO;


public class LC80 extends EmuSys implements
					Z80HaltStateListener,
					Z80PCListener
{
  public static final String SYSNAME              = "LC80";
  public static final String SYSNAME_LC80_U505    = "LC80_U505";
  public static final String SYSNAME_LC80_2716    = "LC80_2716";
  public static final String SYSNAME_LC80_2       = "LC80_2";
  public static final String SYSNAME_LC80_E       = "LC80_E";
  public static final String SYSNAME_LC80         = "LC80";
  public static final String SYSTEXT              = "LC-80";
  public static final String SYSTEXT_LC80_2       = "LC-80.2";
  public static final String SYSTEXT_LC80_E       = "LC-80e";
  public static final String PROP_PREFIX          = "jkcemu.lc80.";
  public static final String PROP_ROM_C000_PREFIX = "rom_c000.";


  public static final int     DEFAULT_PROMPT_AFTER_RESET_MILLIS_MAX = 15000;
  public static final boolean DEFAULT_SWAP_KEY_CHAR_CASE            = true;

  private static byte[] lc80_u505  = null;
  private static byte[] lc80_2716  = null;
  private static byte[] lc80_2     = null;
  private static byte[] lc80e_0000 = null;
  private static byte[] lc80e_c000 = null;

  private String           romOSFile;
  private String           romC000File;
  private byte[]           romOS;
  private byte[]           romC000;
  private byte[]           ram;
  private int[]            kbMatrix;
  private int[]            digitStatus;
  private int[]            digitValues;
  private int              curDigitValue;
  private volatile int     pio1BValue;
  private long             curDisplayTStates;
  private long             displayCheckTStates;
  private boolean          tapeInPhase;
  private boolean          tapeOutLED;
  private volatile boolean tapeOutState;
  private boolean          chessComputer;
  private boolean          chessMode;
  private boolean          haltState;
  private Z80CTC           ctc;
  private Z80PIO           pio1;
  private Z80PIO           pio2;
  private String           sysName;
  private LC80KeyboardFld  keyboardFld;


  public LC80( EmuThread emuThread, Properties props )
  {
    super( emuThread, props, PROP_PREFIX );
    this.chessComputer = false;
    this.romOSFile     = null;
    this.romC000File   = null;
    this.sysName       = EmuUtil.getProperty( props, EmuThread.PROP_SYSNAME );
    if( this.sysName.equals( SYSNAME_LC80_U505 ) ) {
      this.ram = new byte[ 0x0400 ];
    } else {
      this.ram = new byte[ 0x1000 ];
      if( this.sysName.equals( SYSNAME_LC80_E ) ) {
	this.chessComputer = true;
      }
    }

    this.tapeInPhase         = false;
    this.tapeOutLED          = false;
    this.tapeOutState        = false;
    this.chessMode           = false;
    this.haltState           = false;
    this.curDisplayTStates   = 0;
    this.displayCheckTStates = 0;
    this.pio1BValue          = 0xFF;
    this.curDigitValue       = 0xFF;
    this.digitValues         = new int[ 6 ];
    this.digitStatus         = new int[ 6 ];
    this.kbMatrix            = new int[ 6 ];
    this.keyboardFld         = null;

    Z80CPU cpu = emuThread.getZ80CPU();
    this.pio1 = new Z80PIO( "PIO 1" );
    this.pio2 = new Z80PIO( "PIO 2" );
    this.ctc  = new Z80CTC( "CTC" );
    cpu.setInterruptSources( this.ctc, this.pio2, this.pio1 );
    cpu.addMaxSpeedListener( this );
    cpu.addHaltStateListener( this );
    cpu.addTStatesListener( this );
    if( this.chessComputer ) {
      cpu.addPCListener( this, 0x0000, 0xC800 );
    }
    z80MaxSpeedChanged( cpu );

    if( !isReloadExtROMsOnPowerOnEnabled( props ) ) {
      loadROMs( props );
    }
  }


  public static int getDefaultSpeedKHz( Properties props )
  {
    return EmuUtil.getProperty(
		props,
		EmuThread.PROP_SYSNAME ).startsWith( SYSNAME_LC80_E ) ?
								1800 : 900;
  }


  public boolean isChessMode()
  {
    return this.chessMode;
  }


  public void updKeyboardMatrix( int[] kbMatrix )
  {
    synchronized( this.kbMatrix ) {
      int n = Math.min( kbMatrix.length, this.kbMatrix.length );
      int i = 0;
      while( i < n ) {
        this.kbMatrix[ i ] = kbMatrix[ i ];
        i++;
      }
      while( i < this.kbMatrix.length ) {
        this.kbMatrix[ i ] = 0;
        i++;
      }
      putKBMatrixRowValueToPort();
    }
  }


	/* --- Z80HaltStateListener --- */

  @Override
  public void z80HaltStateChanged( Z80CPU cpu, boolean haltState )
  {
    if( haltState != this.haltState ) {
      this.haltState = haltState;
      this.screenFrm.setScreenDirty( true );
    }
  }


	/* --- Z80PCListener --- */

  @Override
  public void z80PCChanged( Z80CPU cpu, int pc )
  {
    switch( pc ) {
      case 0x0000:
	setChessMode( false );
	break;

      case 0xC800:
	setChessMode( true );
	break;
    }
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public boolean canApplySettings( Properties props )
  {
    boolean rv = EmuUtil.getProperty(
			props,
			EmuThread.PROP_SYSNAME ).equals( this.sysName );
    if( rv ) {
      rv = TextUtil.equals(
		this.romOSFile,
		EmuUtil.getProperty(
			props,
			this.propPrefix + PROP_OS_FILE ) );
    }
    if( rv && this.sysName.equals( SYSNAME_LC80_E ) ) {
      rv = TextUtil.equals(
	this.romC000File,
	EmuUtil.getProperty(
		props,
		this.propPrefix + PROP_ROM_C000_PREFIX + PROP_FILE ) );
    }
    return rv;
  }


  @Override
  public AbstractKeyboardFld createKeyboardFld()
  {
    this.keyboardFld = new LC80KeyboardFld( this );
    return this.keyboardFld;
  }


  @Override
  public void die()
  {
    Z80CPU cpu = this.emuThread.getZ80CPU();
    cpu.removeTStatesListener( this );
    cpu.removeHaltStateListener( this );
    cpu.removeMaxSpeedListener( this );
    cpu.setInterruptSources( (Z80InterruptSource[]) null );
    if( this.chessComputer ) {
      cpu.removePCListener( this );
    }
  }


  @Override
  public int getAppStartStackInitValue()
  {
    return 0x23EA;
  }


  @Override
  public Chessman getChessman( int row, int col )
  {
    Chessman rv = null;
    if( this.chessMode
	&& (row >= 0) && (row < 8) && (col >= 0) && (col < 8) )
    {
      switch( getMemByte( 0x2715 + (row * 10) + col, false ) & 0x8F ) {
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

	case 0x81:
	  rv = Chessman.BLACK_PAWN;
	  break;

	case 0x82:
	  rv = Chessman.BLACK_KNIGHT;
	  break;

	case 0x83:
	  rv = Chessman.BLACK_BISHOP;
	  break;

	case 0x84:
	  rv = Chessman.BLACK_ROOK;
	  break;

	case 0x85:
	  rv = Chessman.BLACK_QUEEN;
	  break;

	case 0x86:
	  rv = Chessman.BLACK_KING;
	  break;
      }
    }
    return rv;
  }


  @Override
  public Color getColor( int colorIdx )
  {
    Color color = Color.black;
    switch( colorIdx ) {
      case 1:
	color = this.colorGreenDark;
	break;

      case 2:
	color = this.colorGreenLight;
	break;

      case 3:
	color = this.colorRedDark;
	break;

      case 4:
	color = this.colorRedLight;
	break;
    }
    return color;
  }


  @Override
  public int getColorCount()
  {
    return 5;
  }


  @Override
  public String getHelpPage()
  {
    return "/help/lc80.htm";
  }


  @Override
  public int getMemByte( int addr, boolean m1 )
  {
    if( this.romC000 != null ) {
      addr &= 0xFFFF;
    } else {
      addr &= 0x3FFF;		// unvollstaendige Adressdekodierung
    }

    int rv = 0xFF;
    if( addr < 0x2000 ) {
      if( this.romOS != null ) {
	if( addr < this.romOS.length ) {
	  rv = (int) this.romOS[ addr ] & 0xFF;
	}
      }
    }
    else if( (addr >= 0x2000) && (addr < 0xC000) ) {
      int idx = addr - 0x2000;
      if( idx < this.ram.length ) {
	rv = (int) this.ram[ idx ] & 0xFF;
      }
    }
    else if( addr >= 0xC000 ) {
      if( this.romC000 != null ) {
	int idx = addr - 0xC000;
	if( idx < this.romC000.length ) {
	  rv = (int) this.romC000[ idx ] & 0xFF;
	}
      }
    }
    return rv;
  }


  @Override
  public int getScreenHeight()
  {
    return 85;
  }


  @Override
  public int getScreenWidth()
  {
    return 39 + (65 * this.digitValues.length);
  }


  @Override
  public boolean getSwapKeyCharCase()
  {
    return DEFAULT_SWAP_KEY_CHAR_CASE;
  }


  @Override
  public String getTitle()
  {
    String rv = SYSTEXT;
    switch( this.sysName ) {
      case SYSNAME_LC80_2:
	rv = SYSTEXT_LC80_2;
	break;
      case SYSNAME_LC80_E:
	rv = SYSTEXT_LC80_E;
	break;
    }
    return rv;
  }


  @Override
  public boolean keyPressed(
			int     keyCode,
			boolean ctrlDown,
			boolean shiftDown )
  {
    boolean rv = false;
    synchronized( this.kbMatrix ) {
      switch( keyCode ) {
	case KeyEvent.VK_F1:
	  this.kbMatrix[ 5 ] = 0x80;		// ADR / NEW GAME
	  rv = true;
	  break;

	case KeyEvent.VK_F2:
	  this.kbMatrix[ 5 ] = 0x40;		// DAT / SELF PLAY / SW
	  rv = true;
	  break;

	case KeyEvent.VK_F3:
	  this.kbMatrix[ 0 ] = 0x20;		// LD / RAN
	  rv = true;
	  break;

	case KeyEvent.VK_F4:
	  this.kbMatrix[ 0 ] = 0x40;		// ST / Contr.
	  rv = true;
	  break;

	case KeyEvent.VK_ENTER:
	  this.kbMatrix[ 0 ] = 0x80;		// EX
	  rv = true;
	  break;
      }
    }
    if( rv ) {
      putKBMatrixRowValueToPort();
      updKeyboardFld();
    } else {
      if( keyCode == KeyEvent.VK_ESCAPE ) {
	this.emuThread.fireReset( EmuThread.ResetLevel.WARM_RESET );
	rv = true;
      }
    }
    return rv;
  }


  @Override
  public void keyReleased()
  {
    synchronized( this.kbMatrix ) {
      Arrays.fill( this.kbMatrix, 0 );
    }
    putKBMatrixRowValueToPort();
    updKeyboardFld();
  }


  @Override
  public boolean keyTyped( char keyChar )
  {
    boolean rv = false;
    synchronized( this.kbMatrix ) {
      switch( Character.toUpperCase( keyChar ) ) {
	case '0':
	  this.kbMatrix[ 1 ] = 0x80;
	  rv = true;
	  break;

	case '1':
	  this.kbMatrix[ 1 ] = 0x40;
	  rv = true;
	  break;

	case '2':
	  this.kbMatrix[ 1 ] = 0x20;
	  rv = true;
	  break;

	case '3':
	  this.kbMatrix[ 1 ] = 0x10;
	  rv = true;
	  break;

	case '4':
	  this.kbMatrix[ 2 ] = 0x80;
	  rv = true;
	  break;

	case '5':
	  this.kbMatrix[ 2 ] = 0x40;
	  rv = true;
	  break;

	case '6':
	  this.kbMatrix[ 5 ] = 0x20;
	  rv = true;
	  break;

	case '7':
	  this.kbMatrix[ 2 ] = 0x10;
	  rv = true;
	  break;

	case '8':
	  this.kbMatrix[ 3 ] = 0x80;
	  rv = true;
	  break;

	case '9':
	  this.kbMatrix[ 3 ] = 0x40;
	  rv = true;
	  break;

	case '-':
	  this.kbMatrix[ 5 ] = 0x10;
	  rv = true;
	  break;

	case '+':
	  this.kbMatrix[ 2 ] = 0x20;
	  rv = true;
	  break;

	case 'A':
	  if( this.chessComputer && this.chessMode ) {
	    this.kbMatrix[ 1 ] = 0x40;		// auch 1
	  } else {
	    this.kbMatrix[ 4 ] = 0x20;
	  }
	  rv = true;
	  break;

	case 'B':
	  if( this.chessComputer && this.chessMode ) {
	    this.kbMatrix[ 1 ] = 0x20;		// auch 2
	  } else {
	    this.kbMatrix[ 3 ] = 0x10;
	  }
	  rv = true;
	  break;

	case 'C':
	  if( this.chessComputer && this.chessMode ) {
	    this.kbMatrix[ 1 ] = 0x10;		// auch 3
	  } else {
	    this.kbMatrix[ 4 ] = 0x80;
	  }
	  rv = true;
	  break;

	case 'D':
	  if( this.chessComputer && this.chessMode ) {
	    this.kbMatrix[ 2 ] = 0x80;		// auch 4
	  } else {
	    this.kbMatrix[ 4 ] = 0x40;
	  }
	  rv = true;
	  break;

	case 'E':
	  if( this.chessComputer && this.chessMode ) {
	    this.kbMatrix[ 2 ] = 0x40;		// auch 5
	  } else {
	    this.kbMatrix[ 3 ] = 0x20;
	  }
	  rv = true;
	  break;

	case 'F':
	  if( this.chessComputer && this.chessMode ) {
	    this.kbMatrix[ 5 ] = 0x20;		// auch 6
	  } else {
	    this.kbMatrix[ 4 ] = 0x10;
	  }
	  rv = true;
	  break;

	case 'G':
	  if( this.chessComputer && this.chessMode ) {
	    this.kbMatrix[ 2 ] = 0x10;		// auch 7
	    rv = true;
	  }
	  break;

	case 'H':
	  if( this.chessComputer && this.chessMode ) {
	    this.kbMatrix[ 3 ] = 0x80;		// auch 8
	  } else {
	    this.kbMatrix[ 5 ] = 0x40;		// DAT
	  }
	  rv = true;
	  break;

	case 'K':
	  if( this.chessComputer && this.chessMode ) {
	    this.kbMatrix[ 4 ] = 0x10;		// Koenig
	    rv = true;
	  }
	  break;

	case 'L':
	  if( this.chessComputer && this.chessMode ) {
	    this.kbMatrix[ 4 ] = 0x80;		// Laeufer
	  } else {
	    this.kbMatrix[ 0 ] = 0x20;		// LD
	  }
	  rv = true;
	  break;

	case 'M':
	  if( this.chessComputer && this.chessMode ) {
	    this.kbMatrix[ 3 ] = 0x20;		// Dame
	  } else {
	    this.kbMatrix[ 5 ] = 0x80;		// ADR
	  }
	  rv = true;
	  break;

	case 'N':
	  this.emuThread.getZ80CPU().fireNMI();
	  rv = true;
	  break;

	case 'O':
	  if( this.chessComputer && this.chessMode ) {
	    this.kbMatrix[ 2 ] = 0x20;		// BOARD
	    rv = true;
	  }
	  break;

	case 'P':
	  if( this.chessComputer && this.chessMode ) {
	    this.kbMatrix[ 5 ] = 0x40;		// SELF PLAY / SW
	    rv = true;
	  }
	  break;

	case 'R':
	  if( this.chessComputer && this.chessMode ) {
	    this.kbMatrix[ 0 ] = 0x20;		// RAN
	    rv = true;
	  }
	  break;

	case 'S':
	  if( this.chessComputer && this.chessMode ) {
	    this.kbMatrix[ 3 ] = 0x10;		// Springer
	  } else {
	    this.kbMatrix[ 0 ] = 0x40;		// ST
	  }
	  rv = true;
	  break;

	case 'T':
	  if( this.chessComputer && this.chessMode ) {
	    this.kbMatrix[ 4 ] = 0x40;		// Turm
	    rv = true;
	  }
	  break;

	case 'U':
	  if( this.chessComputer && this.chessMode ) {
	    this.kbMatrix[ 4 ] = 0x20;		// Bauer
	    rv = true;
	  }
	  break;

	case 'V':
	  if( this.chessComputer && this.chessMode ) {
	    this.kbMatrix[ 5 ] = 0x80;		// NEW GAME
	    rv = true;
	  }
	  break;

	case 'W':
	  if( this.chessComputer && this.chessMode ) {
	    this.kbMatrix[ 5 ] = 0x10;		// COLOR (WHITE)
	    rv = true;
	  }
	  break;

	case 'X':
	  this.kbMatrix[ 0 ] = 0x80;		// EX
	  rv = true;
	  break;
      }
    }
    if( rv ) {
      putKBMatrixRowValueToPort();
      updKeyboardFld();
    }
    return rv;
  }


  @Override
  public boolean paintScreen( Graphics g, int x, int y, int screenScale )
  {
    // LED fuer Tonausgabe, L-aktiv
    if( this.tapeOutLED
	|| (!this.tapeOutPhase && !this.tapeOutState) )
    {
      g.setColor( this.colorGreenLight );
    } else {
      g.setColor( this.colorGreenDark );
    }
    g.fillArc(
	x,
	y,
	20 * screenScale,
	20 * screenScale,
	0,
	360 );

    // LED fuer HALT-Zustand
    g.setColor( this.haltState ? this.colorRedLight : this.colorRedDark );
    g.fillArc(
	x,
	y + (30 * screenScale),
	20 * screenScale,
	20 * screenScale,
	0,
	360 );

    // 7-Segment-Anzeige
    x += (50 * screenScale);
    synchronized( this.digitValues ) {
      for( int i = 0; i < this.digitValues.length; i++ ) {
	paint7SegDigit(
		g,
		x,
		y,
		this.digitValues[ i ],
		this.colorGreenDark,
		this.colorGreenLight,
		screenScale );
	x += (65 * screenScale);
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
	  rv &= this.pio1.readDataA();
	  break;

	case 1:
	  rv &= this.pio1.readDataB();
	  break;

	case 2:
	  rv &= this.pio1.readControlA();
	  break;

	case 3:
	  rv &= this.pio1.readControlB();
	  break;
      }
    }
    if( (port & 0x04) == 0 ) {
      switch( port & 0x03 ) {
	case 0:
	  rv &= this.pio2.readDataA();
	  break;

	case 1:
	  rv &= this.pio2.readDataB();
	  break;

	case 2:
	  rv &= this.pio2.readControlA();
	  break;

	case 3:
	  rv &= this.pio2.readControlB();
	  break;
      }
    }
    if( (port & 0x10) == 0 ) {
      rv &= this.ctc.read( port & 0x03, tStates );
    }
    return rv;
  }


  @Override
  public void reset( EmuThread.ResetLevel resetLevel, Properties props )
  {
    super.reset( resetLevel, props );
    if( resetLevel == EmuThread.ResetLevel.POWER_ON ) {
      if( isReloadExtROMsOnPowerOnEnabled( props ) ) {
	loadROMs( props );
      }
      initSRAM( this.ram, props );
    }
    synchronized( this.kbMatrix ) {
      Arrays.fill( this.kbMatrix, 0 );
    }
    synchronized( this.digitValues ) {
      Arrays.fill( this.digitStatus, 0 );
      Arrays.fill( this.digitValues, 0 );
    }
    setChessMode( false );
    this.tapeInPhase = this.emuThread.readTapeInPhase();
  }


  @Override
  public boolean setMemByte( int addr, int value )
  {
    if( this.romC000 != null ) {
      addr &= 0xFFFF;
    } else {
      addr &= 0x3FFF;		// unvollstaendige Adressdekodierung
    }

    boolean rv = false;
    if( (addr >= 0x2000) && (addr < 0xC000) ) {
      int idx = addr - 0x2000;
      if( idx < this.ram.length ) {
	this.ram[ idx ] = (byte) value;
	if( this.chessComputer && (addr >= 0x2715) && (addr < 0x2763) ) {
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
    return this.chessComputer;
  }


  @Override
  public boolean supportsKeyboardFld()
  {
    return true;
  }


  @Override
  public boolean supportsTapeIn()
  {
    return true;
  }


  @Override
  public boolean supportsTapeOut()
  {
    return true;
  }


  @Override
  public void writeIOByte( int port, int value, int tStates )
  {
    if( (port & 0x08) == 0 ) {
      switch( port & 0x03 ) {
	case 0:
	  this.pio1.writeDataA( value );
	  this.curDigitValue = toDigitValue(
				this.pio1.fetchOutValuePortA( false ) );
	  putKBMatrixRowValueToPort();
	  updDisplay();
	  break;

	case 1:
	  this.pio1.writeDataB( value );
	  this.pio1BValue      = this.pio1.fetchOutValuePortB( false );
	  boolean tapeOutPhase = ((this.pio1BValue & 0x02) != 0);
	  if( tapeOutPhase != this.tapeOutPhase ) {
	    this.tapeOutPhase = tapeOutPhase;
	    this.tapeOutState = true;
	    this.screenFrm.setScreenDirty( true );
	  }
	  putKBMatrixRowValueToPort();
	  updDisplay();
	  break;

	case 2:
	  this.pio1.writeControlA( value );
	  break;

	case 3:
	  this.pio1.writeControlB( value );
	  break;
      }
    }
    if( (port & 0x04) == 0 ) {
      switch( port & 0x03 ) {
	case 0:
	  this.pio2.writeDataA( value );
	  break;

	case 1:
	  this.pio2.writeDataB( value );
	  break;

	case 2:
	  this.pio2.writeControlA( value );
	  break;

	case 3:
	  this.pio2.writeControlB( value );
	  break;
      }
    }
    if( (port & 0x10) == 0 ) {
      this.ctc.write( port & 0x03, value, tStates );
    }
  }


  @Override
  public void z80MaxSpeedChanged( Z80CPU cpu )
  {
    super.z80MaxSpeedChanged( cpu );
    this.displayCheckTStates = cpu.getMaxSpeedKHz() * 50;
  }


  @Override
  public void z80TStatesProcessed( Z80CPU cpu, int tStates )
  {
    super.z80TStatesProcessed( cpu, tStates );

    boolean phase = this.emuThread.readTapeInPhase();
    if( phase != this.tapeInPhase ) {
      this.tapeInPhase = phase;
      this.pio1.putInValuePortB( this.tapeInPhase ? 1 : 0, false );
    }
    this.ctc.z80TStatesProcessed( cpu, tStates );
    if( this.displayCheckTStates > 0 ) {
      this.curDisplayTStates += tStates;
      if( this.curDisplayTStates > this.displayCheckTStates ) {
	boolean dirty = false;
	synchronized( this.digitValues ) {
	  for( int i = 0; i < this.digitValues.length; i++ ) {
	    if( this.digitStatus[ i ] > 0 ) {
	      --this.digitStatus[ i ];
	    } else {
	      if( this.digitValues[ i ] != 0 ) {
		this.digitValues[ i ] = 0;
		dirty = true;
	      }
	    }
	  }
	}
	if( this.tapeOutState ) {
	  this.tapeOutLED = !this.tapeOutLED;
	  dirty            = true;
	} else {
	  if( this.tapeOutLED ) {
	    this.tapeOutLED = false;
	    dirty            = true;
	  }
	}
	if( dirty ) {
	  this.screenFrm.setScreenDirty( true );
	}
	this.tapeOutState      = false;
	this.curDisplayTStates = 0;
      }
    }
  }


	/* --- private Methoden --- */

  private void loadROMs( Properties props )
  {
    this.romOSFile = EmuUtil.getProperty(
				props,
				this.propPrefix + PROP_OS_FILE );
    this.romOS = readROMFile( this.romOSFile, 0x2000, "Monitorprogramm" );
    if( this.romOS == null ) {
      if( this.sysName.equals( SYSNAME_LC80_U505 ) ) {
	if( lc80_u505 == null ) {
	  lc80_u505 = readResource( "/rom/lc80/lc80_u505.bin" );
	}
	this.romOS = lc80_u505;
      } else if( this.sysName.equals( SYSNAME_LC80_2 ) ) {
	if( lc80_2 == null ) {
	  lc80_2 = readResource( "/rom/lc80/lc80_2.bin" );
	}
	this.romOS = lc80_2;
      } else if( this.sysName.equals( SYSNAME_LC80_E ) ) {
	if( lc80e_0000 == null ) {
	  lc80e_0000 = readResource( "/rom/lc80/lc80e_0000.bin" );
	}
	this.romOS = lc80e_0000;
      } else {
	if( lc80_2716 == null ) {
	  lc80_2716 = readResource( "/rom/lc80/lc80_2716.bin" );
	}
	this.romOS = lc80_2716;
      }
    }
    if( this.sysName.equals( SYSNAME_LC80_E ) ) {
      this.romC000File = EmuUtil.getProperty(
			props,
			this.propPrefix + PROP_ROM_C000_PREFIX + PROP_FILE );
      this.romC000 = readROMFile(
			this.romC000File,
			0x4000,
			"ROM C000h / Schachprogramm" );
      if( this.romC000 == null ) {
	if( lc80e_c000 == null ) {
	  lc80e_c000 = readResource( "/rom/lc80/lc80e_c000.bin" );
	}
	this.romC000 = lc80e_c000;
      }
    } else {
      this.romC000 = null;
    }
  }


  private void putKBMatrixRowValueToPort()
  {
    int v = 0;
    synchronized( this.kbMatrix ) {
      int m = 0x04;
      for( int i = 0; i < this.kbMatrix.length; i++ ) {
	if( (this.pio1BValue & m) == 0 ) {
	  v |= this.kbMatrix[ i ];
	}
	m <<= 1;
      }
    }
    this.pio2.putInValuePortB( ~v, 0xF0 );
  }


  private void setChessMode( boolean state )
  {
    if( state != this.chessMode ) {
      this.chessMode = state;
      if( this.keyboardFld != null ) {
        this.keyboardFld.repaint();
      }
    }
  }


  /*
   * Eingang: L-Aktiv
   *   Bit: 0 -> B
   *   Bit: 1 -> F
   *   Bit: 2 -> A
   *   Bit: 3 -> G
   *   Bit: 4 -> P
   *   Bit: 5 -> C
   *   Bit: 6 -> E
   *   Bit: 7 -> D
   *
   * Ausgang: H-Aktiv
   *   Bit: 0 -> A
   *   Bit: 1 -> B
   *   Bit: 2 -> C
   *   Bit: 3 -> D
   *   Bit: 4 -> E
   *   Bit: 5 -> F
   *   Bit: 6 -> G
   *   Bit: 7 -> P
   */
  private int toDigitValue( int value )
  {
    int rv = 0;
    if( (value & 0x01) == 0 ) {
      rv |= 0x02;
    }
    if( (value & 0x02) == 0 ) {
      rv |= 0x20;
    }
    if( (value & 0x04) == 0 ) {
      rv |= 0x01;
    }
    if( (value & 0x08) == 0 ) {
      rv |= 0x40;
    }
    if( (value & 0x10) == 0 ) {
      rv |= 0x80;
    }
    if( (value & 0x20) == 0 ) {
      rv |= 0x04;
    }
    if( (value & 0x40) == 0 ) {
      rv |= 0x10;
    }
    if( (value & 0x80) == 0 ) {
      rv |= 0x08;
    }
    return rv;
  }


  private void updDisplay()
  {
    boolean dirty = false;
    synchronized( this.digitValues ) {
      int m = 0x80;
      for( int i = 0; i < this.digitValues.length; i++ ) {
	if( (this.pio1BValue & m) == 0 ) {
	  this.digitStatus[ i ] = 2;
	  if( this.digitValues[ i ] != this.curDigitValue ) {
	    this.digitValues[ i ] = this.curDigitValue;
	    dirty = true;
	  }
	}
	m >>= 1;
      }
    }
    if( dirty )
      this.screenFrm.setScreenDirty( true );
  }


  private void updKeyboardFld()
  {
    if( this.keyboardFld != null )
      this.keyboardFld.updKeySelection( this.kbMatrix );
  }
}