/*
 * (c) 2008-2021 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Verwaltung des emulierten System
 */

package jkcemu.base;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Properties;
import java.util.TreeSet;
import java.util.Set;
import jkcemu.Main;
import jkcemu.audio.AbstractSoundDevice;
import jkcemu.audio.AudioFrm;
import jkcemu.audio.AudioIO;
import jkcemu.emusys.A5105;
import jkcemu.emusys.AC1;
import jkcemu.emusys.BCS3;
import jkcemu.emusys.C80;
import jkcemu.emusys.CustomSys;
import jkcemu.emusys.HueblerEvertMC;
import jkcemu.emusys.HueblerGraphicsMC;
import jkcemu.emusys.KC85;
import jkcemu.emusys.KCcompact;
import jkcemu.emusys.KramerMC;
import jkcemu.emusys.LC80;
import jkcemu.emusys.LLC1;
import jkcemu.emusys.LLC2;
import jkcemu.emusys.NANOS;
import jkcemu.emusys.PCM;
import jkcemu.emusys.Poly880;
import jkcemu.emusys.SC2;
import jkcemu.emusys.SLC1;
import jkcemu.emusys.VCS80;
import jkcemu.emusys.Z1013;
import jkcemu.emusys.Z9001;
import jkcemu.emusys.ZXSpectrum;
import jkcemu.etc.CPUSynchronSoundDevice;
import jkcemu.etc.PSG8910;
import jkcemu.file.LoadData;
import jkcemu.joystick.JoystickFrm;
import jkcemu.joystick.JoystickThread;
import jkcemu.print.PrintMngr;
import jkcemu.text.TextUtil;
import z80emu.Z80CPU;
import z80emu.Z80IOSystem;
import z80emu.Z80Memory;


