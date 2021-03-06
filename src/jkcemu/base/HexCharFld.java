/*
 * (c) 2008-2020 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Komponente fuer eine Hex-Character-Anzeige
 */

package jkcemu.base;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.SystemColor;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseAdapter;
import java.util.ArrayList;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import jkcemu.Main;


public class HexCharFld extends JComponent implements Scrollable
{
  public static final int BYTES_PER_ROW = 16;
  public static final int MARGIN        = 5;

  private static final int SEP_W = 20;
  private static final int PAD_Y = 1;

  private ByteDataSource                dataSrc;
  private java.util.List<CaretListener> caretListeners;
  private Dimension                     prefScrollableVPSize;
  private boolean                       asciiSelected;
  private boolean                       editable;
  private boolean                       nibbleTyped;
  private String                        readOnlyErrMsg;
  private int                           caretPos;
  private int                           markPos;
  private int                           hRow;
  private int                           hChar;
  private int                           wChar;
  private int                           wHex;
  private int                           xHex;
  private int                           xAscii;
  private int                           visibleRows;
  private int                           visibleXLeft;
  private int                           visibleYTop;
  private int                           visibleWidth;
  private int                           visibleHeight;


  public HexCharFld( ByteDataSource dataSrc )
  {
    this.dataSrc              = dataSrc;
    this.caretListeners       = null;
    this.prefScrollableVPSize = null;
    this.asciiSelected        = false;
    this.editable             = true;
    this.nibbleTyped          = false;
    this.readOnlyErrMsg       = null;
    this.caretPos             = -1;
    this.markPos              = -1;
    this.hRow                 = 0;
    this.hChar                = 0;
    this.wChar                = 0;
    this.xHex                 = 0;
    this.xAscii               = 0;
    this.visibleRows          = 0;
    this.visibleXLeft         = 0;
    this.visibleYTop          = 0;
    this.visibleWidth         = 0;
    this.visibleHeight        = 0;

    setFont( FontMngr.getFont( FontMngr.FontUsage.CODE, true ) );
    setBackground( SystemColor.text );
    setForeground( SystemColor.textText );
    addKeyListener(
		new KeyAdapter()
		{
		  @Override
		  public void keyPressed( KeyEvent e )
		  {
		    if( keyAction( e ) ) {
		      e.consume();
		    }
		  }
		} );
    addMouseListener(
		new MouseAdapter()
		{
		  @Override
		  public void mousePressed( MouseEvent e )
		  {
		    mouseAction( e.getX(), e.getY(), false );
		    e.consume();
		  }
		} );
    addMouseMotionListener(
		new MouseAdapter()
		{
		  @Override
		  public void mouseDragged( MouseEvent e )
		  {
		    mouseAction( e.getX(), e.getY(), true );
		    e.consume();
		  }
		} );
  }


  public synchronized void addCaretListener( CaretListener listener )
  {
    if( this.caretListeners == null ) {
      this.caretListeners = new ArrayList<>();
    }
    this.caretListeners.add( listener );
  }


  public synchronized void removeCaretListener( CaretListener listener )
  {
    if( this.caretListeners != null )
      this.caretListeners.remove( listener );
  }


  public void copySelectedBytesAsAscii()
  {
    int dataLen = this.dataSrc.getDataLength();
    int m1      = -1;
    int m2      = -1;
    if( (this.caretPos >= 0) && (this.markPos >= 0) ) {
      m1 = Math.min( this.caretPos, this.markPos );
      m2 = Math.max( this.caretPos, this.markPos );
    } else {
      m1 = this.caretPos;
      m2 = this.caretPos;
    }
    if( m2 >= dataLen ) {
      m2 = dataLen - 1;
    }
    if( m1 >= 0 ) { 
      StringBuilder buf = new StringBuilder( m2 - m1 + 1 );
      while( m1 <= m2 ) {
	int b = this.dataSrc.getDataByte( m1++ );
	buf.append( (char) ((b >= 0x20) && (b < 0x7F) ? b : '.') );
      }
      if( buf.length() > 0 ) {
	EmuUtil.copyToClipboard( this, buf.toString() );
      }
    }
  }


