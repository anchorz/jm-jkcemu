/*
 * (c) 2009-2021 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Emulation des LLC1
 */

package jkcemu.emusys;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Arrays;
import java.util.Properties;
import jkcemu.Main;
import jkcemu.base.AbstractScreenFrm;
import jkcemu.base.AutoInputCharSet;
import jkcemu.base.EmuMemView;
import jkcemu.base.EmuSys;
import jkcemu.base.EmuThread;
import jkcemu.base.EmuUtil;
import jkcemu.base.SourceUtil;
import jkcemu.emusys.llc1.LLC1AlphaScreenDevice;
import jkcemu.emusys.llc1.LLC1KeyboardFld;
import jkcemu.file.SaveDlg;
import jkcemu.text.TextUtil;
import z80emu.Z80CPU;
import z80emu.Z80CTC;
import z80emu.Z80InterruptSource;
import z80emu.Z80MaxSpeedListener;
import z80emu.Z80PIO;
import z80emu.Z80PIOPortListener;


public class LLC1 extends EmuSys implements
					Z80MaxSpeedListener,
					Z80PIOPortListener
{
  public static final String SYSNAME         = "LLC1";
  public static final String PROP_PREFIX     = "jkcemu.llc1.";
  public static final String PROP_ROM_PREFIX = "rom.";
  public static final String PROP_ROM_FILE   = PROP_ROM_PREFIX + PROP_FILE;

  public static final int     DEFAULT_PROMPT_AFTER_RESET_MILLIS_MAX = 500;
  public static final boolean DEFAULT_SWAP_KEY_CHAR_CASE            = true;

  private static final int PASTE_READS_PER_CHAR = 10;

  private static AutoInputCharSet autoInputCharSet = null;

  private static byte[] llc1Font = null;
  private static byte[] llc1Rom  = null;

  private byte[]                fontBytes;
  private byte[]                romBytes;
  private byte[]                ramStatic;
  private byte[]                ramVideo;
  private String                fontFile;
  private String                romFile;
  private LLC1AlphaScreenDevice alphaScreenDevice;
  private LLC1KeyboardFld       keyboardFld;
  private int[]                 keyboardMatrix;
  private int[]                 digitStatus;
  private int[]                 digitValues;
  private int                   digitIdx;
  private int                   keyChar;
  private volatile int          pasteReadCharCounter;
  private volatile int          pasteReadPauseCounter;
  private int                   alphaScreenEnableTStates;
  private boolean               pio1B7Value;
  private long                  curDisplayTStates;
  private long                  displayCheckTStates;
  private Z80CTC                ctc;
  private Z80PIO                pio1;
  private Z80PIO                pio2;


  public LLC1( EmuThread emuThread, Properties props )
  {
    super( emuThread, props, PROP_PREFIX );
    this.fontBytes                = null;
    this.fontFile                 = null;
    this.romBytes                 = null;
    this.romFile                  = null;
    this.ramStatic                = new byte[ 0x0800 ];
    this.ramVideo                 = new byte[ 0x0400 ];
    this.pasteReadCharCounter     = 0;
    this.pasteReadPauseCounter    = 0;
    this.alphaScreenEnableTStates = 0;
    this.alphaScreenDevice        = null;
    this.keyboardFld              = null;
    this.keyboardMatrix           = new int[ 4 ];
    this.digitStatus              = new int[ 8 ];
    this.digitValues              = new int[ 8 ];
    this.digitIdx                 = 0;
    this.displayCheckTStates      = 0;
    this.curDisplayTStates        = 0;
    this.keyChar                  = 0;
    this.pio1B7Value              = false;

    Z80CPU cpu = emuThread.getZ80CPU();
    this.ctc   = new Z80CTC( "CTC" );
    this.pio1  = new Z80PIO( "PIO 1" );	// nicht in der Interrupt-Kette!
    this.pio2  = new Z80PIO( "PIO 2" );
    cpu.setInterruptSources( this.ctc, this.pio2 );
    cpu.addMaxSpeedListener( this );
    cpu.addTStatesListener( this );
    this.pio1.addPIOPortListener( this, Z80PIO.PortInfo.B );

    z80MaxSpeedChanged( cpu );
  }


  public static AutoInputCharSet getAutoInputCharSet()
  {
    if( autoInputCharSet == null ) {
      autoInputCharSet = new AutoInputCharSet();
      autoInputCharSet.addHexChars();
// TODO: Steuertasten
    }
    return autoInputCharSet;
  }


  public void cancelPastingAlphaText()
  {
    this.pasteIter = null;
    informPastingAlphaTextStatusChanged( false );
  }


  public byte[] getAlphaScreenFontBytes()
  {
    return this.fontBytes;
  }


  public static String getBasicProgram( EmuMemView memory )
  {
    return SourceUtil.getTinyBasicProgram(
				memory,
				0x154E,
				memory.getMemWord( 0x141B ) );
  }


  public static int getDefaultSpeedKHz()
  {
    return 2000;
  }


  public void putAlphaKeyChar( int ch )
  {
    this.keyChar = ch;
  }


  public void startPastingAlphaText( String text )
  {
    if( text != null ) {
      if( !text.isEmpty() ) {
	this.pasteIter             = new StringCharacterIterator( text );
	this.pasteReadCharCounter  = 0;
	this.pasteReadPauseCounter = PASTE_READS_PER_CHAR;
	informPastingAlphaTextStatusChanged( true );
      }
    }
  }


  public void updKeyboardMatrix( int[] kbMatrix )
  {
    boolean pressed = false;
    synchronized( this.keyboardMatrix ) {
      int n = Math.min( kbMatrix.length, this.keyboardMatrix.length );
      int i = 0;
      while( i < n ) {
	if( (~this.keyboardMatrix[ i ] & kbMatrix[ i ]) != 0 ) {
	  pressed = true;
	}
	this.keyboardMatrix[ i ] = kbMatrix[ i ];
	i++;
      }
      while( i < this.keyboardMatrix.length ) {
	this.keyboardMatrix[ i ] = 0;
	i++;
      }
    }
    if( pressed ) {
      this.ctc.externalUpdate( 3, 1 );
    }
  }


	/* --- Z80MaxSpeedListener --- */

  @Override
  public void z80MaxSpeedChanged( Z80CPU cpu )
  {
    int maxSpeedKHz          = cpu.getMaxSpeedKHz();
    this.displayCheckTStates = maxSpeedKHz * 50;
  }


	/* --- Z80PIOPortListener --- */

  @Override
  public void z80PIOPortStatusChanged(
				Z80PIO          pio,
				Z80PIO.PortInfo port,
				Z80PIO.Status   status )
  {
    if( (pio == this.pio1)
	&& (port == Z80PIO.PortInfo.B)
	&& ((status == Z80PIO.Status.OUTPUT_AVAILABLE)
	    || (status == Z80PIO.Status.OUTPUT_CHANGED)) )
    {
      if( status == Z80PIO.Status.OUTPUT_AVAILABLE ) {
	this.digitIdx = (this.digitIdx + 1) & 0x07;
      }
      int     bValue  = this.pio1.fetchOutValuePortB( 0xFF, true );
      boolean b7Value = ((bValue & 0x80) != 0);
      if( b7Value != this.pio1B7Value ) {
	if( b7Value ) {
	  this.digitIdx = 0;
	}
	this.pio1B7Value = b7Value;
      }
      bValue &= 0x7F;
      if( bValue != 0 ) {
	synchronized( this.digitValues ) {
	  if( bValue != this.digitValues[ this.digitIdx ] ) {
	    this.digitValues[ this.digitIdx ] = bValue;
	    this.screenFrm.setScreenDirty( true );
	  }
	  this.digitStatus[ this.digitIdx ] = 2;
	}
      }
    }
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public void applySettings( Properties props )
  {
    super.applySettings( props );
    if( this.alphaScreenDevice != null ) {
      this.alphaScreenDevice.applySettings( props );
    }
  }


  @Override
  public boolean canApplySettings( Properties props )
  {
    boolean rv = EmuUtil.getProperty(
			props,
			EmuThread.PROP_SYSNAME ).equals( SYSNAME );
    if( rv ) {
      rv = TextUtil.equals(
		this.romFile,
		EmuUtil.getProperty(
			props,
			this.propPrefix + PROP_ROM_FILE ) );
    }
    return rv;
  }


  @Override
  public LLC1KeyboardFld createKeyboardFld()
  {
    this.keyboardFld = new LLC1KeyboardFld( this );
    return this.keyboardFld;
  }


  @Override
  public void die()
  {
    Z80CPU cpu = this.emuThread.getZ80CPU();
    cpu.removeTStatesListener( this );
    cpu.removeMaxSpeedListener( this );
    cpu.setInterruptSources( (Z80InterruptSource[]) null );
    this.pio1.removePIOPortListener( this, Z80PIO.PortInfo.B );

    super.die();
  }


  @Override
  public int getAppStartStackInitValue()
  {
    return 0x1C00;
  }


  @Override
  public Color getColor( int colorIdx )
  {
    Color color = Color.BLACK;
    switch( colorIdx ) {
      case 1:
        color = this.colorWhite;
        break;

      case 2:
        color = this.colorRedDark;
        break;

      case 3:
        color = this.colorRedLight;
        break;
    }
    return color;
  }


  @Override
  public int getColorCount()
  {
    return 4;
  }


  @Override
  protected long getDelayMillisAfterPasteChar()
  {
    return 50;
  }


  @Override
  protected long getDelayMillisAfterPasteEnter()
  {
    return 150;
  }


  @Override
  protected long getHoldMillisPasteChar()
  {
    return 50;
  }


  @Override
  public String getHelpPage()
  {
    return "/help/llc1.htm";
  }


  @Override
  public int getMemByte( int addr, boolean m1 )
  {
    addr &= 0xFFFF;

    int rv = 0xFF;
    if( (addr < 0x1400) && (this.romBytes != null) ) {
      if( addr < this.romBytes.length ) {
	rv = (int) this.romBytes[ addr ] & 0xFF;
      }
    }
    else if( (addr >= 0x1400) && (addr < 0x1C00) ) {
      int idx = addr - 0x1400;
      if( idx < this.ramStatic.length ) {
	rv = (int) this.ramStatic[ idx ] & 0xFF;
      }
    }
    else if( (addr >= 0x1C00) && (addr < 0x2000) ) {
      int idx = addr - 0x1C00;
      if( idx < this.ramVideo.length ) {
	rv = (int) this.ramVideo[ idx ] & 0xFF;
      }
    }
    return rv;
  }


  @Override
  public int getResetStartAddress( boolean powerOn )
  {
    return 0x0000;
  }


  @Override
  public int getScreenHeight()
  {
    return 85;
  }


  @Override
  public int getScreenWidth()
  {
    return (this.digitValues.length * 65) - 15;
  }


  @Override
  public String getTitle()
  {
    return SYSNAME;
  }


  @Override
  public LLC1AlphaScreenDevice getSecondScreenDevice()
  {
    if( this.alphaScreenDevice == null ) {
      this.alphaScreenDevice = new LLC1AlphaScreenDevice(
						this,
						Main.getProperties() );
    }
    return this.alphaScreenDevice;
  }


  @Override
  public boolean getSwapKeyCharCase()
  {
    return DEFAULT_SWAP_KEY_CHAR_CASE;
  }


  @Override
  public boolean keyPressed(
			int     keyCode,
			boolean ctrlDown,
			boolean shiftDown )
  {
    boolean rv = false;
    switch( keyCode ) {
      case KeyEvent.VK_ENTER:
	synchronized( this.keyboardMatrix ) {
	  this.keyboardMatrix[ 2 ] = 0x82;		// ST
	  this.ctc.externalUpdate( 3, 1 );
	  updKeyboardFld();
	}
	rv = true;
	break;
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
  public boolean keyTyped( char ch )
  {
    boolean rv = false;
    synchronized( this.keyboardMatrix ) {
      switch( ch ) {
	case '0':
	case '1':
	case '2':
	case '3':
	  this.keyboardMatrix[ ch - '0' ] = 0x01;
	  rv = true;
	  break;

	case '4':
	case '5':
	case '6':
	case '7':
	  this.keyboardMatrix[ ch - '4' ] = 0x02;
	  rv = true;
	  break;

	case '8':
	case '9':
	  this.keyboardMatrix[ ch - '8' ] = 0x04;
	  rv = true;
	  break;

	case 'A':
	case 'a':
	  this.keyboardMatrix[ 2 ] = 0x04;
	  rv = true;
	  break;

	case 'B':
	case 'b':
	  this.keyboardMatrix[ 3 ] = 0x04;
	  rv = true;
	  break;

	case 'C':
	case 'c':
	  this.keyboardMatrix[ 0 ] = 0x81;
	  rv = true;
	  break;

	case 'D':
	case 'd':
	  this.keyboardMatrix[ 1 ] = 0x81;
	  rv = true;
	  break;

	case 'E':
	case 'e':
	  this.keyboardMatrix[ 2 ] = 0x81;
	  rv = true;
	  break;

	case 'F':
	case 'f':
	  this.keyboardMatrix[ 3 ] = 0x81;
	  rv = true;
	  break;

	case 'R':
	  this.keyboardMatrix[ 0 ] = 0x82;		// REG
	  rv = true;
	  break;

	case 'M':
	  this.keyboardMatrix[ 1 ] = 0x82;		// EIN
	  rv = true;
	  break;

	case 'X':
	  this.keyboardMatrix[ 2 ] = 0x82;		// ST
	  rv = true;
	  break;

	case 'S':
	  this.keyboardMatrix[ 0 ] = 0x84;		// ES
	  rv = true;
	  break;

	case 'G':
	case 'J':
	  this.keyboardMatrix[ 1 ] = 0x84;		// DL
	  rv = true;
	  break;

	case 'H':
	  this.keyboardMatrix[ 2 ] = 0x84;		// HP
	  rv = true;
	  break;
      }
      if( rv ) {
	this.ctc.externalUpdate( 3, 1 );
	updKeyboardFld();
      }
    }
    return rv;
  }


  @Override
  public void loadROMs( Properties props )
  {
    this.romFile  = EmuUtil.getProperty(
				props,
				this.propPrefix + PROP_ROM_FILE );
    this.romBytes = readROMFile(
			this.romFile,
			0x1400,
			"Monitorprogramm / BASIC-Interpreter" );
    if( this.romBytes == null ) {
      if( llc1Rom == null ) {
	llc1Rom = readResource( "/rom/llc1/llc1rom.bin" );
      }
      this.romBytes = llc1Rom;
    }

    loadFont( props );
  }


  @Override
  public void openBasicProgram()
  {
    String text = getBasicProgram( this.emuThread );
    if( text != null ) {
      this.screenFrm.openText( text );
    } else {
      showNoBasic();
    }
  }


  @Override
  public boolean paintScreen( Graphics g, int x, int y, int screenScale )
  {
    synchronized( this.digitValues ) {
      for( int i = 0; i < this.digitValues.length; i++ ) {
	paint7SegDigit(
		g,
		x,
		y,
		this.digitStatus[ i ] > 0 ? this.digitValues[ i ] : 0,
		this.colorRedDark,
		this.colorRedLight,
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
    if( (port & 0x04) == 0 ) {
      rv &= this.ctc.read( port & 0x03, tStates );
    }
    else if( (port & 0x08) == 0 ) {
      switch( port & 0x03 ) {
	case 0:
	  synchronized( this.keyboardMatrix ) {
	    this.pio1.putInValuePortA( getHexKeyMatrixValue(), 0x8F );
	  }
	  rv &= this.pio1.readDataA();
	  break;

	case 1:
	  rv &= this.pio1.readDataB();
	  break;
      }
    }
    else if( (port & 0x10) == 0 ) {
      switch( port & 0x03 ) {
	case 0:
	  rv &= this.pio2.readDataA();
	  break;

	case 1:
	  {
	    int ch = this.keyChar;
	    if( ch == 0 ) {
	      CharacterIterator iter = this.pasteIter;
	      if( iter != null ) {
		if( this.pasteReadPauseCounter > 0 ) {
		  --this.pasteReadPauseCounter;
		  if( this.pasteReadPauseCounter == 0 ) {
		    this.pasteReadCharCounter = PASTE_READS_PER_CHAR;
		  }
		} else {
		  ch = iter.current();
		  if( ch == CharacterIterator.DONE ) {
		    this.pasteIter             = null;
		    this.pasteReadCharCounter  = 0;
		    this.pasteReadPauseCounter = 0;
		    ch                         = 0;
		    informPastingAlphaTextStatusChanged( false );
		  } else {
		    if( this.pasteReadCharCounter > 0 ) {
		      --this.pasteReadCharCounter;
		    } else {
		      iter.next();
		      this.pasteReadPauseCounter = PASTE_READS_PER_CHAR;
		    }
		  }
		}
	      }
	    }
	    this.pio2.putInValuePortB(
			ch > 0 ? toLLC1Char( ch ) : 0xFF,
			false );
	    rv &= this.pio2.readDataB();
	  }
	  break;
      }
    }
    return rv;
  }


  @Override
  public void reset( boolean powerOn, Properties props )
  {
    super.reset( powerOn, props );
    if( powerOn ) {
      initSRAM( this.ramStatic, props );
      fillRandom( this.ramVideo );
    }
    this.ctc.reset( powerOn );
    this.pio1.reset( powerOn );
    this.pio2.reset( powerOn );
    synchronized( this.keyboardMatrix ) {
      Arrays.fill( this.keyboardMatrix, 0 );
    }
    synchronized( this.digitValues ) {
      Arrays.fill( this.digitStatus, 0 );
      Arrays.fill( this.digitValues, 0 );
    }
    this.keyChar                  = 0;
    this.alphaScreenEnableTStates = getDefaultSpeedKHz() * 200;
    this.pio1B7Value              = false;
  }


  @Override
  public void saveBasicProgram()
  {
    int endAddr = this.emuThread.getMemWord( 0x141B );
    if( (endAddr > 0x154E)
	&& (this.emuThread.getMemByte( endAddr - 1, false ) == 0x0D) )
    {
      (new SaveDlg(
		this.screenFrm,
		0x1400,
		endAddr,
		"LLC1-BASIC-Programm speichern",
		SaveDlg.BasicType.TINYBASIC,
		null )).setVisible( true );
    } else {
      showNoBasic();
    }
  }


  @Override
  public boolean setMemByte( int addr, int value )
  {
    addr &= 0xFFFF;

    boolean rv = false;
    if( (addr >= 0x1400) && (addr < 0x1C00) ) {
      int idx = addr - 0x1400;
      if( idx < this.ramStatic.length ) {
	this.ramStatic[ idx ] = (byte) value;
	rv = true;
      }
    }
    else if( (addr >= 0x1C00) && (addr < 0x2000) ) {
      int idx = addr - 0x1C00;
      if( idx < this.ramVideo.length ) {
	this.ramVideo[ idx ] = (byte) value;
	if( this.alphaScreenDevice != null ) {
	  this.alphaScreenDevice.setScreenDirty( true );
	}
	rv = true;
      }
    }
    return rv;
  }


  @Override
  public boolean supportsKeyboardFld()
  {
    return true;
  }


  @Override
  public boolean supportsOpenBasic()
  {
    return true;
  }


  @Override
  public boolean supportsSaveBasic()
  {
    return true;
  }


  @Override
  public void writeIOByte( int port, int value, int tStates )
  {
    if( (port & 0x04) == 0 ) {
      this.ctc.write( port & 0x03, value, tStates );
    }
    else if( (port & 0x08) == 0 ) {
      switch( port & 0x03 ) {
	case 0:
	  this.pio1.writeDataA( value );
	  break;

	case 1:
	  this.pio1.writeDataB( value );
	  break;

	case 2:
	  this.pio1.writeControlA( value );
	  break;

	case 3:
	  this.pio1.writeControlB( value );
	  break;
      }
    }
    else if( (port & 0x10) == 0 ) {
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
  }


  @Override
  public void writeMemByte( int addr, int value )
  {
    addr &= 0xFFFF;
    setMemByte( addr, value );
    if( (this.alphaScreenEnableTStates <= 0)
	&& (addr >= 0x1C00) && (addr < 0x2000)
	&& (value != 0x00) && (value != 0x20)
	&& (value != 0x40) && (value != 0xFF) )
    {
      checkAndFireOpenSecondScreen();
    }
  }


  @Override
  public void z80TStatesProcessed( Z80CPU cpu, int tStates )
  {
    super.z80TStatesProcessed( cpu, tStates );
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
	if( dirty ) {
	  this.screenFrm.setScreenDirty( true );
	}
	this.curDisplayTStates = 0;
      }
    }
    if( this.alphaScreenEnableTStates > 0 ) {
      this.alphaScreenEnableTStates -= tStates;
    }
  }


	/* --- private Methoden --- */

  private int getHexKeyMatrixValue()
  {
    int rv = 0;
    int a  = (~this.pio1.fetchOutValuePortA( 0xFF ) >> 4) & 0x07;
    int m  = 0x01;
    for( int i = 0; i < this.keyboardMatrix.length; i++ ) {
      if( (this.keyboardMatrix[ i ] & a) != 0 ) {
	rv |= (m | (this.keyboardMatrix[ i ] & 0x80));
      }
      m <<= 1;
    }
    return rv;
  }


  private void informPastingAlphaTextStatusChanged( boolean pasting )
  {
    if( this.alphaScreenDevice != null ) {
      AbstractScreenFrm screenFrm = this.alphaScreenDevice.getScreenFrm();
      if( screenFrm != null ) {
	screenFrm.pastingTextStatusChanged( pasting );
      }
    }
  }


  private void loadFont( Properties props )
  {
    this.fontBytes = readFontByProperty(
				props,
				this.propPrefix + PROP_FONT_FILE, 0x0800 );
    if( this.fontBytes == null ) {
      if( llc1Font == null ) {
	llc1Font = readResource( "/rom/llc1/llc1font.bin" );
      }
      this.fontBytes = llc1Font;
    }
  }


  private static int toLLC1Char( int ch )
  {
    switch( ch ) {
      case '\n':
	ch = '\r';
	break;

      case '\u00B7':
	ch = 0xE0;
	break;

      case '/':
	ch = 0x10;
	break;

      case ';':
	ch = 0x11;
	break;

      case '\"':
	ch = 0x12;
	break;

      case '=':
	ch = 0x13;
	break;

      case '%':
	ch = 0x14;
	break;

      case '&':
	ch = 0x15;
	break;

      case '(':
	ch = 0x16;
	break;

      case ')':
	ch = 0x17;
	break;

      case '_':
	ch = 0x18;
	break;

      case '@':
      case '\u00A7':
	ch = 0x19;
	break;

      case ':':
	ch = 0x1A;
	break;

      case '#':
	ch = 0x1B;
	break;

      case '*':
	ch = 0x1C;
	break;

      case '\'':
	ch = 0x1D;
	break;

      case '!':
	ch = 0x1E;
	break;

      case '?':
	ch = 0x1F;
	break;

      case '\u00AC':
	ch = 0x3A;
	break;

      case '$':
	ch = 0x3B;
	break;

      case '+':
	ch = 0x3C;
	break;

      case '-':
	ch = 0x3D;
	break;

      case '.':
	ch = 0x3E;
	break;

      case ',':
	ch = 0x3F;
	break;

      case '\u0020':
	ch = 0x40;
	break;

      case ']':
	ch = 0x5B;
	break;

      case '[':
	ch = 0x5C;
	break;

      case '>':
	ch = 0x7B;
	break;

      case '<':
	ch = 0x7C;
	break;

      case '|':
	ch = 0x7D;
	break;

      case '^':
	ch = 0x7E;
	break;
    }
    return ch;
  }


  private void updKeyboardFld()
  {
    if( this.keyboardFld != null )
      this.keyboardFld.updKeySelection( this.keyboardMatrix );
  }
}
