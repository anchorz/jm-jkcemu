/*
 * (c) 2008-2021 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Emulation des Z9001 und Nachfolger (KC85/1, KC87)
 */

package jkcemu.emusys;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import jkcemu.audio.AbstractSoundDevice;
import jkcemu.base.AutoInputCharSet;
import jkcemu.base.CharRaster;
import jkcemu.base.EmuSys;
import jkcemu.base.EmuThread;
import jkcemu.base.EmuUtil;
import jkcemu.base.RAMFloppy;
import jkcemu.base.SourceUtil;
import jkcemu.disk.FDC8272;
import jkcemu.disk.FloppyDiskDrive;
import jkcemu.disk.FloppyDiskFormat;
import jkcemu.disk.FloppyDiskInfo;
import jkcemu.disk.GIDE;
import jkcemu.emusys.z9001.Z9001KeyboardFld;
import jkcemu.etc.CPUSynchronSoundDevice;
import jkcemu.etc.K1520Sound;
import jkcemu.etc.Plotter;
import jkcemu.etc.PSG8910;
import jkcemu.etc.RTC7242X;
import jkcemu.file.FileFormat;
import jkcemu.joystick.JoystickThread;
import jkcemu.net.KCNet;
import jkcemu.text.TextUtil;
import jkcemu.usb.VDIP;
import z80emu.Z80CPU;
import z80emu.Z80CTC;
import z80emu.Z80CTCListener;
import z80emu.Z80InterruptSource;
import z80emu.Z80MaxSpeedListener;
import z80emu.Z80MemView;
import z80emu.Z80PCListener;
import z80emu.Z80PIO;
import z80emu.Z80PIOPortListener;
import z80emu.Z80SIO;
import z80emu.Z80SIOChannelListener;


