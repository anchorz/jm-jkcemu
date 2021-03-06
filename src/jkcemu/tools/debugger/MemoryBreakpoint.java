/*
 * (c) 2011-2021 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Halte-/Log-Punkt auf eine Speicherzelle oder einen Speicherbereich
 */

package jkcemu.tools.debugger;

import org.xml.sax.Attributes;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import z80emu.Z80CPU;
import z80emu.Z80InterruptSource;


public class MemoryBreakpoint extends ImportableBreakpoint
{
  public static final String BP_TYPE = "memory";

  private static final String ATTR_ADDR      = "addr";
  private static final String ATTR_SIZE      = "size";
  private static final String ATTR_ON_READ   = "on_read";
  private static final String ATTR_ON_WRITE  = "on_write";
  private static final String ATTR_CONDITION = "condition";
  private static final String ATTR_MASK      = "mask";
  private static final String ATTR_VALUE     = "value";

  // Laenge der einzelnen Befehle
  private static int[] instLen = {
	1, 3, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1,		// 0x00
	2, 3, 1, 1, 1, 1, 2, 1, 2, 1, 1, 1, 1, 1, 2, 1,		// 0x10
	2, 3, 3, 1, 1, 1, 2, 1, 2, 1, 3, 1, 1, 1, 2, 1,		// 0x20
	2, 3, 3, 1, 1, 1, 2, 1, 2, 1, 3, 1, 1, 1, 2, 1,		// 0x30
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,		// 0x40
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,		// 0x50
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,		// 0x60
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,		// 0x70
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,		// 0x80
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,		// 0x90
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,		// 0xA0
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,		// 0xB0
	1, 1, 3, 3, 3, 1, 2, 1, 1, 1, 3, 2, 3, 3, 2, 1,		// 0xC0
	1, 1, 3, 2, 3, 1, 2, 1, 1, 1, 3, 2, 3, 0, 2, 1,		// 0xD0
	1, 1, 3, 1, 3, 1, 2, 1, 1, 1, 3, 1, 3, 0, 2, 1,		// 0xE0
	1, 1, 3, 1, 3, 1, 2, 1, 1, 1, 3, 1, 3, 0, 2, 1 };	// 0xFF

  private static int[] instLenDD_FD = {
	2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,		// 0x00
	2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,		// 0x10
	2, 4, 4, 2, 2, 2, 3, 2, 2, 2, 4, 2, 2, 2, 3, 2,		// 0x20
	2, 2, 2, 2, 3, 3, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,		// 0x30
	2, 2, 2, 2, 2, 2, 3, 2, 2, 2, 2, 2, 2, 2, 3, 2,		// 0x40
	2, 2, 2, 2, 2, 2, 3, 2, 2, 2, 2, 2, 2, 2, 3, 2,		// 0x50
	2, 2, 2, 2, 2, 2, 3, 2, 2, 2, 2, 2, 2, 2, 3, 2,		// 0x60
	3, 3, 3, 3, 3, 3, 2, 3, 2, 2, 2, 2, 2, 2, 3, 2,		// 0x70
	2, 2, 2, 2, 2, 2, 3, 2, 2, 2, 2, 2, 2, 2, 3, 2,		// 0x80
	2, 2, 2, 2, 2, 2, 3, 2, 2, 2, 2, 2, 2, 2, 3, 2,		// 0x90
	2, 2, 2, 2, 2, 2, 3, 2, 2, 2, 2, 2, 2, 2, 3, 2,		// 0xA0
	2, 2, 2, 2, 2, 2, 3, 2, 2, 2, 2, 2, 2, 2, 3, 2,		// 0xB0
	2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2,		// 0xC0
	2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,		// 0xD0
	2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,		// 0xE0
	2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2 };	// 0xF0

