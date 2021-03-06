/*
 * (c) 2008-2021 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Optionen fuer den Basic-Compiler
 */

package jkcemu.programming.basic;

import java.util.Properties;
import jkcemu.base.EmuSys;
import jkcemu.programming.PrgOptions;
import jkcemu.programming.assembler.Z80Assembler;
import jkcemu.text.TextUtil;


public class BasicOptions extends PrgOptions
{
  public static enum BreakOption { NEVER, INPUT, ALWAYS };

  public static final int MAX_HEAP_SIZE      = 0x8000;
  public static final int MIN_HEAP_SIZE      = 0x0200;
  public static final int MIN_STACK_SIZE     = 64;
  public static final int DEFAULT_HEAP_SIZE  = 1024;
  public static final int DEFAULT_STACK_SIZE = 128;

  public static final String DEFAULT_APP_NAME    = "MYAPP";
  public static final String OPTION_BASIC_PREFIX = OPTION_PREFIX + "basic.";


  private static final String OPTION_APP_NAME
			= OPTION_BASIC_PREFIX + "name";

  private static final String OPTION_APP_TYPE_SUB
			= OPTION_BASIC_PREFIX + "subroutine";

  private static final String OPTION_APP_LANG
			= OPTION_BASIC_PREFIX + "language.code";

  private static final String OPTION_BSS_ADDR
			= OPTION_BASIC_PREFIX + "address.bss";

  private static final String OPTION_CODE_ADDR
			= OPTION_BASIC_PREFIX + "address.code";

  private static final String OPTION_CHECK_BOUNDS
			= OPTION_BASIC_PREFIX + "bounds.check";

  private static final String OPTION_CHECK_STACK
			= OPTION_BASIC_PREFIX + "stack.check";

  private static final String OPTION_BREAK
			= OPTION_BASIC_PREFIX + "breakoption";

  private static final String OPTION_HEAP_SIZE
			= OPTION_BASIC_PREFIX + "heap.size";

  private static final String OPTION_INCLUDE_BASIC_LINES
			= OPTION_BASIC_PREFIX + "include_basic_lines";

  private static final String OPTION_INIT_VARS
			= OPTION_BASIC_PREFIX + "init_vars";

  private static final String OPTION_OPEN_DISK_ENABLED
			= OPTION_BASIC_PREFIX + "open.disk.enabled";

  private static final String OPTION_OPEN_CRT_ENABLED
			= OPTION_BASIC_PREFIX + "open.crt.enabled";

  private static final String OPTION_OPEN_LPT_ENABLED
			= OPTION_BASIC_PREFIX + "open.lpt.enabled";

  private static final String OPTION_OPEN_VDIP_ENABLED
			= OPTION_BASIC_PREFIX + "open.vdip.enabled";

  private static final String OPTION_PREFER_REL_JUMPS
			= OPTION_BASIC_PREFIX + "prefer_relative_jumps";

  private static final String OPTION_TARGET
			= OPTION_BASIC_PREFIX + "target";

  private static final String OPTION_PRINT_LINE_NUM_ON_ABORT
			= OPTION_BASIC_PREFIX + "print_line_num_on_abort";

  private static final String OPTION_SHOW_ASM_TEXT
			= OPTION_BASIC_PREFIX + "show_assembler_source";

  private static final String OPTION_STACK_SIZE
			= OPTION_BASIC_PREFIX + "stack.size";

  private static final String OPTION_WARN_IMPLICIT_DECLS
			= OPTION_BASIC_PREFIX + "warn_implicit_decls";

  private static final String OPTION_WARN_TOO_MANY_DIGITS
			= OPTION_BASIC_PREFIX + "warn_too_many_digits";

  private static final String OPTION_WARN_UNUSED_ITEMS
			= OPTION_BASIC_PREFIX + "warn_unused_items";