public class EmuThread extends Thread implements
					Z80IOSystem,
					Z80Memory,
					EmuMemView
{
  public static final String PROP_SYSNAME = "jkcemu.system";

  public static final String PROP_EXT_ROM_RELOAD_ON_POWER_ON
				= "jkcemu.external_rom.reload_on_power_on";
  public static final boolean DEFAULT_EXT_ROM_RELOAD_ON_POWER_ON = false;

  public static final String PROP_MAXSPEED_KHZ = "jkcemu.maxspeed.khz";
  public static final String VALUE_MAXSPEED_KHZ_DEFAULT   = "default";
  public static final String VALUE_MAXSPEED_KHZ_UNLIMITED = "unlimited";

  public static final String PROP_RF_CLEAR_ON_POWER_ON
				= "jkcemu.ramfloppy.clear_on_power_on";
  public static final boolean DEFAULT_RF_CLEAR_ON_POWER_ON = false;


  private ScreenFrm           screenFrm;
  private Z80CPU              z80cpu;
  private Object              monitor;
  private JoystickFrm         joyFrm;
  private JoystickThread[]    joyThreads;
  private byte[]              ram;
  private RAMFloppy           ramFloppy1;
  private RAMFloppy           ramFloppy2;
  private PrintMngr           printMngr;
  private volatile LoadData   loadData;
  private volatile boolean    powerOn;
  private volatile boolean    emuRunning;
  private volatile EmuSys     emuSys;
  private volatile Properties newProps;
  private volatile Boolean    iso646de;


  public EmuThread( ScreenFrm screenFrm, Properties props )
  {
    super( Main.getThreadGroup(), "JKCEMU CPU" );
    this.screenFrm  = screenFrm;
    this.z80cpu     = new Z80CPU( this, this );
    this.monitor    = new Object();
    this.joyFrm     = null;
    this.joyThreads = new JoystickThread[ 2 ];
    this.ram        = new byte[ 0x10000 ];
    this.ramFloppy1 = new RAMFloppy();
    this.ramFloppy2 = new RAMFloppy();
    this.printMngr  = new PrintMngr();
    this.loadData   = null;
    this.powerOn    = true;
    this.emuRunning = false;
    this.emuSys     = createEmuSys( props );
    this.newProps   = null;
    Arrays.fill( this.joyThreads, null );
  }


  public synchronized void applySettings( Properties props )
  {
    if( this.emuSys.canApplySettings( props ) ) {
      this.emuSys.applySettings( props );
      updCPUSpeed( props );
    } else {
      fireReset( true, props );
    }
  }


  public void changeJoystickConnectState( int joyNum )
  {
    JoystickThread jt = null;
    synchronized( this.joyThreads ) {
      if( (joyNum >= 0) && (joyNum < this.joyThreads.length) ) {
	jt = this.joyThreads[ joyNum ];
	if( jt != null ) {
	  try {
	    jt.fireStop();
	    jt.join( 500 );
	  }
	  catch( InterruptedException ex ) {}
	  this.joyThreads[ joyNum ] = null;
	} else {
	  jt = new JoystickThread( this, joyNum, true );
	  this.joyThreads[ joyNum ] = jt;
	  jt.start();
	}
	updJoystickFrm( joyNum );
      }
    }
  }


  public void closeAudioLines()
  {
    closeAudioLine( this.emuSys.getTapeIn() );
    closeAudioLine( this.emuSys.getTapeOut() );
    for( AbstractSoundDevice sndDev : emuSys.getSoundDevices() ) {
      sndDev.fireStop();
      if( sndDev instanceof CPUSynchronSoundDevice ) {
	closeAudioLine( sndDev.getAudioOut() );
      }
    }
  }


  public void fireShowJoystickError( final String msg )
  {
    final Component owner = this.joyFrm;
    if( (owner != null) && (msg != null) ) {
      EmuUtil.fireShowErrorDlg( owner, msg, null );
    }
  }


  public static int getDefaultSpeedKHz( Properties props )
  {
    int    rv      = A5105.getDefaultSpeedKHz();
    String sysName = EmuUtil.getProperty( props, PROP_SYSNAME );
    if( sysName != null ) {
      switch( sysName ) {
	case AC1.SYSNAME:
	  rv = AC1.getDefaultSpeedKHz();
	  break;
	case BCS3.SYSNAME:
	  rv = BCS3.getDefaultSpeedKHz( props );
	  break;
	case C80.SYSNAME:
	  rv = C80.getDefaultSpeedKHz();
	  break;
	case HueblerEvertMC.SYSNAME:
	  rv = HueblerEvertMC.getDefaultSpeedKHz();
	  break;
	case HueblerGraphicsMC.SYSNAME:
	  rv = HueblerGraphicsMC.getDefaultSpeedKHz();
	  break;
	case KC85.SYSNAME_HC900:
	case KC85.SYSNAME_KC85_2:
	case KC85.SYSNAME_KC85_3:
	case KC85.SYSNAME_KC85_4:
	case KC85.SYSNAME_KC85_5:
	  rv = KC85.getDefaultSpeedKHz( props );
	  break;
	case KCcompact.SYSNAME:
	  rv = KCcompact.getDefaultSpeedKHz();
	  break;
	case KramerMC.SYSNAME:
	  rv = KramerMC.getDefaultSpeedKHz();
	  break;
	case LC80.SYSNAME_LC80_U505:
	case LC80.SYSNAME_LC80_2716:
	case LC80.SYSNAME_LC80_2:
	case LC80.SYSNAME_LC80_E:
	case LC80.SYSNAME_LC80_EX:
	  rv = LC80.getDefaultSpeedKHz( props );
	  break;
	case LLC1.SYSNAME:
	  rv = LLC1.getDefaultSpeedKHz();
	  break;
	case LLC2.SYSNAME:
	  rv = LLC2.getDefaultSpeedKHz();
	  break;
	case NANOS.SYSNAME:
	  rv = NANOS.getDefaultSpeedKHz();
	  break;
	case PCM.SYSNAME:
	  rv = PCM.getDefaultSpeedKHz();
	  break;
	case Poly880.SYSNAME:
	  rv = Poly880.getDefaultSpeedKHz();
	  break;
	case SC2.SYSNAME:
	  rv = SC2.getDefaultSpeedKHz();
	  break;
	case SLC1.SYSNAME:
	  rv = SLC1.getDefaultSpeedKHz();
	  break;
	case VCS80.SYSNAME:
	  rv = VCS80.getDefaultSpeedKHz();
	  break;
	case Z1013.SYSNAME_Z1013_01:
	case Z1013.SYSNAME_Z1013_12:
	case Z1013.SYSNAME_Z1013_16:
	case Z1013.SYSNAME_Z1013_64:
	  rv = Z1013.getDefaultSpeedKHz( props );
	  break;
	case Z9001.SYSNAME_KC85_1:
	case Z9001.SYSNAME_KC87:
	case Z9001.SYSNAME_Z9001:
	  rv = Z9001.getDefaultSpeedKHz();
	  break;
	case ZXSpectrum.SYSNAME:
	  rv = ZXSpectrum.getDefaultSpeedKHz( props );
	  break;
	case CustomSys.SYSNAME:
	  rv = CustomSys.getDefaultSpeedKHz( props );
	  break;
      }
    }
    return rv;
  }


  public EmuSys getEmuSys()
  {
    return this.emuSys;
  }


  public Boolean getISO646DE()
  {
    return this.iso646de;
  }


  public PrintMngr getPrintMngr()
  {
    return this.printMngr;
  }


  public int getRAMByte( int addr )
  {
    return (int) this.ram[ addr & 0xFFFF ] & 0xFF;
  }


  public RAMFloppy getRAMFloppy1()
  {
    return this.ramFloppy1;
  }


  public RAMFloppy getRAMFloppy2()
  {
    return this.ramFloppy2;
  }


  public ScreenFrm getScreenFrm()
  {
    return this.screenFrm;
  }


  public Z80CPU getZ80CPU()
  {
    return this.z80cpu;
  }


  public void initDRAM()
  {
    EmuUtil.initDRAM( this.ram );
  }


  public void initSRAM( Properties props )
  {
    EmuUtil.initSRAM( this.ram, props );
  }


  public void joystickThreadTerminated( JoystickThread t )
  {
    synchronized( this.joyThreads ) {
      for( int i = 0; i < this.joyThreads.length; i++ ) {
	if( this.joyThreads[ i ] == t ) {
	  this.joyThreads[ i ] = null;
	  updJoystickFrm( i );
	}
      }
    }
  }


  public boolean keyPressed( KeyEvent e )
  {
    return this.emuSys.keyPressed(
				e.getKeyCode(),
				e.isControlDown(),
				e.isShiftDown() );
  }


  public void keyReleased()
  {
    this.emuSys.keyReleased();
  }


  public void keyTyped( char ch )
  {
    if( emuSys.getSwapKeyCharCase() ) {
      ch = TextUtil.toReverseCase( ch );
    }
    if( this.emuSys.getConvertKeyCharToISO646DE() ) {
      this.emuSys.keyTyped( TextUtil.toISO646DE( ch ) );
    } else {
      this.emuSys.keyTyped( ch );
    }
  }


  public void setBasicMemWord( int addr, int value )
  {
    this.emuSys.setBasicMemByte( addr, value & 0xFF );
    this.emuSys.setBasicMemByte( addr + 1, (value >> 8) & 0xFF );
  }


  public void setISO646DE( boolean state )
  {
    this.iso646de = state;
  }


  public void setJoystickAction( int joyNum, int actionMask )
  {
    this.emuSys.setJoystickAction( joyNum, actionMask );
    JoystickFrm joyFrm = this.joyFrm;
    if( joyFrm != null ) {
      joyFrm.setJoystickAction( joyNum, actionMask );
    }
  }


  public void setJoystickFrm( JoystickFrm joyFrm )
  {
    this.joyFrm = joyFrm;
    synchronized( this.joyThreads ) {
      for( int i = 0; i < this.joyThreads.length; i++ ) {
	updJoystickFrm( i );
      }
    }
  }


  public void setMemWord( int addr, int value )
  {
    setMemByte( addr, value & 0xFF );
    setMemByte( addr + 1, (value >> 8) & 0xFF );
  }


  public void setRAMByte( int addr, int value )
  {
    addr &= 0xFFFF;
    this.ram[ addr ] = (byte) value;
  }


  public void updCPUSpeed( Properties props )
  {
    int    maxSpeedKHz  = getDefaultSpeedKHz( props );
    String maxSpeedText = EmuUtil.getProperty(
				props,
				PROP_MAXSPEED_KHZ );
    if( maxSpeedText.equals( VALUE_MAXSPEED_KHZ_UNLIMITED ) ) {
      maxSpeedKHz = 0;
    } else {
      if( !maxSpeedText.equals( VALUE_MAXSPEED_KHZ_DEFAULT ) ) {
	if( !maxSpeedText.isEmpty() ) {
	  try {
	    int value = Integer.parseInt( maxSpeedText );
	    if( value > 0 ) {
	      maxSpeedKHz = value;
	    }
	  }
	  catch( NumberFormatException ex ) {}
	}
      }
    }
    this.z80cpu.setMaxSpeedKHz( maxSpeedKHz );
  }


	/* --- Empfang von Signalen aus einen anderen Thread --- */

  public void fireReset( boolean powerOn )
  {
    fireReset( powerOn, null );
  }


  /*
   * Diese Methode laedt Daten in den Arbeitsspeicher und startet
   * diese bei Bedarf.
   * Sind die Datenbytes als Programm zu starten, so werden sie
   * in den Emulations-Thread ueberfuehrt und dort geladen und gestartet.
   * Anderenfalls erfolgt das Laden sofort.
   *
   * Um die Programmausfuehrung an einer bestimmten Stelle fortzusetzen
   * (Programmstart), wird die CPU-Emulation zurueckgesetzt.
   * Dadurch wird ein definierter Startzustand und ggf. das Aufwecken
   * des CPU-Emulations-Threads aus dem Wartezustand sichergestellt.
   */
  public void loadIntoMemory(
			LoadData      loadData,
			StringBuilder rvStatusMsg )
  {
    if( !this.z80cpu.isPause() && (loadData.getStartAddr() >= 0) ) {
      synchronized( this.monitor ) {
	this.loadData = loadData;
	interrupt();
	this.z80cpu.fireExit();
      }
    } else {
      loadData.loadIntoMemory( this, rvStatusMsg );
    }
  }


  public void stopEmulator()
  {
    try {
      for( int i = 0; i < this.joyThreads.length; i++ ) {
	JoystickThread jt = this.joyThreads[ i ];
	if( jt != null ) {
	  jt.fireStop();
	}
      }
      synchronized( this ) {
	for( AudioIO audioIO : new AudioIO[] {
					this.emuSys.getTapeIn(),
					this.emuSys.getTapeOut() } )
	{
	  if( audioIO != null ) {
	    audioIO.fireStop();
	  }
	}
	for( AbstractSoundDevice sndDev : emuSys.getSoundDevices() ) {
	  sndDev.fireStop();
	}
      }
      this.emuRunning = false;
      interrupt();
      this.z80cpu.fireExit();
      Thread.sleep( 100 );
    }
    catch( InterruptedException ex ) {}
  }


	/* --- EmuMemView --- */

  @Override
  public int getBasicMemByte( int addr )
  {
    return this.emuSys.getBasicMemByte( addr & 0xFFFF );
  }


	/* --- Z80IOSystem --- */

  @Override
  public int readIOByte( int port, int tStates )
  {
    return this.emuSys.readIOByte( port, tStates );
  }


  @Override
  public void writeIOByte( int port, int value, int tStates )
  {
    this.emuSys.writeIOByte( port, value, tStates );
  }


	/* --- Z80Memory --- */

  @Override
  public int getMemByte( int addr, boolean m1 )
  {
    return this.emuSys.getMemByte( addr & 0xFFFF, m1 );
  }


  @Override
  public int getMemWord( int addr )
  {
    return (getMemByte( addr + 1, false ) << 8) | getMemByte( addr, false );
  }


  @Override
  public int readMemByte( int addr, boolean m1 )
  {
    return this.emuSys.readMemByte( addr & 0xFFFF, m1 );
  }


  @Override
  public boolean setMemByte( int addr, int value )
  {
    return this.emuSys.setMemByte( addr & 0xFFFF, value );
  }


  @Override
  public void writeMemByte( int addr, int value )
  {
    this.emuSys.writeMemByte( addr & 0xFFFF, value );
  }


	/* --- ueberschriebene Methoden fuer Thread --- */

  @Override
  public void run()
  {
    boolean     emuSysChanged = true;
    boolean     resetFired    = false;
    boolean     enableTapeIn  = false;
    boolean     enableTapeOut = false;
    Set<String> enableSDNames = new TreeSet<>();

    try {
      this.emuRunning = true;
      while( this.emuRunning ) {

	/*
	 * Pruefen, ob ein Programm geladen oder der Emulator
	 * tatsaechlich zurueckgesetzt werden soll
	 */
	LoadData loadData = null;
	boolean  powerOn  = false;
	synchronized( this.monitor ) {
	  loadData = this.loadData;
	  powerOn  = this.powerOn;
	  if( loadData != null ) {
	    this.loadData = null;
	  } else {
	    this.powerOn = false;
	  }
	}
	if( loadData != null ) {

	  // nur Daten laden
	  loadData.loadIntoMemory( this, null );
	  this.z80cpu.setRegPC( loadData.getStartAddr() );
	  int spInitValue = this.emuSys.getAppStartStackInitValue();
	  if( spInitValue > 0 ) {
	    this.z80cpu.setRegSP( spInitValue );
	  }

	} else {

	  // echtes RESET
	  resetFired = true;
	  this.z80cpu.reset( powerOn );

	  // neues EmuSys?
	  Properties props = null;
	  synchronized( this.monitor ) {
	    props = this.newProps;
	    if( props != null ) {
	      this.emuSys.die();
	      this.emuSys   = createEmuSys( props );
	      emuSysChanged = true;
	    } else {
	      props = Main.getProperties();
	    }
	  }

	  // ROMs laden
	  if( emuSysChanged
	      || EmuUtil.getBooleanProperty(
				props,
				PROP_EXT_ROM_RELOAD_ON_POWER_ON,
				DEFAULT_EXT_ROM_RELOAD_ON_POWER_ON ) )
	  {
	    this.emuSys.loadROMs( props );
	  }

	  // RAM-Floppies und Druckmanager zuruecksetzen
	  this.printMngr.reset();
	  this.ramFloppy1.reset();
	  this.ramFloppy2.reset();
	  if( powerOn && EmuUtil.getBooleanProperty(
				props,
				PROP_RF_CLEAR_ON_POWER_ON,
				DEFAULT_RF_CLEAR_ON_POWER_ON ) )
	  {
	    if( emuSys.supportsRAMFloppy1()
		&& (this.ramFloppy1.getUsedSize() > 0) )
	    {
	      this.ramFloppy1.clear();
	    }
	    if( emuSys.supportsRAMFloppy2()
		&& (this.ramFloppy2.getUsedSize() > 0) )
	    {
	      this.ramFloppy2.clear();
	    }
	  }

	  // weitere Anpassungen bei neuem EmuSys
	  if( emuSysChanged ) {
	    this.iso646de = null;

	    // Joysticks
	    int nJoys = emuSys.getSupportedJoystickCount();
	    for( int i = 0; i < this.joyThreads.length; i++ ) {
	      JoystickThread jt = null;
	      synchronized( this.joyThreads ) {
		jt = this.joyThreads[ i ];
		if( jt != null ) {
		  if( i >= nJoys ) {
		    jt.fireStop();
		    this.joyThreads[ i ] = null;
		  }
		  jt = null;
		} else {
		  if( i < nJoys ) {
		    jt = new JoystickThread( this, i, false );
		    this.joyThreads[ i ] = jt;
		  }
		}
	      }
	      if( jt != null ) {
		jt.start();
	      }
	      updJoystickFrm( i );
	    }
	  }

	  // CPU-Geschwindigkeit
	  updCPUSpeed( props );

	  // EmuSys zuruecksetzen
	  this.emuSys.reset( powerOn, props );
	  this.z80cpu.setRegPC(
		this.emuSys.getResetStartAddress( powerOn ) );

	  // Fenster informieren
	  if( emuSysChanged ) {
	    fireCallResetFired( this.emuSys, props );
	  } else if( resetFired ) {
	    fireCallResetFired( null, null );
	  }

	  // AutoLoader und AutoInputWorker starten
	  boolean autoLoadInput = (loadData == null);
	  if( !powerOn && (emuSys != null) ) {
	    autoLoadInput = emuSys.getAutoLoadInputOnSoftReset();
	  }
	  if( autoLoadInput ) {
	    AutoLoader.start( this, Main.getProperties() );
	    AutoInputWorker.start( this, Main.getProperties() );
	  }
	}

	// Audio-Kanaele oeffnen und Anforderungen zuruecksetzen
	Set<String> sdNames = new TreeSet<>();
	sdNames.addAll( enableSDNames );
	AudioFrm.lazyFireEnableAudio( enableTapeIn, enableTapeOut, sdNames );

	enableTapeIn  = false;
	enableTapeOut = false;
	enableSDNames.clear();

	// in die Z80-Emulation verzweigen
	this.z80cpu.run();

	// Audio-Kanaele schliessen
	AudioIO tapeIn = this.emuSys.getTapeIn();
	if( tapeIn != null ) {
	  tapeIn.closeLine();
	  enableTapeIn = true;
	}
	AudioIO tapeOut = this.emuSys.getTapeOut();
	if( tapeOut != null ) {
	  tapeOut.closeLine();
	  enableTapeOut = true;
	}
	for( AbstractSoundDevice sndDev : this.emuSys.getSoundDevices() ) {
	  AudioIO audioOut = sndDev.getAudioOut();
	  if( audioOut != null ) {
	    enableSDNames.add( sndDev.toString() );
	  }
	}
	emuSysChanged = false;
      }
      this.emuSys.die();
    }
    catch( Exception ex ) {
      EmuUtil.fireExitSysError( this.screenFrm, null, ex );
    }
  }


	/* --- private Methoden --- */

  private void closeAudioLine( AudioIO audioIO )
  {
    if( audioIO != null ) {
      audioIO.fireStop();
      if( Thread.currentThread() == this ) {
	audioIO.closeLine();
      }
    }
  }


  private EmuSys createEmuSys( Properties props )
  {
    EmuSys emuSys = null;
    switch( EmuUtil.getProperty( props, PROP_SYSNAME ) ) {
      case AC1.SYSNAME:
	emuSys = new AC1( this, props );
	break;
      case BCS3.SYSNAME:
	emuSys = new BCS3( this, props );
	break;
      case C80.SYSNAME:
	emuSys = new C80( this, props );
	break;
      case HueblerEvertMC.SYSNAME:
	emuSys = new HueblerEvertMC( this, props );
	break;
      case HueblerGraphicsMC.SYSNAME:
	emuSys = new HueblerGraphicsMC( this, props );
	break;
      case Z9001.SYSNAME_KC85_1:
      case Z9001.SYSNAME_KC87:
      case Z9001.SYSNAME_Z9001:
	emuSys = new Z9001( this, props );
	break;
      case KC85.SYSNAME_HC900:
      case KC85.SYSNAME_KC85_2:
      case KC85.SYSNAME_KC85_3:
      case KC85.SYSNAME_KC85_4:
      case KC85.SYSNAME_KC85_5:
	emuSys = new KC85( this, props );
	break;
      case KCcompact.SYSNAME:
	emuSys = new KCcompact( this, props );
	break;
      case KramerMC.SYSNAME:
	emuSys = new KramerMC( this, props );
	break;
      case LC80.SYSNAME_LC80_U505:
      case LC80.SYSNAME_LC80_2716:
      case LC80.SYSNAME_LC80_2:
      case LC80.SYSNAME_LC80_E:
      case LC80.SYSNAME_LC80_EX:
	emuSys = new LC80( this, props );
	break;
      case LLC1.SYSNAME:
	emuSys = new LLC1( this, props );
	break;
      case LLC2.SYSNAME:
	emuSys = new LLC2( this, props );
	break;
      case NANOS.SYSNAME:
	emuSys = new NANOS( this, props );
	break;
      case PCM.SYSNAME:
	emuSys = new PCM( this, props );
	break;
      case Poly880.SYSNAME:
	emuSys = new Poly880( this, props );
	break;
      case SC2.SYSNAME:
	emuSys = new SC2( this, props );
	break;
      case SLC1.SYSNAME:
	emuSys = new SLC1( this, props );
	break;
      case VCS80.SYSNAME:
	emuSys = new VCS80( this, props );
	break;
      case Z1013.SYSNAME_Z1013_01:
      case Z1013.SYSNAME_Z1013_12:
      case Z1013.SYSNAME_Z1013_16:
      case Z1013.SYSNAME_Z1013_64:
	emuSys = new Z1013( this, props );
	break;
      case ZXSpectrum.SYSNAME:
	emuSys = new ZXSpectrum( this, props );
	break;
      case CustomSys.SYSNAME:
	emuSys = new CustomSys( this, props );
	break;
    }
    if( emuSys == null ) {
      emuSys = new A5105( this, props );
    }
    return emuSys;
  }


  /*
   * Die Methode fireReset besagt, dass der Emulations-Thread
   * zurueckgesetzt werden soll.
   * Dazu muss dieser ggf. aufgeweckt werden,
   * was durch Z80CPU.fireReset() geschieht.
   * Das eigentliche Zuruecksetzen der CPU und der Peripherie geschieht
   * im Emulations-Thread.
   */
  private void fireReset( boolean powerOn, Properties newProps )
  {
    this.screenFrm.clearScreenSelection();
    this.emuSys.cancelPastingText();
    synchronized( this.monitor ) {
      this.newProps = newProps;
      this.powerOn  = powerOn;
      this.loadData = null;
    }
    this.z80cpu.fireExit();
  }


  private void fireCallResetFired(
			final EmuSys     newEmuSys,
			final Properties newProps )
  {
    final Frame[] frames = Frame.getFrames();
    EventQueue.invokeLater(
		new Runnable()
		{
		  @Override
		  public void run()
		  {
		    callResetFired( newEmuSys, newProps );
		  }
		} );
  }


  private void callResetFired(
			EmuSys     newEmuSys,
			Properties newProps )
  {
    for( Frame f : Frame.getFrames() ) {
      if( f instanceof BaseFrm ) {
	((BaseFrm) f).resetFired( newEmuSys, newProps );
      }
    }
  }


  private void updJoystickFrm( final int joyNum )
  {
    final JoystickFrm joyFrm = this.joyFrm;
    if( (joyFrm != null)
	&& (joyNum >= 0)
	&& (joyNum < this.joyThreads.length) )
    {
      int           nJoys     = emuSys.getSupportedJoystickCount();
      final boolean emulated  = (joyNum < nJoys);
      final boolean connected = (this.joyThreads[ joyNum ] != null);
      EventQueue.invokeLater(
		new Runnable()
		{
		  @Override
		  public void run()
		  {
		    joyFrm.setJoystickState( joyNum, emulated, connected );
		  }
		} );
    }
  }
}