  public void copySelectedBytesAsHex()
  {
    int dataLen = this.dataSrc.getDataLength();
    int m1      = -1;
    int m2      = -1;
    if( (this.caretPos >= 0) && (this.markPos >= 0) ) {
      m1 = Math.min( this.caretPos, this.markPos );
      m2 = Math.max( this.caretPos, this.markPos );
    } else {
      m1 = this.caretPos;
      m2 = this.caretPos;
    }
    if( m2 >= dataLen ) {
      m2 = dataLen - 1;
    }
    if( m1 >= 0 ) { 
      StringBuilder buf = new StringBuilder( (m2 - m1 + 1) * 3 );
      boolean       sp  = false;
      while( m1 <= m2 ) {
	if( sp ) {
	  buf.append( '\u0020' );
	}
	int b = this.dataSrc.getDataByte( m1++ );
	buf.append( EmuUtil.getHexChar( b >> 4 ) );
	buf.append( EmuUtil.getHexChar( b ) );
	sp = true;
      }
      if( buf.length() > 0 ) {
	EmuUtil.copyToClipboard( this, buf.toString() );
      }
    }
  }


  public void copySelectedBytesAsDump()
  {
    int dataLen = this.dataSrc.getDataLength();
    int m1      = -1;
    int m2      = -1;
    if( (this.caretPos >= 0) && (this.markPos >= 0) ) {
      m1 = Math.min( this.caretPos, this.markPos );
      m2 = Math.max( this.caretPos, this.markPos );
    } else {
      m1 = this.caretPos;
      m2 = this.caretPos;
    }
    if( m2 >= dataLen ) {
      m2 = dataLen - 1;
    }
    if( m1 >= 0 ) {
      int rows   = m1 / BYTES_PER_ROW;
      int pos    = rows * BYTES_PER_ROW;  // auf Anfang der Zeile setzen
      rows       = (m2 + BYTES_PER_ROW - 1) / BYTES_PER_ROW;
      int endPos = rows * BYTES_PER_ROW;

      StringBuilder buf = new StringBuilder(
		((m2 - m1) / BYTES_PER_ROW) * ((BYTES_PER_ROW * 4) + 12) );

      int    addrOffs = this.dataSrc.getAddrOffset();
      String addrFmt  = createAddrFmtString();
      while( pos < m2 ) {
	buf.append( String.format( addrFmt, addrOffs + pos ) );
	buf.append( "\u0020\u0020" );
	for( int i = 0; i < BYTES_PER_ROW; i++ ) {
	  int idx = pos + i;
	  if( idx < dataLen ) {
	    buf.append(
		String.format( " %02X", this.dataSrc.getDataByte( idx ) ) );
	  } else {
	    buf.append( "\u0020\u0020\u0020" );
	  }
	}
	buf.append( "\u0020\u0020\u0020" );
	for( int i = 0; i < BYTES_PER_ROW; i++ ) {
	  int idx = pos + i;
	  if( idx >= dataLen ) {
	    break;
	  }
	  char ch = (char) this.dataSrc.getDataByte( pos + i );
	  if( (ch < 0x20) || (ch > 0x7F) ) {
	    ch = '.';
	  }
	  buf.append( ch );
	}
	buf.append( '\n' );
	pos += BYTES_PER_ROW;
      }
      if( buf.length() > 0 ) {
	EmuUtil.copyToClipboard( this, buf.toString() );
      }
    }
  }


  public String createAddrFmtString()
  {
    int maxAddr = this.dataSrc.getAddrOffset()
				+ this.dataSrc.getDataLength()
				- 1;
    if( maxAddr < 0 ) {
      maxAddr = 0;
    }
    int addrDigits = 0;
    while( maxAddr > 0 ) {
      addrDigits++;
      maxAddr >>= 4;
    }
    return String.format( "%%0%dX", addrDigits > 4 ? addrDigits : 4 );
  }


  public int getCaretPosition()
  {
    return this.caretPos;
  }


  public int getCharWidth()
  {
    if( this.wChar == 0 ) {
      calcPositions();
    }
    return this.wChar;
  }