  private static int[] instLenED = {
	2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,		// 0x00
	2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,		// 0x10
	2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,		// 0x20
	2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,		// 0x30
	2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2,		// 0x40
	2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2,		// 0x50
	2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2,		// 0x60
	2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2,		// 0x70
	2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,		// 0x80
	2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,		// 0x90
	2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,		// 0xA0
	2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,		// 0xB0
	2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,		// 0xC0
	2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,		// 0xD0
	2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,		// 0xE0
	2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2 };	// 0xF0

  private int     begAddr;
  private int     endAddr;
  private boolean onRead;
  private boolean onWrite;
  private int     mask;
  private String  cond;
  private int     value;


  public MemoryBreakpoint(
		DebugFrm debugFrm,
		String   name,
		int      begAddr,
		int      endAddr,
		boolean  onRead,
		boolean  onWrite,
		int      mask,
		String   cond,
		int      value ) throws InvalidParamException
  {
    super( debugFrm, name );
    this.onRead  = onRead;
    this.onWrite = onWrite;
    this.mask    = mask & 0xFF;
    this.cond    = checkCondition( cond );
    this.value   = value & 0xFF;
    setAddresses( begAddr, endAddr );
  }


  public static MemoryBreakpoint createByAttrs(
					DebugFrm   debugFrm,
					Attributes attrs )
  {
    MemoryBreakpoint bp = null;
    if( attrs != null ) {
      Integer addr = BreakpointVarLoader.readInteger(
					attrs.getValue( ATTR_ADDR ) );
      if( addr != null ) {
	try {
	  int    size  = getHex4Value( attrs, ATTR_SIZE );
	  int    mask  = 0xFF;
	  int    value = 0;
	  String cond  = null;
	  try {
	    cond = checkCondition( attrs.getValue( ATTR_CONDITION ) );
	    if( cond != null ) {
	      mask  = getHex2Value( attrs, ATTR_MASK );
	      value = getHex2Value( attrs, ATTR_VALUE );
	    }
	  }
	  catch( InvalidParamException ex ) {}
	  bp = new MemoryBreakpoint(
			debugFrm,
			checkName( attrs.getValue( ATTR_NAME ) ),
			addr.intValue(),
			size > 1 ? (addr + size - 1) : -1,
			BreakpointVarLoader.getBooleanValue(
							attrs,
							ATTR_ON_READ ),
			BreakpointVarLoader.getBooleanValue(
							attrs,
							ATTR_ON_WRITE ),
			mask,
			cond,
			value );
	}
	catch( NumberFormatException ex ) {}
	catch( InvalidParamException ex ) {}
      }
    }
    return bp;
  }


  public int getBegAddress()
  {
    return this.begAddr;
  }


  public String getCondition()
  {
    return this.cond;
  }


  public int getEndAddress()
  {
    return this.endAddr;
  }


  public int getMask()
  {
    return this.mask;
  }


  public boolean getOnRead()
  {
    return this.onRead;
  }


  public boolean getOnWrite()
  {
    return this.onWrite;
  }


  public int getValue()
  {
    return this.value;
  }


