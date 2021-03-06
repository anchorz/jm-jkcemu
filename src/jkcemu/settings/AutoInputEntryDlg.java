/*
 * (c) 2015-2020 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Dialog fuer einen AutoInput-Eintrag
 */

package jkcemu.settings;

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.EventObject;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.text.BadLocationException;
import jkcemu.base.AutoInputCharSet;
import jkcemu.base.AutoInputEntry;
import jkcemu.base.BaseDlg;
import jkcemu.base.GUIFactory;
import jkcemu.base.PopupMenuOwner;


public class AutoInputEntryDlg extends BaseDlg implements PopupMenuOwner
{
  private static final String LABEL_WAIT_TIME = "Wartezeit vor Eingabe:";
  private static final String CMD_CHAR_PREFIX = "char:";

  private static int[] waitMillis = {
				0, 200, 500, 1000, 1500,
				2000, 3000, 4000, 5000,
				6000, 7000, 8000, 9000 };

  private static NumberFormat waitFmt = null;

  private AutoInputCharSet  charSet;
  private AutoInputEntry    appliedAutoInputEntry;
  private JComboBox<String> comboWaitSeconds;
  private AutoInputDocument docInputText;
  private JTextField        fldInputText;
  private JTextField        fldRemark;
  private JPopupMenu        mnuSpecialChars;
  private JButton           btnSpecialChars;
  private JButton           btnOK;
  private JButton           btnCancel;


  public static AutoInputEntry openNewEntryDlg(
				Window           owner,
				AutoInputCharSet charSet,
				boolean          swapKeyCharCase,
				int              defaultMillisToWait )
  {
    AutoInputEntryDlg dlg = new AutoInputEntryDlg(
					owner,
					charSet,
					swapKeyCharCase,
					"Neuer AutoInput-Eintrag" );
    dlg.setMillisToWait( defaultMillisToWait );
    dlg.setVisible( true );
    return dlg.appliedAutoInputEntry;
  }


  public static AutoInputEntry openEditEntryDlg(
				Window           owner,
				AutoInputCharSet charSet,
				boolean          swapKeyCharCase,
				AutoInputEntry   entry )
  {
    AutoInputEntryDlg dlg = new AutoInputEntryDlg(
					owner,
					charSet,
					swapKeyCharCase,
					"AutoInput-Eintrag bearbeiten" );
    dlg.setMillisToWait( entry.getMillisToWait() );
    dlg.setInputText( entry.getInputText() );
    dlg.fldRemark.setText( entry.getRemark() );
    dlg.setVisible( true );
    return dlg.appliedAutoInputEntry;
  }


	/* --- PopupMenuOwner --- */