  public int getDataIndexAt( int x, int y )
  {
    int rv = -1;
    if( (this.wChar > 0) && (this.wHex > 0)
	&& (y > MARGIN) && (this.hRow > 0) )
    {
      int col = -1;
      if( (x >= this.xHex)
	  && (x < this.xHex + (BYTES_PER_ROW * this.wHex)) )
      {
	int m = (x - this.xHex) % this.wHex;
	if( m < (2 * this.wChar) ) {
	  col = (x - this.xHex) / this.wHex;
	}
      }
      else if( (x >= this.xAscii)
	       && (x < this.xAscii + (BYTES_PER_ROW * this.wChar)) )
      {
	col = (x - this.xAscii) / this.wChar;
      }
      if( col >= 0 ) {
	int row = (y - MARGIN) / this.hRow;
	rv      = (row * BYTES_PER_ROW) + col;
      }
    }
    if( rv > this.dataSrc.getDataLength() ) {
      rv = -1;
    }
    return rv;
  }


  public int getMarkPosition()
  {
    return this.markPos;
  }


  public int getDefaultPreferredWidth()
  {
    if( this.wChar == 0 ) {
      calcPositions();
    }
    return this.xAscii + (BYTES_PER_ROW * this.wChar) + MARGIN;
  }


  public int getRowHeight()
  {
    return this.hRow;
  }


  public void refresh()
  {
    setCaretPosition( -1, false );

    Component parent = getParent();
    if( parent != null ) {
      if( parent instanceof JViewport ) {
	((JViewport) parent).setView( null );
	((JViewport) parent).setView( this );
      }
    }
    invalidate();
    repaint();
  }


  public void setCaretPosition( int pos, boolean moveOp )
  {
    if( (pos < 0) && (pos >= this.dataSrc.getDataLength()) ) {
      pos    = -1;
      moveOp = false;
    }
    boolean changed = (this.caretPos != pos);
    this.caretPos   = pos;
    if( !moveOp ) {
      this.markPos = pos;
    }
    scrollCaretToVisible();
    repaint();

    if( changed ) {
      this.nibbleTyped = false;
      fireCaretListeners();
    }
  }


  public void setEditable( boolean state, String readOnlyErrMsg )
  {
    this.editable       = state;
    this.readOnlyErrMsg = readOnlyErrMsg;
  }


  public void setSelection( int begPos, int endPos )
  {
    if( (begPos < 0) && (begPos >= this.dataSrc.getDataLength()) ) {
      begPos = -1;
      endPos = -1;
    }
    boolean changed = (this.caretPos != begPos);
    this.caretPos   = endPos;
    this.markPos    = begPos;
    scrollCaretToVisible();
    repaint();

    if( changed ) {
      fireCaretListeners();
    }
  }


	/* --- Scrollable --- */

  @Override
  public Dimension getPreferredScrollableViewportSize()
  {
    Dimension prefSize = this.prefScrollableVPSize;
    return prefSize != null ? prefSize : getPreferredSize();
  }


  @Override
  public int getScrollableBlockIncrement(
				Rectangle visibleRect,
				int       orientation,
				int       direction )
  {
    int rv = 0;
    if( direction < 0 ) {
      rv = this.visibleRows * this.hRow;
    } else if( direction > 0 ) {
      rv = this.wChar;
    }
    return rv;
  }


  @Override
  public boolean getScrollableTracksViewportHeight()
  {
    boolean   rv = false;
    Component p  = getParent();
    if( p != null ) {
      if( (p instanceof JViewport)
	  && (p.getHeight() > getPreferredSize().height) )
      {
	// Viewport voll ausfuellen
	rv = true;
      }
    }
    return rv;
  }


  @Override
  public boolean getScrollableTracksViewportWidth()
  {
    boolean   rv = false;
    Component p  = getParent();
    if( p != null ) {
      if( (p instanceof JViewport)
	  && (p.getWidth() > getPreferredSize().width) )
      {
	// Viewport voll ausfuellen
	rv = true;
      }
    }
    return rv;
  }


