/*
 * (c) 2012-2021 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * LLC2-spezifische Code-Erzeugung des BASIC-Compilers
 * mit Ansteuerung der HIRES-Vollgrafik
 *
 * Fuer den LLC2 wird mit Ausnahme der Grafikbefehle
 * der gleiche Code erzeugt wie fuer SCCH.
 * Aus diesem Grund ist die Klasse von SCCHTaget abgeleitet.
 */

package jkcemu.programming.basic.target;

import jkcemu.base.EmuSys;
import jkcemu.emusys.LLC2;
import jkcemu.emusys.ac1_llc2.AbstractSCCHSys;
import jkcemu.programming.basic.AsmCodeBuf;
import jkcemu.programming.basic.BasicCompiler;
import jkcemu.programming.basic.BasicLibrary;


public class LLC2HIRESTarget extends SCCHTarget
{
  public static final String BASIC_TARGET_NAME = "TARGET_LLC2";

  private boolean needsScreenSizeChar;
  private boolean needsScreenSizePixel;
  private boolean pixUtilAppended;
  private boolean xpsetAppended;
  private boolean xptestAppended;
  private boolean usesScreens;


  public LLC2HIRESTarget()
  {
    setNamedValue( "GRAPHICSCREEN", 1 );
    setNamedValue( "LASTSCREEN", 1 );
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public void appendBssTo( AsmCodeBuf buf )
  {
    super.appendBssTo( buf );
    if( this.usesScreens ) {
      buf.append( "X_M_SCREEN:\tDS\t1\n" );
    }
  }


  @Override
  public void appendEtcPastXOutTo( AsmCodeBuf buf )
  {
    if( this.needsScreenSizeChar ) {
      if( this.usesScreens ) {
	buf.append( "X_HCHR:\tLD\tHL,0020H\n"
		+ "\tJR\tX_SSZC\n"
		+ "X_WCHR:\tLD\tHL,0040H\n"
		+ "X_SSZC:\tLD\tA,(X_M_SCREEN)\n"
		+ "\tOR\tA\n"
		+ "\tRET\tZ\n"
		+ "\tLD\tL,00H\n"
		+ "\tRET\n" );
      } else {
	buf.append( "X_HCHR:\tLD\tHL,0020H\n"
		+ "\tRET\n"
		+ "X_WCHR:\tLD\tHL,0040H\n"
		+ "\tRET\n" );
      }
    }
    if( this.needsScreenSizePixel ) {
      if( this.usesScreens ) {
	buf.append( "X_HPIX:\tLD\tHL,0100H\n"
		+ "\tJR\tX_SSZP\n"
		+ "X_WPIX:\tLD\tHL,0200H\n"
		+ "X_SSZP:\tLD\tA,(X_M_SCREEN)\n"
		+ "\tDEC\tA\n"
		+ "\tRET\tZ\n"
		+ "\tLD\tHL,0000H\n"
		+ "\tRET\n" );
      } else {
	buf.append( "X_HPIX:\n"
		+ "X_WPIX:\tLD\tHL,0000H\n"
		+ "\tRET\n" );
      }
    }
  }


  @Override
  public void appendPreExitTo( AsmCodeBuf buf )
  {
    if( this.usesScreens ) {
      buf.append( "\tXOR\tA\n"
		+ "\tOUT\t(0EEH),A\n" );
    }
  }


  @Override
  public void appendHCharTo( AsmCodeBuf buf )
  {
    buf.append( "\tCALL\tX_HCHR\n" );
    this.needsScreenSizeChar = true;
  }


  @Override
  public void appendHPixelTo( AsmCodeBuf buf )
  {
    buf.append( "\tCALL\tX_HPIX\n" );
    this.needsScreenSizePixel = true;
  }


  @Override
  public void appendInitTo( AsmCodeBuf buf )
  {
    super.appendInitTo( buf );
    if( this.usesScreens ) {
      buf.append( "\tXOR\tA\n"
		+ "\tLD\t(X_M_SCREEN),A\n"
		+ "\tOUT\t(0EEH),A\n" );
    }
  }


  @Override
  public void appendSwitchToTextScreenTo( AsmCodeBuf buf )
  {
    if( this.usesScreens ) {
      buf.append( "\tXOR\tA\n"
		+ "\tOUT\t(0EEH),A\n"
		+ "\tLD\t(X_M_SCREEN),A\n" );
    }
  }


  @Override
  public void appendWCharTo( AsmCodeBuf buf )
  {
    buf.append( "\tCALL\tX_WCHR\n" );
    this.needsScreenSizeChar = true;
  }


  @Override
  public void appendWPixelTo( AsmCodeBuf buf )
  {
    buf.append( "\tCALL\tX_WPIX\n" );
    this.needsScreenSizePixel = true;
  }


  @Override
  public void appendXClsTo( AsmCodeBuf buf )
  {
    if( this.usesScreens ) {
      buf.append( "XCLS:\tLD\tA,(X_M_SCREEN)\n"
		+ "\tCP\t01\n"
		+ "\tJR\tZ,X_CLS_1\n"
		+ "\tLD\tA,0CH\n"
		+ "\tJR\tXOUTCH\n"
		+ "X_CLS_1:\n"
		+ "\tLD\tHL,8000H\n"
		+ "\tXOR\tA\n"
		+ "\tLD\tC,40H\n"
		+ "X_CLS_2:\n"
		+ "\tLD\tB,00H\n"
		+ "X_CLS_3:\n"
		+ "\tLD\t(HL),A\n"
		+ "\tINC\tHL\n"
		+ "\tDJNZ\tX_CLS_3\n"
		+ "\tDEC\tC\n"
		+ "\tJR\tNZ,X_CLS_2\n"
		+ "\tRET\n" );
    } else {
      buf.append( "XCLS:\tLD\tA,0CH\n"
		+ "\tJR\tXOUTCH\n" );
    }
    appendXOutchTo( buf );
  }


  /*
   * Ermittlung der Cursor-Position
   * Rueckgabe:
   *   HL: Bit 0..5:  Spalte
   *       Bit 6..10: Zeile
   *   CY=1: Fehler -> HL=0FFFFH
   */
  @Override
  protected void appendXGetCrsPosTo( AsmCodeBuf buf )
  {
    if( !this.xGetCrsPosAppended ) {
      buf.append( "X_GET_CRS_POS:\n"
		+ "\tLD\tHL,(1800H)\n"
		+ "\tLD\tA,H\n"
		+ "\tAND\t0F8H\n"
		+ "\tCP\t0C0H\n"
		+ "\tRET\tZ\n"		// CY=0
		+ "\tCP\t0F8H\n"
		+ "\tRET\tZ\n"		// CY=0
		+ "\tLD\tHL,0FFFFH\n"
		+ "\tSCF\n"
		+ "\tRET\n" );
      this.xGetCrsPosAppended = true;
    }
  }


  /*
   * Zeichnen einer horizontalen Linie
   * Parameter:
   *   BC: Laenge - 1
   *   DE: linke X-Koordinate, nicht kleiner 0
   *   HL: Y-Koordinate
   */
  @Override
  public void appendXHLineTo( AsmCodeBuf buf, BasicCompiler compiler )
  {
    buf.append( "XHLINE:\tBIT\t7,B\n"
		+ "\tRET\tNZ\n"
		+ "\tPUSH\tBC\n"
		+ "\tCALL\tX_PINFO\n"
		+ "\tPOP\tBC\n"
		+ "\tRET\tC\n"
		+ "\tLD\tD,00H\n"
		+ "X_HLINE_1:\n"
		+ "\tOR\tD\n"
		+ "\tLD\tD,A\n"
		+ "\tSRL\tA\n"
		+ "\tJR\tNC,X_HLINE_2\n"
		+ "\tLD\tA,D\n" );
    if( this.usesX_M_PEN ) {
      buf.append( "\tCALL\tX_PSET_A\n" );
    } else {
      buf.append( "\tOR\t(HL)\n"
		+ "\tLD\t(HL),A\n" );
    }
    buf.append( "\tINC\tHL\n"
		+ "\tLD\tA,L\n"
		+ "\tAND\t3FH\n"
		+ "\tRET\tZ\n"
		+ "\tLD\tA,80H\n"
		+ "\tLD\tD,00H\n"
		+ "X_HLINE_2:\n"
		+ "\tDEC\tBC\n"
		+ "\tBIT\t7,B\n"
		+ "\tJR\tZ,X_HLINE_1\n"
		+ "\tLD\tA,D\n"
		+ "\tOR\tA\n"
		+ "\tJR\tNZ,X_PSET_A\n"
		+ "\tRET\n" );
    appendXPSetTo( buf, compiler );
  }


  /*
   * XPAINT_LEFT:
   *   Fuellen einer Linie ab dem uebergebenen Punkt nach links,
   *   Der Startpunkt selbst wird nicht geprueft
   *   Parameter:
   *     PAINT_M_X:    X-Koordinate Startpunkt
   *     PAINT_M_Y:    Y-Koordinate Startpunkt
   *   Rueckgabe:
   *     (PAINT_M_X1): X-Endkoordinate der gefuellten Linie,
   *                   kleiner oder gleich X-Startpunkt
   *
   * XPAINT_RIGHT:
   *   Fuellen einer Linie ab dem uebergebenen Punkt nach rechts
   *   Parameter:
   *     PAINT_M_X:    X-Koordinate Startpunkt
   *     PAINT_M_Y:    Y-Koordinate Startpunkt
   *   Rueckgabe:
   *     CY=1:         Pixel im Startpunkt bereits gesetzt (gefuellt)
   *                   oder ausserhalb des sichtbaren Bereichs
   *     (PAINT_M_X2): X-Endkoordinate der gefuellten Linie, nur bei CY=0
   */
  @Override
  public void appendXPaintTo( AsmCodeBuf buf, BasicCompiler compiler )
  {
    buf.append( "XPAINT_LEFT:\n"
		+ "\tLD\tDE,(PAINT_M_X)\n"
		+ "\tLD\tA,D\n"
		+ "\tOR\tE\n"
		+ "\tJR\tZ,X_PAINT_LEFT_6\n"
		+ "\tLD\tHL,(PAINT_M_Y)\n"
		+ "\tDEC\tDE\n"
		+ "\tPUSH\tDE\n"
		+ "\tCALL\tX_PINFO\n"
		+ "\tPOP\tDE\n"
		+ "\tJR\tC,X_PAINT_LEFT_5\n"
		+ "\tLD\tC,A\n"
		+ "\tLD\tB,(HL)\n"
		+ "X_PAINT_LEFT_1:\n"
		+ "\tLD\tA,B\n"
		+ "X_PAINT_LEFT_2:\n"
		+ "\tAND\tC\n"
		+ "\tJR\tNZ,X_PAINT_LEFT_4\n"
		+ "\tLD\tA,B\n"
		+ "\tOR\tC\n"
		+ "\tLD\tB,A\n"
		+ "\tDEC\tDE\n"
		+ "\tSLA\tC\n"
		+ "\tJR\tNC,X_PAINT_LEFT_2\n"
		+ "\tLD\t(HL),B\n"
		+ "\tDEC\tHL\n"
		+ "\tBIT\t0,D\n"
		+ "\tJR\tZ,X_PAINT_LEFT_3\n"
		+ "\tLD\tA,E\n"
		+ "\tINC\tA\n"
		+ "\tJR\tZ,X_PAINT_LEFT_5\n"
		+ "X_PAINT_LEFT_3:\n"
		+ "\tLD\tB,(HL)\n"
		+ "\tLD\tC,01H\n"
		+ "\tJR\tX_PAINT_LEFT_1\n"
		+ "X_PAINT_LEFT_4:\n"
		+ "\tLD\t(HL),B\n"
		+ "X_PAINT_LEFT_5:\n"
		+ "\tINC\tDE\n"
		+ "X_PAINT_LEFT_6:\n"
		+ "\tLD\t(PAINT_M_X1),DE\n"
		+ "\tRET\n"
		+ "XPAINT_RIGHT:\n"
		+ "\tLD\tDE,(PAINT_M_X)\n"
		+ "\tLD\tHL,(PAINT_M_Y)\n"
		+ "\tPUSH\tDE\n"
		+ "\tCALL\tX_PINFO\n"
		+ "\tPOP\tDE\n"
		+ "\tRET\tC\n"
		+ "\tLD\tC,A\n"
		+ "\tLD\tB,(HL)\n"
		+ "\tAND\tB\n"
		+ "\tSCF\n"
		+ "\tRET\tNZ\n"
		+ "\tJR\tX_PAINT_RIGHT_3\n"
		+ "X_PAINT_RIGHT_1:\n"
		+ "\tLD\tA,B\n"
		+ "X_PAINT_RIGHT_2:\n"
		+ "\tAND\tC\n"
		+ "\tJR\tNZ,X_PAINT_RIGHT_4\n"
		+ "X_PAINT_RIGHT_3:\n"
		+ "\tLD\tA,B\n"
		+ "\tOR\tC\n"
		+ "\tLD\tB,A\n"
		+ "\tINC\tDE\n"
		+ "\tSRL\tC\n"
		+ "\tJR\tNC,X_PAINT_RIGHT_2\n"
		+ "\tLD\t(HL),B\n"
		+ "\tINC\tHL\n"
		+ "\tLD\tA,D\n"
		+ "\tAND\t01H\n"
		+ "\tOR\tE\n"
		+ "\tJR\tZ,X_PAINT_RIGHT_5\n"
		+ "\tLD\tB,(HL)\n"
		+ "\tLD\tC,80H\n"
		+ "\tJR\tX_PAINT_RIGHT_1\n"
		+ "X_PAINT_RIGHT_4:\n"
		+ "\tLD\t(HL),B\n"
		+ "X_PAINT_RIGHT_5:\n"
		+ "\tDEC\tDE\n"
		+ "\tLD\t(PAINT_M_X2),DE\n"
		+ "\tOR\tA\n"			// CY=0
		+ "\tRET\n" );
    appendPixUtilTo( buf, compiler );
  }


  /*
   * Farbe eines Pixels ermitteln,
   * Die Rueckgabewerte sind identisch zur Funktion XPTEST
   *
   * Parameter:
   *   DE: X-Koordinate
   *   HL: Y-Koordinate
   * Rueckgabe:
   *   HL >= 0: Farbcode des Pixels
   *   HL=-1:   Pixel exisitiert nicht
   */
  @Override
  public void appendXPointTo( AsmCodeBuf buf, BasicCompiler compiler )
  {
    appendXPTestTo( buf, compiler );
  }


  /*
   * Zuruecksetzen eines Pixels
   * Parameter:
   *   DE: X-Koordinate
   *   HL: Y-Koordinate
   */
  @Override
  public void appendXPResTo( AsmCodeBuf buf, BasicCompiler compiler )
  {
    buf.append( "XPRES:\tCALL\tX_PINFO\n"
		+ "\tRET\tC\n" );
    if( this.usesX_M_PEN ) {
      buf.append( "\tJR\tX_PSET_2\n" );
      appendXPSetTo( buf, compiler );
    } else {
      buf.append( "\tCPL\n"
		+ "\tAND\t(HL)\n"
		+ "\tLD\t(HL),A\n"
		+ "\tRET\n" );
    }
    appendPixUtilTo( buf, compiler );
  }


  /*
   * Setzen eines Pixels unter Beruecksichtigung des eingestellten Stiftes
   * Parameter:
   *   DE: X-Koordinate
   *   HL: Y-Koordinate
   */
  @Override
  public void appendXPSetTo( AsmCodeBuf buf, BasicCompiler compiler )
  {
    if( !this.xpsetAppended ) {
      buf.append( "XPSET:\tCALL\tX_PINFO\n"
		+ "\tRET\tC\n"
		+ "X_PSET_A:\n" );
      if( this.usesX_M_PEN ) {
	buf.append( "\tLD\tE,A\n"
		+ "\tLD\tA,(X_M_PEN)\n"
		+ "\tDEC\tA\n"
		+ "\tJR\tZ,X_PSET_3\n"		// Stift 1 (Normal)
		+ "\tDEC\tA\n"
		+ "\tJR\tZ,X_PSET_1\n"		// Stift 2 (Loeschen)
		+ "\tDEC\tA\n"
		+ "\tRET\tNZ\n"
		+ "\tLD\tA,(HL)\n"		// Stift 3 (XOR-Mode)
		+ "\tXOR\tE\n"
		+ "\tLD\t(HL),A\n"
		+ "\tRET\n"
		+ "X_PSET_1:\n"			// Pixel loeschen
		+ "\tLD\tA,E\n"
		+ "X_PSET_2:\n"
		+ "\tCPL\n"
		+ "\tAND\t(HL)\n"
		+ "\tLD\t(HL),A\n"
		+ "\tRET\n"
		+ "X_PSET_3:\n"			// Pixel setzen
		+ "\tLD\tA,E\n" );
      }
      buf.append( "\tOR\t(HL)\n"
		+ "\tLD\t(HL),A\n"
		+ "\tRET\n" );
      appendPixUtilTo( buf, compiler );
      this.xpsetAppended = true;
    }
  }


  /*
   * Testen eines Pixels
   * Parameter:
   *   DE: X-Koordinate
   *   HL: Y-Koordinate
   * Rueckgabe:
   *   HL=0:  Pixel nicht gesetzt
   *   HL=1:  Pixel gesetzt
   *   HL=-1: Pixel existiert nicht
   */
  @Override
  public void appendXPTestTo( AsmCodeBuf buf, BasicCompiler compiler )
  {
    if( !this.xptestAppended ) {
      if( this.usesScreens ) {
	buf.append( "XPOINT:\n"
		+ "XPTEST:\tLD\tA,(X_M_SCREEN)\n"
		+ "\tCP\t01H\n"
		+ "\tJR\tNZ,X_PTEST_1\n"
		+ "\tCALL\tX_PINFO_1\n"
		+ "\tJR\tC,X_PTEST_1\n"
		+ "\tAND\t(HL)\n"
		+ "\tLD\tHL,0000H\n"
		+ "\tRET\tZ\n"
		+ "\tINC\tHL\n"
		+ "\tRET\n"
		+ "X_PTEST_1:\n"
		+ "\tLD\tHL,0FFFFH\n"
		+ "\tRET\n" );
	appendPixUtilTo( buf, compiler );
      } else {
	super.appendXPTestTo( buf, compiler );
      }
      this.xptestAppended = true;
    }
  }


  /*
   * Bildschirmmode einstellen
   *   HL: Screen-Nummer
   *        0: Textmode
   *        1: HIRES-Grafik auf 8000h
   * Rueckgabe:
   *   CY=1: Screen-Nummer nicht unterstuetzt
   */
  @Override
  public void appendXScreenTo( AsmCodeBuf buf )
  {
    if( this.usesScreens ) {
      buf.append( "XSCREEN:\n"
		+ "\tLD\tA,H\n"
		+ "\tOR\tA\n"
		+ "\tJR\tNZ,X_SCREEN_1\n"
		+ "\tLD\tA,(X_M_SCREEN)\n"
		+ "\tCP\tL\n"
		+ "\tRET\tZ\n"
		+ "\tLD\tA,L\n"
		+ "\tOR\tA\n"
		+ "\tJR\tZ,X_SCREEN_3\n"
		+ "\tDEC\tA\n"
		+ "\tJR\tZ,X_SCREEN_2\n"
		+ "X_SCREEN_1:\n"
		+ "\tSCF\n"
		+ "\tRET\n"
		+ "X_SCREEN_2:\n"
		+ "\tLD\tA,50H\n"		// HIRES, 8000h
		+ "X_SCREEN_3:\n"
		+ "\tOUT\t(0EEH),A\n"
		+ "\tLD\tA,L\n"
		+ "\tLD\t(X_M_SCREEN),A\n"
		+ "\tOR\tA\n"			// CY=0
		+ "\tRET\n" );
    }
  }


  @Override
  public int get100msLoopCount()
  {
    return 85;
  }


  @Override
  public String[] getBasicTargetNames()
  {
    return add( super.getBasicTargetNames(), BASIC_TARGET_NAME );
  }


  @Override
  public int getCompatibilityLevel( EmuSys emuSys )
  {
    int rv = 0;
    if( emuSys != null ) {
      if( emuSys instanceof LLC2 ) {
	rv = 3;
      } else if( emuSys instanceof AbstractSCCHSys ) {
	rv = 1;
      }
    }
    return rv;
  }


  @Override
  public int[] getVdipBaseIOAddresses()
  {
    return new int[] { 0xDC, 0xFC, 0xE4 };
  }


  @Override
  public void preAppendLibraryCode( BasicCompiler compiler )
  {
    super.preAppendLibraryCode( compiler );
    if( compiler.usesLibItem( BasicLibrary.LibItem.SCREEN )
	|| compiler.usesLibItem( BasicLibrary.LibItem.XSCREEN ) )
    {
      this.usesScreens = true;
    }
  }


  @Override
  public void reset()
  {
    super.reset();
    this.needsScreenSizeChar  = false;
    this.needsScreenSizePixel = false;
    this.pixUtilAppended      = false;
    this.xpsetAppended        = false;
    this.xptestAppended       = false;
    this.usesScreens          = false;
  }


  @Override
  public boolean supportsGraphics()
  {
    return true;
  }


  @Override
  public boolean supportsXCLS()
  {
    return true;
  }


  @Override
  public boolean supportsXHLINE()
  {
    return true;
  }


  @Override
  public boolean supportsXPAINT_LEFT_RIGHT()
  {
    return true;
  }


  @Override
  public String toString()
  {
    return "LLC2 mit HIRES-Grafik";
  }


	/* --- private Methoden --- */

  private void appendPixUtilTo( AsmCodeBuf buf, BasicCompiler compiler )
  {
    if( !this.pixUtilAppended ) {
      /*
       * Pruefen der Parameter und
       * ermitteln von Informationen zu einem Pixel
       *
       * Parameter:
       *   DE: X-Koordinate (0...512)
       *   HL: Y-Koordinate (0...255)
       *
       * Rueckgabe:
       *   CY=1: Pixel ausserhalb des gueltigen Bereichs
       *   A:    Bitmuster mit einem gesetzten Bit,
       *         dass das Pixel in der Speicherzelle beschreibt
       *   HL:   Speicherzelle, in der sich das Pixel befindet
       */
      if( this.usesScreens ) {
	buf.append( "X_PINFO:\n"
		+ "\tLD\tA,(X_M_SCREEN)\n"
		+ "\tCP\t01H\n"
		+ "\tJR\tNZ,X_PINFO_7\n"
		+ "X_PINFO_1:\n"
		+ "\tLD\tA,H\n"
		+ "\tOR\tA\n"
		+ "\tSCF\n"
		+ "\tRET\tNZ\n"
		+ "\tLD\tA,D\n"
		+ "\tOR\tA\n"
		+ "\tJR\tZ,X_PINFO_2\n"
		+ "\tCP\t02H\n"
		+ "\tCCF\n"
		+ "\tRET\tC\n"
		+ "X_PINFO_2:\n"
		+ "\tLD\tA,E\n"
		+ "\tAND\t07H\n"
		+ "\tLD\tB,A\n"
		+ "\tLD\tC,80H\n"
		+ "\tJR\tZ,X_PINFO_4\n"
		+ "X_PINFO_3:\n"
		+ "\tSRL\tC\n"
		+ "\tDJNZ\tX_PINFO_3\n"
		+ "X_PINFO_4:\n"
		+ "\tSRL\tD\n"
		+ "\tRR\tE\n"
		+ "\tSRL\tE\n"
		+ "\tSRL\tE\n"
		+ "\tPUSH\tDE\n"
		+ "\tPUSH\tHL\n"
		+ "\tLD\tA,L\n"
		+ "\tLD\tHL,0000H\n"
		+ "\tAND\t07H\n"
		+ "\tJR\tZ,X_PINFO_6\n"
		+ "\tLD\tDE,0800H\n"
		+ "X_PINFO_5:\n"
		+ "\tADD\tHL,DE\n"
		+ "\tDEC\tA\n"
		+ "\tJR\tNZ,X_PINFO_5\n"
		+ "X_PINFO_6:\n"
		+ "\tEX\tDE,HL\n"
		+ "\tPOP\tHL\n"
		+ "\tLD\tA,L\n"
		+ "\tAND\t0F8H\n"
		+ "\tLD\tL,A\n"
		+ "\tADD\tHL,HL\n"
		+ "\tADD\tHL,HL\n"
		+ "\tADD\tHL,HL\n"
		+ "\tADD\tHL,DE\n"
		+ "\tEX\tDE,HL\n"
		+ "\tLD\tHL,0BFC0H\n"
		+ "\tOR\tA\n"			// CY=0
		+ "\tSBC\tHL,DE\n"
		+ "\tPOP\tDE\n"
		+ "\tADD\tHL,DE\n"
		+ "\tLD\tA,C\n"
		+ "\tOR\tA\n"			// CY=0
		+ "\tRET\n"
		+ "X_PINFO_7:\n" );
      } else {
	buf.append( "X_PINFO:\n" );
      }
      appendExitNoGraphicsScreenTo( buf, compiler );
      this.pixUtilAppended = true;
    }
  }
}