  private static final String VALUE_BREAK_ALWAYS = "always";
  private static final String VALUE_BREAK_INPUT  = "input";
  private static final String VALUE_BREAK_NEVER  = "never";

  private boolean        appTypeSub;
  private String         appName;
  private String         langCode;
  private String         targetText;
  private AbstractTarget target;
  private EmuSys         emuSys;
  private int            codeBegAddr;
  private int            bssBegAddr;
  private int            heapSize;
  private int            stackSize;
  private boolean        checkStack;
  private boolean        checkBounds;
  private boolean        openCrtEnabled;
  private boolean        openLptEnabled;
  private boolean        openDiskEnabled;
  private boolean        openVdipEnabled;
  private boolean        inclBasicLines;
  private boolean        initVars;
  private boolean        preferRelJumps;
  private boolean        printLineNumOnAbort;
  private boolean        showAsmText;
  private boolean        warnImplicitDecls;
  private boolean        warnTooManyDigits;
  private boolean        warnUnusedItems;
  private BreakOption    breakOption;


  public BasicOptions()
  {
    this( null );
  }


  public BasicOptions( BasicOptions src )
  {
    super( src );
    if( src != null ) {
      this.appTypeSub          = src.appTypeSub;
      this.appName             = src.appName;
      this.langCode            = src.langCode;
      this.targetText          = src.targetText;
      this.target              = src.target;
      this.emuSys              = src.emuSys;
      this.codeBegAddr         = src.codeBegAddr;
      this.bssBegAddr          = src.bssBegAddr;
      this.heapSize            = src.heapSize;
      this.stackSize           = src.stackSize;
      this.checkStack          = src.checkStack;
      this.checkBounds         = src.checkBounds;
      this.openCrtEnabled      = src.openCrtEnabled;
      this.openLptEnabled      = src.openLptEnabled;
      this.openDiskEnabled     = src.openDiskEnabled;
      this.openVdipEnabled     = src.openVdipEnabled;
      this.inclBasicLines      = src.inclBasicLines;
      this.initVars            = src.initVars;
      this.preferRelJumps      = src.preferRelJumps;
      this.printLineNumOnAbort = src.printLineNumOnAbort;
      this.showAsmText         = src.showAsmText;
      this.warnImplicitDecls   = src.warnImplicitDecls;
      this.warnTooManyDigits   = src.warnTooManyDigits;
      this.warnUnusedItems     = src.warnUnusedItems;
      this.breakOption         = src.breakOption;
    } else {
      this.appTypeSub          = false;
      this.appName             = DEFAULT_APP_NAME;
      this.langCode            = null;
      this.targetText          = null;
      this.target              = null;
      this.emuSys              = null;
      this.codeBegAddr         = -1;
      this.bssBegAddr          = -1;
      this.heapSize            = DEFAULT_HEAP_SIZE;
      this.stackSize           = DEFAULT_STACK_SIZE;
      this.checkStack          = true;
      this.checkBounds         = true;
      this.openCrtEnabled      = true;
      this.openLptEnabled      = true;
      this.openDiskEnabled     = true;
      this.openVdipEnabled     = true;
      this.inclBasicLines      = true;
      this.initVars            = true;
      this.preferRelJumps      = true;
      this.printLineNumOnAbort = true;
      this.showAsmText         = false;
      this.warnImplicitDecls   = false;
      this.warnTooManyDigits   = true;
      this.warnUnusedItems     = true;
      this.breakOption         = BreakOption.ALWAYS;
      setAsmSyntax( Z80Assembler.Syntax.ZILOG_ONLY );
      setAllowUndocInst( false );
      setLabelsCaseSensitive( false );
      setPrintLabels( false );
      setReplaceTooLongRelJumps( true );
    }
  }


  public boolean canBreakAlways()
  {
    return this.breakOption == BreakOption.ALWAYS;
  }


  public boolean canBreakOnInput()
  {
    return (this.breakOption == BreakOption.ALWAYS)
	   || (this.breakOption == BreakOption.INPUT);
  }