  @Override
  public JPopupMenu getPopupMenu()
  {
    return this.mnuSpecialChars;
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public boolean doAction( EventObject e )
  {
    boolean rv  = false;
    Object  src = e.getSource();
    if( src != null ) {
      if( src == this.btnSpecialChars ) {
	rv = true;
	this.mnuSpecialChars.show(
			getContentPane(),
			this.btnSpecialChars.getX(),
			this.btnSpecialChars.getY()
				+ this.btnSpecialChars.getHeight() );
      } else if( src == this.btnOK ) {
	rv = true;
	doApply();
      } else if( src == this.btnCancel ) {
	rv = true;
	doClose();
      } else if( e instanceof ActionEvent ) {
	String cmd = ((ActionEvent) e).getActionCommand();
	if( cmd != null ) {
	  try {
	    int crsPos        = -1;
	    int cmdCharPrefix = CMD_CHAR_PREFIX.length();
	    if( cmd.startsWith( CMD_CHAR_PREFIX )
		&& (cmd.length() > cmdCharPrefix) )
	    {
	      crsPos = this.docInputText.insertRawText(
				this.fldInputText.getCaretPosition(),
				cmd.substring( cmdCharPrefix ) );
	    }
	    if( crsPos >= 0 ) {
	      this.fldInputText.setCaretPosition( crsPos );
	    }
	  }
	  catch( BadLocationException ex ) {}
	  catch( IllegalArgumentException ex ) {}
	}
      }
    }
    return rv;
  }


	/* --- Konstruktor --- */

  private AutoInputEntryDlg(
			Window           owner,
			AutoInputCharSet charSet,
			boolean          swapKeyCharCase,
			String           title )
  {
    super( owner, title );
    this.charSet               = charSet;
    this.appliedAutoInputEntry = null;

    // Format fuer Wartezeit
    if( waitFmt == null ) {
      waitFmt = NumberFormat.getNumberInstance();
      if( waitFmt instanceof DecimalFormat ) {
	((DecimalFormat) waitFmt).applyPattern( "#0.0" );
      }
    }


    // Fensterinhalt
    setLayout( new GridBagLayout() );

    GridBagConstraints gbc = new GridBagConstraints(
					0, 0,
					1, 1,
					0.0, 0.0,
					GridBagConstraints.WEST,
					GridBagConstraints.NONE,
					new Insets( 5, 5, 5, 5 ),
					0, 0 );

    add( GUIFactory.createLabel( LABEL_WAIT_TIME ), gbc );

    this.comboWaitSeconds = GUIFactory.createComboBox();
    for( int millis : waitMillis ) {
      this.comboWaitSeconds.addItem(
			waitFmt.format( (double) millis / 1000.0 ) );
    }
    this.comboWaitSeconds.setEditable( true );
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridx++;
    add( this.comboWaitSeconds, gbc );

    gbc.fill = GridBagConstraints.NONE;
    gbc.gridx++;
    add( GUIFactory.createLabel( "Sekunden" ), gbc );

    gbc.gridx = 0;
    gbc.gridy++;
    add( GUIFactory.createLabel( "Eingabetext:" ), gbc );

    final AutoInputDocument docInputText = new AutoInputDocument(
							charSet,
							swapKeyCharCase );
    this.docInputText = docInputText;
    this.fldInputText = new JTextField( this.docInputText, "", 0 )
				{
				  @Override
				  public void copy()
				  {
				    docInputText.copy( this, false );
				  }

				  @Override
				  public void cut()
				  {
				    docInputText.copy( this, true );
				  }

				  @Override
				  public void paste()
				  {
				    docInputText.paste( this );
				  }
				};
    GUIFactory.initFont( this.fldInputText );
    gbc.weightx   = 1.0;
    gbc.fill      = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.gridx++;
    add( this.fldInputText, gbc );

    Font font = this.fldInputText.getFont();
    if( font != null ) {
      this.comboWaitSeconds.setFont( font );
    }

    this.btnSpecialChars = GUIFactory.createButton(
		"Bet\u00E4tigung einer Steuertaste eingeben" );
    gbc.weightx = 0.0;
    gbc.fill    = GridBagConstraints.NONE;
    gbc.gridy++;
    add( this.btnSpecialChars, gbc );

    gbc.gridwidth = 1;
    gbc.gridx     = 0;
    gbc.gridy++;
    add( GUIFactory.createLabel( "Bemerkung:" ), gbc );

    this.fldRemark = GUIFactory.createTextField();
    gbc.weightx    = 1.0;
    gbc.fill       = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth  = GridBagConstraints.REMAINDER;
    gbc.gridx++;
    add( this.fldRemark, gbc );

    JPanel panelBtn   = GUIFactory.createPanel(
				new GridLayout( 1, 2, 5, 5 ) );
    gbc.anchor        = GridBagConstraints.CENTER;
    gbc.weightx       = 0.0;
    gbc.fill          = GridBagConstraints.NONE;
    gbc.insets.top    = 10;
    gbc.insets.bottom = 10;
    gbc.gridx         = 0;
    gbc.gridy++;
    add( panelBtn, gbc );

    this.btnOK = GUIFactory.createButtonOK();
    panelBtn.add( this.btnOK );

    this.btnCancel = GUIFactory.createButtonCancel();
    panelBtn.add( this.btnCancel );


    // Menu fuer Sonderzeichen
    this.mnuSpecialChars = GUIFactory.createPopupMenu();
    for( Character ch : charSet.getSpecialChars() ) {
      String desc = charSet.getDescByChar( ch );
      if( desc != null ) {
	if( !desc.isEmpty() ) {
	  JMenuItem item = GUIFactory.createMenuItem( desc );
	  item.setActionCommand( CMD_CHAR_PREFIX + ch.toString() );
	  item.addActionListener( this );
	  this.mnuSpecialChars.add( item );
	}
      }
    }
    if( charSet.containsCtrlCodes() ) {
      JMenu mnuCtrl = GUIFactory.createMenu( "CTRL-Steuerzeichen" );
      for( int i = 1; i < 27; i++ ) {
	String text = String.format( "^%c", (char) (i + 0x40) );
	String desc = charSet.getCtrlCodeDesc( i );
	if( desc != null ) {
	  if( !desc.isEmpty() ) {
	    text = String.format( "%s  -  %s", text, desc );
	  }
	}
	JMenuItem item = GUIFactory.createMenuItem( text );
	item.setActionCommand(
			CMD_CHAR_PREFIX + String.valueOf( (char) i ) );
	item.addActionListener( this );
	mnuCtrl.add( item );
      }
      if( mnuCtrl.getItemCount() > 0 ) {
	this.mnuSpecialChars.add( mnuCtrl );
      }
    }


    // Fenstergroesse und -position
    pack();
    setParentCentered();
    setResizable( true );


    // Listener
    this.btnSpecialChars.addActionListener( this );
    this.btnOK.addActionListener( this );
    this.btnCancel.addActionListener( this );
  }


	/* --- Aktionen --- */

  private void doApply()
  {
    try {
      String rawText = this.docInputText.getRawText();
      if( rawText != null ) {
	if( rawText.isEmpty() ) {
	  rawText = null;
	}
	if( rawText != null ) {
	  int  millis = 0;
	  try {
	    Object o = this.comboWaitSeconds.getSelectedItem();
	    if( o != null ) {
	      String s = o.toString();
	      if( s != null ) {
		Number value = waitFmt.parse( s );
		if( value != null ) {
		  millis = (int) Math.round( value.doubleValue() * 1000.0 );
		}
	      }
	    }
	    this.appliedAutoInputEntry = new AutoInputEntry(
						millis,
						rawText,
						this.fldRemark.getText() );
	    doClose();
	  }
	  catch( ParseException ex ) {
	    throw new NumberFormatException(
			LABEL_WAIT_TIME + ": Ung\u00FCltiges Format" );
	  }
	} else {
	  showErrorDlg(
		this,
		"Eingabetext: Sie m\u00FCssen mindestens"
				+ " ein Zeichen eingeben!" );
	}
      }
    }
    catch( NumberFormatException ex ) {
      showErrorDlg( this, ex );
    }
  }


	/* --- private Methoden --- */

  private void setInputText( String text )
  {
    boolean swapCase = this.docInputText.getSwapCase();
    this.docInputText.setSwapCase( false );
    this.fldInputText.setText( text );
    this.docInputText.setSwapCase( swapCase );
  }


  private void setMillisToWait( int millis )
  {
    this.comboWaitSeconds.setSelectedItem(
			waitFmt.format( (double) millis / 1000.0 ) );
  }
}
