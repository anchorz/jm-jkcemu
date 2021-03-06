/*
 * (c) 2008-2020 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Berechnung von Pruefsummen und Hashwerten von Dateien
 */

package jkcemu.tools;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.EventObject;
import java.util.regex.PatternSyntaxException;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import jkcemu.Main;
import jkcemu.base.BaseDlg;
import jkcemu.base.BaseFrm;
import jkcemu.base.EmuUtil;
import jkcemu.base.GUIFactory;
import jkcemu.base.HelpFrm;
import jkcemu.base.PopupMenuOwner;
import jkcemu.etc.CksCalculator;
import jkcemu.file.FileEntry;
import jkcemu.file.FileTableModel;


public class FileChecksumFrm extends BaseFrm
				implements
					ListSelectionListener,
					PopupMenuOwner,
					Runnable
{
  private static final String BTN_TEXT_CALCULATE = "Berechnen";
  private static final String HELP_PAGE = "/help/tools/filechecksum.htm";

  private static FileChecksumFrm instance = null;

  private JMenuItem         mnuClose;
  private JMenuItem         mnuCopyUpper;
  private JMenuItem         mnuCopyLower;
  private JMenuItem         mnuCompare;
  private JMenuItem         mnuHelpContent;
  private JPopupMenu        popupMnu;
  private JMenuItem         popupCopyUpper;
  private JMenuItem         popupCopyLower;
  private JMenuItem         popupCompare;
  private JLabel            labelAlgorithm;
  private JComboBox<String> comboAlgorithm;
  private JButton           btnAction;
  private JTable            table;
  private FileTableModel    tableModel;
  private Thread            thread;
  private String            algorithm;
  private CksCalculator     cks;
  private volatile boolean  cancelled;
  private volatile boolean  filesChanged;


  public static void open()
  {
    if( instance != null ) {
      if( instance.getExtendedState() == Frame.ICONIFIED ) {
	instance.setExtendedState( Frame.NORMAL );
      }
    } else {
      instance = new FileChecksumFrm();
    }
    instance.toFront();
    instance.setVisible( true );
  }


  public static void open( Collection<File> files )
  {
    open();
    instance.setFiles( files );
  }


  public void setFiles( Collection<File> files )
  {
    this.cancelled = true;
    synchronized( this.tableModel ) {
      this.filesChanged = true;
      this.tableModel.clear( false );
      if( files != null ) {
	for( File file : files ) {
	  if( file.isFile() ) {
	    FileEntry entry = new FileEntry();
	    entry.setName( file.getName() );
	    entry.setFile( file );
	    this.tableModel.addRow( entry, false );
	  }
	}
      }
      this.tableModel.fireTableDataChanged();
    }
    updFields();
  }


	/* --- ListSelectionListener --- */

  @Override
  public void valueChanged( ListSelectionEvent e )
  {
    updEditBtns();
  }


	/* --- PopupMenuOwner --- */

  @Override
  public JPopupMenu getPopupMenu()
  {
    return this.popupMnu;
  }


	/* --- Runnable --- */

  @Override
  public void run()
  {
    String        algorithm = null;
    CksCalculator cks       = null;
    int           nRows     = 0;
    synchronized( this.tableModel ) {
      algorithm = this.algorithm;
      cks       = this.cks;
      nRows     = this.tableModel.getRowCount();
      if( nRows > 0 ) {
	for( int i = 0; i < nRows; i++ ) {
	  FileEntry entry = this.tableModel.getRow( i );
	  if( entry != null ) {
	    entry.setValue( null );
	  }
	}
	fireTableRowsUpdated( 0, nRows - 1 );
      }
    }
    if( (cks != null) && (nRows > 0) ) {
      for( int i = 0; !this.cancelled && (i < nRows); i++ ) {
	FileEntry entry = null;
	synchronized( this.tableModel ) {
	  if( i < this.tableModel.getRowCount() ) {
	    entry = this.tableModel.getRow( i );
	  }
	}
	if( entry != null ) {
	  InputStream in = null;
	  cks.reset();
	  try {
	    in = new BufferedInputStream(
				new FileInputStream( entry.getFile() ) );
	    entry.setMarked( true );
	    entry.setValue( "Wird berechnet..." );
	    fireTableRowsUpdated( i, i );
	    if( cks != null ) {
	      int b = in.read();
	      while( !this.cancelled && (b != -1) ) {
		cks.update( b );
		b = in.read();
	      }
	      if( !this.cancelled ) {
		entry.setValue( cks.getValue() );
	      }
	    }
	    if( this.cancelled ) {
	      entry.setValue( null );
	    }
	    entry.setMarked( false );
	  }
	  catch( IOException ex ) {
	    String msg = ex.getMessage();
	    if( msg != null ) {
	      entry.setValue( EmuUtil.TEXT_ERROR + ": " + msg );
	    } else {
	      entry.setValue( EmuUtil.TEXT_ERROR );
	    }
	  }
	  finally {
	    if( in != null ) {
	      try {
		in.close();
	      }
	      catch( IOException ex ) {}
	    }
	  }
	  if( !this.filesChanged ) {
	    fireTableRowsUpdated( i, i );
	  }
	} else {
	  this.cancelled = true;
	}
	EventQueue.invokeLater(
		new Runnable()
		{
		  @Override
		  public void run()
		  {
		    updEditBtns();
		  }
		} );
      }
    }
    EventQueue.invokeLater(
		new Runnable()
		{
		  @Override
		  public void run()
		  {
		    calculationFinished();
		  }
		} );
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  protected boolean doAction( EventObject e )
  {
    boolean rv  = false;
    Object  src = e.getSource();
    if( src == this.mnuClose ) {
      rv = true;
      doClose();
    }
    else if( src == this.btnAction ) {
      rv = true;
      doCalculate();
    }
    else if( (src == this.mnuCopyUpper)
	     || (src == this.popupCopyUpper) )
    {
      rv = true;
      doCopyUpper();
    }
    else if( (src == this.mnuCopyLower)
	     || (src == this.popupCopyLower) )
    {
      rv = true;
      doCopyLower();
    }
    else if( (src == this.mnuCompare)
	     || (src == this.popupCompare) )
    {
      rv = true;
      doCompare();
    }
    else if( src == this.mnuHelpContent ) {
      rv = true;
      HelpFrm.openPage( HELP_PAGE );
    }
    return rv;
  }


  @Override
  public boolean doClose()
  {
    boolean rv = super.doClose();
    if( rv ) {
      this.cancelled = true;
      Thread thread  = this.thread;
      if( thread != null ) {
	thread.interrupt();
      }
      instance = null;
    }
    return rv;
  }


  @Override
  protected boolean showPopupMenu( MouseEvent e )
  {
    boolean rv = false;
    if( e != null ) {
      Component c = e.getComponent();
      if( c != null ) {
	this.popupMnu.show( c, e.getX(), e.getY() );
	rv = true;
      }
    }
    return rv;
  }


  @Override
  public void windowClosed( WindowEvent e )
  {
    if( e.getWindow() == this )
      this.cancelled = true;
  }


	/* --- Konstruktor --- */

  private FileChecksumFrm()
  {
    this.thread       = null;
    this.algorithm    = null;
    this.cks          = null;
    this.cancelled    = false;
    this.filesChanged = false;
    setTitle( "JKCEMU Pr\u00FCfsumme-/Hashwert berechnen" );


    // Menu
    JMenu mnuFile = createMenuFile();
    this.mnuClose = createMenuItemClose();
    mnuFile.add( this.mnuClose );

    JMenu mnuEdit = createMenuEdit();

    this.mnuCopyUpper = createMenuItem(
				"Wert in Gro\u00DFschreibweise kopieren" );
    mnuEdit.add( this.mnuCopyUpper );

    this.mnuCopyLower = createMenuItem(
				"Wert in Kleinschreibweise kopieren" );
    mnuEdit.add( this.mnuCopyLower );
    mnuEdit.addSeparator();

    this.mnuCompare = createMenuItem( "Wert mit Zwischenablage vergleichen" );
    mnuEdit.add( this.mnuCompare );

    JMenu mnuHelp       = createMenuHelp();
    this.mnuHelpContent = createMenuItem(
		"Hilfe zur Pr\u00FCsummen/Hashwertberechnung..." );
    mnuHelp.add( this.mnuHelpContent );

    setJMenuBar( GUIFactory.createMenuBar( mnuFile, mnuEdit, mnuHelp ) );


    // Popup-Menu
    this.popupMnu = GUIFactory.createPopupMenu();

    this.popupCopyUpper = createMenuItem(
				"Wert in Gro\u00DFschreibweise kopieren" );
    this.popupMnu.add( this.popupCopyUpper );

    this.popupCopyLower = createMenuItem(
				"Wert in Kleinschreibweise kopieren" );
    this.popupMnu.add( this.popupCopyLower );
    this.popupMnu.addSeparator();

    this.popupCompare = createMenuItem(
				"Wert mit Zwischenablage vergleichen" );
    this.popupMnu.add( this.popupCompare );


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

    this.labelAlgorithm = GUIFactory.createLabel( "Algorithmus:" );
    this.labelAlgorithm.setEnabled( false );
    add( this.labelAlgorithm, gbc );

    this.comboAlgorithm = GUIFactory.createComboBox(
				CksCalculator.getAvailableAlgorithms() );
    this.comboAlgorithm.setEditable( false );
    this.comboAlgorithm.setEnabled( false );
    gbc.gridx++;
    add( this.comboAlgorithm, gbc );

    this.btnAction = GUIFactory.createButton( BTN_TEXT_CALCULATE );
    this.btnAction.setEnabled( false );
    this.btnAction.addActionListener( this );
    gbc.gridx++;
    add( this.btnAction, gbc );

    FileTableModel.Column[] cols = {
				FileTableModel.Column.NAME,
				FileTableModel.Column.VALUE };
    this.tableModel = new FileTableModel( cols );

    this.table = GUIFactory.createTable( this.tableModel );
    this.table.addMouseListener( this );
    this.table.setAutoResizeMode( JTable.AUTO_RESIZE_OFF );
    this.table.setColumnSelectionAllowed( false );
    this.table.setRowSelectionAllowed( true );
    this.table.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );
    gbc.anchor    = GridBagConstraints.NORTHWEST;
    gbc.fill      = GridBagConstraints.BOTH;
    gbc.weightx   = 1.0;
    gbc.weighty   = 1.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.gridx     = 0;
    gbc.gridy++;
    add( GUIFactory.createScrollPane( this.table ), gbc );

    ListSelectionModel selModel = this.table.getSelectionModel();
    if( selModel != null ) {
      selModel.addListSelectionListener( this );
      this.mnuCopyUpper.setEnabled( false );
      this.mnuCopyLower.setEnabled( false );
      this.mnuCompare.setEnabled( false );
      this.popupCopyUpper.setEnabled( false );
      this.popupCopyLower.setEnabled( false );
      this.popupCompare.setEnabled( false );
    }
    EmuUtil.setTableColWidths( this.table, 200, 150 );


    // Fenstergroesse
    setResizable( true );
    if( !applySettings( Main.getProperties() ) ) {
      this.table.setPreferredScrollableViewportSize(
					new Dimension( 350, 200 ) );
      pack();
      setScreenCentered();
      this.table.setPreferredScrollableViewportSize( new Dimension( 1, 1 ) );
    }
  }


	/* --- private Methoden --- */

  private void calculationFinished()
  {
    this.thread = null;
    updFields();
  }


  private void copyToClipboard( String text )
  {
    try {
      if( text != null ) {
	Toolkit tk = EmuUtil.getToolkit( this );
	if( tk != null ) {
	  Clipboard clipboard = tk.getSystemClipboard();
	  if( clipboard != null ) {
	    StringSelection contents = new StringSelection( text );
	    clipboard.setContents( contents, contents );
	  }
	}
      }
    }
    catch( IllegalStateException ex ) {}
  }


  private void doCopyUpper()
  {
    String value = getSelectedValue();
    if( value != null )
      copyToClipboard( value.toUpperCase() );
  }


  private void doCopyLower()
  {
    String value = getSelectedValue();
    if( value != null )
      copyToClipboard( value.toLowerCase() );
  }


  private void doCompare()
  {
    String value = getSelectedValue();
    if( value != null ) {
      if( value.length() > 0 ) {
	String text = null;
	try {
	  Toolkit tk = EmuUtil.getToolkit( this );
	  if( tk != null ) {
	    Clipboard clipboard = tk.getSystemClipboard();
	    if( clipboard != null ) {
	      if( clipboard.isDataFlavorAvailable(
					DataFlavor.stringFlavor ) )
	      {
		Object o = clipboard.getData( DataFlavor.stringFlavor );
		if( o != null ) {
		  text = o.toString();
		}
	      }
	    }
	  }
	}
	catch( IllegalStateException ex1 ) {}
	catch( IOException ex2 ) {}
	catch( UnsupportedFlavorException ex3 ) {}
	if( text != null ) {
	  try {
	    text = text.replaceAll( "[ \t\r\n]", "" );
	  }
	  catch( PatternSyntaxException ex ) {}
	  if( text.length() < 1 ) {
	    text = null;
	  }
	}
	if( text != null ) {
	  if( value.equalsIgnoreCase( text ) ) {
	    JOptionPane.showMessageDialog(
			this,
			"Der ausgew\u00E4hlte Wert stimmt mit dem\n"
				+ "in der Zwischenablage stehenden Text"
				+ " \u00FCberein.",
			"\u00DCbereinstimmung",
			JOptionPane.INFORMATION_MESSAGE );
	  } else {
	    JOptionPane.showMessageDialog(
			this,
			"Der ausgew\u00E4hlte Wert stimmt mit dem\n"
				+ "in der Zwischenablage stehenden Text\n"
				+ "nicht \u00FCberein.",
			"Abweichung",
			JOptionPane.WARNING_MESSAGE );
	  }
	} else {
	  BaseDlg.showErrorDlg(
		this,
		"Die Zwischenablage enth\u00E4lt keinen Text.\n"
			+ "Kopieren Sie bitte den zu pr\u00FCfenden Wert\n"
			+ "in die Zwischenablage und\n"
			+ "rufen die Funktion noch einmal auf." );
	}
      }
    }
  }


  private void doCalculate()
  {
    synchronized( this.tableModel ) {
      if( this.thread != null ) {
	this.cancelled = true;
      } else {
	Object o = this.comboAlgorithm.getSelectedItem();
	if( o != null ) {
	  String algorithm = o.toString();
	  if( algorithm != null ) {
	    this.cks = null;
	    try {
	      this.cks          = new CksCalculator( algorithm );
	      this.algorithm    = algorithm;
	      this.cancelled    = false;
	      this.filesChanged = false;
	      this.thread       = new Thread(
					Main.getThreadGroup(),
					this,
					"JKCEMU Checksum Calculator" );
	      this.thread.start();
	      updFields();
	    }
	    catch( NoSuchAlgorithmException ex ) {
	      BaseDlg.showErrorDlg(
			this,
			"Der Algorithmus wird nicht unterst&uuml;tzt." );
	    }
	  }
	}
      }
    }
  }


  private void fireTableRowsUpdated( final int fromRow, final int toRow )
  {
    final FileTableModel tableModel = this.tableModel;
    EventQueue.invokeLater(
		new Runnable()
		{
		  @Override
		  public void run()
		  {
		    if( toRow < tableModel.getRowCount() )
		      tableModel.fireTableRowsUpdated( fromRow, toRow );
		  }
		} );
  }


  private String getSelectedValue()
  {
    String rv  = null;
    int    row = this.table.getSelectedRow();
    if( row >= 0 ) {
      FileEntry entry = this.tableModel.getRow( row );
      if( entry != null ) {
	if( !entry.isMarked() ) {
	  Object value = entry.getValue();
	  if( value != null ) {
	    rv = value.toString();
	  }
	}
      }
    }
    return rv;
  }


  private void updEditBtns()
  {
    boolean state = false;
    int     row   = this.table.getSelectedRow();
    if( row >= 0 ) {
      FileEntry entry = this.tableModel.getRow( row );
      if( entry != null ) {
	state = !entry.isMarked() && (entry.getValue() != null);
      }
    }
    this.mnuCopyUpper.setEnabled( state );
    this.mnuCopyLower.setEnabled( state );
    this.mnuCompare.setEnabled( state );
    this.popupCopyUpper.setEnabled( state );
    this.popupCopyLower.setEnabled( state );
    this.popupCompare.setEnabled( state );
  }


  private void updFields()
  {
    if( this.thread != null ) {
      this.labelAlgorithm.setEnabled( false );
      this.comboAlgorithm.setEnabled( false );
      this.btnAction.setText( EmuUtil.TEXT_CANCEL );
    } else {
      boolean state = (this.tableModel.getRowCount() > 0);
      this.labelAlgorithm.setEnabled( state );
      this.comboAlgorithm.setEnabled( state );
      this.btnAction.setText( BTN_TEXT_CALCULATE );
      this.btnAction.setEnabled( state );
    }
  }
}