  public String getAppName()
  {
    return this.appName;
  }


  public static BasicOptions getBasicOptions( Properties props )
  {
    BasicOptions options = null;
    if( props != null ) {
      Boolean appTypeSub      = getBoolean( props, OPTION_APP_TYPE_SUB );
      String  appName         = props.getProperty( OPTION_APP_NAME );
      String  langCode        = props.getProperty( OPTION_APP_LANG );
      String  targetText      = props.getProperty( OPTION_TARGET );
      String  breakOptionText = props.getProperty( OPTION_BREAK );
      Integer codeBegAddr     = getInteger( props, OPTION_CODE_ADDR );
      Integer bssBegAddr      = getInteger( props, OPTION_BSS_ADDR );
      Integer heapSize        = getInteger( props, OPTION_HEAP_SIZE );
      Integer stackSize       = getInteger( props, OPTION_STACK_SIZE );
      Boolean checkStack      = getBoolean( props, OPTION_CHECK_STACK );
      Boolean checkBounds     = getBoolean( props, OPTION_CHECK_BOUNDS );
      Boolean openCrtEnabled  = getBoolean( props, OPTION_OPEN_CRT_ENABLED );
      Boolean openLptEnabled  = getBoolean( props, OPTION_OPEN_LPT_ENABLED );
      Boolean openDiskEnabled = getBoolean( props, OPTION_OPEN_DISK_ENABLED );
      Boolean openVdipEnabled = getBoolean( props, OPTION_OPEN_VDIP_ENABLED );
      Boolean preferRelJumps  = getBoolean( props, OPTION_PREFER_REL_JUMPS );
      Boolean showAsmText     = getBoolean( props, OPTION_SHOW_ASM_TEXT );

      Boolean printLineNumOnAbort = getBoolean(
					props,
					OPTION_PRINT_LINE_NUM_ON_ABORT );

      Boolean inclBasicLines = getBoolean(
					props,
					OPTION_INCLUDE_BASIC_LINES );

      Boolean initVars = getBoolean( props, OPTION_INIT_VARS );

      Boolean warnImplicitDecls = getBoolean(
					props,
					OPTION_WARN_IMPLICIT_DECLS );

      Boolean warnTooManyDigits = getBoolean(
					props,
					OPTION_WARN_TOO_MANY_DIGITS );

      Boolean warnUnusedItems = getBoolean(
					props,
					OPTION_WARN_UNUSED_ITEMS );

      if( (appTypeSub != null)
	  || (appName != null)
	  || (langCode != null)
	  || (targetText != null)
	  || (codeBegAddr != null)
	  || (bssBegAddr != null)
	  || (heapSize != null)
	  || (stackSize != null)
	  || (checkStack != null)
	  || (checkBounds != null)
	  || (openCrtEnabled != null)
	  || (openLptEnabled != null)
	  || (openDiskEnabled != null)
	  || (openVdipEnabled != null)
	  || (preferRelJumps != null)
	  || (printLineNumOnAbort != null)
	  || (showAsmText != null)
	  || (inclBasicLines != null)
	  || (initVars != null)
	  || (warnImplicitDecls != null)
	  || (warnTooManyDigits != null)
	  || (warnUnusedItems != null)
	  || (breakOptionText != null) )
      {
	options = new BasicOptions();

	if( appTypeSub != null ) {
	  options.appTypeSub = appTypeSub;
	}
	if( appName != null ) {
	  options.appName = appName;
	}
	if( langCode != null ) {
	  options.langCode = langCode;
	}
	if( targetText != null ) {
	  options.targetText = targetText;
	}
	if( codeBegAddr != null ) {
	  options.codeBegAddr = codeBegAddr.intValue();
	}
	if( bssBegAddr != null ) {
	  options.bssBegAddr = bssBegAddr.intValue();
	}
	if( heapSize != null ) {
	  options.heapSize = heapSize.intValue();
	}
	if( stackSize != null ) {
	  options.stackSize = stackSize.intValue();
	}
	if( checkStack != null ) {
	  options.checkStack = checkStack.booleanValue();
	}
	if( checkBounds != null ) {
	  options.checkBounds = checkBounds.booleanValue();
	}
	if( openCrtEnabled != null ) {
	  options.openCrtEnabled = openCrtEnabled.booleanValue();
	}
	if( openLptEnabled != null ) {
	  options.openLptEnabled = openLptEnabled.booleanValue();
	}
	if( openDiskEnabled != null ) {
	  options.openDiskEnabled = openDiskEnabled.booleanValue();
	}
	if( openVdipEnabled != null ) {
	  options.openVdipEnabled = openVdipEnabled.booleanValue();
	}
	if( preferRelJumps != null ) {
	  options.preferRelJumps = preferRelJumps.booleanValue();
	}
	if( printLineNumOnAbort != null ) {
	  options.printLineNumOnAbort = printLineNumOnAbort.booleanValue();
	}
	if( showAsmText != null ) {
	  options.showAsmText = showAsmText.booleanValue();
	}
	if( inclBasicLines != null ) {
	  options.inclBasicLines = inclBasicLines.booleanValue();
	}
	if( initVars != null ) {
	  options.initVars = initVars.booleanValue();
	}
	if( warnImplicitDecls != null ) {
	  options.warnImplicitDecls = warnImplicitDecls.booleanValue();
	}
	if( warnTooManyDigits != null ) {
	  options.warnTooManyDigits = warnTooManyDigits.booleanValue();
	}
	if( warnUnusedItems != null ) {
	  options.warnUnusedItems = warnUnusedItems.booleanValue();
	}
	if( breakOptionText != null ) {
	  if( breakOptionText.equals( VALUE_BREAK_NEVER ) ) {
	    options.breakOption = BreakOption.NEVER;
	  } else if( breakOptionText.equals( VALUE_BREAK_INPUT ) ) {
	    options.breakOption = BreakOption.INPUT;
	  } else {
	    options.breakOption = BreakOption.ALWAYS;
	  }
	}
      }
    }
    return options;
  }


