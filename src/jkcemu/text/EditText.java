/*
 * (c) 2008-2020 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Verwaltung eines im Editor befindlichen Textes
 */

package jkcemu.text;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.dnd.DropTarget;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PushbackInputStream;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.undo.UndoManager;
import jkcemu.Main;
import jkcemu.base.BaseDlg;
import jkcemu.base.EmuUtil;
import jkcemu.base.SourceUtil;
import jkcemu.base.UserCancelException;
import jkcemu.file.FileFormat;
import jkcemu.file.FileInfo;
import jkcemu.file.LoadData;
import jkcemu.emusys.AC1;
import jkcemu.emusys.BCS3;
import jkcemu.emusys.KC85;
import jkcemu.emusys.KramerMC;
import jkcemu.emusys.LC80;
import jkcemu.emusys.LLC1;
import jkcemu.emusys.Z1013;
import jkcemu.emusys.Z9001;
import jkcemu.file.FileUtil;
import jkcemu.programming.PrgOptions;
import jkcemu.programming.PrgSource;
import jkcemu.programming.basic.BasicOptions;
import z80emu.Z80MemView;


public class EditText implements
				CaretListener,
				DocumentListener,
				UndoableEditListener
{
  public static final String TEXT_WITH_BOM = " mit Byte-Order-Markierung";

  public static final String PROP_PROPERTIES_TYPE
				= "jkcemu.properties.type";
  public static final String PROP_PRG_SOURCE_FILE_NAME
				= "jkcemu.programming.source.file.name";
  public static final String VALUE_PROPERTIES_TYPE_PROJECT = "project";

  private static class TextProps
  {
    private String  encodingName;
    private String  encodingDesc;
    private String  lineEnd;
    private boolean hasBOM;
    private boolean charsLost;
    private int     eofByte;

    private TextProps( String encodingName, String encodingDesc )
    {
      this.encodingName = encodingName;
      this.encodingDesc = encodingDesc;
      this.lineEnd      = null;
      this.hasBOM       = false;
      this.charsLost    = false;
      this.eofByte      = -1;
    }
  };


  private boolean       used;
  private boolean       charsLostOnOpen;
  private boolean       dataChanged;
  private boolean       prjChanged;
  private boolean       saved;
  private boolean       askFileNameOnSave;
  private boolean       byteOrderMark;
  private boolean       trimLines;
  private int           eofByte;
  private PrgOptions    prgOptions;
  private String        fileName;
  private File          file;
  private File          prjFile;
  private CharConverter charConverter;
  private String        encodingName;
  private String        encodingDesc;
  private String        lineEnd;
  private String        textValue;
  private String        textName;
  private int           textNum;
  private JTextArea     textArea;
  private Component     tab;
  private DropTarget    dropTarget1;
  private DropTarget    dropTarget2;
  private UndoManager   undoMngr;
  private EditText      resultEditText;
  private TextEditFrm   textEditFrm;


  public EditText(
		TextEditFrm textEditFrm,
		Component   tab,
		JTextArea   textArea )
  {
    init( textEditFrm );
    this.textName = null;
    this.textNum  = this.textEditFrm.getNewTextNum();
    setComponents( tab, textArea );
  }


  public EditText(
		TextEditFrm   textEditFrm,
		File          file,
		byte[]        fileBytes,
		String        fileName,
		CharConverter charConverter,
		String        encodingName,
		String        encodingDesc,
		boolean       ignoreEofByte )
				throws IOException, UserCancelException
  {
    init( textEditFrm );
    loadFile(
	file,
	fileBytes,
	fileName,
	charConverter,
	encodingName,
	encodingDesc,
	ignoreEofByte );
  }


  public boolean canUndo()
  {
    return this.undoMngr.canUndo();
  }


  public void die()
  {
    disableDropTargets();
    if( this.textArea != null ) {
      this.textArea.removeCaretListener( this );
      Document doc = this.textArea.getDocument();
      if( doc != null ) {
	doc.removeDocumentListener( this );
	doc.removeUndoableEditListener( this );
      }
    }
    this.textEditFrm   = null;
    this.file          = null;
    this.fileName      = null;
    this.charConverter = null;
    this.encodingDesc  = null;
    this.encodingName  = null;
    this.lineEnd       = null;
    this.textName      = null;
    this.textValue     = null;
    this.textArea      = null;
    this.tab           = null;
    this.undoMngr.discardAllEdits();
  }


  public boolean getAskFileNameOnSave()
  {
    return this.askFileNameOnSave;
  }


  public int getCaretPosition()
  {
    return this.textArea != null ? this.textArea.getCaretPosition() : 0;
  }


  public CharConverter getCharConverter()
  {
    return this.charConverter;
  }


  public boolean getCharsLostOnOpen()
  {
    return this.charsLostOnOpen;
  }


  public String getEncodingDescription()
  {
    return this.encodingDesc;
  }


  public String getEncodingName()
  {
    return this.encodingName;
  }


  public int getEofByte()
  {
    return this.eofByte;
  }


  public File getFile()
  {
    return this.file;
  }


  public String getFileName()
  {
    return this.fileName;
  }


  public JScrollPane getJScrollPane()
  {
    JScrollPane sp = null;
    Component   c  = this.tab;
    while( (sp == null) && (c != null) ) {
      if( c instanceof JScrollPane ) {
	sp = (JScrollPane) c;
	break;
      }
      c = c.getParent();
    }
    return sp;
  }


  public JTextArea getJTextArea()
  {
    return this.textArea;
  }


  public String getLineEnd()
  {
    return this.lineEnd;
  }


  public String getName()
  {
    if( this.textName == null ) {
      this.textName = "Neuer Text";
      if( this.textNum > 1 ) {
	this.textName += String.format( "<%d>", this.textNum );
      }
    }
    return this.textName;
  }


  public PrgOptions getPrgOptions()
  {
    return this.prgOptions;
  }


  public File getProjectFile()
  {
    return this.prjFile;
  }


  public EditText getResultEditText()
  {
    return this.resultEditText;
  }


  public Component getRowHeader()
  {
    Component   c  = null;
    JScrollPane sp = getJScrollPane();
    if( sp != null ) {
      JViewport vp = sp.getRowHeader();
      if( vp != null ) {
	c = vp.getView();
      }
    }
    return c;
  }


  public Component getTab()
  {
    return this.tab;
  }


  public int getTabSize()
  {
    int rv = 8;
    if( this.textArea != null ) {
      rv = this.textArea.getTabSize();
      if( rv < 1 ) {
	rv = 8;
      }
    }
    return rv;
  }


  public String getText()
  {
    String text = null;
    if( this.textArea != null ) {
      text = this.textArea.getText();
    }
    else if( this.textValue != null ) {
      text = this.textValue;
    }
    return text != null ? text : "";
  }


  public TextEditFrm getTextEditFrm()
  {
    return this.textEditFrm;
  }


  public int getTextLength()
  {
    if( this.textArea != null ) {
      return this.textArea.getDocument().getLength();
    }
    if( this.textValue != null ) {
      return this.textValue.length();
    }
    return 0;
  }


  public boolean getTrimLines()
  {
    return this.trimLines;
  }


  public void gotoLine( int lineNum )
  {
    this.textEditFrm.setState( Frame.NORMAL );
    this.textEditFrm.toFront();
    this.textEditFrm.setSelectedTab( this.tab );
    this.textEditFrm.gotoLine( this.textArea, lineNum );
  }


  public boolean hasByteOrderMark()
  {
    return this.byteOrderMark;
  }


  public boolean hasDataChanged()
  {
    return this.dataChanged;
  }


  public boolean hasProjectChanged()
  {
    return this.prjChanged || (this.prgOptions == null);
  }


  public boolean hasRowHeader()
  {
    return (getRowHeader() != null);
  }


  public boolean hasText()
  {
    return (getTextLength() > 0);
  }


  public boolean isSameFile( File file )
  {
    return ((this.file != null) && (file != null)) ?
				FileUtil.equals( this.file, file )
				: false;
  }


  public boolean isSameText( PrgSource src )
  {
    boolean rv = false;
    if( src != null ) {
      String srcText = src.getOrgText();
      if( srcText != null ) {
	rv = (!srcText.isEmpty() && srcText.equals( getText() ));
      }
      if( !rv ) {
	rv = isSameFile( src.getFile() );
      }
    }
    return rv;
  }


  public boolean isSaved()
  {
    return this.saved;
  }


  public boolean isUsed()
  {
    return this.used;
  }


  public void loadFile(
		File          file,
		byte[]        fileBytes,
		String        fileName,
		CharConverter charConverter,
		String        encodingName,
		String        encodingDesc,
		boolean       ignoreEofByte )
			throws IOException, UserCancelException
  {
    try {
      TextProps textProps = new TextProps( encodingName, encodingDesc );
      boolean   filtered  = false;
      FileInfo  fileInfo  = null;
      String    text      = null;
      String    info      = null;
      if( (fileBytes == null) && (file != null) ) {
	fileBytes = FileUtil.readFile(
				file,
				true,
				Integer.MAX_VALUE );
      }
      if( fileBytes != null ) {
	if( (charConverter == null) && (encodingName == null) ) {

	  // Speicherabbilddatei?
	  fileInfo = FileInfo.analyzeFile( fileBytes, file );
	  if( fileInfo != null ) {
	    FileFormat fileFmt = fileInfo.getFileFormat();
	    if( fileFmt != null ) {
	      try {
		LoadData basicLoadData = null;
		if( fileFmt.equals( FileFormat.BIN ) ) {
		  info = "BCS3-BASIC-Programm";
		  text = BCS3.getBasicProgram( fileBytes );
		}
		else if( fileFmt.equals( FileFormat.BASIC_PRG ) ) {
		  basicLoadData = FileInfo.createLoadData(
							fileBytes,
							fileFmt );
		}
		else if( fileFmt.equals( FileFormat.HEADERSAVE ) ) {
		  LoadData loadData = null;
		  switch( fileInfo.getFileType() ) {
		    case 'A':
		      loadData = FileInfo.createLoadData(
							fileBytes,
							fileFmt );
		      if( loadData != null ) {
			info = "EDAS*4-Quelltext";
			text = SourceUtil.getEDAS4Text(
						loadData,
						fileInfo.getBegAddr() );
		      }
		      break;

		    case 'B':
		      basicLoadData = FileInfo.createLoadData(
							fileBytes,
							fileFmt );
		      break;

		    case 'b':
		      loadData = FileInfo.createLoadData(
							fileBytes,
							fileFmt );
		      if( loadData != null ) {
			switch( loadData.getBegAddr() ) {
			  case 0x1000:
			    info = "Z1013-TinyBASIC-Programm";
			    text = Z1013.getTinyBasicProgram( loadData );
			    break;

			  case 0x1400:
			    info = "LLC1-TinyBASIC-Programm";
			    text = LLC1.getBasicProgram( loadData );
			    break;

			  case 0x18C0:
			    info = "AC1-MiniBASIC-Programm";
			    text = AC1.getTinyBasicProgram( loadData );
			    break;
			}
		      }
		      break;

		    case 'I':
		    case 'T':
		      if( fileBytes.length > 32 ) {
			StringBuilder buf = new StringBuilder(
						fileBytes.length - 32 );
			boolean cr = false;
			for( int i = 32; i < fileBytes.length; i++ ) {
			  int b = fileBytes[ i ] & 0xFF;
			  if( b == '\r' ) {
			    cr = true;
			    buf.append( '\n' );
			  }
			  else if( b == '\n' ) {
			    if( cr ) {
			      cr = false;
			    } else {
			      buf.append( '\n' );
			    }
			  }
			  else if( b == 0x1E ) {
			    buf.append( '\n' );
			  }
			  else if( (b == '\t') || (b >= 0x20) ) {
			    buf.append( (char) b );
			  }
			}
			text = buf.toString();
			info = "Headersave-Textdatei";
		      }
		      break;
		  }
		}
		else if( fileFmt.equals( FileFormat.KCB )
			 || fileFmt.equals( FileFormat.KCB_BLKN )
			 || fileFmt.equals( FileFormat.KCB_BLKN_CKS )
			 || fileFmt.equals( FileFormat.KCTAP_BASIC_PRG )
			 || fileFmt.equals( FileFormat.KCBASIC_HEAD_PRG )
			 || fileFmt.equals( FileFormat.KCBASIC_HEAD_PRG_BLKN )
			 || fileFmt.equals(
				FileFormat.KCBASIC_HEAD_PRG_BLKN_CKS )
			 || fileFmt.equals( FileFormat.KCBASIC_PRG ) )
		{
		  LoadData loadData = FileInfo.createLoadData(
							fileBytes,
							fileFmt );
		  if( loadData != null ) {
		    text = getKCBasicProgram(
					loadData,
					fileInfo.getBegAddr() );
		    info = "KC-BASIC-Programm";
		  }
		}
		if( basicLoadData != null ) {
		  int addr = fileInfo.getBegAddr();
		  switch( addr ) {
		    case 0x03C0:
		    case 0x0400:
		    case 0x0401:
		      info = "KC-BASIC-Programm";
		      text = getKCBasicProgram( basicLoadData, 0x0401 );
		      break;

		    case 0x1001:
		      info = "KramerMC-BASIC-Programm";
		      text = KramerMC.getBasicProgram( basicLoadData );
		      break;

		    case 0x2400:
		      info = "LC-80ex-BASIC-Programm";
		      text = LC80.getBasicProgram( basicLoadData );
		      break;

		    case 0x2BC0:
		    case 0x2C00:
		    case 0x2C01:
		      info = "KC-BASIC-Programm";
		      text = getKCBasicProgram( basicLoadData, 0x2C01 );
		      break;

		    case 0x60F7:
		    case 0x6300:
		    case 0x6FB7:
		      /*
		       * Das SCCH-BASIC fuer den LLC2 ist bzgl.
		       * des BASIC-Dialekts und
		       * der Adressen des Quelltextes
		       * identisch zu der Version fuer den AC1.
		       * Aus diesem Grund gibt es hier keine
		       * spezielle Behandlung fuer LLC2-BASIC-Programme.
		       */
		      info = "AC1/LLC2 BASIC-Programm";
		      text = AC1.getBasicProgram(
						this.textEditFrm,
						basicLoadData );
		      break;
		  }
		}
	      }
	      catch( IOException ex ) {
		text = null;
	      }
	    }
	  }
	  if( text != null ) {
	    StringBuilder buf = new StringBuilder( 512 );
	    if( info != null ) {
	      buf.append( info );
	      if( !info.endsWith( ":" ) ) {
		buf.append( ':' );
	      }
	      buf.append( '\n' );
	    }
	    buf.append( "Die Datei ist keine reine Textdatei und kann\n"
			+ "deshalb auch nicht als solche ge\u00F6ffnet"
			+ " werden.\n"
			+ "Der in der Datei enthaltene Text wird aber\n"
			+ "extrahiert und als neue Textdatei"
			+ " ge\u00F6ffnet." );
	    BaseDlg.showInfoDlg( this.textEditFrm, buf.toString() );
	  }
	}
	if( text != null ) {
	  filtered      = true;
	  charConverter = null;
	} else {
	  if( fileBytes.length > 0 ) {
	    text = readText(
			fileBytes,
			charConverter,
			ignoreEofByte,
			textProps );
	    if( textProps.charsLost
		&& (charConverter == null)
		&& (encodingName == null) )
	    {
	      /*
	       * Wenn beim Einlesen der Datei mit dem Systemzeichensatz
	       * Zeichen nicht gemappt werden konnten,
	       * wird die Datei mit ISO-8859-1 (Latin1) eingelesen.
	       */
	      CharConverter cc2 = new CharConverter(
					CharConverter.Encoding.LATIN1 );
	      TextProps props2 = new TextProps(
					cc2.getEncodingName(),
					cc2.toString() );
	      String text2 = readText(
				fileBytes,
				cc2,
				ignoreEofByte,
				props2 );
	      if( (text2 != null) && !props2.charsLost ) {
		text          = text2;
	        charConverter = cc2;
		textProps     = props2;
	      }
	    }
	  }
	}
      }
      this.used          = true;
      this.byteOrderMark = textProps.hasBOM;
      this.charConverter = charConverter;
      this.encodingName  = textProps.encodingName;
      this.encodingDesc  = textProps.encodingDesc;
      this.eofByte       = textProps.eofByte;
      if( filtered ) {
	this.file     = null;
	this.fileName = null;
	this.textName = "Neuer Text";
	if( file != null ) {
	  this.textName += (" (Quelle: " + file.getName() + ")");
	}
	setDataUnchanged( false );
      } else {
	this.file     = file;
	this.fileName = fileName;
	if( file != null ) {
	  this.textName = file.getName();
	} else {
	  this.textName = fileName;
	}
	setDataUnchanged( true );
      }
      if( this.textArea != null ) {
	this.textArea.setText( text );
	this.textArea.setCaretPosition( 0 );
	this.textValue = null;
      } else {
	this.textValue = text;
      }
      this.undoMngr.discardAllEdits();
      this.textEditFrm.updUndoButtons();
      this.textEditFrm.updCaretButtons();
      this.textEditFrm.updTitle();

      this.charsLostOnOpen = textProps.charsLost;
      if( textProps.charsLost ) {
	StringBuilder buf = new StringBuilder( 512 );
	buf.append( "Die Datei enth\u00E4lt Bytes bzw. Bytefolgen,"
		+ " die sich nicht\n"
		+ "als Zeichen im" );
	if( (charConverter == null) && (encodingName == null) ) {
	  buf.append( " Systemzeichensatz" );
	} else {
	  buf.append( " dem ausgew\u00E4hlten Zeichensatz" );
	}
	buf.append( " abbilden lassen.\n"
		+ "Diese Bytes wurden ignoriert. Sie sollten evtl. versuchen,\n"
		+ "die Datei mit einem anderen Zeichensatz"
		+ " zu \u00F6ffnen\n"
		+ "(siehe Men\u00FCpunkt"
		+ " \'\u00D6ffnen mit Zeichensatz...\')." );
	final String    msg   = buf.toString();
	final Component owner = this.textEditFrm;
	EventQueue.invokeLater(
		new Runnable()
		{
		  @Override
		  public void run()
		  {
		    BaseDlg.showWarningDlg( owner, msg );
		  }
		} );
      }
    }
    catch( UnsupportedEncodingException ex ) {
      throw new IOException(
		encodingName + ": Der Zeichensatz wird auf dieser Plattform"
			+ " nicht unterst\u00FCtzt." );
    }
  }


  public void pasteText( String text )
  {
    if( (text != null) && (this.textArea != null) )
      this.textArea.replaceSelection( text );
  }


  public void removeRowHeader()
  {
    JScrollPane sp = getJScrollPane();
    if( sp != null ) {
      sp.setRowHeader( null );
    }
  }


  public void replaceText( String text )
  {
    boolean docChanged = false;
    if( this.textArea != null ) {
      Document doc = this.textArea.getDocument();
      if( doc != null ) {
	try {
	  int len = doc.getLength();
	  if( doc instanceof AbstractDocument ) {
	    ((AbstractDocument) doc).replace( 0, len, text, null );
	  } else {
	    if( len > 0 ) {
	      doc.remove( 0, len );
	    }
	    if( text != null ) {
	      doc.insertString( 0, text, null );
	    }
	  }
	  docChanged = true;
	}
	catch( BadLocationException ex ) {}
      }
      if( !docChanged ) {
	this.textArea.setText( text );
      }
      this.textArea.setCaretPosition( 0 );
    }
    else if( this.textValue != null ) {
      this.textValue = text;
    }
    if( !docChanged ) {
      this.undoMngr.discardAllEdits();
    }
    this.textEditFrm.updUndoButtons();
    this.textEditFrm.updCaretButtons();
  }


  public void saveFile(
		Component     owner,
		File          file,
		CharConverter charConverter,
		String        encodingName,
		String        encodingDesc,
		boolean       byteOrderMark,
		int           eofByte,
		boolean       trimLines,
		String        lineEnd ) throws IOException
  {
    boolean charsLost = false;
    String  text      = (this.textArea != null ?
				this.textArea.getText() : this.textValue);
    if( text == null ) {
      text = "";
    }

    // Zeilenende
    if( lineEnd == null ) {
      lineEnd = System.getProperty( "line.separator" );
    }
    byte[] lineEndBytes = null;
    if( lineEnd != null ) {
      lineEndBytes = lineEnd.getBytes();
      if( lineEndBytes != null ) {
	if( lineEndBytes.length < 1 ) {
	  lineEndBytes = null;
	}
      }
    }
    if( lineEnd == null ) {
      lineEndBytes = new byte[] { (byte) '\r', (byte) '\n' };
    }

    OutputStream outStream = null;
    try {
      outStream = FileUtil.createOptionalGZipOutputStream( file );

      BufferedWriter outWriter = null;
      if( charConverter == null ) {
	if( encodingName != null ) {
	  if( byteOrderMark ) {
	    if( encodingName.equalsIgnoreCase( "UTF-8" ) ) {
	      outStream.write( 0xEF );
	      outStream.write( 0xBB );
	      outStream.write( 0xBF );
	    } else if( encodingName.equalsIgnoreCase( "UTF-16BE" ) ) {
	      outStream.write( 0xFE );
	      outStream.write( 0xFF );
	    } else if( encodingName.equalsIgnoreCase( "UTF-16LE" ) ) {
	      outStream.write( 0xFF );
	      outStream.write( 0xFE );
	    } else {
	      /*
	       * Bei UTF-16 wird die Byte-Order-Markierung
	       * automatisch erzeugt und muss deshalb hier entfallen.
	       */
	      byteOrderMark = false;
	    }
	  }
	  outWriter = new BufferedWriter(
			new OutputStreamWriter( outStream, encodingName ) );
	} else {
	  byteOrderMark = false;
	  outWriter     = new BufferedWriter(
				new OutputStreamWriter( outStream ) );
	}
      }

      int len = text.length();
      int ch;

      if( trimLines ) {
	int spaceBegPos = -1;
	for( int i = 0; i < len; i++ ) {
	  ch = text.charAt( i );
	  switch( ch ) {
	    case '\n':
	      if( outWriter != null ) {
		writeLineEnd( outWriter, lineEnd );
	      } else {
		outStream.write( lineEndBytes );
	      }
	      spaceBegPos = -1;
	      break;

	    case '\t':
	    case '\u0020':
	      if( spaceBegPos < 0 ) {
		spaceBegPos = i;
	      }
	      break;

	    default:
	      // Pruefen, ob vor dem Zeichen Leerzeichen waren,
	      // die ausgegeben werden muessen
	      if( spaceBegPos >= 0 ) {
		for( int k = spaceBegPos; k < i; k++ ) {
		  char chSpace = text.charAt( k );
		  if( outWriter != null ) {
		    outWriter.write( chSpace );
		  } else {
		    outStream.write( (int) chSpace & 0xFF );
		  }
		}
		spaceBegPos = -1;
	      }
	      if( outWriter != null ) {
		outWriter.write( ch );
	      } else {
		if( !writeChar( outStream, charConverter, ch ) ) {
		  charsLost = true;
		}
	      }
	  }
	}

      } else {

	if( outWriter != null ) {
	  for( int i = 0; i < len; i++ ) {
	    ch = text.charAt( i );
	    if( ch == '\n' ) {
	      writeLineEnd( outWriter, lineEnd );
	    } else {
	      outWriter.write( ch );
	    }
	  }
	} else {
	  for( int i = 0; i < len; i++ ) {
	    ch = text.charAt( i );
	    if( ch == '\n' ) {
	      outStream.write( lineEndBytes );
	    } else {
	      if( !writeChar( outStream, charConverter, ch ) ) {
		charsLost = true;
	      }
	    }
	  }
	}
      }

      if( outWriter != null ) {
	if( eofByte >= 0 ) {
	  outWriter.write( eofByte );
	}
	outWriter.flush();
	outWriter.close();		// schliesst auch "outStream"
      } else {
	if( eofByte >= 0 ) {
	  outStream.write( eofByte );
	}
	outStream.close();
      }
      outStream = null;

      if( !isSameFile( file ) ) {
	setProjectChanged( true );
	this.prjFile = null;
      }

      this.file          = file;
      this.fileName      = fileName;
      this.charConverter = charConverter;
      this.encodingName  = encodingName;
      this.encodingDesc  = encodingDesc;
      this.byteOrderMark = byteOrderMark;
      this.eofByte       = eofByte;
      this.trimLines     = trimLines;
      this.lineEnd       = lineEnd;
      this.textName      = this.file.getName();
      setDataUnchanged( true );
      this.textEditFrm.updTitle();

      if( charsLost ) {
	BaseDlg.showWarningDlg(
		owner,
		"Der Text enth\u00E4lt Zeichen, die in dem gew\u00FCnschten\n"
			+ "Zeichensatz nicht existieren und somit auch"
			+ " nicht\n"
			+ "gespeichert werden k\u00F6nnen, d.h.,\n"
			+ "diese Zeichen fehlen in der gespeicherten"
			+ " Datei." );
      }
    }
    catch( UnsupportedEncodingException ex ) {
      throw new IOException(
		encodingDesc + ": Der Zeichensatz wird auf dieser Plattform"
			+ " nicht unterst\u00FCtzt." );
    }
    finally {
      EmuUtil.closeSilently( outStream );
    }
  }


  public boolean saveProject( Frame frame, boolean askFileName )
  {
    boolean rv = false;
    if( this.file != null ) {
      File prjFile = this.prjFile;
      if( (prjFile == null) || askFileName ) {
	File preSelection = prjFile;
	if( preSelection == null ) {
	  File   srcParent = this.file.getParentFile();
	  String srcName   = this.file.getName();
	  if( srcName != null ) {
	    String prjName = srcName;
	    int    pos     = srcName.lastIndexOf( '.' );
	    if( (pos >= 0) && (pos < srcName.length() ) ) {
	      prjName = srcName.substring( 0, pos );
	    }
	    prjName += ".prj";
	    if( srcParent != null ) {
	      preSelection = new File( srcParent, prjName );
	    } else {
	      preSelection = new File( prjName );
	    }
	  }
	}
	prjFile = FileUtil.showFileSaveDlg(
				frame,
				"Projekt speichern",
				preSelection,
				FileUtil.getProjectFileFilter() );
      }
      if( prjFile != null ) {
	OutputStream out = null;
	try {
	  out = new FileOutputStream( prjFile );

	  Properties props = new Properties();
	  props.setProperty(
			PROP_PROPERTIES_TYPE,
			VALUE_PROPERTIES_TYPE_PROJECT );

	  // Pfad relativ zur Projektdatei
	  String filename   = this.file.getPath();
	  File   prjDirFile = prjFile.getParentFile();
	  if( prjDirFile != null ) {
	    String prjDir = prjDirFile.getPath();
	    if( !prjDir.endsWith( File.separator ) ) {
	      prjDir += File.separator;
	    }
	    int prjDirLen = prjDir.length();
	    if( filename.startsWith( prjDir )
		&& (filename.length() > prjDirLen) )
	    {
	      filename = filename.substring( prjDirLen );
	    }
	  }
	  props.setProperty( PROP_PRG_SOURCE_FILE_NAME, filename );

	  if( this.prgOptions == null ) {
	    this.prgOptions = new BasicOptions();
	  }
	  this.prgOptions.putOptionsTo( props );
	  props.storeToXML( out, "Programming Options" );
	  out.close();
	  out = null;
	  rv  = true;
	  /*
	   * Projekt erst auf "geaendert" und anschliessend
	   * auf "nicht geaendert" setzen,
	   * damit garantiert auch das Fenster informiert wird.
	   */
	  this.prjFile    = prjFile;
	  this.prjChanged = true;
	  setProjectChanged( false );
	  Main.setLastFile( prjFile, Main.FILE_GROUP_PROJECT );
	}
	catch( IOException ex ) {
	  BaseDlg.showErrorDlg(
		frame,
		"Speichern des Projektes fehlgeschlagen:\n"
			+ ex.getMessage() );
	}
	finally {
	  EmuUtil.closeSilently( out );
	}
      }
    } else {
      BaseDlg.showErrorDlg(
		frame,
		"Das Projekt kann nicht gespeichert werden,\n"
			+ "da kein Dateiname f\u00FCr den Quelltext"
			+ " bekannt ist.\n"
			+ "Speichern Sie bitte zuerst den Quelltext\n"
			+ "und dann das Projekt." );
    }
    return rv;
  }



  public void setAskFileNameOnSave( boolean state )
  {
    this.askFileNameOnSave = state;
  }


  public void setCaretPosition( int pos )
  {
    if( this.textArea != null ) {
      try {
	this.textArea.setCaretPosition( pos );
      }
      catch( IllegalArgumentException ex ) {}
    }
  }


  public void setComponents( Component tab, JTextArea textArea )
  {
    this.tab      = tab;
    this.textArea = textArea;

    // Text anzeigen
    if( this.textArea != null ) {
      this.textArea.setText( this.textValue );
      this.textArea.setCaretPosition( 0 );
      this.textArea.addCaretListener( this );
      this.textValue = null;

      // unbedingt hinter Text anzeigen
      Document doc = this.textArea.getDocument();
      if( doc != null ) {
	doc.addDocumentListener( this );
	doc.addUndoableEditListener( this );
      }
    }

    // Komponenten als Drop-Ziele aktivieren
    disableDropTargets();
    if( tab != null ) {
      this.dropTarget1 = new DropTarget( tab, this.textEditFrm );
      this.dropTarget1.setActive( true );
    }
    if( textArea != null ) {
      this.dropTarget2 = new DropTarget( textArea, this.textEditFrm );
      this.dropTarget2.setActive( true );
    }
  }


  public void setDataChanged()
  {
    boolean wasChanged = this.dataChanged;
    this.dataChanged   = true;
    this.saved         = false;
    if( this.dataChanged != wasChanged ) {
      this.textEditFrm.dataChangedStateChanged( this );
    }
  }


  public void setPrgOptions( PrgOptions options )
  {
    boolean prjChanged = true;
    if( (this.prgOptions != null) && (options != null) ) {
      setProjectChanged( !this.prgOptions.sameOptions( options ) );
    } else {
      if( ((this.prgOptions != null) && (options == null))
	  || ((this.prgOptions == null) && (options != null)) )
      {
	setProjectChanged( true );
      }
    }
    this.prgOptions = options;
  }


  public void setProject( File file, Properties props )
  {
    this.prgOptions = PrgOptions.getPrgOptions( props );
    this.prjFile    = file;
    this.prjChanged = false;
  }


  public void setProjectChanged( boolean state )
  {
    boolean wasChanged = this.prjChanged;
    this.prjChanged    = state;
    if( this.prjChanged != wasChanged ) {
      this.textEditFrm.dataChangedStateChanged( this );
    }
  }


  public void setText( String text )
  {
    if( this.textArea != null ) {
      this.textArea.setText( text );
      this.textArea.setCaretPosition( 0 );
    }
    else if( this.textValue != null ) {
      this.textValue = text;
    }
    this.undoMngr.discardAllEdits();
    this.textEditFrm.updUndoButtons();
    this.textEditFrm.updCaretButtons();
    this.textEditFrm.updTitle();
    setDataUnchanged( false );
  }


  public void setResultEditText( EditText editText )
  {
    this.resultEditText = editText;
  }


  public void undo()
  {
    if( this.undoMngr.canUndo() ) {
      this.undoMngr.undo();
    }
    this.textEditFrm.updUndoButtons();
  }


	/* --- CaretListener --- */

  @Override 
  public void caretUpdate( CaretEvent e )
  {
    this.textEditFrm.caretPositionChanged();
  }


	/* --- DocumentListener --- */
 
  @Override 
  public void changedUpdate( DocumentEvent e )
  {
    docChanged( e );
    this.used = true;
  }


  @Override
  public void insertUpdate( DocumentEvent e )
  {
    docChanged( e );
    this.used = true;
  }


  @Override 
  public void removeUpdate( DocumentEvent e )
  {
    docChanged( e );
    this.used = true;
  }


	/* --- UndoableEditListener --- */

  @Override 
  public void undoableEditHappened( UndoableEditEvent e )
  {
    this.undoMngr.undoableEditHappened( e );
    this.textEditFrm.updUndoButtons();
  }


	/* --- private Methoden --- */

  private void init( TextEditFrm textEditFrm )
  {
    this.textEditFrm       = textEditFrm;
    this.undoMngr          = new UndoManager();
    this.prgOptions        = null;
    this.file              = null;
    this.fileName          = null;
    this.charConverter     = null;
    this.encodingName      = null;
    this.encodingDesc      = null;
    this.lineEnd           = null;
    this.resultEditText    = null;
    this.textName          = null;
    this.textValue         = null;
    this.textArea          = null;
    this.tab               = null;
    this.dropTarget1       = null;
    this.dropTarget2       = null;
    this.used              = false;
    this.charsLostOnOpen   = false;
    this.dataChanged       = false;
    this.prjChanged        = false;
    this.saved             = true;
    this.askFileNameOnSave = false;
    this.byteOrderMark     = false;
    this.trimLines         = false;
    this.eofByte           = -1;
  }


  private void disableDropTargets()
  {
    DropTarget dt = this.dropTarget1;
    if( dt != null ) {
      this.dropTarget1 = null;
      dt.setActive( false );
    }
    dt = this.dropTarget2;
    if( dt != null ) {
      this.dropTarget2 = null;
      dt.setActive( false );
    }
  }


  private void docChanged( DocumentEvent e )
  {
    if( this.textArea != null ) {
      if( e.getDocument() == this.textArea.getDocument() ) {
	setDataChanged();
	this.textEditFrm.updTextButtons();
      }
    }
  }


  private String getKCBasicProgram(
			LoadData loadData,
			int      begAddr ) throws UserCancelException
  {
    String rv = null;

    String kc85Basic =  SourceUtil.getBasicProgram(
						loadData,
						begAddr,
						KC85.basicTokens );

    String z9001Basic = SourceUtil.getBasicProgram(
						loadData,
						begAddr,
						Z9001.basicTokens );

    String z1013Basic = SourceUtil.getBasicProgram(
						loadData,
						begAddr,
						Z1013.basicTokens );

    if( (kc85Basic != null)
	&& (z1013Basic != null)
	&& (z9001Basic != null) )
    {
      if( kc85Basic.equals( z1013Basic ) && kc85Basic.equals( z9001Basic ) ) {
	rv         = kc85Basic;
	kc85Basic  = null;
	z1013Basic = null;
	z9001Basic = null;
      }
    }
    if( rv == null ) {
      if( (kc85Basic != null)
	  || (z1013Basic != null)
	  || (z9001Basic != null) )
      {
	java.util.List<String> optionTexts = new ArrayList<>( 3 );
	Map<String,String>     basicTexts  = new HashMap<>();
	if( kc85Basic != null ) {
	  String optionText = "KC85/2...5";
	  optionTexts.add( optionText );
	  basicTexts.put( optionText, kc85Basic );
	}
	if( z9001Basic != null ) {
	  String optionText = "KC87, Z9001";
	  optionTexts.add( optionText );
	  basicTexts.put( optionText, z9001Basic );
	}
	if( z1013Basic != null ) {
	  String optionText = "Z1013";
	  optionTexts.add( optionText );
	  basicTexts.put( optionText, z1013Basic );
	}
	int n = optionTexts.size();
	if( n == 1 ) {
	  String optionText = optionTexts.get( 0 );
	  if( optionText != null ) {
	    rv = basicTexts.get( optionText );
	  }
	}
	else if( n > 1 ) {
	  try {
	    optionTexts.add( EmuUtil.TEXT_CANCEL );
	    String[] options = optionTexts.toArray( new String[ n + 1 ] );
	    if( options != null ) {
	      JOptionPane pane = new JOptionPane(
		"Das KC-BASIC-Programm enth\u00E4lt Tokens,"
			+ " die auf den einzelnen\n"
			+ "Systemen unterschiedliche Anweisungen"
			+ " repr\u00E4sentieren.\n"
			+ "Auf welchem System wurde das BASIC-Programm"
			+ " erstellt?",
		JOptionPane.QUESTION_MESSAGE );
	      pane.setOptions( options );
	      pane.setWantsInput( false );
	      pane.createDialog(
			this.textEditFrm,
			"BASIC-Version" ).setVisible( true );
	      Object value = pane.getValue();
	      if( value != null ) {
		rv = basicTexts.get( value );
	      }
	      if( rv == null ) {
		throw new UserCancelException();
	      }
	    }
	  }
	  catch( ArrayStoreException ex ) {}
	}
      }
    }
    return rv;
  }


  private static int readChar(
			Reader        reader,
			InputStream   inStream,
			boolean       ignoreEofByte,
			CharConverter charConverter ) throws IOException
  {
    int ch = -1;
    if( reader != null ) {
      ch = reader.read();
    } else {
      ch = inStream.read();
      if( (ch != -1)
	     && (ch != '\n') && (ch != '\r')
	     && (ch != '\f') && (ch != '\u001E')
	     && (ignoreEofByte || (ch != 0x1A))
	     && (charConverter != null) )
      {
	ch = charConverter.toUnicode( ch );
      }
    }
    return ch;
  }


  private static String readText(
				byte[]        fileBytes,
				CharConverter charConverter,
				boolean       ignoreEofByte,
				TextProps     textProps ) throws IOException
  {
    StringBuilder       textBuf  = new StringBuilder( fileBytes.length );
    PushbackReader      reader   = null;
    PushbackInputStream inStream = null;
    boolean             has1E    = false;
    boolean             hasCR    = false;
    boolean             hasNL    = false;
    try {

      // Byte-Order-Markierung pruefen
      String bomEnc = null;
      int    bomLen = 0;
      if( fileBytes.length >= 2 ) {
	int b0 = (int) fileBytes[ 0 ] & 0xFF;
	int b1 = (int) fileBytes[ 1 ] & 0xFF;
	if( (b0 == 0xFE) && (b1 == 0xFF) ) {
	  bomEnc = "UTF-16BE";
	  bomLen = 2;
	} else if( (b0 == 0xFF) && (b1 == 0xFE) ) {
	  bomEnc = "UTF-16LE";
	  bomLen = 2;
	} else if( (b0 == 0xEF) && (b1 == 0xBB) ) {
	  if( fileBytes.length >= 3 ) {
	    if( ((int) fileBytes[ 2 ] & 0xFF) == 0xBF ) {
	      bomEnc = "UTF-8";
	      bomLen = 3;
	    }
	  }
	}
      }

      /*
       * Wenn eine Byte-Order-Markierung existiert,
       * dann wird daraus das Encoding ausgewertet.
       * Wurde allerdings ein Encoding uebergeben,
       * dass nicht dem der Byte-Order-Markierung entspricht,
       * dann hat das uebergebene die hoehere Prioritaet, d.h.,
       * die Bytes der Byte-Order-Markierung werden als Text gewertet.
       */
      if( bomEnc != null ) {
	if( (charConverter == null) && (textProps.encodingName == null) ) {
	  textProps.encodingName = bomEnc;
	  textProps.encodingDesc = bomEnc + TEXT_WITH_BOM;
	  textProps.hasBOM       = true;
	} else {
	  if( textProps.encodingName != null ) {
	    if( textProps.encodingName.equals( bomEnc ) ) {
	      textProps.encodingDesc = bomEnc + TEXT_WITH_BOM;
	      textProps.hasBOM       = true;
	    }
	  }
	}
      }

      // Eingabestreams mit evtl. Zeichensatzumwandlung oeffnen
      if( !textProps.hasBOM ) {
	bomLen = 0;
      }
      inStream = new PushbackInputStream(
			new ByteArrayInputStream(
				fileBytes,
				bomLen,
				fileBytes.length - bomLen ) );
      if( charConverter == null ) {
	if( textProps.encodingName != null ) {
	  reader = new PushbackReader(
			new InputStreamReader(
					inStream,
					textProps.encodingName ) );
	} else {
	  reader = new PushbackReader(
			new InputStreamReader( inStream ) );
	}
      }

      // erste Zeile bis zum Zeilenende lesen
      int ch = readChar( reader, inStream, ignoreEofByte, charConverter );
      while( (ch != -1)
	     && (ch != '\n') && (ch != '\r')
	     && (ch != '\f') && (ch != '\u001E') )
      {
	if( !ignoreEofByte && (ch == 0x1A) ) {
	  textProps.eofByte = ch;
	  ch                = -1;
	  break;
	}
	if( ch == CharConverter.REPLACEMENT_CHAR ) {
	  textProps.charsLost = true;
	} else {
	  textBuf.append( (char) ch );
	}
	ch = readChar( reader, inStream, ignoreEofByte, charConverter );
      }

      // Zeilenende pruefen
      if( ch == '\r' ) {

	// Zeilenende ist entweder CR oder CRLF
	hasCR = true;
	textBuf.append( '\n' );

	// kommt noch ein LF?
	ch = readChar( reader, inStream, ignoreEofByte, charConverter );
	if( ch == CharConverter.REPLACEMENT_CHAR ) {
	  textProps.charsLost = true;
	}
	else if( ch == '\n' ) {
	  // Zeilenende ist CRLF
	  hasNL = true;
	}
	else if( ch != -1 ) {
	  // Zeilenende ist nur CR
	  // gelesenes Zeichen zurueck
	  if( reader != null ) {
	    reader.unread( ch );
	  } else {
	    inStream.unread( ch );
	  }
	}
      }
      else if( ch == '\n' ) {
	textBuf.append( '\n' );
	hasNL = true;
      }
      else if( ch == '\u001E' ) {
	textBuf.append( '\n' );
	has1E = true;
      }

      // restliche Datei einlesen, aber nur,
      // wenn das zuletzt gelesene Zeichen nicht EOF war
      if( ch != -1 ) {
	boolean wasCR = false;
	ch = readChar( reader, inStream, ignoreEofByte, charConverter );
	while( ch >= 0 ) {
	  if( !ignoreEofByte && (ch == 0x1A) ) {
	    textProps.eofByte = ch;
	    break;
	  }
	  if( ch == CharConverter.REPLACEMENT_CHAR ) {
	    textProps.charsLost = true;
	  }
	  else if( ch == '\r' ) {
	    textBuf.append( '\n' );
	    wasCR = true;
	  } else {
	    if( ch == '\n' ) {
	      if( !wasCR ) {
		textBuf.append( (char) ch );
	      }
	    }
	    else if( ch == '\u001E' ) {
	      textBuf.append( '\n' );
	    }
	    // Nullbytes und Textendezeichen herausfiltern
	    else if( (ch != 0)
		     && (ch != '\u0003')
		     && (ch != '\u0004') )
	    {
	      textBuf.append( (char) ch );
	    }
	    wasCR = false;
	  }
	  ch = readChar( reader, inStream, ignoreEofByte, charConverter );
	}
      }
      if( reader != null ) {
	reader.close();
	reader = null;
      } else {
	inStream.close();
      }
      inStream = null;

      // alles OK -> Text und Werte uebernehmen
      if( hasCR ) {
	if( hasNL ) {
	  textProps.lineEnd = "\r\n";
	} else {
	  textProps.lineEnd = "\r";
	}
      } else {
	if( hasNL ) {
	  textProps.lineEnd = "\n";
	} else if( has1E ) {
	  textProps.lineEnd = "\u001E";
	} else {
	  textProps.lineEnd = null;
	}
      }
    }
    finally {
      EmuUtil.closeSilently( reader );
      EmuUtil.closeSilently( inStream );
    }
    return textBuf.toString();
  }


  private void setDataUnchanged( final boolean saved )
  {
    final EditText editText  = this;
    editText.dataChanged     = false;
    editText.saved           = saved;
    editText.charsLostOnOpen = false;
    EventQueue.invokeLater(
	new Runnable()
	{
	  @Override
	  public void run()
	  {
	    editText.dataChanged = false;
	    editText.saved       = saved;
	    if( editText.textEditFrm != null ) {
	      editText.textEditFrm.dataChangedStateChanged( editText );
	    }
	  }
	} );
  }


  private boolean writeChar(
			OutputStream  out,
			CharConverter charConverter,
			int           ch ) throws IOException
  {
    boolean rv = false;
    if( (ch > 0) && (charConverter != null) ) {
      ch = charConverter.toCharsetByte( (char) ch );
    }
    if( (ch > 0) && (ch <= 0xFF) ) {
      out.write( ch );
      rv = true;
    }
    return rv;
  }


  private void writeLineEnd(
			BufferedWriter outWriter,
			String         lineEnd ) throws IOException
  {
    if( lineEnd != null ) {
      outWriter.write( lineEnd );
    } else {
      outWriter.newLine();
    }
  }
}
