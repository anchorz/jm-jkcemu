/*
 * (c) 2011-2021 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Halte-/Log-Punkt auf eine Eingabeadresse oder
 * auf einen Bereich von Eingabeadressen
 */

package jkcemu.tools.debugger;

import org.xml.sax.Attributes;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import z80emu.Z80CPU;
import z80emu.Z80InterruptSource;


public class InputBreakpoint extends AbstractBreakpoint
{
  public static final String BP_TYPE = "input";

  private static final String ATTR_PORT = "port";
  private static final String ATTR_SIZE = "size";

  private boolean is8Bit;
  private int     begPort;
  private int     endPort;


  public InputBreakpoint(
		DebugFrm debugFrm,
		boolean  is8Bit,
		int      begPort,
		int      endPort )
  {
    super( debugFrm );
    this.is8Bit  = is8Bit;
    this.begPort = begPort & 0xFFFF;
    this.endPort = (endPort >= 0 ? (endPort & 0xFFFF) : -1);
    if( this.is8Bit ) {
      this.begPort &= 0xFF;
      if( this.endPort >= 0 ) {
	this.endPort &= 0xFF;
	setText( String.format( "%02X-%02X", this.begPort, this.endPort ) );
      } else {
	setText( String.format( "%02X", this.begPort ) );
      }
    } else {
      if( this.endPort >= 0 ) {
	setText( String.format( "%04X-%04X", this.begPort, this.endPort ) );
      } else {
	setText( String.format( "%04X", this.begPort ) );
      }
    }
  }


  public static InputBreakpoint createByAttrs(
					DebugFrm   debugFrm,
					Attributes attrs )
  {
    InputBreakpoint bp = null;
    if( attrs != null ) {
      String portText = attrs.getValue( ATTR_PORT );
      if( portText != null ) {
	portText = portText.toUpperCase();
	int len  = portText.length();
	if( len > 0 ) {
	  try {
	    int port = BreakpointVarLoader.getIntValue( portText );
	    int size = getHex4Value( attrs, ATTR_SIZE );
	    if( portText.endsWith( "H" ) ) {
	      --len;
	    }
	    bp = new InputBreakpoint(
				debugFrm,
				len < 3,
				port,
				size > 1 ? (port + size - 1) : -1 );
	  }
	  catch( NumberFormatException ex ) {}
	}
      }
    }
    return bp;
  }


  public boolean get8Bit()
  {
    return this.is8Bit;
  }


  public int getBegPort()
  {
    return this.begPort;
  }


  public int getEndPort()
  {
    return this.endPort;
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  protected boolean matchesImpl( Z80CPU cpu, Z80InterruptSource iSource )
  {
    boolean rv  = false;
    int     pc  = cpu.getRegPC();
    int     op0 = cpu.getMemByte( pc, false );
    int     op1 = cpu.getMemByte( pc + 1, false );
    if( op0 == 0xDB ) {			// IN A
      rv = matchesPort( (cpu.getRegA() << 8) | op1 );
    }
    else if( op0 == 0xED ) {
      if( (op1 == 0x40)			// IN B,(C)
	  || (op1 == 0x48)		// IN C,(C)
	  || (op1 == 0x50)		// IN D,(C)
	  || (op1 == 0x58)		// IN E,(C)
	  || (op1 == 0x60)		// IN H,(C)
	  || (op1 == 0x68)		// IN L,(C)
	  || (op1 == 0x70)		// IN F,(C)
	  || (op1 == 0x78)		// IN A,(C)
	  || (op1 == 0xA2)		// INI
	  || (op1 == 0xAA)		// IND
	  || (op1 == 0xB2)		// INIR
	  || (op1 == 0xBA) )		// INDR
      {
	rv = matchesPort( cpu.getRegBC() );
      }
    }
    return rv;
  }


  @Override
  public void writeTo( Document doc, Node parent )
  {
    Element elem = createBreakpointElement( doc, BP_TYPE );
    elem.setAttribute(
		ATTR_PORT,
		this.is8Bit ?
			toHex2( this.begPort )
			: toHex4( this.begPort ) );
    int size = 1;
    if( this.endPort > this.begPort ) {
      size = this.endPort - this.begPort + 1;
    }
    elem.setAttribute(
		ATTR_SIZE,
		this.is8Bit ? toHex2( size ) : toHex4( size ) );
    appendAttributesTo( elem );
    parent.appendChild( elem );
  }


	/* --- private Methoden --- */

  private boolean matchesPort( int port )
  {
    if( this.is8Bit ) {
      port &= 0x00FF;
    } else {
      port &= 0xFFFF;
    }
    return (port == this.begPort)
	   || ((port >= this.begPort) && (port <= this.endPort));
  }
}