  public BreakOption getBreakOption()
  {
    return this.breakOption;
  }


  public int getBssBegAddr()
  {
    return this.bssBegAddr;
  }


  public boolean getCheckBounds()
  {
    return this.checkBounds;
  }


  public boolean getCheckStack()
  {
    return this.checkStack;
  }


  public int getCodeBegAddr()
  {
    return this.codeBegAddr;
  }


  public EmuSys getEmuSys()
  {
    return this.emuSys;
  }


  public int getHeapSize()
  {
    return this.heapSize;
  }


  public boolean getIncludeBasicLines()
  {
    return this.inclBasicLines;
  }


  public boolean getInitVars()
  {
    return this.initVars;
  }


  public String getLangCode()
  {
    return this.langCode;
  }


  public boolean getPreferRelativeJumps()
  {
    return this.preferRelJumps;
  }


  public boolean getPrintLineNumOnAbort()
  {
    return this.printLineNumOnAbort;
  }


  public boolean getShowAssemblerText()
  {
    return this.showAsmText;
  }


  public int getStackSize()
  {
    return this.stackSize;
  }


  public AbstractTarget getTarget()
  {
    return this.target;
  }


  public String getTargetText()
  {
    return this.targetText;
  }


  public boolean getWarnImplicitDecls()
  {
    return this.warnImplicitDecls;
  }


  public boolean getWarnTooManyDigits()
  {
    return this.warnTooManyDigits;
  }


  public boolean getWarnUnusedItems()
  {
    return this.warnUnusedItems;
  }


  public boolean isAppTypeSubroutine()
  {
    return this.appTypeSub;
  }


