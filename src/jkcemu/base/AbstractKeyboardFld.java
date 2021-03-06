/*
 * (c) 2011-2018 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Abstrakte Komponente fuer eine Tastaturansicht
 */

package jkcemu.base;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.TimerTask;
import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import jkcemu.Main;


public abstract class AbstractKeyboardFld<T extends EmuSys>
				extends JComponent
				implements MouseListener
{
  public static class KeyData
  {
    public int     x;
    public int     y;
    public int     w;
    public int     h;
    public String  text1;
    public String  text2;
    public String  text3;
    public Color   color;
    public Image   image;
    public int     col;
    public int     value;
    public boolean shift;
    public boolean locked;
    public String  toolTipText;

    public KeyData(
		int     x,
		int     y,
		int     w,
		int     h,
		String  text1,
		String  text2,
		String  text3,
		Color   color,
		Image   image,
		int     col,
		int     value,
		boolean shift,
		String  toolTipText )
    {
      this.x           = x;
      this.y           = y;
      this.w           = w;
      this.h           = h;
      this.text1       = text1;
      this.text2       = text2;
      this.text3       = text3;
      this.color       = color;
      this.image       = image;
      this.col         = col;
      this.value       = value;
      this.shift       = shift;
      this.toolTipText = toolTipText;
    }
  };

  protected T                       emuSys;
  protected KeyData[]               keys;
  protected java.util.List<KeyData> selectedKeys;

  private KeyData[]       shiftKeys;
  private boolean         holdShift;
  private boolean         lastShiftPressed;
  private java.util.Timer nmiTimer;
  private java.util.Timer resetTimer;


  protected AbstractKeyboardFld(
			T       emuSys,
			int     numKeys,
			boolean enableToolTips )
  {
    this.emuSys           = emuSys;
    this.keys             = new KeyData[ numKeys ];
    this.selectedKeys     = new ArrayList<>();
    this.shiftKeys        = null;
    this.holdShift        = true;
    this.lastShiftPressed = false;
    this.nmiTimer         = null;
    this.resetTimer       = null;
    addMouseListener( this );
    if( enableToolTips ) {
      ToolTipManager.sharedInstance().registerComponent( this );
    }
  }


  public    abstract boolean accepts( EmuSys emuSys );
  protected abstract void    keySelectionChanged();
  public    abstract void    setEmuSys( EmuSys emuSys )
				throws IllegalArgumentException;


  public boolean changeShiftSelectionTo( boolean state )
  {
    boolean rv = false;
    if( this.shiftKeys != null ) {
      if( this.shiftKeys.length > 0 ) {
	synchronized( this.selectedKeys ) {
	  if( state ) {
	    for( KeyData k : this.shiftKeys ) {
	      k.locked = true;
	      if( !this.selectedKeys.contains( k ) ) {
		this.selectedKeys.add( k );
		rv = true;
	      }
	    }
	  } else {
	    for( KeyData k : this.shiftKeys ) {
	      if( this.selectedKeys.remove( k ) ) {
		rv = true;
	      }
	    }
	  }
	}
      }
    }
    if( rv ) {
      keySelectionChanged();
      repaint();
    }
    return rv;
  }


  protected void drawMultiLineString(
				Graphics g,
				int      xBtn,
				int      yBtn,
				int      wBtn,
				int      hBtn,
				String   text,
				int      fontSize )
  {
    if( text != null ) {
      String line1 = null;
      String line2 = null;
      int    eol   = text.indexOf( '\n' );
      if( eol >= 0 ) {
	line1 = text.substring( 0, eol );
	if( (eol + 1) < text.length() ) {
	  line2 = text.substring( eol + 1 );
	}
      } else {
	line1 = text;
      }
      if( line1 != null ) {
	int         wText = -1;
	FontMetrics fm    = g.getFontMetrics();
	if( fm != null ) {
	  wText = fm.stringWidth( line1 );
	  if( line2 != null ) {
	    wText = Math.max( wText, fm.stringWidth( line2 ) );
	  }
	}
	int xText = xBtn + ((wBtn - wText) / 2) + 1;
	int yText = yBtn + ((hBtn - fontSize) / 2) + fontSize - 2;
	if( line2 != null ) {
	  yText -= ((fontSize / 2) - 1);
	}
	g.drawString( line1, xText, yText );
	if( line2 != null ) {
	  g.drawString( line2, xText, yText + fontSize + 1 );
	}
      }
    }
  }


  protected static void drawStringCenter(
				Graphics g,
				String   text,
				int      x,
				int      y,
				int      w )
  {
    if( text != null ) {
      if( !text.isEmpty() ) {
	FontMetrics fm = g.getFontMetrics();
	if( fm != null ) {
	  g.drawString( text, x + ((w - fm.stringWidth( text )) / 2), y );
	} else {
	  g.drawString( text, x, y );
	}
      }
    }
  }


  protected static void drawStringRight(
				Graphics g,
				String   text,
				int      x,
				int      y,
				int      w )
  {
    if( text != null ) {
      if( !text.isEmpty() ) {
	FontMetrics fm = g.getFontMetrics();
	if( fm != null ) {
	  g.drawString( text, x + w - fm.stringWidth( text ), y );
	} else {
	  g.drawString( text, x, y );
	}
      }
    }
  }


  protected synchronized void fireNMIAfterDelay()
  {
    if( this.nmiTimer == null ) {
      this.nmiTimer = new java.util.Timer();
    }
    final ScreenFrm screenFrm = this.emuSys.getScreenFrm();
    if( screenFrm != null ) {
      this.nmiTimer.schedule(
		new TimerTask()
		{
		  public void run()
		  {
		    EmuThread emuThread = screenFrm.getEmuThread();
		    if( emuThread != null ) {
		      emuThread.getZ80CPU().fireNMI();
		    }
		  }
		},
		200L );
    }
  }


  protected synchronized void fireResetAfterDelay()
  {
    if( this.resetTimer == null ) {
      this.resetTimer = new java.util.Timer();
    }
    final ScreenFrm screenFrm = this.emuSys.getScreenFrm();
    if( screenFrm != null ) {
      this.resetTimer.schedule(
		new TimerTask()
		{
		  public void run()
		  {
		    screenFrm.fireReset( false );
		  }
		},
		200L );
    }
  }


  public boolean getHoldShift()
  {
    return this.holdShift;
  }


  public String getKeyboardName()
  {
    return null;
  }


  protected Image getImage( String resource )
  {
    return Main.getLoadedImage( Main.getScreenFrm(), resource );
  }


  public boolean getSelectionChangeOnShiftOnly()
  {
    return false;
  }


  @Override
  public String getToolTipText( MouseEvent e )
  {
    String rv = null;
    int    x  = e.getX();
    int    y  = e.getY();
    for( KeyData k : this.keys ) {
      if( (x >= k.x) && (x < (k.x + k.w))
	  && (y >= k.y) && (y < (k.y + k.h)) )
      {
	rv = k.toolTipText;
	break;
      }
    }
    return rv;
  }


  public boolean hasShiftKeys()
  {
    boolean rv = false;
    for( KeyData k : this.keys ) {
      if( k.shift ) {
	rv = true;
	break;
      }
    }
    return rv;
  }


  protected static boolean hits( KeyData k, MouseEvent e )
  {
    boolean rv = false;
    if( k != null ) {
      int x = e.getX();
      int y = e.getY();
      if( (x >= k.x) && (x < (k.x + k.w))
	  && (y >= k.y) && (y < (k.y + k.h)) )
      {
	rv = true;
      }
    }
    return rv;
  }


  protected boolean isKeySelected( KeyData key )
  {
    boolean rv = false;
    synchronized( this.selectedKeys ) {
      rv = this.selectedKeys.contains( key );
    }
    return rv;
  }


  public void reset()
  {
    synchronized( this.selectedKeys ) {
      this.selectedKeys.clear();
    }
    repaint();
  }


  public void setHoldShift( boolean state )
  {
    if( state != this.holdShift ) {
      this.holdShift = state;
      if( !this.holdShift ) {
        // Durch die Option gedrueckt gehaltene Tasten wieder loslassen
	boolean dirty  = false;
	synchronized( this.selectedKeys ) {
	  int n = this.selectedKeys.size();
	  for( int i = n - 1; i >= 0; --i ) {
	    KeyData key = this.selectedKeys.get( i );
	    if( !key.locked ) {
	      this.selectedKeys.remove( i );
	      dirty = true;
	    }
	  }
	}
	if( dirty ) {
	  keySelectionChanged();
	  repaint();
	}
      }
    }
  }


  protected void setShiftKeys( KeyData... keys )
  {
    this.shiftKeys = keys;
  }


  public void updKeySelection( int[] kbMatrix )
  {
    boolean dirty = false;
    synchronized( this.selectedKeys ) {
      dirty = !this.selectedKeys.isEmpty();
      this.selectedKeys.clear();
      if( kbMatrix != null ) {
	for( int col = 0; col < kbMatrix.length; col++ ) {
	  if( kbMatrix[ col ] != 0 ) {
	    for( KeyData key : this.keys ) {
	      if( (key.col == col)
		  && ((key.value & kbMatrix[ col ]) != 0) )
	      {
		dirty      = true;
		key.locked = false;
		this.selectedKeys.add( key );
	      }
	    }
	  }
	}
      }
    }
    if( dirty ) {
      repaint();
    }
  }


	/* --- MouseListener --- */

  @Override
  public void mouseClicked( MouseEvent e )
  {
    // leer
  }


  @Override
  public void mouseEntered( MouseEvent e )
  {
    // leer
  }


  @Override
  public void mouseExited( MouseEvent e )
  {
    // leer
  }


  @Override
  public void mousePressed( MouseEvent e )
  {
    if( (this.keys != null) && (e.getComponent() == this) ) {
      Boolean locked   = null;
      int     mouseBtn = e.getButton();
      if( e.isControlDown()
	  || (mouseBtn == MouseEvent.BUTTON2)
	  || (mouseBtn == MouseEvent.BUTTON3) )
      {
	locked = Boolean.TRUE;
      } else if( mouseBtn == MouseEvent.BUTTON1 ) {
	locked = Boolean.FALSE;
      }
      if( locked != null ) {
	for( KeyData key : this.keys ) {
	  if( hits( key, e ) ) {
	    synchronized( this.selectedKeys ) {
	      if( this.selectedKeys.contains( key ) ) {
		this.selectedKeys.remove( key );
	      } else {
		key.locked = locked.booleanValue();
		this.selectedKeys.add( key );
	      }
	    }
	    if( locked.booleanValue() ) {
	      this.lastShiftPressed = false;
	    } else {
	      this.lastShiftPressed = key.shift;
	    }
	    keySelectionChanged();
	    repaint();
	  }
	}
	e.consume();
      }
    }
  }


  @Override
  public void mouseReleased( MouseEvent e )
  {
    if( e.getComponent() == this ) {
      boolean dirty = false;
      synchronized( this.selectedKeys ) {
	int n = this.selectedKeys.size();
	for( int i = n - 1; i >= 0; --i ) {
	  KeyData key = this.selectedKeys.get( i );
	  if( !key.locked
	      && (!this.holdShift || !key.shift || !this.lastShiftPressed) )
	  {
	    this.selectedKeys.remove( i );
	    dirty = true;
	  }
	}
      }
      if( dirty ) {
	keySelectionChanged();
	repaint();
      }
      e.consume();
    }
  }
}