public class Z9001 extends EmuSys implements
					ActionListener,
					FDC8272.DriveSelector,
					Z80CTCListener,
					Z80MaxSpeedListener,
					Z80PCListener,
					Z80PIOPortListener,
					Z80SIOChannelListener
{
  public static final String SYSNAME_KC85_1 = "KC85_1";
  public static final String SYSNAME_KC87   = "KC87";
  public static final String SYSNAME_Z9001  = "Z9001";

  public static final String SYSTEXT_KC85_1 = "KC85/1";

  public static final String PROP_PREFIX_KC85_1 = "jkcemu.kc85_1.";
  public static final String PROP_PREFIX_KC87   = "jkcemu.kc87.";
  public static final String PROP_PREFIX_Z9001  = "jkcemu.z9001.";

  public static final String PROP_80CHARS_ENABLED   = "80_chars.enabled";
  public static final String PROP_FONT_PROGRAMMABLE = "font.programmable";
  public static final String PROP_GRAPHIC_TYPE      = "graphic.type";
  public static final String PROP_PLOTTER_ENABLED   = "plotter.enabled";
  public static final String PROP_PRINTER_MOD_ENABLED
						= "printer_module.enabled";

  public static final String PROP_RAM16K4000_ENABLED = "ram_16k_4000.enabled";
  public static final String PROP_RAM64K_ENABLED     = "ram_64k.enabled";
  public static final String PROP_RAM16K8000_ENABLED = "ram_16k_8000.enabled";

  public static final String PROP_ROM_MOD_PREFIX     = "rom_module.";
  public static final String PROP_ROM10KC000_ENABLED = "rom_10k_c000.enabled";
  public static final String PROP_ROM16K4000_ENABLED = "rom_16k_4000.enabled";
  public static final String PROP_ROM16K8000_ENABLED = "rom_16k_8000.enabled";
  public static final String PROP_ROM32K4000_ENABLED = "rom_32k_4000.enabled";
  public static final String PROP_ROM64K_ENABLED     = "rom_64k.enabled";
  public static final String PROP_ROMBOOT_ENABLED    = "rom_boot.enabled";
  public static final String PROP_ROMMEGA_ENABLED    = "rom_mega.enabled";

  public static final String VALUE_GRAPHIC_KRT      = "krt";
  public static final String VALUE_GRAPHIC_ROBOTRON = "robotron";

  public static final int     DEFAULT_PROMPT_AFTER_RESET_MILLIS_MAX = 2500;
  public static final boolean DEFAULT_SWAP_KEY_CHAR_CASE            = true;

  public static final String[] basicTokens = {
    "END",       "FOR",      "NEXT",    "DATA",		// 0x80
    "INPUT",     "DIM",      "READ",    "LET",
    "GOTO",      "RUN",      "IF",      "RESTORE",
    "GOSUB",     "RETURN",   "REM",     "STOP",
    "OUT",       "ON",       "NULL",    "WAIT",		// 0x90
    "DEF",       "POKE",     "DOKE",    "AUTO",
    "LINES",     "CLS",      "WIDTH",   "BYE",
    "!",         "CALL",     "PRINT",   "CONT",
    "LIST",      "CLEAR",    "CLOAD",   "CSAVE",	// 0xA0
    "NEW",       "TAB(",     "TO",      "FN",
    "SPC(",      "THEN",     "NOT",     "STEP",
    "+",         "-",        "*",       "/",
    "^",         "AND",      "OR",      ">",		// 0xB0
    "=",         "<",        "SGN",     "INT",
    "ABS",       "USR",      "FRE",     "INP",
    "POS",       "SQR",      "RND",     "LN",
    "EXP",       "COS",      "SIN",     "TAN",		// 0xC0
    "ATN",       "PEEK",     "DEEK",    "PI",
    "LEN",       "STR$",     "VAL",     "ASC",
    "CHR$",      "LEFT$",    "RIGHT$",  "MID$",
    "LOAD",      "TRON",     "TROFF",   "EDIT",		// 0xD0
    "ELSE",      "INKEY$",   "JOYST",   "STRING$",
    "INSTR",     "RENUMBER", "DELETE",  "PAUSE",
    "BEEP",      "WINDOW",   "BORDER",  "INK",
    "PAPER",     "AT",       "PSET",    "LINE",		// 0xE0
    "CIRCLE",    "!",        "PAINT",   "LABEL",
    "SIZE",      "ZERO",     "HOME",    "!",
    "GCLS",      "SCALE",    "SCREEN",  "POINT",
    "XPOS",      "!",        "YPOS" };			// 0xF0

  private static final String[] biosCallNames = {
			"INIT",  "WBOOT", "CONST", "CONIN",
			"COOUT", "LIST",  "PUNCH", "READER",
			"GSTIK", "BOSER", "STIME", "GTIME",
			"SDMA",  "READ",  "WRITE", "LLIST",
			"GCURS", "SCURS", "BOSER", "GIOBY",
			"SIOBY", "GMEM",  "SMEM" };

  private static final int basicRGBValues[][] = {
				{ 0,   0,   0 },
				{ 255, 0,   0 },
				{ 0,   255, 0 },
				{ 255, 255, 0 },
				{ 0,   0,   255 },
				{ 255, 0,   255 },
				{ 0,   255, 255 },
				{ 255, 255, 255 } };

  private static final int[][] kbMatrixNormal = {
		{ '0', '1', '2', '3', '4', '5', '6', '7' },
		{ '8', '9', ':', ';', ',', '=', '.', '?' },
		{ '@', 'A', 'B',  'C', 'D', 'E', 'F', 'G'  },
		{ 'H', 'I', 'J',  'K', 'L', 'M', 'N', 'O'  },
		{ 'P', 'Q', 'R',  'S', 'T', 'U', 'V', 'W'  },
		{ 'X', 'Y', 'Z',  0,   0,   0,   '^', 0    } };

  private static final int[][] kbMatrixShift = {
		{ '_', '!', '\"', '#', '$', '%', '&', '\'' },
		{ '(', ')', '*',  '+', '<', '-', '>', '/'  },
		{ 0,   'a', 'b', 'c', 'd', 'e', 'f', 'g' },
		{ 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o' },
		{ 'p', 'q', 'r', 's', 't', 'u', 'v', 'w' },
		{ 'x', 'y', 'z', 0,   0,   0  , 0, 0   } };

  // Mapping der Zeichen ab Code 128
  private static final int[] char128ToUnicode = {
		      -1,       -1,       -1,       -1,		// 128
		      -1,       -1,       -1,       -1,		// 132
		      -1,       -1, '\u25C7',       -1,		// 136
		'\u25EF',       -1, '\u25E4', '\u25E3',		// 140
		'\u2571', '\u2572',       -1,       -1,		// 144
		      -1,       -1,       -1,       -1,		// 148
		      -1,       -1,       -1,       -1,		// 152
		      -1,       -1,       -1,       -1,		// 156
		'\u2501', '\u2503', '\u253B', '\u2523',		// 160
		'\u2533', '\u252B', '\u254B', '\u2517',		// 164
		'\u250F', '\u2513', '\u251B',       -1,		// 168
		      -1,       -1,       -1, '\u2573',		// 172
		'\u2598', '\u259D', '\u2597', '\u2596',		// 176
		'\u258C', '\u2590', '\u2580', '\u2584',		// 180
		'\u259A', '\u259E', '\u259F', '\u2599',		// 184
		'\u259B', '\u259C', '\u25E2', '\u25E5',		// 188
		'\u2595',       -1,       -1,       -1,		// 192
		'\u265F',       -1,       -1, '\u2592',		// 196
		      -1, '\u2666', '\u2663', '\u2665',		// 200
		'\u2660',       -1,       -1, '\u25CF',		// 204
		      -1,       -1,       -1,       -1,		// 208
		      -1,       -1,       -1,       -1,		// 212
		      -1,       -1,       -1,       -1,		// 216
		      -1,       -1,       -1,       -1,		// 220
		      -1,       -1,       -1,       -1,		// 224
		      -1,       -1,       -1,       -1,		// 228
		      -1,       -1,       -1,       -1,		// 232
		      -1,       -1,       -1,       -1,		// 236
		      -1,       -1,       -1,       -1,		// 240
		      -1,       -1,       -1,       -1,		// 244
		'\u2581', '\u2582', '\u2583', '\u2584',		// 248
		'\u2585', '\u2586', '\u2587', '\u2588' };	// 248

  /*
   * Hardwaremaessig bedingt koennen die Module den RETI-Befehl
   * nicht erkennen, wenn der Befehl in den unteren 16 KByte
   * des Arbeitsspeichers ausgefuehrt wird.
   + Um diesen Effekt zu emulieren, werden die Interrupt-Quellen
   * der Module mit einer Wrapper-Klasse gekapselt,
   * die die benoetigte Funktionalitaet bereitstellt.
   */
  protected class ModuleInterruptSource implements Z80InterruptSource
  {
    private Z80InterruptSource iSource;
    private Z80CPU             cpu;

    public ModuleInterruptSource(
			Z80CPU             cpu,
			Z80InterruptSource iSource )
    {
      this.cpu     = cpu;
      this.iSource = iSource;
    }

    @Override
    public void appendInterruptStatusHTMLTo( StringBuilder buf )
    {
      this.iSource.appendInterruptStatusHTMLTo( buf );
    }

    @Override
    public int interruptAccept()
    {
      return this.iSource.interruptAccept();
    }

    /*
     * Wenn der RETI-Befehl in den unteren 16 KByte RAM ausgefuehrt wird,
     * erkennen die Module diesen und somit das Interrupt-Ende nicht,
     * d.h., in der Emulation darf in dem Fall die Methode
     * "interruptFinish(...)"  nicht aufgerufen werden.
     */
    @Override
    public boolean interruptFinish( int addr )
    {
      boolean rv = false;
      if( (addr & 0xFFFF) >= 0x4000 ) {
	rv = this.iSource.interruptFinish( addr );
      }
      return rv;
    }

    @Override
    public boolean isInterruptAccepted()
    {
      return this.iSource.isInterruptAccepted();
    }

    @Override
    public boolean isInterruptRequested()
    {
      return this.iSource.isInterruptRequested();
    }

    @Override
    public String toString()
    {
      return this.iSource.toString();
    }
  };

  private static final int GRAPHIC_NONE     = 0;
  private static final int GRAPHIC_ROBOTRON = 1;
  private static final int GRAPHIC_KRT      = 2;
  private static final int PLOTTER_WIDTH    = 1800;
  private static final int PLOTTER_HEIGHT   = 2550;

  private static final FloppyDiskInfo[] availableFloppyDisks = {
		new FloppyDiskInfo(
			"/disks/z9001/z9001cpasys.dump.gz",
			"Z9001 CP/A Systemdiskette",
			0, 2048, true ) };

  private static AutoInputCharSet autoInputCharSet = null;

  private static byte[] os11            = null;
  private static byte[] os12            = null;
  private static byte[] os13            = null;
  private static byte[] basic86         = null;
  private static byte[] bootROMBytes    = null;
  private static byte[] megaROMBytes    = null;
  private static byte[] printerModBytes = null;
  private static byte[] kc87FontBytes   = null;
  private static byte[] z9001FontBytes  = null;

  private byte[]                 fontBytes;
  private byte[]                 romOS;
  private byte[]                 romBasic;
  private byte[]                 romBoot;
  private byte[]                 romMega;
  private byte[]                 rom16k4000;
  private byte[]                 rom32k4000;
  private byte[]                 rom16k8000;
  private byte[]                 rom10kC000;
  private byte[]                 ramFont;
  private byte[]                 ramColor;
  private byte[]                 ramColor2;
  private byte[]                 ramVideo;
  private byte[]                 ramVideo2;
  private byte[]                 ramPixel;
  private byte[]                 ramExt;
  private boolean                ram16k4000;
  private boolean                ram16k8000;
  private boolean                ram64k;
  private boolean                romModuleEnabled;
  private int                    megaROMSeg;
  private int                    fontOffs;
  private int                    graphType;
  private int                    graphBank;
  private int                    graphAddrL;
  private int                    graphBgColor;
  private int                    graphFgColor;
  private boolean                graphBorder;
  private boolean                graphMode;
  private boolean                graphicLED;
  private volatile boolean       fixedScreenSize;
  private boolean                kc87;
  private boolean                pasteFast;
  private boolean                plotterPenState;
  private boolean                plotterMoveState;
  private boolean                printerModule;
  private boolean                pcListenerAdded;
  private boolean                mode20Rows;
  private boolean                c80Active;
  private boolean                c80Enabled;
  private boolean                c80MemSwap;
  private boolean                colorSwap;
  private boolean                fdcReset;
  private boolean                fdcTC;
  private boolean                rf1ReadOnly;
  private boolean                rf2ReadOnly;
  private boolean                ram4000ExtEnabled;
  private boolean                ramC000Enabled;
  private boolean                ramFontActive;
  private boolean                ramFontEnabled;
  private int                    lineNum;
  private int                    lineTStates;
  private int                    tStatesPerLine;
  private int                    tStatesVisible;
  private int                    borderColorIdx;
  private int                    joy0ActionMask;
  private int                    joy1ActionMask;
  private int[]                  kbMatrix;
  private String                 sysName;
  private String                 romOSFile;
  private String                 romBasicFile;
  private String                 romModuleFile;
  private RAMFloppy              ramFloppy1;
  private RAMFloppy              ramFloppy2;
  private Z80PIO                 pio90;
  private Z80PIO                 pio88;
  private Z80CTC                 ctc80;
  private Z80CTC                 ctcA8;		// CTC im Druckermodul
  private Z80SIO                 sioB0;		// SIO im Druckermodul
  private FDC8272                fdc;
  private GIDE                   gide;
  private RTC7242X               rtc;
  private Plotter                plotter;
  private CPUSynchronSoundDevice loudspeaker;
  private K1520Sound             k1520Sound;
  private KCNet                  kcNet;
  private VDIP                   vdip;
  private Z9001KeyboardFld       keyboardFld;
  private javax.swing.Timer      blinkTimer;
  private Color[]                colors;
  private FloppyDiskDrive[]      floppyDiskDrives;


  public Z9001( EmuThread emuThread, Properties props )
  {
    super( emuThread, props, "" );
    this.romOS         = null;
    this.romOSFile     = null;
    this.romBasic      = null;
    this.romBasicFile  = null;
    this.rom16k4000    = null;
    this.rom32k4000    = null;
    this.rom16k8000    = null;
    this.rom10kC000    = null;
    this.romBoot       = null;
    this.romMega       = null;
    this.romModuleFile = null;
    this.ram16k4000    = false;
    this.ram16k8000    = false;
    this.ram64k        = false;
    this.graphicLED    = false;
    this.keyboardFld   = null;

    this.sysName = EmuUtil.getProperty( props, EmuThread.PROP_SYSNAME );
    switch( this.sysName ) {
      case SYSNAME_KC87:
	this.kc87       = true;
	this.propPrefix = PROP_PREFIX_KC87;
	break;
      case SYSNAME_KC85_1:
	this.kc87       = false;
	this.propPrefix = PROP_PREFIX_KC85_1;
	break;
      default:
	this.kc87       = false;
	this.propPrefix = PROP_PREFIX_Z9001;
    }
    this.loudspeaker = new CPUSynchronSoundDevice( "Lautsprecher" );

    if( emulatesFloppyDisk( props ) ) {
      this.floppyDiskDrives = new FloppyDiskDrive[ 2 ];
      Arrays.fill( this.floppyDiskDrives, null );
      this.fdc = new FDC8272( this, 4 );
    } else {
      this.floppyDiskDrives = null;
      this.fdc              = null;
    }
    if( emulatesPlotter( props ) ) {
      this.plotter = new Plotter();
      this.plotter.applySettings( props );
      this.plotter.setPageSize( PLOTTER_WIDTH, PLOTTER_HEIGHT );
    } else {
      this.plotter = null;
    }
    if( emulatesK1520Sound( props ) ) {
      this.k1520Sound = new K1520Sound( this, 0x38 );
    } else {
      this.k1520Sound = null;
    }
    if( emulatesKCNet( props ) ) {
      this.kcNet = new KCNet( "Netzwerk-PIO (E/A-Adressen C0h-C3h)" );
    } else {
      this.kcNet = null;
    }
    if( emulatesVDIP( props ) ) {
      this.vdip = new VDIP(
			0,
			this.emuThread.getZ80CPU(),
			"USB-PIO (E/A-Adressen DCh-DFh)" );
      this.vdip.applySettings( props );
    } else {
      this.vdip = null;
    }
    this.printerModule = emulatesPrinterModule( props );
    if( this.printerModule ) {
      if( printerModBytes == null ) {
	printerModBytes = readResource( "/rom/z9001/modprinter.bin" );
      }
    }
    this.gide = GIDE.getGIDE( this.screenFrm, props, this.propPrefix );
    this.rtc  = emulatesRTC( props ) ? new RTC7242X() : null;

    this.c80Active       = false;
    this.c80Enabled      = emulates80CharsMode( props );
    this.fixedScreenSize = isFixedScreenSize( props );
    this.ramVideo        = new byte[ 0x0400 ];
    this.ramVideo2       = (this.c80Enabled ? new byte[ 0x0400 ] : null);
    this.ram16k4000      = emulatesRAM16K4000( props );
    this.ram16k8000      = emulatesRAM16K8000( props );
    this.ram64k          = emulatesRAM64K( props );
    this.ramExt          = null;
    if( this.ram64k ) {
      this.ramExt = new byte[ 0x4000 ];
    }

    this.ramFloppy1 = RAMFloppy.prepare(
				this.emuThread.getRAMFloppy1(),
				this.sysName,
				RAMFloppy.RFType.ADW,
				"RAM-Floppy an E/A-Adressen 20h/21h",
				props,
				this.propPrefix + PROP_RF1_PREFIX );

    this.ramFloppy2 = RAMFloppy.prepare(
				this.emuThread.getRAMFloppy2(),
				this.sysName,
				RAMFloppy.RFType.ADW,
				"RAM-Floppy an E/A-Adressen 24h/25h",
				props,
				this.propPrefix + PROP_RF2_PREFIX );

    this.lineNum         = 0;
    this.lineTStates     = 0;
    this.pcListenerAdded = false;
    this.mode20Rows      = false;
    this.colorSwap       = false;
    this.borderColorIdx  = 0;
    this.colors          = new Color[ basicRGBValues.length ];
    createColors( props );
    applyPasteFast( props );

    this.kbMatrix = new int[ 8 ];

    Z80CPU                             cpu      = emuThread.getZ80CPU();
    java.util.List<Z80InterruptSource> iSources = new ArrayList<>();
    this.pio90 = new Z80PIO( "Tastatur-PIO (E/A-Adressen 90h-93h)" );
    this.pio88 = new Z80PIO( "System-PIO (E/A-Adressen 88h-8Bh)" );
    this.ctc80 = new Z80CTC( "System-CTC (E/A-Adressen 80h-83h)" );
    iSources.add( this.pio90 );
    iSources.add( this.pio88 );
    iSources.add( this.ctc80 );
    if( this.printerModule ) {
      this.ctcA8 = new Z80CTC( "Druckermodul-CTC (E/A-Adressen A8h-ABh)" );
      this.sioB0 = new Z80SIO( "Druckermodul-SIO (E/A-Adressen B0h-B3h)" );
      iSources.add( new ModuleInterruptSource( cpu, this.ctcA8 ) );
      iSources.add( new ModuleInterruptSource( cpu, this.sioB0 ) );
    } else {
      this.ctcA8 = null;
      this.sioB0 = null;
    }
    if( this.k1520Sound != null ) {
      iSources.add( new ModuleInterruptSource( cpu, this.k1520Sound ) );
    }
    if( this.kcNet != null ) {
      iSources.add( new ModuleInterruptSource( cpu, this.kcNet ) );
    }
    if( this.vdip != null ) {
      iSources.add( new ModuleInterruptSource( cpu, this.vdip ) );
    }
    try {
      cpu.setInterruptSources(
	iSources.toArray( new Z80InterruptSource[ iSources.size() ] ) );
    }
    catch( ArrayStoreException ex ) {}
    this.ctc80.setTimerConnection( 2, 3 );
    this.ctc80.addCTCListener( this );
    if( this.sioB0 != null ) {
      this.sioB0.addChannelListener( this, 0 );
    }
    if( this.ctcA8 != null ) {
      this.ctcA8.addCTCListener( this );
    }
    this.pio88.addPIOPortListener( this, Z80PIO.PortInfo.A );
    this.pio88.addPIOPortListener( this, Z80PIO.PortInfo.B );
    cpu.addMaxSpeedListener( this );
    cpu.addTStatesListener( this );
    checkAddPCListener( props );

    this.ramPixel  = null;
    this.graphType = getGraphicType( props );
    if( this.graphType == GRAPHIC_ROBOTRON ) {
      this.ramPixel = new byte[ 0x1800 ];
    } else if( this.graphType == GRAPHIC_KRT ) {
      this.ramPixel = new byte[ 0x2000 ];
    }

    if( getColorMode( props ) ) {
      this.ramColor   = new byte[ 0x0400 ];
      this.ramColor2  = (this.c80Enabled ? new byte[ 0x0400 ] : null);
      this.blinkTimer = new javax.swing.Timer( 200, this );
      this.blinkTimer.start();
    } else {
      this.ramColor   = null;
      this.ramColor2  = null;
      this.blinkTimer = null;
    }

    if( emulatesProgrammableFont( props ) ) {
      this.ramFont = new byte[ 0x0400 ];
    } else {
      this.ramFont = null;
    }

    z80MaxSpeedChanged( cpu );
  }


  public static AutoInputCharSet getAutoInputCharSet()
  {
    if( autoInputCharSet == null ) {
      autoInputCharSet = new AutoInputCharSet();
      autoInputCharSet.addAsciiChars();
      autoInputCharSet.addEnterChar();
      autoInputCharSet.addCursorChars();
      autoInputCharSet.addEscChar();
      autoInputCharSet.addKeyChar( 3, "STOP" );
      autoInputCharSet.addKeyChar( 26, "INS" );
      autoInputCharSet.addKeyChar( 31, "DEL" );
      autoInputCharSet.addSpecialChar(
			25,
			AutoInputCharSet.VIEW_BEG,
			"Zum Anfang" );
      autoInputCharSet.addSpecialChar(
			24,
			AutoInputCharSet.VIEW_END,
			"Zum Ende" );
    }
    return autoInputCharSet;
  }


  public boolean emulatesGraphicKRT()
  {
    return (this.graphType == GRAPHIC_KRT);
  }


  public static FloppyDiskInfo[] getAvailableFloppyDisks()
  {
    return availableFloppyDisks;
  }


  public static int getDefaultSpeedKHz()
  {
    return 2458;	// eigentlich 2,4576 MHz
  }


  public boolean getGraphicLED()
  {
    return this.graphicLED;
  }


  public static int toUnicode( int ch )
  {
    int rv = -1;
    if( ch >= 0 ) {
      if( (ch < 0x20) || (ch == 0x7F) ) {
	rv = '\u2588';
      } else if( (ch >= 0x20) && (ch < 0x7F) ) {
	rv = ch;
      } else {
	int idx = ch - 0x80;
	if( (idx >= 0) && (idx < char128ToUnicode.length) ) {
	  rv = char128ToUnicode[ idx ];
	}
      }
    }
    return rv;
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
      putKeyboardMatrixValuesToPorts();
    }
  }


	/* --- ActionListener --- */

  @Override
  public void actionPerformed( ActionEvent e )
  {
    if( (e.getSource() == this.blinkTimer)
	&& !this.emuThread.getZ80CPU().isPause() )
    {
      this.colorSwap = !this.colorSwap;
      this.screenFrm.setScreenDirty( true );
    }
  }


	/* --- FDC8272.DriveSelector --- */

  @Override
  public FloppyDiskDrive getFloppyDiskDrive( int driveNum )
  {
    FloppyDiskDrive rv = null;
    if( this.floppyDiskDrives != null ) {
      if( (driveNum >= 0) && (driveNum < this.floppyDiskDrives.length) ) {
	rv = this.floppyDiskDrives[ driveNum ];
      }
    }
    return rv;
  }


	/* --- Z80CTCListener --- */

  @Override
  public void z80CTCUpdate( Z80CTC ctc, int timerNum )
  {
    if( (ctc == this.ctc80) && (timerNum == 0) ) {
      this.tapeOutPhase = !this.tapeOutPhase;
      updLoudspeaker( this.pio88.fetchOutValuePortA( 0xFF ) );
    }
    else if( (ctc == this.ctcA8) && (timerNum == 0)
	     && (this.sioB0 != null) )
    {
      this.sioB0.clockPulseSenderA();
      this.sioB0.clockPulseReceiverA();
    }
  }


	/* --- Z80PCListener --- */

  @Override
  public void z80PCChanged( Z80CPU cpu, int pc )
  {
    if( (pc == 0x0005) && (cpu.getRegC() == 5) ) {
      this.emuThread.getPrintMngr().putByte( cpu.getRegE() );
      cpu.setFlagCarry( false );
      cpu.setRegPC( cpu.doPop() );
    }
  }


	/* --- Z80MaxSpeedListener --- */

  @Override
  public void z80MaxSpeedChanged( Z80CPU cpu )
  {
    int maxSpeedKHz = cpu.getMaxSpeedKHz();
    this.tStatesPerLine = maxSpeedKHz * 20 / 312;
    this.tStatesVisible = Math.round( this.tStatesPerLine / 2 );
    this.loudspeaker.z80MaxSpeedChanged( cpu );
    if( this.fdc != null ) {
      this.fdc.setTStatesPerMilli( maxSpeedKHz );
    }
    if( this.k1520Sound != null ) {
      this.k1520Sound.z80MaxSpeedChanged( cpu );
    }
    if( this.kcNet != null ) {
      this.kcNet.z80MaxSpeedChanged( cpu );
    }
  }


	/* --- Z80PIOPortListener --- */

  @Override
  public void z80PIOPortStatusChanged(
				Z80PIO          pio,
				Z80PIO.PortInfo port,
				Z80PIO.Status   status )
  {
    if( (pio == this.pio88)
	&& ((status == Z80PIO.Status.OUTPUT_AVAILABLE)
	    || (status == Z80PIO.Status.OUTPUT_CHANGED)) )
    {
      if( port == Z80PIO.PortInfo.A ) {
	int v = this.pio88.fetchOutValuePortA( 0xFF );
	updScreenConfig( v );
	updLoudspeaker( v );
	setGraphicLED( (v & 0x40) != 0 );
      }
      else if( (port == Z80PIO.PortInfo.B)
	       && (this.plotter != null) )
      {
	int     v = this.pio88.fetchOutValuePortB( 0xFF );
	boolean p = ((v & 0x80) != 0);
	boolean m = ((v & 0x04) != 0);
	if( p != this.plotterPenState ) {
	  this.plotterPenState = p;
	  this.plotter.setPenState( p );
	}
	if( m != this.plotterMoveState ) {
	  this.plotterMoveState = m;
	  if( m ) {
	    int d = ((v & 0x01) != 0 ? 1 : -1);
	    if( (v & 0x02) != 0 ) {
	      this.plotter.movePen( 0, d );
	    } else {
	      this.plotter.movePen( d, 0 );
	    }
	  }
	}
      }
    }
  }


	/* --- Z80SIOChannelListener --- */

  @Override
  public void z80SIOByteSent( Z80SIO sio, int channel, int value )
  {
    if( this.printerModule && (sio == this.sioB0) && (channel == 0) ) {
      this.emuThread.getPrintMngr().putByte( value );
      this.sioB0.setClearToSendA( false );
      this.sioB0.setClearToSendA( true );
    }
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public void appendStatusHTMLTo( StringBuilder buf, Z80CPU cpu )
  {
    buf.append( "<h1>" );
    EmuUtil.appendHTML( buf, getTitle() );
    buf.append( " Speicherkonfiguration</h1>\n"
        + "<table border=\"1\">\n"
	+ "<tr><td>F000h-FFFFh:</td><td>Betriebssystem-ROM</td></tr>\n"
	+ "<tr><td>EC00h-EFFFh:</td><td>" );
    if( (this.ramPixel != null)
	&& (this.graphType == GRAPHIC_KRT)
	&& this.graphMode )
    {
      buf.append( "KRT-Pixel-RAM, Segment " );
      buf.append( this.graphBank );
    } else {
      buf.append( "Text-BWS" );
      if( this.c80Enabled ) {
	buf.append( " Segment " );
	buf.append( this.c80MemSwap ? "1" : "0" );
      }
    }
    buf.append( "</td></tr>\n"
	+ "<tr><td>E800h-EC00h:</td><td>" );
    if( this.ramFontEnabled && (this.ramFont != null) ) {
      buf.append( "Zeichengenerator-RAM" );
    } else if( this.ramColor != null ) {
      buf.append( "Farbattribut-RAM" );
      if( this.c80Enabled ) {
	buf.append( " Segment " );
	buf.append( this.c80MemSwap ? "1" : "0" );
      }
    }
    buf.append( "</td></tr>\n"
	+ "<tr><td>C000h-E7FFh:</td><td>" );
    if( this.ram64k && this.ramC000Enabled ) {
      buf.append( "RAM" );
    } else if( this.romModuleEnabled && (this.romBoot != null) ) {
      buf.append( "Boot-ROM" );
    } else if( this.romModuleEnabled && (this.romMega != null) ) {
      buf.append( "Mega-ROM Segment " );
      buf.append( this.megaROMSeg );
    } else if( this.rom10kC000 != null ) {
      buf.append( "ROM-Modul" );
    } else if( this.kc87 && (this.romBasic != null) ) {
      buf.append( "BASIC-ROM" );
    }
    buf.append( "</td></tr>\n" );
    if( (this.rom16k8000 != null) || (this.rom32k4000 != null) ) {
      buf.append( "<tr><td>8000h-BFFFh:</td><td>ROM-Modul</td></tr>\n" );
    } else if( this.ram16k8000 || this.ram64k ) {
      buf.append( "<tr><td>8000h-BFFFh:</td><td>RAM</td></tr>\n" );
    } else if( this.printerModule && (printerModBytes != null) ) {
      buf.append( "<tr><td>B800h-BFFFh:</td>"
		+ "<td>Druckermodul-ROM</td></tr>\n"
		+ "<tr><td>8000h-B7FFh:</td><td></td></tr>\n" );
    } else {
      buf.append( "<tr><td>8000h-BFFFh:</td><td></td></tr>\n" );
    }
    buf.append( "<tr><td>4000h-7FFFh:</td><td>" );
    if( (this.rom16k4000 != null) || (this.rom32k4000 != null) ) {
      buf.append( "ROM-Modul" );
    } else if( this.ram64k ) {
      buf.append( "RAM Segment " );
      buf.append(
	this.ram4000ExtEnabled && (this.ramExt != null) ? "1" : "0" );
    }
    buf.append( "</td></tr>\n"
	+ "<tr><td>0000h-3FFFh:</td><td>RAM</td></tr>\n"
	+ "</table>\n"
	+ "<br/><br/>\n"
	+ "<h1>" );
    EmuUtil.appendHTML( buf, getTitle() );
    buf.append( " Status</h1>\n"
	+ "<table border=\"1\">\n"
	+ "<tr><td>Bildausgabe:</td><td>" );
    if( this.graphMode ) {
      if( this.graphType == GRAPHIC_ROBOTRON ) {
	buf.append( "ROBOTRON-Vollgrafikerweiterung" );
      } else if( this.graphType == GRAPHIC_KRT ) {
	buf.append( "KRT-Grafik" );
      }
    } else {
      buf.append( "Textmodus, " );
      if( this.c80Enabled ) {
	buf.append( this.c80Active ? "8" : "4" );
	buf.append( "0x2" );
	buf.append( this.mode20Rows ? "0" : "4" );
      } else {
	buf.append( this.mode20Rows ? "20" : "24" );
	buf.append( " Zeilen" );
      }
    }
  }


  @Override
  public void applySettings( Properties props )
  {
    super.applySettings( props );

    boolean state = isFixedScreenSize( props );
    if( state != this.fixedScreenSize ) {
      this.fixedScreenSize = state;
      this.screenFrm.fireScreenSizeChanged();
    }
    checkAddPCListener( props );
    createColors( props );
    loadFont( props );
    applyPasteFast( props );
    if( this.plotter != null ) {
      this.plotter.applySettings( props );
    }
    if( this.vdip != null ) {
      this.vdip.applySettings( props );
    }
  }


  @Override
  public boolean canApplySettings( Properties props )
  {
    boolean rv = EmuUtil.getProperty(
			props,
			EmuThread.PROP_SYSNAME ).equals( this.sysName );
    if( rv && (emulatesRAM16K4000( props ) != this.ram16k4000) ) {
      rv = false;
    }
    if( rv && (emulatesRAM16K8000( props ) != this.ram16k8000) ) {
      rv = false;
    }
    if( rv && (emulatesRAM64K( props ) != this.ram64k) ) {
      rv = false;
    }
    if( rv ) {
      rv = TextUtil.equals(
		this.romOSFile,
		EmuUtil.getProperty( props,  propPrefix + PROP_OS_FILE ) );
    }
    if( rv && this.kc87 ) {
      rv = TextUtil.equals(
		this.romBasicFile,
		EmuUtil.getProperty(
			props,
			propPrefix + PROP_BASIC_PREFIX + PROP_FILE ) );
    }
    if( rv ) {
      rv = equalsROMModule(
		this.rom16k4000,
		this.romModuleFile,
		props,
		this.propPrefix + PROP_ROM16K4000_ENABLED,
		this.propPrefix + PROP_ROM_MOD_PREFIX + PROP_FILE );
    }
    if( rv ) {
      rv = equalsROMModule(
		this.rom32k4000,
		this.romModuleFile,
		props,
		this.propPrefix + PROP_ROM32K4000_ENABLED,
		this.propPrefix + PROP_ROM_MOD_PREFIX + PROP_FILE );
    }
    if( rv ) {
      rv = equalsROMModule(
		this.rom16k8000,
		this.romModuleFile,
		props,
		this.propPrefix + PROP_ROM16K8000_ENABLED,
		this.propPrefix + PROP_ROM_MOD_PREFIX + PROP_FILE );
    }
    if( rv ) {
      rv = equalsROMModule(
		this.rom10kC000,
		this.romModuleFile,
		props,
		this.propPrefix + PROP_ROM10KC000_ENABLED,
		this.propPrefix + PROP_ROM_MOD_PREFIX + PROP_FILE );
    }
    if( rv ) {
      rv = equalsROMModule(
		this.romBoot,
		this.romModuleFile,
		props,
		this.propPrefix + PROP_ROMBOOT_ENABLED,
		this.propPrefix + PROP_ROM_MOD_PREFIX + PROP_FILE );
    }
    if( rv ) {
      rv = equalsROMModule(
		this.romMega,
		this.romModuleFile,
		props,
		this.propPrefix + Z9001.PROP_ROMMEGA_ENABLED,
		this.propPrefix + PROP_ROM_MOD_PREFIX + PROP_FILE );
    }
    if( rv && (emulatesFloppyDisk( props ) != (this.fdc != null)) ) {
      rv = false;
    }
    if( rv && (emulatesPrinterModule( props ) != this.printerModule) ) {
      rv = false;
    }
    if( rv && (emulatesPlotter( props ) != (this.plotter != null)) ) {
      rv = false;
    }
    if( rv && (emulatesK1520Sound( props ) != (this.k1520Sound != null)) ) {
      rv = false;
    }
    if( rv && (emulatesKCNet( props ) != (this.kcNet != null)) ) {
      rv = false;
    }
    if( rv && (emulatesVDIP( props ) != (this.vdip != null)) ) {
      rv = false;
    }
    if( rv && (emulatesRTC( props ) != (this.rtc != null)) ) {
      rv = false;
    }
    if( rv ) {
      rv = GIDE.complies( this.gide, props, this.propPrefix );
    }
    if( rv ) {
      rv = RAMFloppy.complies(
			this.ramFloppy1,
			this.sysName,
			RAMFloppy.RFType.ADW,
			props,
			this.propPrefix + PROP_RF1_PREFIX );
    }
    if( rv ) {
      rv = RAMFloppy.complies(
			this.ramFloppy2,
			this.sysName,
			RAMFloppy.RFType.ADW,
			props,
			this.propPrefix + PROP_RF2_PREFIX );
    }
    if( rv ) {
      if( getColorMode( props ) != (this.ramColor != null) ) {
	rv = false;
      }
    }
    if( rv ) {
      if( getGraphicType( props ) != this.graphType ) {
	rv = false;
      }
    }
    if( rv && (emulatesProgrammableFont( props ) != (this.ramFont != null)) ) {
      rv = false;
    }
    if( rv && (emulates80CharsMode( props ) != this.c80Enabled) ) {
      rv = false;
    }
    return rv;
  }


  @Override
  public boolean canExtractScreenText()
  {
    return !this.graphMode;
  }


  @Override
  public Z9001KeyboardFld createKeyboardFld()
  {
    this.keyboardFld = new Z9001KeyboardFld( this );
    return this.keyboardFld;
  }


  @Override
  public void die()
  {
    if( this.blinkTimer != null ) {
      this.blinkTimer.stop();
    }
    if( this.sioB0 != null ) {
      this.sioB0.removeChannelListener( this, 0 );
    }
    this.ctc80.removeCTCListener( this );
    if( this.ctcA8 != null ) {
      this.ctcA8.removeCTCListener( this );
    }

    this.pio88.removePIOPortListener( this, Z80PIO.PortInfo.A );
    this.pio88.removePIOPortListener( this, Z80PIO.PortInfo.B );

    Z80CPU cpu = this.emuThread.getZ80CPU();
    cpu.removeMaxSpeedListener( this );
    cpu.removeTStatesListener( this );
    cpu.setInterruptSources( (Z80InterruptSource[]) null );
    if( this.pcListenerAdded ) {
      cpu.removePCListener( this );
    }
    this.loudspeaker.fireStop();
    if( this.ramFloppy1 != null ) {
      this.ramFloppy1.deinstall();
    }
    if( this.ramFloppy2 != null ) {
      this.ramFloppy2.deinstall();
    }
    if( this.fdc != null ) {
      this.fdc.die();
    }
    if( this.plotter != null ) {
      this.plotter.die();
    }
    if( this.gide != null ) {
      this.gide.die();
    }
    if( this.k1520Sound != null ) {
      this.k1520Sound.die();
    }
    if( this.kcNet != null ) {
      this.kcNet.die();
    }
    if( this.vdip != null ) {
      this.vdip.die();
    }
    this.loudspeaker.fireStop();
    super.die();
  }


  @Override
  public int getBorderColorIndex()
  {
    return this.borderColorIdx;
  }


  @Override
  public Color getColor( int colorIdx )
  {
    Color color = Color.BLACK;
    if( this.ramColor != null ) {
      if( (colorIdx >= 0) && (colorIdx < colors.length) ) {
	color = colors[ colorIdx ];
      }
    } else {
      if( colorIdx > 0 ) {
	color = this.colorWhite;
      }
    }
    return color;
  }


  @Override
  public int getColorCount()
  {
    return this.ramColor != null ? 8 : 2;
  }


  @Override
  public int getColorIndex( int x, int y )
  {
    int rv = 0;
    if( this.fixedScreenSize || this.screenFrm.isFullScreenMode() ) {
      y /= 2;
    }
    if( this.graphMode
	&& (this.graphType == GRAPHIC_ROBOTRON)
	&& (this.ramPixel != null) )
    {
      boolean done = false;
      if( this.fixedScreenSize || this.screenFrm.isFullScreenMode() ) {
	x /= 2;
      }
      x -= 32;		// Grafikausgabe ueber Alpha-Ausgabe zentrieren
      if( (x >= 0) && (x < 256) ) {
	int col = x / 8;
	int idx = (y * 32) + col;
	if( (idx >= 0) && (idx < this.ramPixel.length) ) {
	  int m = 0x80;
	  int n = x % 8;
	  if( n > 0 ) {
	    m >>= n;
	  }
	  byte b = this.ramPixel[ idx ];
	  rv   = ((int) b & m) != 0 ? this.graphFgColor : this.graphBgColor;
	  done = true;
	}
      }
      if( !done && this.graphBorder ) {
	rv = this.borderColorIdx;
      }
    } else {
      int b    = 0;
      int col  = x / 8;
      byte[] vram = this.ramVideo;
      if( this.c80Active && (this.ramVideo2 != null) ) {
	if( (col & 0x01) != 0 ) {
	  vram = this.ramVideo2;
	}
	col /= 2;
      } else {
	if( this.fixedScreenSize || this.screenFrm.isFullScreenMode() ) {
	  x   /= 2;
	  col /= 2;
	}
      }
      int row  = 0;
      int yChr = 0;
      int rMax = 0;
      if( this.mode20Rows ) {
	row  = y / 9;
	yChr = y % 9;
	rMax = 20;
      } else {
	row  = y / 8;
	yChr = y % 8;
	rMax = 24;
      }
      if( (yChr < 8) && (row < rMax) ) {
	int offs = (row * 40) + col;
	if( this.graphMode
	    && (this.graphType == GRAPHIC_KRT)
	    && (this.ramPixel != null) )
	{
	  int idx = (yChr * 0x0400) + offs;
	  if( (idx >= 0) && (idx < this.ramPixel.length) ) {
	    b = this.ramPixel[ idx ];
	  }
	} else {
	  if( this.fontBytes != null ) {
	    if( (offs >= 0) && (offs < vram.length) ) {
	      int ch  = (int) vram[ offs ] & 0xFF;
	      int idx = (ch * 8) + yChr;
	      if( this.ramFontActive
		  && (this.ramFont != null)
		  && (ch >= 0x80) )
	      {
		idx -= 0x0400;
		if( (idx >= 0) && (idx < this.ramFont.length ) ) {
		  b = this.ramFont[ idx ];
		}
	      } else {
		idx += this.fontOffs;
		if( (idx >= 0) && (idx < this.fontBytes.length ) ) {
		  b = this.fontBytes[ idx ];
		}
	      }
	    }
	  }
	}
	int m = 0x80;
	int n = x % 8;
	if( n > 0 ) {
	  m >>= n;
	}
	if( (b & m) != 0 ) {
	  rv = 1;
	}
	if( this.ramColor != null ) {
	  byte[] cram = this.ramColor;
	  if( (this.ramColor2 != null) && (vram == this.ramVideo2) ) {
	    cram = this.ramColor2;
	  }
	  int colorInfo = 0;
	  if( (offs >= 0) && (offs < cram.length) ) {
	    colorInfo = (int) cram[ offs ] & 0xFF;
	  }
	  if( ((colorInfo & 0x80) != 0) && this.colorSwap ) {
	    rv = (rv != 0 ? 0 : 1);
	  }
	  if( rv != 0 ) {
	    rv = (colorInfo >> 4) & 0x07;
	  } else {
	    rv = colorInfo & 0x07;
	  }
	}
      } else {
	if( this.ramColor != null ) {
	  rv = this.borderColorIdx;
	}
      }
    }
    return rv;
  }


  @Override
  public CharRaster getCurScreenCharRaster()
  {
    CharRaster rv = null;
    if( !this.graphMode ) {
      int colCount   = (this.c80Active ? 80 : 40);
      int rowCount   = (this.mode20Rows ? 20 : 24);
      int rowHeight  = (this.mode20Rows ? 9 : 8);
      int charWidth  = 8;
      int charHeight = 8;
      if( this.fixedScreenSize || this.screenFrm.isFullScreenMode() ) {
	rowHeight *= 2;
	if( !this.c80Active ) {
	  charWidth *= 2;
	}
	charHeight *= 2;
      }
      rv = new CharRaster(
			colCount,
			rowCount,
			rowHeight,
			charWidth,
			charHeight );
    }
    return rv;
  }


  @Override
  public FloppyDiskFormat getDefaultFloppyDiskFormat()
  {
    return FloppyDiskFormat.FMT_800K_I4;
  }


  @Override
  protected long getDelayMillisAfterPasteChar()
  {
    return this.pasteFast ? 0 : 100;
  }


  @Override
  protected long getDelayMillisAfterPasteEnter()
  {
    return this.pasteFast ? 0 : 200;
  }


  @Override
  protected long getHoldMillisPasteChar()
  {
    return this.pasteFast ? 0 : 60;
  }


  @Override
  public String getHelpPage()
  {
    return "/help/z9001.htm";
  }


  @Override
  public int getMemByte( int addr, boolean m1 )
  {
    return getMemByteInternal( addr, false );
  }


  @Override
  public Plotter getPlotter()
  {
    return this.plotter;
  }


  @Override
  public int getResetStartAddress( boolean powerOn )
  {
    return 0xF000;
  }


  @Override
  protected int getScreenChar( CharRaster chRaster, int chX, int chY )
  {
    int ch  = -1;
    if( !this.graphMode ) {
      byte[] vram = this.ramVideo;
      if( this.c80Active && (this.ramVideo2 != null) ) {
	if( (chX & 0x01) != 0 ) {
	  vram = this.ramVideo2;
	}
	chX /= 2;
      }
      int idx = (chY * 40) + chX;
      if( (idx >= 0) && (idx < vram.length) ) {
	int b = (int) vram[ idx ] & 0xFF;
	if( this.fontOffs > 0 ) {
	  switch( b ) {
	    case 0x0B:			// Ae
	      ch = '\u00C4';
	      break;
	    case 0x0C:			// Oe
	      ch = '\u00D6';
	      break;
	    case 0x0D:			// Ue
	      ch = '\u00DC';
	      break;
	    case 0x1B:			// ae
	      ch = '\u00E4';
	      break;
	    case 0x1C:			// oe
	      ch = '\u00F6';
	      break;
	    case 0x1D:			// ue
	      ch = '\u00FC';
	      break;
	    case 0x1E:			// sz
	      ch = '\u00DF';
	      break;
	  }
	}
	if( ch < 0 ) {
	  if( (b >= 0x20) && (b < 0x7F) ) {
	    ch = b;
	  } else {
	    ch = toUnicode( b );
	  }
	}
      }
    }
    return ch;
  }


  @Override
  public int getScreenHeight()
  {
    int rv = 192;
    if( this.fixedScreenSize || this.screenFrm.isFullScreenMode() ) {
      rv *= 2;
    }
    return rv;
  }


  @Override
  public int getScreenWidth()
  {
    int rv = 320;
    if( this.fixedScreenSize || this.screenFrm.isFullScreenMode()
	|| (this.c80Active && !this.graphMode) )
    {
      rv *= 2;
    }
    return rv;
  }


  @Override
  public AbstractSoundDevice[] getSoundDevices()
  {
    return this.k1520Sound != null ?
		new AbstractSoundDevice[] {
				this.loudspeaker,
				this.k1520Sound.getSoundDevice() }
		: new AbstractSoundDevice[] { this.loudspeaker };
  }


  @Override
  public FloppyDiskInfo[] getSuitableFloppyDisks()
  {
    return this.fdc != null ? availableFloppyDisks : null;
  }


  @Override
  public int getSupportedFloppyDiskDriveCount()
  {
    return this.floppyDiskDrives != null ? this.floppyDiskDrives.length : 0;
  }


  @Override
  public int getSupportedJoystickCount()
  {
    return 2;
  }


  @Override
  public boolean getSwapKeyCharCase()
  {
    return DEFAULT_SWAP_KEY_CHAR_CASE;
  }


  @Override
  public String getTitle()
  {
    return this.sysName.equals( SYSNAME_KC85_1 ) ?
					SYSTEXT_KC85_1 : this.sysName;
  }


  @Override
  public VDIP[] getVDIPs()
  {
    return this.vdip != null ?
			new VDIP[] { this.vdip }
			: super.getVDIPs();
  }


  @Override
  public boolean hasKCBasicInROM()
  {
    return this.romBasic != null;
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
	case KeyEvent.VK_BACK_SPACE:
	  this.kbMatrix[ 0 ] = 0x40;
	  rv = true;
	  break;

	case KeyEvent.VK_LEFT:
	  if( ctrlDown ) {
	    this.kbMatrix[ 3 ] = 0x20;		// Zeilenanfang
	  } else {
	    if( shiftDown ) {
	      this.kbMatrix[ 0 ] = 0x80;	// Shift
	    }
	    this.kbMatrix[ 0 ] = 0x40;		// Links
	  }
	  rv = true;
	  break;

	case KeyEvent.VK_RIGHT:
	  if( ctrlDown ) {
	    this.kbMatrix[ 3 ] = 0x20;		// Zeilenende
	    this.kbMatrix[ 0 ] = 0x80;		// (Shift+Zeilenanfang)
	  } else {
	    if( shiftDown ) {
	      this.kbMatrix[ 0 ] = 0x80;	// Shift
	    }
	    this.kbMatrix[ 1 ] = 0x40;		// Rechts
	  }
	  rv = true;
	  break;

	case KeyEvent.VK_DOWN:
	  if( shiftDown ) {
  	    this.kbMatrix[ 0 ] = 0x80;		// Shift
	  }
	  this.kbMatrix[ 2 ] = 0x40;		// Runter
	  rv = true;
	  break;

	case KeyEvent.VK_UP:
	  if( shiftDown ) {
  	    this.kbMatrix[ 0 ] = 0x80;		// Shift
	  }
	  this.kbMatrix[ 3 ] = 0x40;		// Hoch
	  rv = true;
	  break;

	case KeyEvent.VK_ESCAPE:
	  if( shiftDown ) {
	    this.kbMatrix[ 0 ] = 0x80;		// CR LN (Shift+ESC)
	  }
	  this.kbMatrix[ 4 ] = 0x40;
	  rv = true;
	  break;

	case KeyEvent.VK_ENTER:
	  this.kbMatrix[ 5 ] = 0x40;
	  rv = true;
	  break;

	case KeyEvent.VK_SPACE:
	  this.kbMatrix[ 7 ] = 0x40;
	  rv = true;
	  break;

	case KeyEvent.VK_INSERT:
	  if( shiftDown ) {
	    this.kbMatrix[ 0 ] = 0x80;		// Shift
	  }
	  this.kbMatrix[ 5 ] = 0x20;
	  rv = true;
	  break;

	case KeyEvent.VK_DELETE:		// DELETE (Shift-INSERT)
	  this.kbMatrix[ 0 ] = 0x80;		// Shift
	  this.kbMatrix[ 5 ] = 0x20;		// INSERT
	  rv = true;
	  break;

	case KeyEvent.VK_F1:			// LIST
	  this.kbMatrix[ 4 ] = 0x80;
	  rv = true;
	  break;

	case KeyEvent.VK_F2:			// RUN
	  this.kbMatrix[ 5 ] = 0x80;
	  rv = true;
	  break;

	case KeyEvent.VK_F3:			// STOP
	  this.kbMatrix[ 6 ] = 0x40;
	  rv = true;
	  break;

	case KeyEvent.VK_PAUSE:			// PAUSE
	case KeyEvent.VK_F4:
	  this.kbMatrix[ 4 ] = 0x20;
	  rv = true;
	  break;

	case KeyEvent.VK_F5:			// CONT (Shift-PAUSE)
	  this.kbMatrix[ 0 ] = 0x80;		// Shift
	  this.kbMatrix[ 4 ] = 0x20;		// PAUSE
	  rv = true;
	  break;

	case KeyEvent.VK_F6:			// CR LN (Shift-ESC)
	  this.kbMatrix[ 0 ] = 0x80;		// Shift
	  this.kbMatrix[ 4 ] = 0x40;		// ESC
	  rv = true;
	  break;

	case KeyEvent.VK_F7:			// COLOR
	  this.kbMatrix[ 1 ] = 0x80;
	  rv = true;
	  break;

	case KeyEvent.VK_F8:			// GRAPHIC
	  this.kbMatrix[ 3 ] = 0x80;
	  rv = true;
	  break;
      }
    }
    if( rv ) {
      putKeyboardMatrixValuesToPorts();
      updKeyboardFld();
    }
    return rv;
  }


  @Override
  public void keyReleased()
  {
    synchronized( this.kbMatrix ) {
      Arrays.fill( this.kbMatrix, 0 );
    }
    putKeyboardMatrixValuesToPorts();
    updKeyboardFld();
  }


  @Override
  public boolean keyTyped( char ch )
  {
    boolean rv = false;
    synchronized( this.kbMatrix ) {
      if( (ch >= 1) && (ch <='\u0020') ) {
	if( setCharInKBMatrix( ch + 0x40, kbMatrixNormal ) ) {
	  this.kbMatrix[ 2 ] |= 0x80;		// Control
	  rv = true;
	}
	else if( setCharInKBMatrix( ch + 0x40, kbMatrixShift ) ) {
	  this.kbMatrix[ 2 ] |= 0x80;		// Control
	  rv = true;
	}
      } else {
	if( setCharInKBMatrix( ch, kbMatrixNormal ) ) {
	  rv = true;
	} else if( setCharInKBMatrix( ch, kbMatrixShift ) ) {
	  this.kbMatrix[ 0 ] |= 0x80;		// Shift
	  rv = true;
	}
      }
    }
    if( rv ) {
      putKeyboardMatrixValuesToPorts();
      updKeyboardFld();
    }
    return rv;
  }


  @Override
  public void loadROMs( Properties props )
  {
    this.romOSFile = EmuUtil.getProperty(
				props,
				this.propPrefix + PROP_OS_FILE );
    this.romOS = readROMFile( this.romOSFile, 0x1000, "Betriebssystem" );
    if( this.romOS == null ) {
      switch( this.sysName ) {
	case SYSNAME_KC87:
	  if( os13 == null ) {
	    os13 = readResource( "/rom/z9001/os13.bin" );
	  }
	  this.romOS = os13;
	  break;
	case SYSNAME_KC85_1:
	  if( os12 == null ) {
	    os12 = readResource( "/rom/z9001/os12.bin" );
	  }
	  this.romOS = os12;
	  break;
	default:
	  if( os11 == null ) {
	    os11 = readResource( "/rom/z9001/os11.bin" );
	  }
	  this.romOS = os11;
      }
    }

    // BASIC-ROM
    if( this.kc87 ) {
      this.romBasicFile = EmuUtil.getProperty(
			props,
			this.propPrefix + PROP_BASIC_PREFIX + PROP_FILE );
      this.romBasic = readROMFile( this.romBasicFile, 0x2800, "BASIC" );
      if( this.romBasic == null ) {
	if( basic86 == null ) {
	  basic86 = readResource( "/rom/z9001/basic86.bin" );
	}
	this.romBasic = basic86;
      }
    } else {
      this.romBasicFile = null;
      this.romBasic     = null;
    }

    // Modul-ROM
    this.romModuleFile = props.getProperty(
		      this.propPrefix + PROP_ROM_MOD_PREFIX + PROP_FILE );
    if( emulatesROM16K4000( props ) ) {
      this.rom16k4000 = readROMFile(
			this.romModuleFile,
			0x4000,
			"ROM-Modul 4000h-7FFFh" );
    }
    else if( emulatesROM32K4000( props ) ) {
      this.rom32k4000 = readROMFile(
			this.romModuleFile,
			0x8000,
			"ROM-Modul 4000h-BFFFh" );
    }
    else if( emulatesROM16K8000( props ) ) {
      this.rom16k8000 = readROMFile(
			this.romModuleFile,
			0x4000,
			"ROM-Modul 8000h-BFFFh" );
    }
    else if( emulatesROM10KC000( props ) ) {
      this.rom10kC000 = readROMFile(
			this.romModuleFile,
			0x4000,
			"ROM-Modul C000h-E7FFh" );
    }
    else if( emulatesBootROM( props ) ) {
      this.romBoot = readROMFile(
			this.romModuleFile,
			0x4000,
			"Boot-ROM-Modul" );
      if( this.romBoot == null ) {
	if( bootROMBytes == null ) {
	  bootROMBytes = readResource( "/rom/z9001/bootrom.bin" );
	}
	this.romBoot = bootROMBytes;
      }
    }
    else if( emulatesMegaROM( props ) ) {
      this.romMega = readROMFile(
			this.romModuleFile,
			256 * 10240,
			"Mega-ROM-Modul" );
      if( this.romMega == null ) {
	if( megaROMBytes == null ) {
	  megaROMBytes = readResource( "/rom/z9001/megarom.bin.gz" );
	}
	this.romMega = megaROMBytes;
      }
    }

    // Zeichensatz
    loadFont( props );
  }


  @Override
  public void openBasicProgram()
  {
    SourceUtil.openKCBasicProgram(
			this.screenFrm,
			this.kc87 ? 0x0401 : 0x2C01,
			basicTokens );
  }


  @Override
  protected boolean pasteChar( char ch ) throws InterruptedException
  {
    boolean rv = false;
    if( this.pasteFast ) {
      if( (ch > 0) && (ch <= 0xFF) ) {
	if( ch == '\n' ) {
	  ch = '\r';
	}
	while( getMemByte( 0x0025, false ) != 0 ) {
	  Thread.sleep( 10 );
	}
	setMemByte( 0x0024, ch );
	setMemByte( 0x0025, ch );
	rv = true;
      }
    } else {
      rv = super.pasteChar( ch );
    }
    return rv;
  }


  @Override
  public int readIOByte( int port, int tStates )
  {
    int rv = 0xFF;
    switch( port & 0xFF ) {
      case 4:
	this.ram4000ExtEnabled = false;
	break;

      case 5:
	if( this.ramExt != null ) {
	  this.ram4000ExtEnabled = true;
	}
	break;

      case 6:
	this.ramC000Enabled = false;
	break;

      case 7:
	if( this.ramExt != null ) {
	  this.ramC000Enabled = true;
	}
	break;

      case 0x10:
      case 0x98:
      case 0x9A:
      case 0x9C:
      case 0x9E:
	if( this.fdc != null ) {
	  rv = this.fdc.readMainStatusReg();
	}
	break;

      case 0x11:
      case 0x99:
      case 0x9B:
      case 0x9D:
      case 0x9F:
	if( this.fdc != null ) {
	  rv = this.fdc.readData();
	}
	break;

      case 0x12:	// Terminal Count bei Rossendorf Floppy Disk Modul
      case 0x13:
        if( this.fdc != null ) {
          this.fdc.fireTC();
        }
        break;

      case 0x20:
	if( this.ramFloppy1 != null ) {
	  rv = this.ramFloppy1.readByte( port );
	}
	break;

      case 0x24:
	if( this.ramFloppy2 != null ) {
	  rv = this.ramFloppy2.readByte( port );
	}
	break;

      case 0x80:				// A2=0
      case 0x81:
      case 0x82:
      case 0x83:
      case 0x84:				// A2=1
      case 0x85:
      case 0x86:
      case 0x87:
	rv = this.ctc80.read( port & 0x03, tStates );
	break;

      case 0x88:				// A2=0
      case 0x8C:				// A2=1
	rv = this.pio88.readDataA();
	break;

      case 0x89:				// A2=0
      case 0x8D:				// A2=1
	rv = this.pio88.readDataB();
	break;

      case 0x90:				// A2=0
      case 0x94:				// A2=1
	rv = this.pio90.readDataA();
	break;

      case 0x91:				// A2=0
      case 0x95:				// A2=1
	rv = this.pio90.readDataB();
	break;

      case 0xA8:				// A2=0
      case 0xA9:
      case 0xAA:
      case 0xAB:
      case 0xAC:				// A2=1
      case 0xAD:
      case 0xAE:
      case 0xAF:
	if( this.ctcA8 != null ) {
	  rv = this.ctcA8.read( port & 0x03, tStates );
	}
	break;

      case 0xB0:				// A2=0
      case 0xB4:				// A2=1
	if( this.sioB0 != null ) {
	  rv = this.sioB0.readDataA();
	}
	break;

      case 0xB1:				// A2=0
      case 0xB5:				// A2=1
	if( this.sioB0 != null ) {
	  rv = this.sioB0.readDataB();
	}
	break;

      case 0xB2:				// A2=0
      case 0xB6:				// A2=1
	if( this.sioB0 != null ) {
	  rv = this.sioB0.readControlA();
	}
	break;

      case 0xB3:				// A2=0
      case 0xB7:				// A2=1
	if( this.sioB0 != null ) {
	  rv = this.sioB0.readControlB();
	}
	break;

      case 0xB8:
	if( this.ramPixel != null ) {
	  if( this.graphType == GRAPHIC_ROBOTRON ) {
	    rv = this.graphBgColor | (this.graphFgColor << 4);
	    if( this.graphMode ) {
	      rv |= 0x08;
	    }
	    if( this.graphBorder ) {
	      rv |= 0x40;
	    }
	  } else if( this.graphType == GRAPHIC_KRT ) {
	    rv = this.graphBank;
	    if( this.graphMode ) {
	      rv |= 0x08;
	    }
	  }
	}
	break;

      case 0xBA:
	if( (this.ramPixel != null)
	    && (this.graphType == GRAPHIC_ROBOTRON) )
	{
	  int addr = (port & 0xFF00) | this.graphAddrL;
	  if( (addr >= 0) && (addr < this.ramPixel.length) ) {
	    rv = (int) this.ramPixel[ addr ] & 0xFF;
	  }
	}
	break;

      case 0xC0:
      case 0xC1:
      case 0xC2:
      case 0xC3:
	if( this.kcNet != null ) {
	  rv = this.kcNet.read( port );
	}
	break;

      case 0xDC:
      case 0xDD:
      case 0xDE:
      case 0xDF:
	if( this.vdip != null ) {
	  rv = this.vdip.read( port );
	}
	break;

      default:
	if( (this.k1520Sound != null) && ((port & 0xF8) == 0x38) ) {
	  rv = this.k1520Sound.read( port, tStates );
	}
	else if( (this.gide != null) && ((port & 0xF0) == 0x50) ) {
	  int value = this.gide.read( port );
	  if( value >= 0 ) {
	    rv = value;
	  }
	}
	else if( (this.rtc != null) && ((port & 0xF0) == 0x60) ) {
	  rv = this.rtc.read( port );
	}
    }
    return rv;
  }


  @Override
  public int readMemByte( int addr, boolean m1 )
  {
    return getMemByteInternal( addr, true );
  }


  @Override
  public int reassembleSysCall(
			Z80MemView    memory,
			int           addr,
			StringBuilder buf,
			boolean       sourceOnly,
			int           colMnemonic,
			int           colArgs,
			int           colRemark )
  {
    return reassSysCallTable(
			memory,
			addr,
			0xF000,
			biosCallNames,
			buf,
			sourceOnly,
			colMnemonic,
			colArgs,
			colRemark );
  }


  @Override
  public void reset( boolean powerOn, Properties props )
  {
    super.reset( powerOn, props );
    if( powerOn ) {
      initDRAM();
      EmuUtil.initDRAM( this.ramExt );
      fillRandom( this.ramVideo );
      if( this.ramVideo2 != null ) {
	fillRandom( this.ramVideo2 );
      }
      if( this.ramColor != null ) {
	fillRandom( this.ramColor );
      }
      if( this.ramColor2 != null ) {
	fillRandom( this.ramColor2 );
      }
      if( this.ramFont != null ) {
	fillRandom( this.ramFont );
      }
      if( this.ramPixel != null ) {
	fillRandom( this.ramPixel );
      }
    }
    this.ctc80.reset( powerOn );
    this.pio88.reset( powerOn );
    this.pio90.reset( powerOn );
    if( this.ctcA8 != null ) {
      this.ctcA8.reset( powerOn );
    }
    if( this.sioB0 != null ) {
      this.sioB0.reset( powerOn );
      this.sioB0.setClearToSendA( true );
      this.sioB0.setClearToSendB( true );
    }
    if( this.fdc != null ) {
      this.fdc.reset( powerOn );
    }
    if( this.floppyDiskDrives != null ) {
      for( int i = 0; i < this.floppyDiskDrives.length; i++ ) {
	FloppyDiskDrive drive = this.floppyDiskDrives[ i ];
	if( drive != null ) {
	  drive.reset();
	}
      }
    }
    if( this.blinkTimer != null ) {
      this.blinkTimer.restart();
    }
    Arrays.fill( this.kbMatrix, 0 );
    this.joy0ActionMask    = 0;
    this.joy1ActionMask    = 0;
    this.megaROMSeg        = 0;
    this.fontOffs          = 0;
    this.graphAddrL        = 0;
    this.graphBgColor      = 0;
    this.graphFgColor      = 0;
    this.graphBorder       = false;
    this.graphMode         = false;
    this.c80MemSwap        = false;
    this.plotterPenState   = false;
    this.plotterMoveState  = false;
    this.fdcReset          = false;
    this.fdcTC             = false;
    this.rf1ReadOnly       = false;
    this.rf2ReadOnly       = false;
    this.ram4000ExtEnabled = false;
    this.ramC000Enabled    = false;
    this.ramFontActive     = false;
    this.ramFontEnabled    = false;
    this.romModuleEnabled  = ((this.romBoot != null)
					|| (this.romMega != null));
    this.loudspeaker.reset();
    if( this.plotter != null ) {
      this.plotter.reset();
      this.pio88.putInValuePortB( 0x00, 0x20 );		// Plotter Ready
    }
    if( this.gide != null ) {
      this.gide.reset();
    }
    if( this.k1520Sound != null ) {
      this.k1520Sound.reset( powerOn );
    }
    if( this.kcNet != null ) {
      this.kcNet.reset( powerOn );
    }
    if( this.vdip != null ) {
      this.vdip.reset( powerOn );
    }
    setGraphicLED( false );
    upd80CharsMode( false );
    updScreenConfig( 0 );
    this.screenFrm.fireUpdScreenTextActionsEnabled();
  }


  @Override
  public void saveBasicProgram()
  {
    SourceUtil.saveKCBasicProgram(
			this.screenFrm,
			this.kc87 ? 0x0401 : 0x2C01 );
  }


  @Override
  public void setFloppyDiskDrive( int idx, FloppyDiskDrive drive )
  {
    if( this.floppyDiskDrives != null ) {
      if( (idx >= 0) && (idx < this.floppyDiskDrives.length) ) {
	this.floppyDiskDrives[ idx ] = drive;
      }
    }
  }


  @Override
  public void setJoystickAction( int joyNum, int actionMask )
  {
    if( joyNum == 0 ) {
      this.joy0ActionMask = actionMask;
    } else if( joyNum == 1 ) {
      this.joy1ActionMask = actionMask;
    }
    putKeyboardMatrixValuesToPorts();
  }


  @Override
  public boolean setMemByte( int addr, int value )
  {
    return setMemByteInternal( addr, value, false );
  }


  @Override
  public boolean shouldAskConvertScreenChar()
  {
    return (this.fontBytes != z9001FontBytes)
	   && (this.fontBytes != kc87FontBytes);
  }


  @Override
  public boolean supportsCopyToClipboard()
  {
    return true;
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
  public boolean supportsPasteFromClipboard()
  {
    return true;
  }


  @Override
  public boolean supportsPrinter()
  {
    return this.printerModule || this.pcListenerAdded;
  }


  @Override
  public boolean supportsRAMFloppy1()
  {
    return this.ramFloppy1 != null;
  }


  @Override
  public boolean supportsRAMFloppy2()
  {
    return this.ramFloppy2 != null;
  }


  @Override
  public boolean supportsSaveBasic()
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
  public void tapeInPhaseChanged()
  {
    this.pio88.strobePortA();
  }


  @Override
  public void updSysCells(
			int        begAddr,
			int        len,
			FileFormat fileFmt,
			int        fileType )
  {
    SourceUtil.updKCBasicSysCells(
			this.emuThread,
			begAddr,
			len,
			fileFmt,
			fileType );
  }


  @Override
  public void writeIOByte( int port, int value, int tStates )
  {
    switch( port & 0xFF ) {
      case 4:
	this.ram4000ExtEnabled = false;
	break;

      case 5:
	if( this.ramExt != null ) {
	  this.ram4000ExtEnabled = true;
	}
	break;

      case 6:
	this.ramC000Enabled = false;
	break;

      case 7:
	if( this.ramExt != null ) {
	  this.ramC000Enabled = true;
	}
	break;

      case 0x11:	// FDC DATA bei Rossendorf Floppy Disk Modul
      case 0x99:	// FDC DATA bei Robotron Floppy Disk Modul
      case 0x9B:
      case 0x9D:
      case 0x9F:
	if( this.fdc != null ) {
	  this.fdc.write( value );
	}
	break;

      case 0x12:	// Terminal Count bei Rossendorf Floppy Disk Modul
      case 0x13:
        if( this.fdc != null ) {
          this.fdc.fireTC();
        }
	break;

      case 0x20:
      case 0x21:
	if( this.ramFloppy1 != null ) {
	  this.ramFloppy1.writeByte( port, value );
	}
	break;

      case 0x24:
      case 0x25:
	if( this.ramFloppy2 != null ) {
	  this.ramFloppy2.writeByte( port, value );
	}
	break;

      case 0x80:				// A2=0
      case 0x81:
      case 0x82:
      case 0x83:
      case 0x84:				// A2=1
      case 0x85:
      case 0x86:
      case 0x87:
	this.ctc80.write( port & 0x03, value, tStates );
	break;

      case 0x88:				// A2=0
      case 0x8C:				// A2=1
	this.pio88.writeDataA( value );
	break;

      case 0x89:				// A2=0
      case 0x8D:				// A2=1
	this.pio88.writeDataB( value );
	break;

      case 0x8A:				// A2=0
      case 0x8E:				// A2=1
	this.pio88.writeControlA( value );
	break;

      case 0x8B:				// A2=0
      case 0x8F:				// A2=1
	this.pio88.writeControlB( value );
	break;

      case 0x90:				// A2=0
      case 0x94:				// A2=1
	this.pio90.writeDataA( value );		// Tastatur Spalten
	this.pio90.putInValuePortB( getKeyboardRowValue(), 0xFF );
	break;

      case 0x91:				// A2=0
      case 0x95:				// A2=1
	this.pio90.writeDataB( value );		// Tastatur Zeilen
	this.pio90.putInValuePortA( getKeyboardColValue(), 0xFF );
	break;

      case 0x92:				// A2=0
      case 0x96:				// A2=1
	this.pio90.writeControlA( value );
	break;

      case 0x93:				// A2=0
      case 0x97:				// A2=1
	this.pio90.writeControlB( value );
	break;

      /*
       * Die Ports A0h und A1h werden sowohl vom Robotron Floppy-Disk-Modul
       * als auch von der 80-Zeichensteuerung verwendet.
       * In der Emulation muss deshalb eine Prioritisierung
       * vorgenommen werden:
       * Wenn das Floppy-Disk-Modul emuliert wird,
       * werden diese beiden Ports fuer das Floppy-Disk-Modul verwendet.
       */
      case 0xA0:	// Zusatzregister des Robotron Floppy Disk Moduls
      case 0xA1:
      case 0xA2:
      case 0xA3:
      case 0xA4:
      case 0xA5:
      case 0xA6:
      case 0xA7:
	if( this.fdc != null ) {
	  boolean tc = ((value & 0x10) != 0);
	  if( tc && (tc != this.fdcTC) ) {
	    this.fdc.fireTC();
	  }
	  boolean res = ((value & 0x20) != 0);
	  if( res && (res != this.fdcReset) ) {
	    this.fdc.reset( false );
	  }
	  this.fdcTC    = tc;
	  this.fdcReset = res;
	} else {
	  if( this.c80Enabled && ((port & 0xFE) == 0xA0) ) {
	    this.c80MemSwap = ((port & 0x01) != 0);
	  }
	}
	break;

      /*
       * Die Ports A8h und A9h werden sowohl vom Drucker-Modul
       * als auch von der 80-Zeichensteuerung verwendet.
       * In der Emulation muss deshalb eine Prioritisierung
       * vorgenommen werden:
       * Wenn das Drucker-Modul emuliert wird,
       * werden diese beiden Ports fuer das Drucker-Modul verwendet.
       */
      case 0xA8:				// A2=0
      case 0xA9:
      case 0xAA:
      case 0xAB:
      case 0xAC:				// A2=1
      case 0xAD:
      case 0xAE:
      case 0xAF:
	if( this.ctcA8 != null ) {
	  this.ctcA8.write( port & 0x03, value, tStates );
	} else {
	  if( this.c80Enabled && ((port & 0xFE) == 0xA8) ) {
	    upd80CharsMode( (port & 0x01) != 0 );
	  }
	}
	break;

      case 0xB0:				// A2=0
      case 0xB4:				// A2=1
	if( this.sioB0 != null ) {
	  this.sioB0.writeDataA( value );
	}
	break;

      case 0xB1:				// A2=0
      case 0xB5:				// A2=1
	if( this.sioB0 != null ) {
	  this.sioB0.writeDataB( value );
	}
	break;

      case 0xB2:				// A2=0
      case 0xB6:				// A2=1
	if( this.sioB0 != null ) {
	  this.sioB0.writeControlA( value );
	}
	break;

      case 0xB3:				// A2=0
      case 0xB7:				// A2=1
	if( this.sioB0 != null ) {
	  this.sioB0.writeControlB( value );
	}
	break;

      case 0xB8:
	if( this.ramPixel != null ) {
	  boolean graphMode = this.graphMode;
	  if( this.graphType == GRAPHIC_ROBOTRON ) {
	    this.graphBgColor = value & 0x07;
	    this.graphFgColor = (value >> 4) & 0x07;
	    this.graphBorder  = ((value & 0x80) != 0);
	    graphMode         = ((value & 0x08) != 0);
	    this.screenFrm.setScreenDirty( true );
	  } else if( this.graphType == GRAPHIC_KRT ) {
	    this.graphBank = value & 0x07;
	    graphMode      = ((value & 0x08) != 0);
	    this.screenFrm.setScreenDirty( true );
	  }
	  if( graphMode != this.graphMode ) {
	    this.graphMode = graphMode;
	    this.screenFrm.fireUpdScreenTextActionsEnabled();
	  }
	}
	break;

      case 0xB9:
	if( (this.ramPixel != null)
	    && (this.graphType == GRAPHIC_ROBOTRON) )
	{
	  this.graphAddrL = value & 0xFF;
	}
	break;

      case 0xBA:
	if( (this.ramPixel != null)
	    && (this.graphType == GRAPHIC_ROBOTRON) )
	{
	  int addr = (port & 0xFF00) | this.graphAddrL;
	  if( (addr >= 0) && (addr < this.ramPixel.length) ) {
	    this.ramPixel[ addr ] = (byte) value;
	    this.screenFrm.setScreenDirty( true );
	  }
	}
	break;

      case 0xBB:
	if( this.fontBytes != null ) {
	  if( this.fontOffs > 0 ) {
	    this.fontOffs = 0;
	  } else {
	    if( this.fontBytes.length > 0x0800 ) {
	      this.fontOffs = 0x0800;
	    }
	  }
	}
	break;

      case 0xBC:
	if( this.c80Enabled ) {
	  upd80CharsMode( false );
	}
	break;

      case 0xBD:
	if( this.c80Enabled ) {
	  upd80CharsMode( true );
	}
	break;

      case 0xBE:
	if( this.c80Enabled ) {
	  this.c80MemSwap = false;
	}
	break;

      case 0xBF:
	if( this.c80Enabled ) {
	  this.c80MemSwap = true;
	}
	break;

      case 0xC0:
      case 0xC1:
      case 0xC2:
      case 0xC3:
	if( this.kcNet != null ) {
	  this.kcNet.write( port, value );
	}
	break;

      case 0xDC:
      case 0xDD:
      case 0xDE:
      case 0xDF:
	if( this.vdip != null ) {
	  this.vdip.write( port, value );
	}
	break;

      case 0xFF:
	this.megaROMSeg = (value & 0xFF);
	break;

      default:
	if( (this.k1520Sound != null) && ((port & 0xF8) == 0x38) ) {
	  this.k1520Sound.write( port, value, tStates );
	}
	else if( (this.gide != null) && ((port & 0xF0) == 0x50) ) {
	  this.gide.write( port, value );
	}
	else if( (this.rtc != null) && ((port & 0xF0)) == 0x60 ) {
	  this.rtc.write( port, value );
	}
    }
  }


  @Override
  public void writeMemByte( int addr, int value )
  {
    if( this.ramFont != null ) {
      switch( addr ) {
	case 0xEBFC:
	  this.ramFontActive  = false;
	  this.ramFontEnabled = true;
	  this.screenFrm.setScreenDirty( true );
	  break;
	case 0xEBFE:
	  this.ramFontActive  = true;
	  this.ramFontEnabled = false;
	  this.screenFrm.setScreenDirty( true );
	  break;
	case 0xEBFF:
	  this.ramFontActive  = false;
	  this.ramFontEnabled = false;
	  this.screenFrm.setScreenDirty( true );
	  break;
      }
    }
    if( addr >= 0xF800 ) {
      this.romModuleEnabled = ((addr & 0x0400) == 0);
    }
    setMemByteInternal( addr, value, true );
  }


  @Override
  public void z80TStatesProcessed( Z80CPU cpu, int tStates )
  {
    super.z80TStatesProcessed( cpu, tStates );
    this.ctc80.z80TStatesProcessed( cpu, tStates );
    this.loudspeaker.z80TStatesProcessed( cpu, tStates );
    if( this.ctcA8 != null ) {
      this.ctcA8.z80TStatesProcessed( cpu, tStates );
    }
    if( this.fdc != null ) {
      this.fdc.z80TStatesProcessed( cpu, tStates );
    }
    if( this.k1520Sound != null ) {
      this.k1520Sound.z80TStatesProcessed( cpu, tStates );
    }
    if( this.kcNet != null ) {
      this.kcNet.z80TStatesProcessed( cpu, tStates );
    }

    // Zugriffe auf den Bildwiederhol- und Farbspeicher verlangsamen
    if( (this.tStatesPerLine > 0) && (this.tStatesVisible > 0) ) {
      this.lineTStates += tStates;
      if( this.lineTStates >= this.tStatesPerLine ) {
	this.lineTStates %= this.tStatesPerLine;
	if( this.lineNum < 311 ) {
	  this.lineNum++;
	} else {
	  this.lineNum = 0;
	}
      }
    }
  }


	/* --- private Methoden --- */

  private void adjustVideoRAMAccessTStates()
  {
    if( (this.lineNum < 192)
	&& (this.tStatesPerLine > 0)
	&& (this.tStatesVisible > 0)
	&& (this.lineTStates < this.tStatesVisible) )
    {
      this.emuThread.getZ80CPU().addWaitStates(
			this.tStatesVisible - this.lineTStates );
    }
  }


  private void applyPasteFast( Properties props )
  {
    this.pasteFast = EmuUtil.getBooleanProperty(
			props,
			this.propPrefix + PROP_PASTE_FAST,
			true );
  }


  private synchronized void checkAddPCListener( Properties props )
  {
    boolean state = EmuUtil.getBooleanProperty(
			props,
			this.propPrefix + PROP_CATCH_PRINT_CALLS,
			true );
    if( state != this.pcListenerAdded ) {
      Z80CPU cpu = this.emuThread.getZ80CPU();
      if( state ) {
	cpu.addPCListener( this, 0x0005 );
      } else {
	cpu.removePCListener( this );
      }
      this.pcListenerAdded = state;
    }
  }


  private void createColors( Properties props )
  {
    float brightness = getBrightness( props );
    if( (brightness >= 0F) && (brightness <= 1F) ) {
      for( int i = 0; i < this.colors.length; i++ ) {
	this.colors[ i ] = new Color(
		Math.round( basicRGBValues[ i ][ 0 ] * brightness ),
		Math.round( basicRGBValues[ i ][ 1 ] * brightness ),
		Math.round( basicRGBValues[ i ][ 2 ] * brightness ) );
      }
    }
  }


  private boolean emulates80CharsMode( Properties props )
  {
    return EmuUtil.getBooleanProperty(
			props,
			this.propPrefix + PROP_80CHARS_ENABLED,
			false );
  }


  private boolean emulatesPlotter( Properties props )
  {
    return EmuUtil.getBooleanProperty(
			props,
			this.propPrefix + PROP_PLOTTER_ENABLED,
			false );
  }


  private boolean emulatesRAM16K4000( Properties props )
  {
    return EmuUtil.getBooleanProperty(
			props,
			this.propPrefix + PROP_RAM16K4000_ENABLED,
			false );
  }


  private boolean emulatesRAM16K8000( Properties props )
  {
    return EmuUtil.getBooleanProperty(
			props,
			this.propPrefix + PROP_RAM16K8000_ENABLED,
			false );
  }


  private boolean emulatesRAM64K( Properties props )
  {
    return EmuUtil.getBooleanProperty(
			props,
			this.propPrefix + PROP_RAM64K_ENABLED,
			false );
  }


  private boolean emulatesROM16K4000( Properties props )
  {
    return EmuUtil.getBooleanProperty(
			props,
			this.propPrefix + PROP_ROM16K4000_ENABLED,
			false );
  }


  private boolean emulatesROM32K4000( Properties props )
  {
    return EmuUtil.getBooleanProperty(
			props,
			this.propPrefix + PROP_ROM32K4000_ENABLED,
			false );
  }


  private boolean emulatesROM16K8000( Properties props )
  {
    return EmuUtil.getBooleanProperty(
			props,
			this.propPrefix + PROP_ROM16K8000_ENABLED,
			false );
  }


  private boolean emulatesROM10KC000( Properties props )
  {
    return EmuUtil.getBooleanProperty(
			props,
			this.propPrefix + PROP_ROM10KC000_ENABLED,
			false );
  }


  private boolean emulatesBootROM( Properties props )
  {
    return EmuUtil.getBooleanProperty(
			props,
			this.propPrefix + PROP_ROMBOOT_ENABLED,
			false );
  }


  private boolean emulatesMegaROM( Properties props )
  {
    return EmuUtil.getBooleanProperty(
			props,
			this.propPrefix + Z9001.PROP_ROMMEGA_ENABLED,
			false );
  }


  private boolean emulatesPrinterModule( Properties props )
  {
    return EmuUtil.getBooleanProperty(
			props,
			this.propPrefix + PROP_PRINTER_MOD_ENABLED,
			false );
  }


  private boolean emulatesProgrammableFont( Properties props )
  {
    return EmuUtil.getBooleanProperty(
			props,
			this.propPrefix + PROP_FONT_PROGRAMMABLE,
			false );
  }


  private boolean emulatesRTC( Properties props )
  {
    return EmuUtil.getBooleanProperty(
			props,
			this.propPrefix + PROP_RTC_ENABLED,
			false );
  }


  private static boolean equalsROMModule(
				byte[]     currentBytes,
				String     currentFile,
				Properties props,
				String     propEnabled,
				String     propFile )
  {
    boolean rv = false;
    if( EmuUtil.getBooleanProperty( props, propEnabled, false ) ) {
      if( (currentBytes != null)
	  && TextUtil.equals(
		currentFile,
		EmuUtil.getProperty( props, propFile ) ) )
      {
	rv = true;
      }
    } else {
      if( currentBytes == null ) {
	rv = true;
      }
    }
    return rv;
  }


  private boolean getColorMode( Properties props )
  {
    return EmuUtil.getBooleanProperty(
			props,
			this.propPrefix + PROP_COLOR,
			true );
  }


  private int getGraphicType( Properties props )
  {
    int    rv        = GRAPHIC_NONE;
    String graphType = EmuUtil.getProperty(
			props,
			this.propPrefix + PROP_GRAPHIC_TYPE ).toLowerCase();
    if( graphType.equals( "robotron" ) ) {
      rv = GRAPHIC_ROBOTRON;
    }
    else if( graphType.equals( "krt" ) ) {
      rv = GRAPHIC_KRT;
    }
    return rv;
  }


  private int getKeyboardColValue()
  {
    int rv       = 0;
    int rowValue = ~this.pio90.fetchOutValuePortB( 0xFF );
    int mask     = 0x01;
    synchronized( this.kbMatrix ) {
      for( int i = 0; i < this.kbMatrix.length; i++ ) {
	if( (rowValue & this.kbMatrix[ i ]) != 0 ) {
	  rv |= mask;
	}
	mask <<= 1;
      }
    }
    if( (rowValue & 0x40) != 0 ) {
      if( (this.joy0ActionMask & JoystickThread.LEFT_MASK) != 0 ) {
	rv |= 0x01;
      }
      if( (this.joy0ActionMask & JoystickThread.RIGHT_MASK) != 0 ) {
	rv |= 0x02;
      }
      if( (this.joy0ActionMask & JoystickThread.DOWN_MASK) != 0 ) {
	rv |= 0x04;
      }
      if( (this.joy0ActionMask & JoystickThread.UP_MASK) != 0 ) {
	rv |= 0x08;
      }
      if( (this.joy0ActionMask & JoystickThread.BUTTONS_MASK) != 0 ) {
	rv |= 0x10;
      }
    }
    if( (rowValue & 0x80) != 0 ) {
      if( (this.joy1ActionMask & JoystickThread.LEFT_MASK) != 0 ) {
	rv |= 0x01;
      }
      if( (this.joy1ActionMask & JoystickThread.RIGHT_MASK) != 0 ) {
	rv |= 0x02;
      }
      if( (this.joy1ActionMask & JoystickThread.DOWN_MASK) != 0 ) {
	rv |= 0x04;
      }
      if( (this.joy1ActionMask & JoystickThread.UP_MASK) != 0 ) {
	rv |= 0x08;
      }
      if( (this.joy1ActionMask & JoystickThread.BUTTONS_MASK ) != 0 ) {
	rv |= 0x10;
      }
    }
    return ~rv & 0xFF;
  }


  private int getKeyboardRowValue()
  {
    int colValue = ~this.pio90.fetchOutValuePortA( 0xFF );
    int rv       = 0;
    int mask     = 0x01;
    synchronized( this.kbMatrix ) {
      for( int i = 0; i < this.kbMatrix.length; i++ ) {
	if( (colValue & mask) != 0 ) {
	  rv |= this.kbMatrix[ i ];
	}
	mask <<= 1;
      }
    }
    if( (colValue & 0x01) != 0 ) {
      if( (this.joy0ActionMask & JoystickThread.LEFT_MASK) != 0 ) {
	rv |= 0x40;
      }
      if( (this.joy1ActionMask & JoystickThread.LEFT_MASK) != 0 ) {
	rv |= 0x80;
      }
    }
    if( (colValue & 0x02) != 0 ) {
      if( (this.joy0ActionMask & JoystickThread.RIGHT_MASK) != 0 ) {
	rv |= 0x40;
      }
      if( (this.joy1ActionMask & JoystickThread.RIGHT_MASK) != 0 ) {
	rv |= 0x80;
      }
    }
    if( (colValue & 0x04) != 0 ) {
      if( (this.joy0ActionMask & JoystickThread.DOWN_MASK) != 0 ) {
	rv |= 0x40;
      }
      if( (this.joy1ActionMask & JoystickThread.DOWN_MASK) != 0 ) {
	rv |= 0x80;
      }
    }
    if( (colValue & 0x08) != 0 ) {
      if( (this.joy0ActionMask & JoystickThread.UP_MASK) != 0 ) {
	rv |= 0x40;
      }
      if( (this.joy1ActionMask & JoystickThread.UP_MASK) != 0 ) {
	rv |= 0x80;
      }
    }
    if( (colValue & 0x10) != 0 ) {
      if( (this.joy0ActionMask & JoystickThread.BUTTONS_MASK) != 0 ) {
	rv |= 0x40;
      }
      if( (this.joy1ActionMask & JoystickThread.BUTTONS_MASK) != 0 ) {
	rv |= 0x80;
      }
    }
    return ~rv & 0xFF;
  }


  private int getMemByteInternal( int addr, boolean emuWaitStates )
  {
    addr &= 0xFFFF;

    int rv = 0xFF;
    if( addr < 0x4000 ) {
      rv = this.emuThread.getRAMByte( addr );
    }
    else if( (addr >= 0x4000) && (addr < 0x8000) ) {
      if( this.rom16k4000 != null ) {
	int idx = addr - 0x4000;
	if( idx < this.rom16k4000.length ) {
	  rv = (int) this.rom16k4000[ idx ] & 0xFF;
	}
      } else if( this.rom32k4000 != null ) {
	int idx = addr - 0x4000;
	if( idx < this.rom32k4000.length ) {
	  rv = (int) this.rom32k4000[ idx ] & 0xFF;
	}
      } else if( this.ram64k ) {
	if( this.ram4000ExtEnabled && (this.ramExt != null) ) {
	  int idx = addr - 0x4000;
	  if( idx < this.ramExt.length ) {
	    rv = (int) this.ramExt[ idx ] & 0xFF;
	  }
	} else {
	  rv = this.emuThread.getRAMByte( addr );
	}
      } else if( this.ram16k4000 ) {
	rv = this.emuThread.getRAMByte( addr );
      }
    }
    else if( (addr >= 0x8000) && (addr < 0xC000) ) {
      if( this.rom32k4000 != null ) {
	int idx = addr - 0x4000;
	if( idx < this.rom32k4000.length ) {
	  rv = (int) this.rom32k4000[ idx ] & 0xFF;
	}
      } else if( this.rom16k8000 != null ) {
	int idx = addr - 0x8000;
	if( idx < this.rom16k8000.length ) {
	  rv = (int) this.rom16k8000[ idx ] & 0xFF;
	}
      } else if( this.ram16k8000 || this.ram64k ) {
	rv = this.emuThread.getRAMByte( addr );
      } else if( this.printerModule && (printerModBytes != null) ) {
	/*
	 * Der ROM des Druckermoduls ist nur bei einer RAM-Bestueckung
	 * bis max. 32 KByte aktiv.
	 * Deshalb wird dieser ROM erst nach dem RAM abgefragt.
	 */
	int idx = addr - 0xB800;
	if( (idx >= 0) && (idx < printerModBytes.length) ) {
	  rv = (int) printerModBytes[ idx ] & 0xFF;
	}
      }
    }
    else if( (addr >= 0xC000) && (addr < 0xE800) ) {
      if( this.ram64k && this.ramC000Enabled ) {
	rv = this.emuThread.getRAMByte( addr );
      } else if( this.romModuleEnabled && (this.romBoot != null) ) {
	int idx = addr - 0xC000;
	if( idx < this.romBoot.length ) {
	  rv = (int) this.romBoot[ idx ] & 0xFF;
	}
      } else if( this.romModuleEnabled && (this.romMega != null) ) {
	int idx = 0;
	if( addr < 0xC800 ) {
	  idx = addr - 0xC000 + (this.megaROMSeg * 2048);
	} else if( addr < 0xD000 ) {
	  idx = addr - 0xC800 + ((256 + this.megaROMSeg) * 2048);
	} else if( addr < 0xD800 ) {
	  idx = addr - 0xD000 + (((2 * 256) + this.megaROMSeg) * 2048);
	} else if( addr < 0xE000 ) {
	  idx = addr - 0xD800 + (((3 * 256) + this.megaROMSeg) * 2048);
	} else {
	  idx = addr - 0xE000 + (((4 * 256) + this.megaROMSeg) * 2048);
	}
	if( (idx >= 0) && (idx < this.romMega.length) ) {
	  rv = (int) this.romMega[ idx ] & 0xFF;
	}
      } else if( this.rom10kC000 != null ) {
	int idx = addr - 0xC000;
	if( idx < this.rom10kC000.length ) {
	  rv = (int) this.rom10kC000[ idx ] & 0xFF;
	}
      } else if( this.kc87 && (this.romBasic != null) ) {
	int idx = addr - 0xC000;
	if( idx < this.romBasic.length ) {
	  rv = (int) this.romBasic[ idx ] & 0xFF;
	}
      }
    }
    else if((addr >= 0xE800) && (addr < 0xEC00) ) {
      if( this.ramFontEnabled && (this.ramFont != null) ) {
	int idx = addr - 0xE800;
	if( idx < this.ramFont.length ) {
	  rv = (int) this.ramFont[ addr - 0xE800 ] & 0xFF;
	}
      } else if( this.ramColor != null ) {
	if( this.c80MemSwap ) {
	  rv = (int) this.ramColor2[ addr - 0xE800 ] & 0xFF;
	} else {
	  rv = (int) this.ramColor[ addr - 0xE800 ] & 0xFF;
	}
	if( emuWaitStates ) {
	  adjustVideoRAMAccessTStates();
	}
      }
    }
    else if( (addr >= 0xEC00) && (addr < 0xF000) ) {
      int idx = addr - 0xEC00;
      if( (this.ramPixel != null)
	  && (this.graphType == GRAPHIC_KRT)
	  && this.graphMode )
      {
	idx += (this.graphBank * 0x0400);
	if( (idx >= 0) && (idx < this.ramPixel.length) ) {
	  rv = (int) this.ramPixel[ idx ] & 0xFF;
	}
      } else {
	if( this.c80MemSwap ) {
	  rv = (int) this.ramVideo2[ idx ] & 0xFF;
	} else {
	  rv = (int) this.ramVideo[ idx ] & 0xFF;
	}
      }
      if( emuWaitStates ) {
	adjustVideoRAMAccessTStates();
      }
    }
    else if( (addr >= 0xF000) && (this.romOS != null) ) {
      int idx = addr - 0xF000;
      if( idx < this.romOS.length ) {
	rv = (int) this.romOS[ idx ] & 0xFF;
      }
    }
    return rv;
  }


  private boolean isFixedScreenSize( Properties props )
  {
    return this.c80Enabled
	   && EmuUtil.getBooleanProperty(
			props,
			this.propPrefix + PROP_FIXED_SCREEN_SIZE,
			false );
  }


  private void loadFont( Properties props )
  {
    this.fontBytes = readFontByProperty(
				props,
				this.propPrefix + PROP_FONT_FILE,
				0x1000 );
    if( this.fontBytes == null ) {
      if( this.sysName.equals( SYSNAME_KC87 ) ) {
	if( kc87FontBytes == null ) {
	  kc87FontBytes = readResource( "/rom/z9001/kc87font.bin" );
	}
	this.fontBytes = kc87FontBytes;
      } else {
	if( z9001FontBytes == null ) {
	  z9001FontBytes = readResource( "/rom/z9001/z9001font.bin" );
	}
	this.fontBytes = z9001FontBytes;
      }
    }
  }


  private void putKeyboardMatrixValuesToPorts()
  {
    this.pio90.putInValuePortB( getKeyboardRowValue(), 0xFF );
    this.pio90.putInValuePortA( getKeyboardColValue(), 0xFF );
  }


  private boolean setCharInKBMatrix( int ch, int[][] matrixChars )
  {
    boolean rv   = false;
    int     mask = 1;
    for( int row = 0; row < matrixChars.length; row++ ) {
      for( int col = 0;
	   (col < matrixChars[ row ].length) && (col < this.kbMatrix.length);
	   col++ )
      {
	if( matrixChars[ row ][ col ] == ch ) {
	  this.kbMatrix[ col ] = mask;
	  rv = true;
	  break;
	}
      }
      mask <<= 1;
    }
    return rv;
  }


  private void setGraphicLED( boolean state )
  {
    if( state != this.graphicLED ) {
      this.graphicLED = state;
      if( this.keyboardFld != null ) {
	this.keyboardFld.repaint();
      }
    }
  }


  private boolean setMemByteInternal(
				int     addr,
				int     value,
				boolean emuWaitStates )
  {
    addr &= 0xFFFF;

    boolean rv = false;
    if( addr < 0x4000 ) {
      this.emuThread.setRAMByte( addr, value );
      rv = true;
    }
    else if( (addr >= 0x4000) && (addr < 0x8000) ) {
      if( (this.rom16k4000 == null) && (this.rom32k4000 == null) ) {
	if( this.ram64k ) {
	  if( this.ram4000ExtEnabled && (this.ramExt != null) ) {
	    int idx = addr - 0x4000;
	    if( idx < this.ramExt.length ) {
	      this.ramExt[ idx ] = (byte) value;
	      rv = true;
	    }
	  } else {
	    this.emuThread.setRAMByte( addr, value );
	    rv = true;
	  }
	} else if( this.ram16k4000 ) {
	  this.emuThread.setRAMByte( addr, value );
	  rv = true;
	}
      }
    }
    else if( (addr >= 0x8000) && (addr < 0xC000) ) {
      if( (this.rom32k4000 == null) && (this.rom16k8000 == null)
	  && (this.ram16k8000 || this.ram64k) )
      {
	this.emuThread.setRAMByte( addr, value );
	rv = true;
      }
    }
    else if( (addr >= 0xC000) && (addr < 0xE800) ) {
      if( this.ram64k && this.ramC000Enabled ) {
	this.emuThread.setRAMByte( addr, value );
	rv = true;
      }
    }
    else if((addr >= 0xE800) && (addr < 0xEC00) ) {
      int idx = addr - 0xE800;
      if( this.ramFontEnabled && (this.ramFont != null) ) {
	if( idx < this.ramFont.length ) {
	  this.ramFont[ idx ] = (byte) value;
	  rv = true;
	}
      } else if( this.ramColor != null ) {
	if( this.c80MemSwap ) {
	  this.ramColor2[ idx ] = (byte) value;
	} else {
	  this.ramColor[ idx ] = (byte) value;
	}
	this.screenFrm.setScreenDirty( true );
	rv = true;
	if( emuWaitStates ) {
	  adjustVideoRAMAccessTStates();
	}
      }
    }
    else if( (addr >= 0xEC00) && (addr < 0xF000) ) {
      int idx = addr - 0xEC00;
      if( (this.ramPixel != null)
	  && (this.graphType == GRAPHIC_KRT)
	  && this.graphMode )
      {
	idx += (this.graphBank * 0x0400);
	if( (idx >= 0) && (idx < this.ramPixel.length) ) {
	  this.ramPixel[ idx ] = (byte) value;
	  this.screenFrm.setScreenDirty( true );
	  rv = true;
	}
      } else {
	if( this.c80MemSwap ) {
	  this.ramVideo2[ idx ] = (byte) value;
	} else {
	  this.ramVideo[ idx ] = (byte) value;
	}
	this.screenFrm.setScreenDirty( true );
	rv = true;
      }
      if( emuWaitStates ) {
	adjustVideoRAMAccessTStates();
      }
    }
    return rv;
  }


  private void upd80CharsMode( boolean state )
  {
    if( this.c80Active != state ) {
      this.c80Active = state;
      this.screenFrm.setScreenDirty( true );
      if( !this.fixedScreenSize ) {
	this.screenFrm.fireScreenSizeChanged();
      }
    }
  }


  private void updKeyboardFld()
  {
    if( this.keyboardFld != null ) {
      this.keyboardFld.updKeySelection( this.kbMatrix );
    }
  }


  private void updLoudspeaker( int pio88PortAValue )
  {
    this.loudspeaker.setCurPhase(
		!(!this.tapeOutPhase && ((pio88PortAValue & 0x80) != 0)) );
  }


  private void updScreenConfig( int value )
  {
    boolean mode20Rows = ((value & 0x04) != 0);
    int     colorIdx   = (value >> 3) & 0x07;
    if( (mode20Rows != this.mode20Rows)
	|| (colorIdx != this.borderColorIdx) )
    {
      this.mode20Rows     = mode20Rows;
      this.borderColorIdx = colorIdx;
      this.screenFrm.setScreenDirty( true );
    }
  }
}