  public boolean isOpenCrtEnabled()
  {
    return this.openCrtEnabled;
  }


  public boolean isOpenDiskEnabled()
  {
    return this.openDiskEnabled;
  }


  public boolean isOpenLptEnabled()
  {
    return this.openLptEnabled;
  }


  public boolean isOpenVdipEnabled()
  {
    return this.openVdipEnabled;
  }


  public void setAppName( String appName )
  {
    this.appName = appName;
  }


  public void setAppTypeSubroutine( boolean state )
  {
    this.appTypeSub = state;
  }


  public void setBreakOption( BreakOption value )
  {
    this.breakOption = value;
  }


  public void setCheckBounds( boolean state )
  {
    this.checkBounds = state;
  }


  public void setCheckStack( boolean state )
  {
    this.checkStack = state;
  }


  public void setBssBegAddr( int value )
  {
    this.bssBegAddr = value;
  }


  public void setCodeBegAddr( int value )
  {
    this.codeBegAddr = value;
  }


  public void setEmuSys( EmuSys emuSys )
  {
    this.emuSys = emuSys;
  }


  public void setHeapSize( int value )
  {
    this.heapSize = value;
  }


  public void setIncludeBasicLines( boolean state )
  {
    this.inclBasicLines = state;
  }


  public void setInitVars( boolean state )
  {
    this.initVars = state;
  }


  public void setLangCode( String langCode )
  {
    this.langCode = langCode;
  }


  public void setOpenCrtEnabled( boolean state )
  {
    this.openCrtEnabled = state;
  }


  public void setOpenLptEnabled( boolean state )
  {
    this.openLptEnabled = state;
  }


  public void setOpenDiskEnabled( boolean state )
  {
    this.openDiskEnabled = state;
  }


  public void setOpenVdipEnabled( boolean state )
  {
    this.openVdipEnabled = state;
  }


  public void setTarget( AbstractTarget target )
  {
    this.target     = target;
    this.targetText = target.toString();
  }


  public void setPreferRelativeJumps( boolean state )
  {
    this.preferRelJumps = state;
  }


  public void setPrintLineNumOnAbort( boolean state )
  {
    this.printLineNumOnAbort = state;
  }


  public void setShowAssemblerText( boolean state )
  {
    this.showAsmText = state;
  }


  public void setStackSize( int value )
  {
    this.stackSize = value;
  }


  public void setWarnImplicitDecls( boolean state )
  {
    this.warnImplicitDecls = state;
  }


  public void setWarnTooManyDigits( boolean state )
  {
    this.warnTooManyDigits = state;
  }


  public void setWarnUnusedItems( boolean state )
  {
    this.warnUnusedItems = state;
  }


	/* --- ueberschrieben Methoden --- */

  /*
   * Die Methode vergleicht nur die eigentlichen Optionen,
   * die auch in die Profildatei geschrieben werden.
   */
  @Override
  public boolean sameOptions( PrgOptions options )
  {
    boolean rv = super.sameOptions( options );
    if( rv && (options != null) ) {
      if( options instanceof BasicOptions ) {
	BasicOptions o = (BasicOptions) options;
	rv = (this.appTypeSub == o.appTypeSub)
		&& equals( this.appName, o.appName )
		&& equals( this.langCode, o.langCode )
		&& equals( this.targetText, o.targetText )
		&& equals( this.breakOption, o.breakOption )
		&& (this.codeBegAddr         == o.codeBegAddr)
		&& (this.bssBegAddr          == o.bssBegAddr)
		&& (this.heapSize            == o.heapSize)
		&& (this.stackSize           == o.stackSize)
		&& (this.checkStack          == o.checkStack)
		&& (this.checkBounds         == o.checkBounds)
		&& (this.openCrtEnabled      == o.openCrtEnabled)
		&& (this.openLptEnabled      == o.openLptEnabled)
		&& (this.openDiskEnabled     == o.openDiskEnabled)
		&& (this.openVdipEnabled     == o.openVdipEnabled)
		&& (this.inclBasicLines      == o.inclBasicLines)
		&& (this.initVars            == o.initVars)
		&& (this.preferRelJumps      == o.preferRelJumps)
		&& (this.printLineNumOnAbort == o.printLineNumOnAbort)
		&& (this.showAsmText         == o.showAsmText)
		&& (this.warnImplicitDecls   == o.warnImplicitDecls)
		&& (this.warnTooManyDigits   == o.warnTooManyDigits)
		&& (this.warnUnusedItems     == o.warnUnusedItems);
      }
    }
    return rv;
  }