  @Override
  public int getScrollableUnitIncrement(
				Rectangle visibleRect,
				int       orientation,
				int       direction )
  {
    int rv = 0;
    if( direction < 0 ) {
      rv = this.hRow;
    } else if( direction > 0 ) {
      rv = this.wChar;
    }
    return rv;
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public Dimension getPreferredSize()
  {
    Dimension rv = null;
    if( isPreferredSizeSet() ) {
      rv = super.getPreferredSize();
    } else {
      int h   = 0;
      int w   = 0;
      int len = this.dataSrc.getDataLength();
      if( len > 0 ) {
	int rows = (len + BYTES_PER_ROW - 1) / BYTES_PER_ROW;
	h        = (rows * this.hRow) + (2 * MARGIN);
	w        = getDefaultPreferredWidth();
      }
      rv = new Dimension( w, h );
    }
    return rv;
  }


  @Override
  public boolean isFocusable()
  {
    return true;
  }


  @Override
  public void paintComponent( Graphics g )
  {
    // sichtbarer Bereich ermitteln
    int visibleRows = 0;
    int xVisible    = 0;
    int yVisible    = 0;
    int yOffs       = 0;
    int wVisible    = getWidth();
    int hVisible    = getHeight();
    if( (wVisible > 0) && (hVisible > 0) ) {

      // sichtbarer Bereich ermitteln
      Component parent = getParent();
      if( parent != null ) {
	if( parent instanceof JViewport ) {
	  Rectangle r = ((JViewport) parent).getViewRect();
	  if( r != null ) {
	    xVisible += r.x;
	    yVisible += r.y;
	    wVisible = r.width;
	    hVisible = r.height;
	    yOffs    = r.y;
	  }
	}
      }

      // Hintergrund loeschen
      g.setColor( getBackground() );
      g.setPaintMode();
      g.fillRect( xVisible, yVisible, wVisible, hVisible );

      // Inhalt erzeugen
      int dataLen = this.dataSrc.getDataLength();
      if( (dataLen > 0) && (this.hRow > 0) ) {
	if( this.wChar == 0 ) {
	  calcPositions();
	}
	g.setFont( getFont() );

	// Anfang und Ende der Markierung
	int m1 = -1;
	int m2 = -1;
	if( (this.caretPos >= 0) && (this.markPos >= 0) ) {
	  m1 = Math.min( this.caretPos, this.markPos );
	  m2 = Math.max( this.caretPos, this.markPos );
	} else {
	  m1 = this.caretPos;
	  m2 = this.caretPos;
	}

	// Hex-ASCII-Text ausgeben
	int pos = ((yOffs - MARGIN) / this.hRow) * BYTES_PER_ROW;
	int y   = (pos / BYTES_PER_ROW) * this.hRow;
	if( y < 0 ) {
	  y = 0;
	}
	y += MARGIN;
	y += this.hChar;
	int    addrOffs = this.dataSrc.getAddrOffset();
	String addrFmt  = createAddrFmtString();
	while( (pos < dataLen)
	       && (y < (yVisible + hVisible + this.hChar)) )
	{
	  visibleRows++;
	  g.setColor( getForeground() );
	  g.drawString( String.format( addrFmt, addrOffs + pos ), MARGIN, y );
	  int x = this.xHex;
	  for( int i = 0; i < BYTES_PER_ROW; i++ ) {
	    int idx = pos + i;
	    if( idx >= dataLen ) {
	      break;
	    }
	    if( (idx >= m1) && (idx <= m2) ) {
	      int nMark = 2;
	      if( (i < BYTES_PER_ROW - 1) && (idx < m2) ) {
		nMark = 3;
	      }
	      g.setColor( SystemColor.textHighlight );
	      g.fillRect(
			x,
			y - this.hChar + 2,
			nMark * this.wChar,
			this.hRow );
	      g.setColor( SystemColor.textHighlightText );
	    } else {
	      g.setColor( getForeground() );
	    }
	    g.drawString(
		String.format( "%02X", this.dataSrc.getDataByte( idx ) ),
		x,
		y );
	    x += this.wHex;
	  }
	  x = this.xAscii;
	  for( int i = 0; i < BYTES_PER_ROW; i++ ) {
	    int idx = pos + i;
	    if( idx >= dataLen ) {
	      break;
	    }
	    char ch = (char) this.dataSrc.getDataByte( pos + i );
	    if( (ch < 0x20) || (ch >= 0x7F) ) {
	      ch = '.';
	    }
	    if( (idx >= m1) && (idx <= m2) ) {
	      g.setColor( SystemColor.textHighlight );
	      g.fillRect( x, y - this.hChar + 2, this.wChar, this.hRow );
	      g.setColor( SystemColor.textHighlightText );
	    } else {
	      g.setColor( getForeground() );
	    }
	    g.drawString( Character.toString( ch ), x, y );
	    x += this.wChar;
	  }
	  y   += this.hRow;
	  pos += BYTES_PER_ROW;
	}
	if( visibleRows < 1 ) {
	  visibleRows = 1;
	}
      }
    }
    this.visibleRows   = visibleRows;
    this.visibleXLeft  = xVisible;
    this.visibleYTop   = yVisible;
    this.visibleWidth  = wVisible;
    this.visibleHeight = hVisible;
  }


  @Override
  public void setFont( Font font )
  {
    super.setFont( font );
    this.wChar = 0;	// Positionen neu berechnen
    this.hChar = font.getSize();
    this.hRow  = this.hChar + PAD_Y;
    revalidate();
    repaint();
  }


	/* --- private Methoden --- */

  private void calcPositions()
  {
    FontMetrics fm = getFontMetrics( getFont() );
    if( fm != null ) {
      this.wChar  = fm.stringWidth( "0" );
      this.wHex   = 3 * this.wChar;
      this.xHex   = MARGIN + (6 * this.wChar) + SEP_W;
      this.xAscii = this.xHex + (BYTES_PER_ROW * this.wHex) + SEP_W;
    }
  }


  private void fireCaretListeners()
  {
    if( this.caretListeners != null ) {
      synchronized( this.caretListeners ) {
	final Object source   = this;
	final int    caretPos = this.caretPos;
	final int    markPos  = this.markPos;

	CaretEvent e = new CaretEvent( source )
			{
			  @Override
			  public int getDot()
			  {
			    return caretPos;
			  }

			  @Override
			  public int getMark()
			  {
			    return markPos;
			  }
			};
	for( CaretListener listener : this.caretListeners ) {
	  listener.caretUpdate( e );
	}
      }
    }
  }


  private boolean keyAction( KeyEvent e )
  {
    boolean rv           = false;
    int     keyCode      = e.getKeyCode();
    int     nibbleValue  = -1;
    int     dataLen      = this.dataSrc.getDataLength();
    if( (this.caretPos >= 0) && (this.caretPos < dataLen) ) {
      rv        = true;
      int steps = 0;
      switch( keyCode ) {
	case KeyEvent.VK_0:
	  nibbleValue = 0;
	  break;
	case KeyEvent.VK_1:
	  nibbleValue = 1;
	  break;
	case KeyEvent.VK_2:
	  nibbleValue = 2;
	  break;
	case KeyEvent.VK_3:
	  nibbleValue = 3;
	  break;
	case KeyEvent.VK_4:
	  nibbleValue = 4;
	  break;
	case KeyEvent.VK_5:
	  nibbleValue = 5;
	  break;
	case KeyEvent.VK_6:
	  nibbleValue = 6;
	  break;
	case KeyEvent.VK_7:
	  nibbleValue = 7;
	  break;
	case KeyEvent.VK_8:
	  nibbleValue = 8;
	  break;
	case KeyEvent.VK_9:
	  nibbleValue = 9;
	  break;
	case KeyEvent.VK_A:
	  nibbleValue = 0x0A;
	  break;
	case KeyEvent.VK_B:
	  nibbleValue = 0x0B;
	  break;
	case KeyEvent.VK_C:
	  nibbleValue = 0x0C;
	  break;
	case KeyEvent.VK_D:
	  nibbleValue = 0x0D;
	  break;
	case KeyEvent.VK_E:
	  nibbleValue = 0x0E;
	  break;
	case KeyEvent.VK_F:
	  nibbleValue = 0x0F;
	  break;
	case KeyEvent.VK_LEFT:
	  steps = -1;
	  break;
	case KeyEvent.VK_RIGHT:
	  steps = 1;
	  break;
	case KeyEvent.VK_UP:
	  steps = -BYTES_PER_ROW;
	  break;
	case KeyEvent.VK_DOWN:
	  steps = BYTES_PER_ROW;
	  break;
	case KeyEvent.VK_PAGE_UP:
	  steps = -(this.visibleRows * BYTES_PER_ROW);
	  break;
	case KeyEvent.VK_PAGE_DOWN:
	  steps = this.visibleRows * BYTES_PER_ROW;
	  break;
	case KeyEvent.VK_BEGIN:
	case KeyEvent.VK_HOME:
	  steps = -this.caretPos;
	  break;
	case KeyEvent.VK_END:
	  steps = dataLen - this.caretPos - 1;
	  break;
	default:
	  rv = false;
      }
      if( nibbleValue >= 0 ) {
	if( !this.dataSrc.getDataReadOnly()
	    && !e.isControlDown()
	    && !e.isMetaDown() )
	{
	  int idx = this.caretPos;
	  if( (idx >= 0) && (idx < this.dataSrc.getDataLength()) ) {
	    if( this.editable ) {
	      int b = this.dataSrc.getDataByte( this.caretPos );
	      if( this.nibbleTyped ) {
		if( this.dataSrc.setDataByte(
			idx,
			(b & 0xF0) | (nibbleValue & 0x0F) ) )
		{
		  repaint();
		}
		this.nibbleTyped = false;
		steps = 1;
	      } else {
		if( this.dataSrc.setDataByte(
			idx,
			((nibbleValue << 4) & 0xF0) | (b & 0x0F) ) )
		{
		  repaint();
		}
		this.nibbleTyped = true;
	      }
	    } else {
	      if( this.readOnlyErrMsg != null ) {
		BaseDlg.showSuppressableInfoDlg(
					this,
					this.readOnlyErrMsg );
	      }
	    }
	  }
	}
      } else {
	this.nibbleTyped = false;
      }
      if( steps != 0 ) {
	int idx = this.caretPos + steps;
	if( Math.abs( steps ) > 1 ) {
	  if( idx < 0 ) {
	    // gleiche Spalte erste Zeile
	    idx += ((1 - (idx / BYTES_PER_ROW)) * BYTES_PER_ROW);
	  } else if( idx >= dataLen ) {
	    // gleiche Spalte letzte Zeile
	    idx -= ((1 + ((idx - dataLen) / BYTES_PER_ROW))
						* BYTES_PER_ROW);
	  }
	}
	if( (idx >= 0) && (idx < dataLen) ) {
	  setCaretPosition( idx, e.isShiftDown() );
	}
      }
    }
    return rv;
  }


  private void mouseAction( int x, int y, boolean moveOp )
  {
    int idx = getDataIndexAt( x, y );
    if( idx >= 0 ) {
      if( (x >= this.xAscii)
	  && (x < (this.xAscii + (this.wChar * BYTES_PER_ROW))) )
      {
	this.asciiSelected = true;
      } else {
	this.asciiSelected = false;
      }
      setCaretPosition( idx, moveOp );
    }
    requestFocus();
  }


  private void scrollCaretToVisible()
  {
    if( (this.caretPos >= 0)
	&& (this.caretPos < this.dataSrc.getDataLength()) )
    {
      Component parent = getParent();
      if( parent != null ) {
	if( parent instanceof JViewport ) {
	  Point p  = null;
	  int   c  = this.caretPos % BYTES_PER_ROW;
	  int   x  = 0;
	  int   wB = this.wChar;
	  if( this.asciiSelected ) {
	    x = this.xAscii + (c * this.wChar);
	  } else {
	    x = this.xHex + (c * this.wHex);
	    wB += this.wChar;
	  }
	  int y = MARGIN + ((this.caretPos / BYTES_PER_ROW) * this.hRow);
	  if( x < this.visibleXLeft ) {
	    Point vp = ((JViewport) parent).getViewPosition();
	    if( vp != null ) {
	      p = new Point( x, vp.y );
	    }
	  }
	  else if( (x + wB) > (this.visibleXLeft + this.visibleWidth) ) {
	    Point vp = ((JViewport) parent).getViewPosition();
	    if( vp != null ) {
	      p = new Point( x - this.visibleWidth + wB, vp.y );
	    }
	  }
	  else if( (y - this.hChar) < this.visibleYTop ) {
	    Point vp = ((JViewport) parent).getViewPosition();
	    if( vp != null ) {
	      p = new Point( vp.x, y - this.hChar );
	    }
	  }
	  else if( y > (this.visibleYTop + this.visibleHeight) )
	  {
	    Point vp = ((JViewport) parent).getViewPosition();
	    if( vp != null ) {
	      p = new Point( vp.x, y - this.visibleHeight + this.hChar );
	    }
	  }
	  if( p != null ) {
	    if( p.x < 0 ) {
	      p.x = 0;
	    }
	    if( p.y < 0 ) {
	      p.y = 0;
	    }
	    ((JViewport) parent).setViewPosition( p );
	  }
	}
      }
    }
  }
}