  public void setAddresses( int begAddr, int endAddr )
  {
    this.begAddr = begAddr & 0xFFFF;
    this.endAddr = (endAddr >= 0 ? (endAddr & 0xFFFF) : -1);

    StringBuilder buf  = new StringBuilder();
    String        name = getName();
    if( name != null ) {
      buf.append( name );
      buf.append( ':' );
    }
    buf.append( String.format( "%04X", this.begAddr ) );
    if( this.endAddr >= 0 ) {
      buf.append( String.format( "-%04X", this.endAddr ) );
    }
    buf.append( ':' );
    if( this.onRead ) {
      buf.append( 'R' );
    }
    if( this.onWrite ) {
      buf.append( 'W' );
    }
    if( this.cond != null ) {
      if( this.mask != 0xFF ) {
        buf.append(
                String.format(
                        ":(Wert&%02X)%s%02X",
                        this.mask,
                        this.cond,
                        this.value ) );
      } else {
        buf.append(
                String.format(
                        ":Wert%s%02X",
                        this.cond,
                        this.value ) );
      }
    }
    setText( buf.toString() );
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public int getAddress()
  {
    return this.begAddr;
  }


  @Override
  protected boolean matchesImpl( Z80CPU cpu, Z80InterruptSource iSource )
  {
    boolean rv  = false;
    int     pc  = cpu.getRegPC();
    int     op0 = cpu.getMemByte( pc, false );
    int     op1 = cpu.getMemByte( pc + 1, false );
    int     op2 = cpu.getMemByte( pc + 2, false );
    int     op3 = cpu.getMemByte( pc + 3, false );
    if( this.onRead ) {
      rv = matchesRead( cpu, pc, op0, op1, op2, op3 );
    }
    if( !rv && this.onWrite ) {
      rv = matchesWrite( cpu, iSource, pc, op0, op1, op2, op3 );
    }
    return rv;
  }


  @Override
  public void writeTo( Document doc, Node parent )
  {
    Element elem = createBreakpointElement( doc, BP_TYPE );
    elem.setAttribute( ATTR_ADDR, toHex4( this.begAddr ) );
    elem.setAttribute(
		ATTR_SIZE,
		toHex4( this.endAddr > this.begAddr ?
				(this.endAddr - this.begAddr + 1)
				: 1 ) );
    elem.setAttribute( ATTR_ON_READ, Boolean.toString( this.onRead ) );
    elem.setAttribute( ATTR_ON_WRITE, Boolean.toString( this.onWrite ) );
    appendAttributesTo( elem );
    if( this.cond != null ) {
      if( !this.cond.isEmpty() ) {
	elem.setAttribute( ATTR_CONDITION, this.cond );
	elem.setAttribute( ATTR_MASK, toHex2( this.mask ) );
	elem.setAttribute( ATTR_VALUE, toHex2( this.value ) );
      }
    }
    parent.appendChild( elem );
  }


	/* --- private Methoden --- */

  private static int computeRelAddr( int baseAddr, int d )
  {
    return (baseAddr + (int) ((byte) d)) & 0xFFFF;
  }


  private static int getBitInstValue( Z80CPU cpu, int opc, int value )
  {
    int rv = -1;
    switch( opc & 0xF8 ) {
      case 0x00:                                        // RLC
	rv = (value << 1) & 0xFE;
	if( (value & 0x80) != 0 ) {
	  rv |= 0x01;
	}
	break;
      case 0x08:                                        // RRC
	rv = (value >> 1) & 0x7F;
	if( (value & 0x01) != 0 ) {
	  rv |= 0x80;
	}
	break;
      case 0x10:                                        // RL
	rv = (value << 1) & 0xFE;
	if( cpu.getFlagCarry() ) {
	  rv |= 0x01;
	}
	break;
      case 0x18:                                        // RR
	rv = (value >> 1) & 0x7F;
	if( cpu.getFlagCarry() ) {
	  rv |= 0x80;
	}
	break;
      case 0x20:                                        // SLA
	rv = (value << 1) & 0xFE;
	break;
      case 0x28:                                        // SRA
	rv = ((value >> 1) & 0x7F) | (value & 0x80);
	break;
      case 0x30:                                        // *SLL
	rv = ((value << 1) & 0xFE) | 0x01;
	break;
      case 0x38:                                        // SRL
	rv = (value >> 1) & 0x7F;
	break;
      case 0x80:                                        // RES 0
	rv = value & 0xFE;
	break;
      case 0x88:                                        // RES 1
	rv = value & 0xFD;
	break;
      case 0x90:                                        // RES 2
	rv = value & 0xFB;
	break;
      case 0x98:                                        // RES 3
	rv = value & 0xF7;
	break;
      case 0xA0:                                        // RES 4
	rv = value & 0xEF;
	break;
      case 0xA8:                                        // RES 5
	rv = value & 0xDF;
	break;
      case 0xB0:                                        // RES 6
	rv = value & 0xBF;
	break;
      case 0xB8:                                        // RES 7
	rv = value & 0x7F;
	break;
      case 0xC0:                                        // SET 0
	rv = value | 0x01;
	break;
      case 0xC8:                                        // SET 1
	rv = value | 0x02;
	break;
      case 0xD0:                                        // SET 2
	rv = value | 0x04;
	break;
      case 0xD8:                                        // SET 3
	rv = value | 0x08;
	break;
      case 0xE0:                                        // SET 4
	rv = value | 0x10;
	break;
      case 0xE8:                                        // SET 5
	rv = value | 0x20;
	break;
      case 0xF0:                                        // SET 6
	rv = value | 0x40;
	break;
      case 0xF8:                                        // SET 7
	rv = value | 0x80;
	break;
    }
    return rv;
  }


  private boolean matchesRead(
			Z80CPU cpu,
			int    pc,
			int    op0,
			int    op1,
			int    op2,
			int    op3 )
  {
    boolean rv = false;

    /*
     * Pruefen, ob schon das Lesen der Befehlscodes
     * dem Halte-/Log-Punkt entspricht
     */
    int opLen = 0;
    if( (op0 == 0xDD) || (op0 == 0xFD) ) {
      if( (op1 >= 0) && (op1 < instLenDD_FD.length) ) {
	opLen = instLenDD_FD[ op1 ];
      }
    } else if( op0 == 0xED ) {
      if( (op1 >= 0) && (op1 < instLenED.length) ) {
	opLen = instLenED[ op1 ];
      }
    } else {
      if( (op0 >= 0) && (op1 < instLen.length) ) {
	opLen = instLen[ op0 ];
      }
    }
    int nextPC = pc + opLen;
    if( nextPC > 0x10000 ) {
      nextPC = 0x10000;
    }
    for( int tmpAddr = pc; tmpAddr < nextPC; tmpAddr++  ) {
      if( matchesReadAccess( tmpAddr, cpu ) ) {
	rv = true;
	break;
      }
    }
    if( !rv ) {

      /*
       * Pruefen, ob durch die Ausfuehrung eines Befehls
       * ein Halte-/Log-Punkt erreicht wird.
       */
      int addr1 = -1;
      int addr2 = -1;
      switch( op0 ) {
	case 0x0A:				// LD A,(BC)
	  addr1 = cpu.getRegBC();
	  break;
	case 0x1A:				// LD A,(DE)
	  addr1 = cpu.getRegDE();
	  break;
	case 0x2A:				// LD HL,(nn)
	  addr1 = (op2 << 8) | op1;
	  addr2 = (addr1 + 1) & 0xFFFF;
	  break;
	case 0x34:				// INC (HL)
	case 0x35:				// DEC (HL)
	case 0x46:				// LD B,(HL)
	case 0x4E:				// LD C,(HL)
	case 0x56:				// LD D,(HL)
	case 0x5E:				// LD E,(HL)
	case 0x66:				// LD H,(HL)
	case 0x6E:				// LD L,(HL)
	case 0x7E:				// LD A,(HL)
	case 0x86:				// ADD (HL)
	case 0x8E:				// ADC (HL)
	case 0x96:				// SUB (HL)
	case 0x9E:				// SBC (HL)
	case 0xA6:				// AND (HL)
	case 0xAE:				// XOR (HL)
	case 0xB6:				// OR (HL)
	case 0xBE:				// CP (HL)
	  addr1 = cpu.getRegHL();
	  break;
	case 0x3A:				// LD A,(nn)
	  addr1 = (op2 << 8) | op1;
	  break;
	case 0xC0:				// RET NZ
	case 0xC1:				// POP BC
	case 0xC8:				// RET Z
	case 0xC9:				// RET
	case 0xD0:				// RET NC
	case 0xD1:				// POP DE
	case 0xD8:				// RET C
	case 0xE0:				// RET PC
	case 0xE1:				// POP HL
	case 0xE8:				// RET PE
	case 0xEB:				// EX (SP),HL
	case 0xF0:				// RET P
	case 0xF1:				// POP AF
	case 0xF8:				// RET M
	  addr1 = cpu.getRegSP();
	  addr2 = (addr1 + 1) & 0xFFFF;
	  break;
	case 0xCB:
	  if( (op1 < 0x80) && ((op1 & 0x07) == 0x06) ) {
	    // Rotations-, Schiebe- und Bittestbefehle auf (HL)
	    addr1 = cpu.getRegHL();
	  }
	  break;
	case 0xED:
	  switch( op1 ) {
	    case 0x4B:				// LD BC,(nn)
	    case 0x5B:				// LD DE,(nn)
	    case 0x6B:				// LD *HL,(nn)
	    case 0x7B:				// LD SP,(nn)
	      addr1 = (op3 << 8) | op2;
	      addr2 = (addr1 + 1) & 0xFFFF;
	      break;
	    case 0x45:				// RETN
	    case 0x4D:				// RETI
	    case 0x55:				// *RETN
	    case 0x5D:				// *RETN
	    case 0x65:				// *RETN
	    case 0x6D:				// *RETN
	    case 0x75:				// *RETN
	    case 0x7D:				// *RETN
	      addr1 = cpu.getRegSP();
	      addr2 = (addr1 + 1) & 0xFFFF;
	      break;
	    case 0x67:				// RRD
	    case 0x6F:				// RLD
	    case 0xA0:				// LDI
	    case 0xA1:				// CPI
	    case 0xA3:				// OUTI
	    case 0xB0:				// LDIR
	    case 0xB1:				// CPIR
	    case 0xB3:				// OTIR
	    case 0xA8:				// LDD
	    case 0xA9:				// CPD
	    case 0xAB:				// OUTD
	    case 0xB8:				// LDDR
	    case 0xB9:				// CPDR
	    case 0xBB:				// OTDR
	      addr1 = cpu.getRegHL();
	      break;
	  }
	  break;
	case 0xDD:
	case 0xFD:
	  {
	    int ixy = (op0 == 0xDD ? cpu.getRegIX() : cpu.getRegIY());
	    switch( op1 ) {
	      case 0x34:			// INC (IXY+d)
	      case 0x35:			// DEC (IXY+d)
	      case 0x46:			// LD B,(IXY+d)
	      case 0x4E:			// LD C,(IXY+d)
	      case 0x56:			// LD D,(IXY+d)
	      case 0x5E:			// LD E,(IXY+d)
	      case 0x66:			// LD H,(IXY+d)
	      case 0x6E:			// LD L,(IXY+d)
	      case 0x7E:			// LD A,(IXY+d)
	      case 0x86:			// ADD (IXY+d)
	      case 0x8E:			// ADC (IXY+d)
	      case 0x96:			// SUB (IXY+d)
	      case 0x9E:			// SBC (IXY+d)
	      case 0xA6:			// AND (IXY+d)
	      case 0xAE:			// XOR (IXY+d)
	      case 0xB6:			// OR (IXY+d)
	      case 0xBE:			// CP (IXY+d)
		addr1 = computeRelAddr( ixy, op2 );
		break;
	      case 0xCB:
		if( op3 < 0x80 ) {
		  // Rotations-, Schiebe- und Bittestbefehle auf (IXY+d)
		  addr1 = computeRelAddr( ixy, op2 );
		}
		break;
	      case 0xE1:			// POP IXY
	      case 0xEB:			// EX (SP),IXY
		addr1 = cpu.getRegSP();
		addr2 = (addr1 + 1) & 0xFFFF;
		break;
	    }
	  }
	  break;
      }
      if( addr1 >= 0 ) {
	rv = matchesReadAccess( addr1, cpu );
      }
      if( !rv && (addr2 >= 0) ) {
	rv = matchesReadAccess( addr2, cpu );
      }
    }
    return rv;
  }


  private boolean matchesReadAccess( int addr, Z80CPU cpu )
  {
    boolean rv = false;
    if( (addr == this.begAddr)
	|| ((addr >= this.begAddr) && (addr <= this.endAddr)) )
    {
      if( this.cond != null ) {
	rv = checkValues(
		cpu.getMemByte( addr, false ) & this.mask,
		this.cond,
		this.value );
      } else {
	rv = true;
      }
    }
    return rv;
  }


  private boolean matchesWrite(
			Z80CPU             cpu,
			Z80InterruptSource iSource,
			int                pc,
			int                op0,
			int                op1,
			int                op2,
			int                op3 )
  {
    boolean rv     = false;
    int     addr1  = -1;
    int     addr2  = -1;
    int     value1 = -1;
    int     value2 = -1;
    switch( op0 ) {
      case 0x02:				// LD (BC),A
	addr1  = cpu.getRegBC();
	value1 = cpu.getRegA();
	break;
      case 0x12:				// LD (DE),A
	addr1  = cpu.getRegDE();
	value1 = cpu.getRegA();
	break;
      case 0x22:				// LD (nn),HL
	addr1  = (op2 << 8) | op1;
	addr2  = (addr1 + 1) & 0xFFFF;
	value1 = cpu.getRegL();
	value2 = cpu.getRegH();
	break;
      case 0x32:				// LD (nn),A
	addr1  = (op2 << 8) | op1;
	value1 = cpu.getRegA();
	break;
      case 0x34:				// INC (HL)
	addr1  = cpu.getRegHL();
	value1 = (cpu.getMemByte( addr1, false ) + 1) & 0xFF;
	break;
      case 0x35:				// DEC (HL)
	addr1  = cpu.getRegHL();
	value1 = (cpu.getMemByte( addr1, false ) - 1) & 0xFF;
	break;
      case 0x36:				// LD (HL),n
	addr1  = cpu.getRegHL();
	value1 = op1;
	break;
      case 0x70:				// LD (HL),B
	addr1  = cpu.getRegHL();
	value1 = cpu.getRegB();
	break;
      case 0x71:				// LD (HL),C
	addr1  = cpu.getRegHL();
	value1 = cpu.getRegC();
	break;
      case 0x72:				// LD (HL),D
	addr1  = cpu.getRegHL();
	value1 = cpu.getRegC();
	break;
      case 0x73:				// LD (HL),E
	addr1  = cpu.getRegHL();
	value1 = cpu.getRegE();
	break;
      case 0x74:				// LD (HL),H
	addr1  = cpu.getRegHL();
	value1 = cpu.getRegH();
	break;
      case 0x75:				// LD (HL),L
	addr1  = cpu.getRegHL();
	value1 = cpu.getRegL();
	break;
      case 0x77:				// LD (HL),A
	addr1  = cpu.getRegHL();
	value1 = cpu.getRegA();
	break;
      case 0xC4:				// CALL NZ,nn
      case 0xCC:				// CALL Z,nn
      case 0xCD:				// CALL nn
      case 0xD4:				// CALL NC,nn
      case 0xDC:				// CALL C,nn
      case 0xE4:				// CALL PO,nn
      case 0xEC:				// CALL PE,nn
      case 0xF4:				// CALL P,nn
      case 0xFC:				// CALL M,nn
	addr1  = (cpu.getRegSP() - 2) & 0xFFFF;
	addr2  = (cpu.getRegSP() - 1) & 0xFFFF;
	value1 = (pc + 3) & 0xFF;
	value2 = ((pc + 3) >> 8) & 0xFF;
	break;
      case 0xC5:				// PUSH BC
	addr1  = (cpu.getRegSP() - 2) & 0xFFFF;
	addr2  = (cpu.getRegSP() - 1) & 0xFFFF;
	value1 = cpu.getRegC();
	value2 = cpu.getRegB();
	break;
      case 0xD5:				// PUSH DE
	addr1  = (cpu.getRegSP() - 2) & 0xFFFF;
	addr2  = (cpu.getRegSP() - 1) & 0xFFFF;
	value1 = cpu.getRegE();
	value2 = cpu.getRegD();
	break;
      case 0xE5:				// PUSH HL
	addr1  = (cpu.getRegSP() - 2) & 0xFFFF;
	addr2  = (cpu.getRegSP() - 1) & 0xFFFF;
	value1 = cpu.getRegL();
	value2 = cpu.getRegH();
	break;
      case 0xF5:				// PUSH AF
	addr1  = (cpu.getRegSP() - 2) & 0xFFFF;
	addr2  = (cpu.getRegSP() - 1) & 0xFFFF;
	value1 = cpu.getRegF();
	value2 = cpu.getRegA();
	break;
      case 0xC7:				// RST 00
      case 0xCF:				// RST 08
      case 0xD7:				// RST 10
      case 0xDF:				// RST 18
      case 0xE7:				// RST 20
      case 0xEF:				// RST 28
      case 0xF7:				// RST 30
      case 0xFF:				// RST 38
	addr1  = (cpu.getRegSP() - 2) & 0xFFFF;
	addr2  = (cpu.getRegSP() - 1) & 0xFFFF;
	value1 = (pc + 1) & 0xFF;
	value2 = ((pc + 1) >> 8) & 0xFF;
	break;
      case 0xCB:
	if( ((op1 < 0x40) || (op1 >= 0x80))
	     && (op1 & 0x07) == 0x06 )		// (HL)
	{
	  addr1  = cpu.getRegHL();
	  value1 = getBitInstValue(
			cpu,
			op1,
			cpu.getMemByte( addr1, false ) );
	}
	break;
      case 0xE3:				// EX (SP),HL
	addr1  = cpu.getRegSP();
	addr2  = (addr1 + 1) & 0xFFFF;
	value1 = cpu.getRegL();
	value2 = cpu.getRegH();
	break;
      case 0xED:
	switch( op1 ) {
	  case 0x43:				// LD (nn),BC
	    addr1  = (op3 << 8) | op2;
	    addr2  = (addr1 + 1) & 0xFFFF;
	    value1 = cpu.getRegC();
	    value2 = cpu.getRegB();
	    break;
	  case 0x53:				// LD (nn),DE
	    addr1  = (op3 << 8) | op2;
	    addr2  = (addr1 + 1) & 0xFFFF;
	    value1 = cpu.getRegE();
	    value2 = cpu.getRegD();
	    break;
	  case 0x63:				// *LD (nn),HL
	    addr1  = (op3 << 8) | op2;
	    addr2  = (addr1 + 1) & 0xFFFF;
	    value1 = cpu.getRegL();
	    value2 = cpu.getRegH();
	    break;
	  case 0x67:				// RRD
	    addr1  = cpu.getRegHL();
	    value1 = (cpu.getMemByte( addr1, false ) >> 4)
				| ((cpu.getRegA() << 4) & 0xF0);
	    break;
	  case 0x6F:				// RLD
	    addr1  = cpu.getRegHL();
	    value1 = (cpu.getMemByte( addr1, false ) << 4)
				| (cpu.getRegA() & 0x0F);
	    break;
	  case 0x73:				// LD (nn),SP
	    addr1  = (op3 << 8) | op2;
	    addr2  = (addr1 + 1) & 0xFFFF;
	    value1 = cpu.getRegSP() & 0xFF;
	    value2 = (cpu.getRegSP() >> 8) & 0xFF;
	    break;
	  case 0xA0:				// LDI
	  case 0xA8:				// LDD
	  case 0xB0:				// LDIR
	  case 0xB8:				// LDDR
	    addr1  = cpu.getRegDE();
	    value1 = cpu.getMemByte( cpu.getRegHL(), false );
	    break;
	  case 0xA2:				// INI
	  case 0xB2:				// INIR
	  case 0xAA:				// IND
	  case 0xBA:				// INDR
	    addr1 = cpu.getRegHL();
	    break;
	}
	break;
      case 0xDD:
      case 0xFD:
	{
	  int ixy = (op0 == 0xDD ? cpu.getRegIX() : cpu.getRegIY());
	  switch( op1 ) {
	    case 0x22:				// LD (nn),IX/IY
	      addr1  = (op3 << 8) | op2;
	      addr2  = (addr1 + 1) & 0xFFFF;
	      value1 = ixy & 0xFF;
	      value2 = (ixy >> 8) & 0xFF;
	      break;
	    case 0x34:				// INC (IX+d)
	      addr1 = computeRelAddr( ixy, op2 );
	      value1 = (cpu.getMemByte( addr1, false ) + 1) & 0xFF;
	      break;
	    case 0x35:				// DEC (IX+d)
	      addr1 = computeRelAddr( ixy, op2 );
	      value1 = (cpu.getMemByte( addr1, false ) - 1) & 0xFF;
	      break;
	    case 0x36:				// LD (IX+d),n
	      addr1  = computeRelAddr( ixy, op2 );
	      value1 = op3;
	      break;
	    case 0x70:				// LD (IXY+d),B
	      addr1  = computeRelAddr( ixy, op2 );
	      value1 = cpu.getRegB();
	      break;
	    case 0x71:				// LD (IXY+d),C
	      addr1  = computeRelAddr( ixy, op2 );
	      value1 = cpu.getRegC();
	      break;
	    case 0x72:				// LD (IXY+d),D
	      addr1  = computeRelAddr( ixy, op2 );
	      value1 = cpu.getRegC();
	      break;
	    case 0x73:				// LD (IXY+d),E
	      addr1  = computeRelAddr( ixy, op2 );
	      value1 = cpu.getRegE();
	      break;
	    case 0x74:				// LD (IXY+d),H
	      addr1  = computeRelAddr( ixy, op2 );
	      value1 = cpu.getRegH();
	      break;
	    case 0x75:				// LD (IXY+d),L
	      addr1  = computeRelAddr( ixy, op2 );
	      value1 = cpu.getRegL();
	      break;
	    case 0x77:				// LD (IXY+d),A
	      addr1  = computeRelAddr( ixy, op2 );
	      value1 = cpu.getRegA();
	      break;
	    case 0xCB:
	      if( (op3 < 0x40) || (op3 >= 0x80) ) {
		addr1  = computeRelAddr( ixy, op2 );
		value1 = getBitInstValue(
				cpu,
				op3,
				cpu.getMemByte( addr1, false ) );
	      }
	      break;
	    case 0xE3:				// EX (SP),IX/IY
	      addr1  = cpu.getRegSP();
	      addr2  = (addr1 + 1) & 0xFFFF;
	      value1 = ixy & 0xFF;
	      value2 = (ixy >> 8) & 0xFF;
	      break;
	    case 0xE5:				// PUSH IX/IY
	      addr1 = cpu.getRegSP() - 2;
	      addr2  = (addr1 + 1) & 0xFFFF;
	      value1 = ixy & 0xFF;
	      value2 = (ixy >> 8) & 0xFF;
	      break;
	  }
	}
	break;
    }
    if( addr1 >= 0 ) {
      rv = matchesWriteAccess( addr1, value1 );
    }
    if( !rv && (addr2 >= 0) ) {
      rv = matchesWriteAccess( addr2, value2 );
    }
    if( !rv && (iSource != null) ) {
      /*
       * Im Fall einer Interrupt-Annahme wurde der PC gekellert,
       * weshalb auch diese beiden Bytes geprueft werden muessen.
       */
      int sp = cpu.getRegSP();
      rv = matchesWriteAccess( sp, cpu.getMemByte( sp, false ) );
      if( !rv ) {
	sp = (sp - 1) & 0xFFFF;
	rv = matchesWriteAccess( sp, cpu.getMemByte( sp, false ) );
      }
    }
    return rv;
  }


  private boolean matchesWriteAccess( int addr, int value )
  {
    boolean rv = false;
    if( (addr == this.begAddr)
	|| ((addr >= this.begAddr) && (addr <= this.endAddr)) )
    {
      if( this.cond != null ) {
	rv = checkValues( value & this.mask, this.cond, this.value );
      } else {
	rv = true;
      }
    }
    return rv;
  }
}