  @Override
  public void putOptionsTo( Properties props )
  {
    super.putOptionsTo( props );
    if( props != null ) {
      props.setProperty(
		OPTION_APP_TYPE_SUB,
                Boolean.toString( this.appTypeSub ) );

      props.setProperty(
		OPTION_APP_NAME,
		this.appName != null ? this.appName : "" );

      props.setProperty(
		OPTION_APP_LANG,
		this.langCode != null ? this.langCode : "" );

      props.setProperty(
		OPTION_TARGET,
		this.targetText != null ? this.targetText : "" );

      props.setProperty(
		OPTION_CODE_ADDR,
                Integer.toString( this.codeBegAddr ) );

      props.setProperty(
		OPTION_BSS_ADDR,
		Integer.toString( this.bssBegAddr ) );

      props.setProperty(
		OPTION_HEAP_SIZE,
                Integer.toString( this.heapSize ) );

      props.setProperty(
		OPTION_STACK_SIZE,
                Integer.toString( this.stackSize ) );

      props.setProperty(
		OPTION_CHECK_STACK,
                Boolean.toString( this.checkStack ) );

      props.setProperty(
		OPTION_CHECK_BOUNDS,
                Boolean.toString( this.checkBounds ) );

      props.setProperty(
		OPTION_OPEN_CRT_ENABLED,
                Boolean.toString( this.openCrtEnabled ) );

      props.setProperty(
		OPTION_OPEN_LPT_ENABLED,
                Boolean.toString( this.openLptEnabled ) );

      props.setProperty(
		OPTION_OPEN_DISK_ENABLED,
                Boolean.toString( this.openDiskEnabled ) );

      props.setProperty(
		OPTION_OPEN_VDIP_ENABLED,
                Boolean.toString( this.openVdipEnabled ) );

      props.setProperty(
		OPTION_PREFER_REL_JUMPS,
                Boolean.toString( this.preferRelJumps ) );

      props.setProperty(
		OPTION_PRINT_LINE_NUM_ON_ABORT,
                Boolean.toString( this.printLineNumOnAbort ) );

      props.setProperty(
		OPTION_SHOW_ASM_TEXT,
                Boolean.toString( this.showAsmText ) );

      props.setProperty(
		OPTION_INCLUDE_BASIC_LINES,
                Boolean.toString( this.inclBasicLines ) );

      props.setProperty(
		OPTION_INIT_VARS,
                Boolean.toString( this.initVars ) );

      props.setProperty(
		OPTION_WARN_IMPLICIT_DECLS,
		Boolean.toString( this.warnImplicitDecls ) );

      props.setProperty(
		OPTION_WARN_TOO_MANY_DIGITS,
		Boolean.toString( this.warnTooManyDigits ) );

      props.setProperty(
		OPTION_WARN_UNUSED_ITEMS,
		Boolean.toString( this.warnUnusedItems ) );

      if( this.breakOption != null ) {
	String value = VALUE_BREAK_ALWAYS;
	switch( this.breakOption ) {
	  case NEVER:
	    value = VALUE_BREAK_NEVER;
	    break;
	  case INPUT:
	    value = VALUE_BREAK_INPUT;
	    break;
	}
	props.setProperty( OPTION_BREAK, value );
      }
    }
  }
}
